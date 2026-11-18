package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import java.io.IOException;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WithMockJwt
class CharacterBackgroundIT extends AbstractIntegrationTest {

    private String body(String text, long version) {
        return "{\"content\":" + pageContent(text) + ",\"version\":" + version + "}";
    }

    private MvcTestResult write(UUID characterId, String text, long version) {
        return mvc.put().uri("/characters/{characterId}/background", characterId)
                .contentType(MediaType.APPLICATION_JSON).content(body(text, version)).exchange();
    }

    private MvcTestResult write(UUID characterId, String text, long version, RequestPostProcessor user) {
        return mvc.put().uri("/characters/{characterId}/background", characterId).with(user)
                .contentType(MediaType.APPLICATION_JSON).content(body(text, version)).exchange();
    }

    private UUID characterOfCurrentUser() {
        createAppUser();
        return createGameCharacter("Thorin");
    }

    private String storedPreview(UUID characterId) {
        return jdbc.queryForObject("select preview from character_background where character_id = ?", String.class, characterId);
    }

    private int storedTextLength(UUID characterId) {
        return jdbc.queryForObject("select text_length from character_background where character_id = ?", Integer.class, characterId);
    }

    private UUID gameMasterOfCurrentUser() {
        UUID id = characterOfCurrentUser();
        jdbc.update("update game_character set kind = 'GAME_MASTER' where id = ?", id);
        return id;
    }

    private MvcTestResult pdf(UUID characterId) {
        return mvc.get().uri("/characters/{characterId}/background/pdf", characterId).exchange();
    }

