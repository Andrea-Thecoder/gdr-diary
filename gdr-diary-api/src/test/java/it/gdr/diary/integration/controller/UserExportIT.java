package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import it.gdr.diary.model.AppUser;
import it.gdr.diary.model.Payment;
import it.gdr.diary.model.PaymentProvider;
import it.gdr.diary.model.PaymentStatus;
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
        return new String(entries.get("dati.json"), StandardCharsets.UTF_8);
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
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(entries.keySet()).containsExactly("dati.json", "LEGGIMI.txt", "profilo/avatar.png",
                    folder + "avatar.png", folder + "schede/01-alira-dei-salici-level-9.pdf", folder + "diari/01-chapter-one/copertina.png");
            softly.assertThat(entries.get(folder + "avatar.png")).isEqualTo(PNG);
            softly.assertThat(entries.get(folder + "schede/01-alira-dei-salici-level-9.pdf")).isEqualTo(PDF);
            softly.assertThat(List.<Object>of(
                    JsonPath.read(data, "$.format"),
                    JsonPath.read(data, "$.account.email"),
                    JsonPath.read(data, "$.account.avatarFile"),
                    JsonPath.read(data, "$.characters[*].name"),
                    JsonPath.read(data, "$.characters[0].background.content[0].content[0].text"),
                    JsonPath.read(data, "$.characters[0].sheets[0]['title','gameSystem','originalFileName','file']"),
                    JsonPath.read(data, "$.characters[0].diaries[0].pages[*].content.content[0].content[0].text"),
                    JsonPath.read(data, "$.payments[*].description")))
                    .containsExactly(
                            "diario-gdr-export",
                            TestJwt.USER_EMAIL,
                            "profilo/avatar.png",
                            List.of("Alira dei Salici"),
                            "Born in the woods of Alira dei Salici",
                            Map.of("title", "Alira dei Salici, level 9", "gameSystem", "D&D 5e",
                                    "originalFileName", "sheet.pdf", "file", folder + "schede/01-alira-dei-salici-level-9.pdf"),
                            List.of("Page one of Alira dei Salici", "Page two of Alira dei Salici"),
                            List.of("Premium annual"));
            softly.assertThat(data).doesNotContain("Sam", "Their payment");
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
    void getUserExport_userWithoutData_returnsZipAttachmentWithEmptyLists() throws IOException {
        createAppUser();

        MvcTestResult result = export();

        Map<String, byte[]> entries = unzip(result);
        assertThat(result).hasStatusOk().hasContentType("application/zip").headers().hasValue(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"diario-gdr-" + LocalDate.now(ZoneOffset.UTC) + ".zip\"; filename*=UTF-8''diario-gdr-"
                        + LocalDate.now(ZoneOffset.UTC) + ".zip");
        assertThat(List.<Object>of(entries.keySet(), JsonPath.read(data(entries), "$.characters"), JsonPath.read(data(entries), "$.payments")))
                .containsExactly(Set.of("dati.json", "LEGGIMI.txt"), List.of(), List.of());
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
