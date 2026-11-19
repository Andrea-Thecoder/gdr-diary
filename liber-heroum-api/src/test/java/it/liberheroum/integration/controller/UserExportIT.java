package it.liberheroum.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import it.liberheroum.integration.support.AbstractIntegrationTest;
import it.liberheroum.integration.support.TestJwt;
import it.liberheroum.integration.support.WithMockJwt;
import it.liberheroum.model.AppUser;
import it.liberheroum.model.Payment;
import it.liberheroum.model.PaymentProvider;
import it.liberheroum.model.PaymentStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WithMockJwt
class UserExportIT extends AbstractIntegrationTest {

    private MvcTestResult export() {
        return mvc.get().uri("/me/export").exchange();
    }

    private static Map<String, byte[]> unzip(MvcTestResult result) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                entry = zip.getNextEntry();
            }
        }
        return entries;
    }

    private static String data(Map<String, byte[]> entries) {
        return new String(entries.get("per-altri-programmi/dati.json"), StandardCharsets.UTF_8);
    }

    private static String pdfText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private AppUser appUser(String subject) {
        return database.find(AppUser.class).where().eq("keycloakId", subject).findOne();
    }

    private void savePayment(AppUser owner, String description) {
        Payment payment = new Payment();
        payment.setAppUser(owner);
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setProviderPaymentId("pi_" + UUID.randomUUID());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setAmountCents(2990);
        payment.setCurrency("EUR");
        payment.setDescription(description);
        payment.setPaidAt(Instant.parse("2026-11-01T10:00:00Z"));
        payment.setCustomerEmail("alira@example.com");
        payment.setRetainUntil(LocalDate.now().plusYears(10));
        database.save(payment);
    }

    private UUID characterWithEverything(String name, RequestPostProcessor user) {
        UUID characterId = createGameCharacter(name, user);
        mvc.put().uri("/characters/{id}/avatar", characterId).with(user).multipart().file(pngFile("avatar.png")).exchange();
        mvc.put().uri("/characters/{characterId}/background", characterId).with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + pageContent("Born in the woods of " + name) + ",\"version\":0}").exchange();
        mvc.post().uri("/characters/{id}/sheets", characterId).with(user).multipart().file(pdfFile("sheet.pdf"))
                .param("title", name + ", level 9").param("system", "D&D 5e").exchange();
        UUID diaryId = createDiary(characterId, "Chapter one", user);
        mvc.put().uri("/diaries/{id}/cover", diaryId).with(user).multipart().file(pngFile("cover.png")).exchange();
        createDiaryPage(diaryId, "Page one of " + name, user);
        createDiaryPage(diaryId, "Page two of " + name, user);
        return characterId;
    }

    @Test
    void getUserExport_userWithEverything_containsAllTheDataAndFilesOfTheUserOnly() throws IOException {
        createAppUser();
        mvc.put().uri("/me/avatar").multipart().file(pngFile("me.png")).exchange();
        characterWithEverything("Alira dei Salici", CURRENT_USER);
        savePayment(appUser(TestJwt.USER_SUBJECT), "Premium annual");
        createAppUser(TestJwt.otherUser());
        characterWithEverything("Sam", TestJwt.otherUser());
        savePayment(appUser(TestJwt.OTHER_SUBJECT), "Their payment");

        Map<String, byte[]> entries = unzip(export());

        String folder = "personaggi/01-alira-dei-salici/";
        String data = data(entries);
        String csv = new String(entries.get("pagamenti.csv"), StandardCharsets.UTF_8);
        String story = pdfText(entries.get(folder + "storia.pdf"));
        String diary = pdfText(entries.get(folder + "diari/01-chapter-one.pdf"));
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(entries.keySet()).containsExactly("LEGGIMI.txt", "profilo/avatar.png",
                    folder + "avatar.png", folder + "storia.pdf", folder + "schede/01-alira-dei-salici-level-9.pdf",
                    folder + "diari/01-chapter-one.pdf", folder + "diari/01-chapter-one-copertina.png",
                    "pagamenti.csv", "per-altri-programmi/dati.json");
            softly.assertThat(entries.get(folder + "avatar.png")).isEqualTo(PNG);
            softly.assertThat(entries.get(folder + "schede/01-alira-dei-salici-level-9.pdf")).isEqualTo(PDF);
            softly.assertThat(story)
                    .contains("Il Background di Alira dei Salici", "Born in the woods of Alira dei Salici");
            softly.assertThat(diary)
                    .contains("Chapter one", "Pagina 1", "Page one of Alira dei Salici", "Pagina 2", "Page two of Alira dei Salici");
            softly.assertThat(csv).contains("Premium annual;29,90;EUR;Pagato").doesNotContain("Their payment");
            softly.assertThat(new String(entries.get("LEGGIMI.txt"), StandardCharsets.UTF_8)).contains(TestJwt.USER_EMAIL);
            softly.assertThat(List.<Object>of(
                    JsonPath.read(data, "$.account"),
                    JsonPath.read(data, "$.characters[*].name"),
                    JsonPath.read(data, "$.characters[0].storyFile"),
                    JsonPath.read(data, "$.characters[0].background.content[0].content[0].text"),
                    JsonPath.read(data, "$.characters[0].diaries[0].pdfFile"),
                    JsonPath.read(data, "$.characters[0].diaries[0].pages[*].content.content[0].content[0].text"),
                    JsonPath.read(data, "$.payments[*].description")))
                    .containsExactly(
                            Map.of("email", TestJwt.USER_EMAIL),
                            List.of("Alira dei Salici"),
                            folder + "storia.pdf",
                            "Born in the woods of Alira dei Salici",
                            folder + "diari/01-chapter-one.pdf",
                            List.of("Page one of Alira dei Salici", "Page two of Alira dei Salici"),
                            List.of("Premium annual"));
            softly.assertThat(data).doesNotContain("Sam", "Their payment", "settings");
        });
    }

    @Test
    void getUserExport_diaryWithBookmarkAndTags_leavesThemOut() throws IOException {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "The ambush");
        mvc.put().uri("/pages/{id}/tag", pageId).contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"Combattimento\"}").exchange();
        mvc.put().uri("/diaries/{id}/bookmark", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageId\":\"" + pageId + "\"}").exchange();

        String data = data(unzip(export()));

        assertThat(data).contains("The ambush").doesNotContain("Combattimento", "bookmark", pageId.toString());
    }

    @Test
    void getUserExport_masterWithoutStoryImagesAndPages_exportsOnlyAnEmptyDiaryPdf() throws IOException {
        createAppUser();
        UUID masterId = UUID.fromString(json(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"The Master\",\"kind\":\"GAME_MASTER\"}").exchange(), "$.id"));
        createDiary(masterId, "Session notes");

        Map<String, byte[]> entries = unzip(export());

        String diaryPdf = "personaggi/01-the-master/diari/01-session-notes.pdf";
        assertThat(List.<Object>of(entries.keySet(), pdfText(entries.get(diaryPdf)).contains("Questo diario non ha ancora pagine.")))
                .containsExactly(Set.of("LEGGIMI.txt", diaryPdf, "pagamenti.csv", "per-altri-programmi/dati.json"), true);
    }

    @Test
    void getUserExport_userWithoutData_returnsZipAttachmentWithEmptyLists() throws IOException {
        createAppUser();

        MvcTestResult result = export();

        Map<String, byte[]> entries = unzip(result);
        assertThat(result).hasStatusOk().hasContentType("application/zip").headers().hasValue(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"liber-heroum-" + LocalDate.now(ZoneOffset.UTC) + ".zip\"; filename*=UTF-8''liber-heroum-"
                        + LocalDate.now(ZoneOffset.UTC) + ".zip");
        assertThat(List.<Object>of(entries.keySet(), JsonPath.read(data(entries), "$.characters"), JsonPath.read(data(entries), "$.payments")))
                .containsExactly(Set.of("LEGGIMI.txt", "pagamenti.csv", "per-altri-programmi/dati.json"), List.of(), List.of());
    }

    @Test
    void getUserExport_loginOlderThanMaxAge_returnsUnauthorizedWithStepUpChallenge() {
        createAppUser();

        MvcTestResult result = mvc.get().uri("/me/export")
                .with(TestJwt.authenticatedAt(Instant.now().minus(Duration.ofMinutes(10)))).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).headers().hasValue(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer error=\"insufficient_user_authentication\", "
                        + "error_description=\"A more recent authentication is required\", max_age=\"300\"");
    }

    @Test
    void getUserExport_userNotCreated_returnsNotFound() {
        assertThat(export()).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                .extractingPath("$.violations[0].message").isEqualTo("User not found");
    }
}
