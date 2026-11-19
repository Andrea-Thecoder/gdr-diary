package it.gdr.diary.services;

import io.ebean.Database;
import io.ebean.Transaction;
import io.ebean.TxScope;
import io.ebean.annotation.TxIsolation;
import it.gdr.diary.config.AppUserConfig;
import it.gdr.diary.model.AppUser;
import it.gdr.diary.model.CharacterBackground;
import it.gdr.diary.model.CharacterSheet;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.model.Payment;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.security.RecentAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class UserExportService {

    static final String FORMAT = "diario-gdr-export";
    static final int FORMAT_VERSION = 1;
    static final String DATA_FILE = "dati.json";
    static final String README_FILE = "LEGGIMI.txt";
    private static final int NAME_MAX_LENGTH = 40;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/webp", "webp", "application/pdf", "pdf");
    private static final String README = """
            Diario GDR - copia dei tuoi dati
            ================================

            dati.json contiene, in formato JSON:
            - account: email, piano premium, impostazioni e immagine del profilo;
            - characters: i personaggi e i master, con la storia (background), le schede e i diari con tutte le pagine;
            - payments: i pagamenti registrati sull'account.

            Il testo della storia e delle pagine è nel formato JSON dell'editor (ProseMirror/Tiptap):
            ogni paragrafo è un nodo "paragraph" e il testo sta nei nodi "text".

            I campi che finiscono con "File" (avatarFile, coverFile, file) indicano il percorso del file
            in questo archivio: le immagini stanno accanto al personaggio o al diario a cui appartengono,
            le schede in PDF nella cartella "schede" del personaggio.

            I feedback inviati dal modulo pubblico non sono legati all'account e non sono in questa copia.
            """;

    private final Database database;
    private final JwtInspector jwtInspector;
    private final RecentAuthentication recentAuthentication;
    private final AppUserConfig config;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UserExport getUserExport() {
        recentAuthentication.require(config.getExportMaxAuthAge());
        String subject = jwtInspector.getSubject();
        Instant now = clock.instant();
        try (Transaction transaction = database.beginTransaction(
                TxScope.required().setReadOnly(true).setIsolation(TxIsolation.REPEATABLE_READ))) {
            AppUser user = getUserExportAccount(subject);
            List<UserExport.File> files = new ArrayList<>();
            String avatarFile = addFile(files, "profilo/avatar", user.getAvatarDocument());
            UserExport.Account account = new UserExport.Account(user.getEmail(), user.isPremium(), user.getSettings(), avatarFile);
            List<UserExport.Character> characters = exportCharacters(subject, files);
            List<UserExport.Payment> payments = exportPayments(subject);
            transaction.commit();
            UserExport.Data data = new UserExport.Data(FORMAT, FORMAT_VERSION, now, account, characters, payments);
            return new UserExport("diario-gdr-" + now.atZone(ZoneOffset.UTC).toLocalDate() + ".zip", data, List.copyOf(files));
        }
    }

    public void writeUserExport(UserExport export, OutputStream out) {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(DATA_FILE));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export.data()));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(README_FILE));
            zip.write(README.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (UserExport.File file : export.files()) {
                zip.putNextEntry(new ZipEntry(file.path()));
                documentService.copyDocumentContent(file.document(), zip);
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the export of the user data", e);
        }
    }

    private List<UserExport.Character> exportCharacters(String subject, List<UserExport.File> files) {
        List<GameCharacter> characters = database.find(GameCharacter.class)
                .setLabel("exportCharacters")
                .fetch("avatarDocument")
                .where().eq("owner.keycloakId", subject)
                .orderBy("name asc, id asc")
                .findList();
        Map<UUID, Map<String, Object>> backgrounds = database.find(CharacterBackground.class)
                .setLabel("exportBackgrounds")
                .where().eq("character.owner.keycloakId", subject)
                .findList().stream()
                .collect(Collectors.toMap(background -> background.getCharacter().getId(), CharacterBackground::getContent));
        Map<UUID, List<CharacterSheet>> sheets = database.find(CharacterSheet.class)
                .setLabel("exportSheets")
                .fetch("document")
                .where().eq("character.owner.keycloakId", subject)
                .orderBy("title asc, id asc")
                .findList().stream()
                .collect(Collectors.groupingBy(sheet -> sheet.getCharacter().getId()));
        Map<UUID, List<Diary>> diaries = database.find(Diary.class)
                .setLabel("exportDiaries")
                .fetch("coverDocument")
                .where().eq("character.owner.keycloakId", subject)
                .orderBy("title asc, id asc")
                .findList().stream()
                .collect(Collectors.groupingBy(diary -> diary.getCharacter().getId()));
        Map<UUID, List<DiaryPage>> pages = database.find(DiaryPage.class)
                .setLabel("exportPages")
                .where().eq("diary.character.owner.keycloakId", subject)
                .orderBy("pageNumber asc")
                .findList().stream()
                .collect(Collectors.groupingBy(page -> page.getDiary().getId()));
        List<UserExport.Character> result = new ArrayList<>();
        for (int index = 0; index < characters.size(); index++) {
            GameCharacter character = characters.get(index);
            String folder = "personaggi/" + numbered(index, character.getName(), "personaggio");
            result.add(new UserExport.Character(
                    character.getName(),
                    character.getKind(),
                    character.getCampaignName(),
                    character.getBackgroundColor(),
                    addFile(files, folder + "/avatar", character.getAvatarDocument()),
                    backgrounds.get(character.getId()),
                    exportSheets(sheets.getOrDefault(character.getId(), List.of()), folder, files),
                    exportDiaries(diaries.getOrDefault(character.getId(), List.of()), pages, folder, files)));
        }
        return result;
    }

    private List<UserExport.Sheet> exportSheets(List<CharacterSheet> sheets, String folder, List<UserExport.File> files) {
        List<UserExport.Sheet> result = new ArrayList<>();
        for (int index = 0; index < sheets.size(); index++) {
            CharacterSheet sheet = sheets.get(index);
            String file = addFile(files, folder + "/schede/" + numbered(index, sheet.getTitle(), "scheda"), sheet.getDocument());
            result.add(new UserExport.Sheet(sheet.getTitle(), sheet.getGameSystem(), sheet.getDocument().getName(), file));
        }
        return result;
    }

    private List<UserExport.Diary> exportDiaries(List<Diary> diaries, Map<UUID, List<DiaryPage>> pages, String folder,
                                                 List<UserExport.File> files) {
        List<UserExport.Diary> result = new ArrayList<>();
        for (int index = 0; index < diaries.size(); index++) {
            Diary diary = diaries.get(index);
            String diaryFolder = folder + "/diari/" + numbered(index, diary.getTitle(), "diario");
            List<UserExport.Page> diaryPages = pages.getOrDefault(diary.getId(), List.of()).stream()
                    .map(page -> new UserExport.Page(page.getPageNumber(), page.getContent()))
                    .toList();
            result.add(new UserExport.Diary(diary.getTitle(), diary.getDescription(), diary.getCoverColor(), diary.getFont(),
                    diary.getFontSize(), addFile(files, diaryFolder + "/copertina", diary.getCoverDocument()), diaryPages));
        }
        return result;
    }

    private List<UserExport.Payment> exportPayments(String subject) {
        return database.find(Payment.class)
                .setLabel("exportPayments")
                .where().eq("appUser.keycloakId", subject)
                .orderBy("paidAt desc nulls last, id asc")
                .findList().stream()
                .map(payment -> new UserExport.Payment(payment.getProvider(), payment.getProviderPaymentId(),
                        payment.getStatus(), payment.getAmountCents(), payment.getCurrency(), payment.getDescription(),
                        payment.getPaidAt(), payment.getCustomerEmail(), payment.getCustomerName(),
                        payment.getCustomerTaxCode(), payment.getCustomerCountry(), payment.getInvoiceNumber(),
                        payment.getReceiptUrl()))
                .toList();
    }

    private AppUser getUserExportAccount(String subject) {
        AppUser user = database.find(AppUser.class)
                .setLabel("getUserExportAccount")
                .fetch("avatarDocument")
                .where().eq("keycloakId", subject)
                .findOne();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private static String addFile(List<UserExport.File> files, String pathWithoutExtension, Document document) {
        if (document == null) {
            return null;
        }
        String path = pathWithoutExtension + "." + EXTENSIONS.getOrDefault(document.getMimeType(), "bin");
        files.add(new UserExport.File(path, document));
        return path;
    }

    private static String numbered(int index, String name, String fallback) {
        return "%02d-%s".formatted(index + 1, FileNames.slug(name, NAME_MAX_LENGTH, fallback));
    }
}
