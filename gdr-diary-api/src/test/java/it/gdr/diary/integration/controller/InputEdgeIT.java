package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

@WithMockJwt
class InputEdgeIT extends AbstractIntegrationTest {

    private static final String LONE_SURROGATE = "\\ud800";

    private static String nestedArrays(int depth) {
        return "[".repeat(depth) + "]".repeat(depth);
    }

    private static String nestedBlockquotes(int quotes) {
        return "{\"type\":\"doc\",\"content\":[" + "{\"type\":\"blockquote\",\"content\":[".repeat(quotes)
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"deep\"}]}"
                + "]}".repeat(quotes) + "]}";
    }

    private static String nestedDocument(int levels) {
        return "{\"type\":\"doc\",\"content\":[" + "{\"type\":\"blockquote\",\"content\":[".repeat(levels)
                + "]}".repeat(levels) + "]}";
    }

    private UUID createCharacterId() {
        createAppUser();
        return createGameCharacter("Aragorn");
    }

    @Test
    void updateAppUserSettings_loneSurrogateInValue_returnsBadRequest() {
        createAppUser();

        assertThat(mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\":{\"a\":\"" + LONE_SURROGATE + "\"}}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiaryPage_loneSurrogateInText_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + pageContent(LONE_SURROGATE) + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_loneSurrogateInName_returnsBadRequest() {
        createAppUser();

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + LONE_SURROGATE + "\",\"kind\":\"PLAYER\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiary_fontSizeOutOfShortRange_returnsBadRequest() {
        UUID characterId = createCharacterId();

        assertThat(mvc.post().uri("/characters/{id}/diaries", characterId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Chapter one\",\"fontSize\":40000}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiary_fontSizeOutOfShortRange_messageDoesNotLeakClassNames() {
        UUID characterId = createCharacterId();

        assertThat(mvc.post().uri("/characters/{id}/diaries", characterId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Chapter one\",\"fontSize\":40000}"))
                .bodyJson().extractingPath("$.violations[0].message").asString().doesNotContain("it.gdr");
    }

    @Test
    void createGameCharacter_unknownKind_returnsBadRequest() {
        createAppUser();

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Aragorn\",\"kind\":\"X\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_unknownKind_namesTheField() {
        createAppUser();

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Aragorn\",\"kind\":\"X\"}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("kind");
    }

    @Test
    void createGameCharacter_unknownKind_messageDoesNotLeakClassNames() {
        createAppUser();

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Aragorn\",\"kind\":\"X\"}"))
                .bodyJson().extractingPath("$.violations[0].message").asString().doesNotContain("it.gdr");
    }


    @Test
    void getDiaryById_diaryOfOtherUser_returnsExceptionResponseWithStatus404() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        assertThat(mvc.get().uri("/diaries/{id}", othersDiary))
                .bodyJson().extractingPath("$.status").isEqualTo(404);
    }

    @Test
    void getDiaryById_diaryOfOtherUser_returnsExceptionResponseWithNotFoundTitle() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        assertThat(mvc.get().uri("/diaries/{id}", othersDiary))
                .bodyJson().extractingPath("$.title").isEqualTo("NOT_FOUND");
    }

    @Test
    void getDiaryById_diaryOfOtherUser_returnsDiaryNotFoundMessage() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        assertThat(mvc.get().uri("/diaries/{id}", othersDiary))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Diary not found");
    }

    @Test
    void findAllGameCharacters_pageZero_reportsPageAsViolationField() {
        createAppUser();

        assertThat(mvc.get().uri("/characters?page=0"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("page");
    }

    @Test
    void findAllGameCharacters_sizeOver100_reportsSizeAsViolationField() {
        createAppUser();

        assertThat(mvc.get().uri("/characters?size=101"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("size");
    }

    @Test
    void updateAppUserAvatar_pdfContent_reportsUnsupportedFileTypeMessage() {
        createAppUser();
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7 content".getBytes());

        assertThat(mvc.put().uri("/me/avatar").multipart().file(pdf))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Unsupported file type");
    }

    @Test
    void getGameCharacterById_notAUuid_returnsBadRequest() {
        createAppUser();

        assertThat(mvc.get().uri("/characters/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiaryPagesByDiary_nonNumericPage_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.get().uri("/diaries/{id}/pages?page=abc", diaryId)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateAppUserSettings_twoThousandNestedArrays_returnsBadRequest() {
        createAppUser();

        assertThat(mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\":{\"a\":" + nestedArrays(2000) + "}}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiaryPage_nestedBeyondMaxDepth_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + nestedBlockquotes(49) + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiaryPage_nestedBeyondMaxDepth_reportsNestingTooDeep() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + nestedBlockquotes(49) + "}"))
                .bodyJson().extractingPath("$.violations[0].message").asString().contains("nesting too deep");
    }

    @Test
    void createDiaryPage_nestedExactlyAtMaxDepth_returnsCreated() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + nestedBlockquotes(47) + "}"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createDiaryPage_sixHundredNestedLevels_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + nestedDocument(600) + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
