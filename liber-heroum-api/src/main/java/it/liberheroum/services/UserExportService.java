package it.liberheroum.services;

import io.ebean.Database;
import io.ebean.Transaction;
import io.ebean.TxScope;
import io.ebean.annotation.TxIsolation;
import it.liberheroum.config.AppUserConfig;
import it.liberheroum.model.AppUser;
import it.liberheroum.model.CharacterBackground;
import it.liberheroum.model.CharacterSheet;
import it.liberheroum.model.Diary;
import it.liberheroum.model.DiaryPage;
import it.liberheroum.model.Document;
import it.liberheroum.model.GameCharacter;
import it.liberheroum.model.Payment;
import it.liberheroum.security.JwtInspector;
import it.liberheroum.security.RecentAuthentication;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class UserExportService {

    static final String FORMAT = "liber-heroum-export";
    static final int FORMAT_VERSION = 2;
    static final String README_FILE = "LEGGIMI.txt";
    static final String PAYMENTS_FILE = "pagamenti.csv";
    static final String DATA_FILE = "per-altri-programmi/dati.json";
    private static final int NAME_MAX_LENGTH = 40;
    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/webp", "webp", "application/pdf", "pdf");

    private final Database database;
    private final JwtInspector jwtInspector;
    private final RecentAuthentication recentAuthentication;
    private final AppUserConfig config;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RichTextPdfRenderer renderer;

    public UserExport getUserExport() {
        recentAuthentication.require(config.getExportMaxAuthAge());
        String subject = jwtInspector.getSubject();
        Instant now = clock.instant();
        try (Transaction transaction = database.beginTransaction(
                TxScope.required().setReadOnly(true).setIsolation(TxIsolation.REPEATABLE_READ))) {
            AppUser user = getUserExportAccount(subject);
            List<UserExport.Entry> files = new ArrayList<>();
            addStoredFile(files, "profilo/avatar", user.getAvatarDocument());
            List<UserExport.Character> characters = exportCharacters(subject, files);
            List<Payment> payments = findPayments(subject);
            transaction.commit();
            UserExport.Data data = new UserExport.Data(FORMAT, FORMAT_VERSION, now, new UserExport.Account(user.getEmail()),
                    characters, payments.stream().map(UserExportService::toPayment).toList());
            List<UserExport.Entry> entries = new ArrayList<>();
            entries.add(new UserExport.GeneratedFile(README_FILE, () -> readme(user.getEmail(), now).getBytes(StandardCharsets.UTF_8)));
            entries.addAll(files);
            entries.add(new UserExport.GeneratedFile(PAYMENTS_FILE, () -> PaymentsCsv.of(payments).getBytes(StandardCharsets.UTF_8)));
            entries.add(new UserExport.GeneratedFile(DATA_FILE, () -> objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data)));
            return new UserExport("liber-heroum-" + now.atZone(ZoneOffset.UTC).toLocalDate() + ".zip", List.copyOf(entries));
        }
    }

    public void writeUserExport(UserExport export, OutputStream out) {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (UserExport.Entry entry : export.entries()) {
                zip.putNextEntry(new ZipEntry(entry.path()));
                switch (entry) {
                    case UserExport.StoredFile stored -> documentService.copyDocumentContent(stored.document(), zip);
                    case UserExport.GeneratedFile generated -> zip.write(generated.content().get());
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the export of the user data", e);
        }
    }

    private List<UserExport.Character> exportCharacters(String subject, List<UserExport.Entry> files) {
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
            String avatarFile = addStoredFile(files, folder + "/avatar", character.getAvatarDocument());
            Map<String, Object> background = backgrounds.get(character.getId());
            String storyFile = background == null ? null : addGeneratedFile(files, folder + "/storia.pdf",
                    () -> renderer.render(CharacterBackgroundPdfService.TITLE_PREFIX + character.getName(),
                            StringUtils.trimToNull(character.getCampaignName()), background));
            result.add(new UserExport.Character(
                    character.getName(),
                    character.getKind(),
                    character.getCampaignName(),
                    character.getBackgroundColor(),
                    avatarFile,
                    storyFile,
                    background,
                    exportSheets(sheets.getOrDefault(character.getId(), List.of()), folder, files),
                    exportDiaries(character, diaries.getOrDefault(character.getId(), List.of()), pages, folder, files)));
        }
        return result;
    }

    private List<UserExport.Sheet> exportSheets(List<CharacterSheet> sheets, String folder, List<UserExport.Entry> files) {
        List<UserExport.Sheet> result = new ArrayList<>();
        for (int index = 0; index < sheets.size(); index++) {
            CharacterSheet sheet = sheets.get(index);
            String file = addStoredFile(files, folder + "/schede/" + numbered(index, sheet.getTitle(), "scheda"), sheet.getDocument());
            result.add(new UserExport.Sheet(sheet.getTitle(), sheet.getGameSystem(), sheet.getDocument().getName(), file));
        }
        return result;
    }

    private List<UserExport.Diary> exportDiaries(GameCharacter character, List<Diary> diaries, Map<UUID, List<DiaryPage>> pages,
                                                 String folder, List<UserExport.Entry> files) {
        String subtitle = Stream.of(character.getName(), StringUtils.trimToNull(character.getCampaignName()))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(" · "));
        List<UserExport.Diary> result = new ArrayList<>();
        for (int index = 0; index < diaries.size(); index++) {
            Diary diary = diaries.get(index);
            String name = folder + "/diari/" + numbered(index, diary.getTitle(), "diario");
            List<DiaryPage> diaryPages = pages.getOrDefault(diary.getId(), List.of());
            List<Map<String, Object>> contents = diaryPages.stream().map(DiaryPage::getContent).toList();
            String pdfFile = addGeneratedFile(files, name + ".pdf", () -> renderer.renderPages(diary.getTitle(), subtitle, contents));
            String coverFile = addStoredFile(files, name + "-copertina", diary.getCoverDocument());
            result.add(new UserExport.Diary(diary.getTitle(), diary.getDescription(), diary.getCoverColor(), diary.getFont(),
                    diary.getFontSize(), coverFile, pdfFile,
                    diaryPages.stream().map(page -> new UserExport.Page(page.getPageNumber(), page.getContent())).toList()));
        }
        return result;
    }

    private List<Payment> findPayments(String subject) {
        return database.find(Payment.class)
                .setLabel("findPayments")
                .where().eq("appUser.keycloakId", subject)
                .orderBy("paidAt desc nulls last, id asc")
                .findList();
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

    private static String readme(String email, Instant now) {
        return """
                Liber Heroum - copia dei tuoi dati
                ==================================

                Account: %s
                Copia creata il %s.

                Cosa trovi in questo archivio:
                - personaggi/: una cartella per ogni personaggio e master, con l'immagine (avatar),
                  la storia del personaggio in storia.pdf, le schede caricate nella cartella schede/
                  e i diari nella cartella diari/: un PDF per diario, con una pagina per ogni pagina scritta,
                  e la copertina accanto se l'hai caricata;
                - profilo/: l'immagine del tuo profilo, se l'hai caricata;
                - pagamenti.csv: i pagamenti del tuo account, da aprire con Excel, LibreOffice o Numbers;
                - per-altri-programmi/dati.json: gli stessi testi e dati in un formato che altri programmi
                  possono importare (portabilità dei dati, GDPR). Non serve aprirlo per leggere i tuoi diari.

                Non ci sono i segnalibri e i tag delle pagine, né i messaggi inviati dal modulo di feedback,
                che non sono legati all'account.
                """.formatted(email, DATE.format(now.atZone(ZONE)));
    }

    private static UserExport.Payment toPayment(Payment payment) {
        return new UserExport.Payment(payment.getProvider(), payment.getProviderPaymentId(), payment.getStatus(),
                payment.getAmountCents(), payment.getCurrency(), payment.getDescription(), payment.getPaidAt(),
                payment.getCustomerEmail(), payment.getCustomerName(), payment.getCustomerTaxCode(),
                payment.getCustomerCountry(), payment.getInvoiceNumber(), payment.getReceiptUrl());
    }

    private static String addStoredFile(List<UserExport.Entry> files, String pathWithoutExtension, Document document) {
        if (document == null) {
            return null;
        }
        String path = pathWithoutExtension + "." + EXTENSIONS.getOrDefault(document.getMimeType(), "bin");
        files.add(new UserExport.StoredFile(path, document));
        return path;
    }

    private static String addGeneratedFile(List<UserExport.Entry> files, String path, Supplier<byte[]> content) {
        files.add(new UserExport.GeneratedFile(path, content));
        return path;
    }

    private static String numbered(int index, String name, String fallback) {
        return "%02d-%s".formatted(index + 1, FileNames.slug(name, NAME_MAX_LENGTH, fallback));
    }
}