    private static String pdfText(MvcTestResult result) throws IOException {
        try (PDDocument document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void getCharacterBackground_characterWithoutBackground_returnsEmptyDocumentAtVersionZeroAndCreatesNoRow() {
        UUID characterId = characterOfCurrentUser();

        MvcTestResult result = mvc.get().uri("/characters/{characterId}/background", characterId).exchange();

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.textLength").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.content.type").isEqualTo("doc");
        assertThat(countRows("character_background")).isZero();
    }

    @Test
    void updateCharacterBackground_firstWrite_returnsVersionOneStoresPreviewAndLengthAndReadsBack() {
        UUID characterId = characterOfCurrentUser();

        MvcTestResult result = write(characterId, "Born in Erebor", 0);

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(1);
        assertThat(storedPreview(characterId)).isEqualTo("Born in Erebor");
        assertThat(storedTextLength(characterId)).isEqualTo(14);
        assertThat(mvc.get().uri("/characters/{characterId}/background", characterId)).bodyJson()
                .extractingPath("$.content.content[0].content[0].text").isEqualTo("Born in Erebor");
    }

    @Test
    void updateCharacterBackground_secondWriteWithCurrentVersion_returnsNextVersionAndKeepsASingleRow() {
        UUID characterId = characterOfCurrentUser();
        write(characterId, "Born in Erebor", 0);

        MvcTestResult result = write(characterId, "Raised in Dale", 1);

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(2);
        assertThat(countRows("character_background")).isEqualTo(1);
        assertThat(storedPreview(characterId)).isEqualTo("Raised in Dale");
    }

    @Test
    void updateCharacterBackground_staleVersion_returnsConflictAndKeepsTheNewerText() {
        UUID characterId = characterOfCurrentUser();
        write(characterId, "Born in Erebor", 0);
        write(characterId, "Other tab", 1);

        MvcTestResult result = write(characterId, "This tab", 1);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(storedPreview(characterId)).isEqualTo("Other tab");
    }

    @Test
    void updateCharacterBackground_versionZeroWhenItExists_returnsConflict() {
        UUID characterId = characterOfCurrentUser();
        write(characterId, "Born in Erebor", 0);

        assertThat(write(characterId, "Second first write", 0)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void updateCharacterBackground_versionAboveZeroWithoutBackground_returnsConflict() {
        UUID characterId = characterOfCurrentUser();

        assertThat(write(characterId, "Born in Erebor", 3)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void updateCharacterBackground_invalidNode_returnsBadRequestAndCreatesNoRow() {
        UUID characterId = characterOfCurrentUser();

        MvcTestResult result = mvc.put().uri("/characters/{characterId}/background", characterId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":{\"type\":\"doc\",\"content\":[{\"type\":\"script\"}]},\"version\":0}").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(countRows("character_background")).isZero();
    }

    @Test
    void updateCharacterBackground_textOverTheLimit_returnsBadRequestAndCreatesNoRow() {
        UUID characterId = characterOfCurrentUser();

        MvcTestResult result = write(characterId, "a".repeat(50_001), 0);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(countRows("character_background")).isZero();
    }

    @Test
    void getCharacterBackground_singleTextAtTheLimit_returnsIt() {
        UUID characterId = characterOfCurrentUser();
        MvcTestResult written = write(characterId, "a".repeat(50_000), 0);

        MvcTestResult result = mvc.get().uri("/characters/{characterId}/background", characterId).exchange();

        assertThat(written).hasStatusOk();
        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.textLength").isEqualTo(50_000);
    }

    @Test
    void updateCharacterBackground_longText_storesPreviewOfThreeHundredCharacters() {
        UUID characterId = characterOfCurrentUser();

        write(characterId, "a".repeat(5_000), 0);

        assertThat(storedPreview(characterId)).hasSize(300);
    }

    @Test
    void getGameCharacter_withAndWithoutBackground_returnsPreviewOnlyWhenPresent() {
        UUID characterId = characterOfCurrentUser();
        MvcTestResult before = mvc.get().uri("/characters/{id}", characterId).exchange();
        write(characterId, "Born in Erebor", 0);

        MvcTestResult after = mvc.get().uri("/characters/{id}", characterId).exchange();

        assertThat(before).bodyJson().extractingPath("$.backgroundPreview").isNull();
        assertThat(after).bodyJson().extractingPath("$.backgroundPreview").isEqualTo("Born in Erebor");
    }

    @Test
    void background_gameMaster_returnsBadRequestOnReadWriteAndPdfAndCreatesNoRow() {
        UUID master = gameMasterOfCurrentUser();

        MvcTestResult read = mvc.get().uri("/characters/{characterId}/background", master).exchange();
        MvcTestResult written = write(master, "Nope", 0);
        MvcTestResult pdf = pdf(master);

        assertThat(read).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(written).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(pdf).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(countRows("character_background")).isZero();
    }

    @Test
    void getCharacterBackground_characterOfOtherUser_returnsNotFound() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Sam", TestJwt.otherUser());

        assertThat(mvc.get().uri("/characters/{characterId}/background", othersCharacter)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/characters/{characterId}/background", "/characters/{characterId}/background/pdf"})
    void backgroundRoute_unknownCharacter_returnsNotFound(String path) {
        createAppUser();

        assertThat(mvc.get().uri(path, UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateCharacterBackground_characterOfOtherUser_returnsNotFoundAndCreatesNoRow() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Sam", TestJwt.otherUser());

        MvcTestResult result = write(othersCharacter, "Hijacked", 0);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(countRows("character_background")).isZero();
    }

    @Test
    void getCharacterBackground_ownerReadsHisOwnWhileOtherUserHasOne_seesOnlyHisOwn() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Sam", TestJwt.otherUser());
        write(othersCharacter, "Sam's secret", 0, TestJwt.otherUser());
        UUID mine = characterOfCurrentUser();

        assertThat(mvc.get().uri("/characters/{characterId}/background", mine)).bodyJson()
                .extractingPath("$.version").isEqualTo(0);
    }

    @Test
    void deleteGameCharacter_withBackground_deletesTheCharacterAndItsBackground() {
        UUID characterId = characterOfCurrentUser();
        write(characterId, "Born in Erebor", 0);

        MvcTestResult result = mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(result).hasStatusOk();
        assertThat(countRows("game_character")).isZero();
        assertThat(countRows("character_background")).isZero();
    }

    @Test
    void backgroundCharacterColumn_schema_isUnique() {
        Integer count = jdbc.queryForObject("select count(*) from pg_constraint where conname = 'uq_character_background_character_id'", Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void getCharacterBackgroundPdf_withBackground_returnsADownloadablePdfWithNameAndText() throws IOException {
        UUID characterId = characterOfCurrentUser();
        write(characterId, "Born in Erebor", 0);

        MvcTestResult result = pdf(characterId);

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF).headers().hasValue("Content-Disposition",
                "attachment; filename=\"background-thorin.pdf\"; filename*=UTF-8''background-thorin.pdf");
        assertThat(pdfText(result)).contains("Il Background di Thorin", "Born in Erebor");
    }

    @Test
    void getCharacterBackgroundPdf_singleTextAtTheLimit_returnsAPdf() {
        UUID characterId = characterOfCurrentUser();
        write(characterId, "a".repeat(50_000), 0);

        assertThat(pdf(characterId)).hasStatusOk();
    }

    @Test
    void getCharacterBackgroundPdf_withoutBackground_returnsNotFound() {
        UUID characterId = characterOfCurrentUser();

        assertThat(pdf(characterId)).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                .extractingPath("$.violations[0].message").isEqualTo("Background not found");
    }

    @Test
    void getCharacterBackgroundPdf_characterOfOtherUser_returnsNotFound() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Sam", TestJwt.otherUser());
        write(othersCharacter, "Secret story", 0, TestJwt.otherUser());
        createAppUser();

        assertThat(pdf(othersCharacter)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
