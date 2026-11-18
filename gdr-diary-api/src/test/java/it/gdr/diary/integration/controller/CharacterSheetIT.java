package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import it.gdr.diary.config.CharacterSheetConfig;
import it.gdr.diary.config.StorageConfig;
import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.BlockingTransaction;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

@WithMockJwt
class CharacterSheetIT extends AbstractIntegrationTest {

    private static final String INSERT_DOCUMENT = "insert into document (id, size, created_at, updated_at, version, name, mime_type, "
            + "bucket, object_key, created_by, updated_by) values (?, 1, now(), now(), 1, 'racing.pdf', 'application/pdf', "
            + "'bucket', 'key', 'test', 'test')";
    private static final String INSERT_SHEET = "insert into character_sheet (id, character_id, document_id, created_at, updated_at, "
            + "version, title, created_by, updated_by) values (?, ?, ?, now(), now(), 1, 'Racing', 'test', 'test')";

    @Autowired
    private StorageConfig storageConfig;

    @Autowired
    private CharacterSheetConfig sheetConfig;

    private static RequestPostProcessor currentUser() {
        return TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user");
    }

    private static MockMultipartFile fakePdf(String name) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_PDF_VALUE, "this is not a pdf".getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile pngAsPdf(String name) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_PDF_VALUE, PNG);
    }

    private MvcTestResult uploadSheet(UUID characterId, String title, String system, MockMultipartFile file, RequestPostProcessor user) {
        if (system == null) {
            return mvc.post().uri("/characters/{id}/sheets", characterId).with(user).multipart().file(file)
                    .param("title", title).exchange();
        }
        return mvc.post().uri("/characters/{id}/sheets", characterId).with(user).multipart().file(file)
                .param("title", title).param("system", system).exchange();
    }

    private MvcTestResult uploadSheet(UUID characterId, String title) {
        return uploadSheet(characterId, title, null, pdfFile("sheet.pdf"), CURRENT_USER);
    }

    private UUID createSheet(UUID characterId, String title, String system) {
        return UUID.fromString(json(uploadSheet(characterId, title, system, pdfFile("sheet.pdf"), CURRENT_USER), "$.id"));
    }

    private UUID createSheet(UUID characterId, String title) {
        return createSheet(characterId, title, null);
    }

    private UUID characterWithOwner(String name) {
        createAppUser();
        return createGameCharacter(name);
    }

    private List<UUID> createSheets(UUID characterId, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(number -> createSheet(characterId, "Sheet " + number)).toList();
    }

    private static List<String> listAt(MvcTestResult result, String path) {
        return JsonPath.read(responseBody(result), path);
    }

    private String sheetObjectKey(UUID sheetId) {
        return jdbc.queryForObject("select d.object_key from document d join character_sheet s on s.document_id = d.id where s.id = ?",
                String.class, sheetId);
    }

    private static String objectKeyOf(String url) {
        String path = URI.create(url).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String queryParam(String url, String name) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
    }

    private HttpResponse<byte[]> download(String url) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static Stream<MockMultipartFile> invalidFiles() {
        return Stream.of(fakePdf("sheet.pdf"), pngAsPdf("sheet.pdf"), pngFile("sheet.png"), pdfFile("sheet.png"));
    }

    private UUID sheetOfOtherUser() {
        createAppUser();
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        return UUID.fromString(json(uploadSheet(othersCharacter, "Secret", null, pdfFile("s.pdf"), TestJwt.otherUser()), "$.id"));
    }

    // ---- POST /characters/{characterId}/sheets ----

    @Test
    void createCharacterSheet_validPdf_returnsCreatedAndStoresSheetDocumentAndFile() throws Exception {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = uploadSheet(characterId, "Alira, level 9", "D&D 5e", pdfFile("alira.pdf"), CURRENT_USER);

        UUID id = UUID.fromString(json(result, "$.id"));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(List.of(json(result, "$.title"), json(result, "$.system"), json(result, "$.fileName"), json(result, "$.characterId"),
                json(result, "$.sizeBytes")))
                .containsExactly("Alira, level 9", "D&D 5e", "alira.pdf", characterId.toString(), String.valueOf(PDF.length));
        assertThat(jdbc.queryForMap("select character_id, title, game_system, created_by from character_sheet where id = ?", id))
                .containsEntry("character_id", characterId).containsEntry("title", "Alira, level 9")
                .containsEntry("game_system", "D&D 5e").containsEntry("created_by", TestJwt.USER_SUBJECT);
        assertThat(jdbc.queryForMap("select d.mime_type, d.name, d.size from document d join character_sheet s on s.document_id = d.id "
                + "where s.id = ?", id))
                .containsEntry("mime_type", "application/pdf").containsEntry("name", "alira.pdf").containsEntry("size", (long) PDF.length);
        assertThat(storedObjectKeys()).containsExactly(sheetObjectKey(id));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"'  Alira  ','  D&D 5e ',Alira,D&D 5e", "Alira,NULL,Alira,NULL"})
    void createCharacterSheet_titleAndSystemInput_storesTrimmedOrNullValues(String title, String system, String expectedTitle,
                                                                           String expectedSystem) {
        UUID characterId = characterWithOwner("Alira");

        UUID id = createSheet(characterId, title, system);

        assertThat(jdbc.queryForMap("select title, game_system from character_sheet where id = ?", id))
                .containsEntry("title", expectedTitle).containsEntry("game_system", expectedSystem);
    }

    @Test
    void createCharacterSheet_severalSheetsForTheSameCharacter_storesThemAllWithDistinctFiles() throws Exception {
        UUID characterId = characterWithOwner("Alira");

        createSheets(characterId, 3);

        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(3);
    }

    @Test
    void createCharacterSheet_contentNotAPdf_returnsBadRequestWithReason() {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = uploadSheet(characterId, "Alira", null, fakePdf("sheet.pdf"), CURRENT_USER);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(result, "$.violations[0].message")).isEqualTo("Unsupported file type");
    }

    @ParameterizedTest
    @MethodSource("invalidFiles")
    void createCharacterSheet_invalidFile_returnsBadRequestAndStoresNothing(MockMultipartFile file) throws Exception {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = uploadSheet(characterId, "Alira", null, file, CURRENT_USER);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(0);
    }

    @Test
    void createCharacterSheet_characterOfOtherUser_returnsNotFoundAndStoresNothing() throws Exception {
        createAppUser();
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        MvcTestResult result = uploadSheet(othersCharacter, "Alira");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(0);
    }

    @Test
    void createCharacterSheet_characterAtLimit_returnsBadRequestWithReasonAndStoresNothingMore() throws Exception {
        UUID characterId = characterWithOwner("Alira");
        int limit = sheetConfig.getMaxSheetsPerCharacter();
        createSheets(characterId, limit);

        MvcTestResult result = uploadSheet(characterId, "One too many");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(result, "$.violations[0].message")).isEqualTo("The character has too many sheets (" + limit + ")");
        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(limit);
    }

    @Test
    void createCharacterSheet_otherCharacterAtLimit_isNotAffected() {
        UUID full = characterWithOwner("Alira");
        UUID empty = createGameCharacter("Borin");
        createSheets(full, sheetConfig.getMaxSheetsPerCharacter());

        assertThat(uploadSheet(empty, "First sheet of Borin")).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createCharacterSheet_sheetDeletedWhileAtLimit_allowsNewOne() {
        UUID characterId = characterWithOwner("Alira");
        List<UUID> sheets = createSheets(characterId, sheetConfig.getMaxSheetsPerCharacter());
        mvc.delete().uri("/sheets/{id}", sheets.get(0)).exchange();

        assertThat(uploadSheet(characterId, "Replacement")).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createCharacterSheet_concurrentUploadsNearTheLimit_neverExceedTheLimit() throws Exception {
        UUID characterId = characterWithOwner("Alira");
        int limit = sheetConfig.getMaxSheetsPerCharacter();
        createSheets(characterId, limit - 2);

        List<CompletableFuture<MvcTestResult>> uploads = IntStream.range(0, 5)
                .mapToObj(number -> CompletableFuture.supplyAsync(
                        () -> uploadSheet(characterId, "Race " + number, null, pdfFile("race.pdf"), currentUser())))
                .toList();
        uploads.forEach(CompletableFuture::join);

        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(limit);
    }

    // ---- GET /characters/{characterId}/sheets ----

    @Test
    void findAllCharacterSheetsByCharacter_twoCharacters_returnsOnlySheetsOfRequestedOne() {
        UUID alira = characterWithOwner("Alira");
        UUID borin = createGameCharacter("Borin");
        createSheet(alira, "Alira sheet");
        createSheet(borin, "Borin sheet");

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets", alira).exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly("Alira sheet");
    }

    @Test
    void findAllCharacterSheetsByCharacter_noSheets_returnsEmptyList() {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets", characterId).exchange();

        assertThat(listAt(result, "$.list")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "descending=false,'Charlie,Alpha,Bravo'",
            "sort=version,'Charlie,Alpha,Bravo'",
            "descending=true,'Bravo,Alpha,Charlie'",
            "sort=title,'Alpha,Bravo,Charlie'",
            "sort=system,'Bravo,Charlie,Alpha'"})
    void findAllCharacterSheetsByCharacter_sortParameters_returnsExpectedOrder(String query, String expectedTitles) {
        UUID characterId = characterWithOwner("Alira");
        createSheet(characterId, "Charlie", "D&D 5e");
        createSheet(characterId, "Alpha", "Pathfinder 2e");
        createSheet(characterId, "Bravo", "Call of Cthulhu");

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets?" + query, characterId).exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly(expectedTitles.split(","));
    }

    @Test
    void findAllCharacterSheetsByCharacter_titleFilter_matchesIgnoringCase() {
        UUID characterId = characterWithOwner("Alira");
        createSheet(characterId, "Alira, level 9");
        createSheet(characterId, "Backup copy");

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets?title=ALIRA", characterId).exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly("Alira, level 9");
    }

    @Test
    void findAllCharacterSheetsByCharacter_systemFilter_matchesWholeSystemIgnoringCase() {
        UUID characterId = characterWithOwner("Alira");
        createSheet(characterId, "One", "D&D 5e");
        createSheet(characterId, "Two", "D&D 3.5");
        createSheet(characterId, "Three", null);

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets", characterId).param("system", "d&d 5E").exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly("One");
    }

    @Test
    void findAllCharacterSheetsByCharacter_pagination_returnsRequestedPageTotalsAndUrls() {
        UUID characterId = characterWithOwner("Alira");
        createSheets(characterId, 5);

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets?page=2&size=2", characterId).exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly("Sheet 3", "Sheet 4");
        assertThat(List.of(json(result, "$.totalRows"), json(result, "$.totalPages"))).containsExactly("5", "3");
        assertThat(listAt(result, "$.list[*].url")).hasSize(2).allSatisfy(url -> assertThat(url).startsWith("http"));
        assertThat(listAt(result, "$.list[*].downloadUrl")).hasSize(2).allSatisfy(url -> assertThat(url).startsWith("http"));
    }

    @Test
    void findAllCharacterSheetsByCharacter_characterOfOtherUser_returnsNotFound() {
        createAppUser();
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        assertThat(mvc.get().uri("/characters/{id}/sheets", othersCharacter)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- GET /sheets/{id} ----

    @Test
    void getCharacterSheetById_ownSheet_returnsDetailWithUrlsOfStoredObject() {
        UUID characterId = characterWithOwner("Alira");
        UUID id = UUID.fromString(json(uploadSheet(characterId, "Alira, level 9", "D&D 5e", pdfFile("Scheda di Alira.pdf"), CURRENT_USER), "$.id"));

        MvcTestResult result = mvc.get().uri("/sheets/{id}", id).exchange();

        String expiry = String.valueOf(storageConfig.getUrlExpiry().toSeconds());
        String url = json(result, "$.url");
        String downloadUrl = json(result, "$.downloadUrl");
        assertThat(result).hasStatusOk();
        assertThat(List.of(json(result, "$.id"), json(result, "$.characterId"), json(result, "$.title"), json(result, "$.system"),
                json(result, "$.fileName"), json(result, "$.sizeBytes")))
                .containsExactly(id.toString(), characterId.toString(), "Alira, level 9", "D&D 5e", "Scheda di Alira.pdf",
                        String.valueOf(PDF.length));
        assertThat(List.of(objectKeyOf(url), objectKeyOf(downloadUrl))).containsOnly(sheetObjectKey(id));
        assertThat(List.of(queryParam(url, "X-Amz-Expires"), queryParam(downloadUrl, "X-Amz-Expires"))).containsOnly(expiry);
        assertThat(URLDecoder.decode(queryParam(downloadUrl, "response-content-disposition"), StandardCharsets.UTF_8))
                .isEqualTo("attachment; filename*=UTF-8''Scheda%20di%20Alira.pdf");
        assertThat(queryParam(url, "response-content-disposition")).isNull();
    }

    @Test
    void getCharacterSheetById_ownSheet_urlServesThePdfInline() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");
        String url = json(mvc.get().uri("/sheets/{id}", id).exchange(), "$.url");

        HttpResponse<byte[]> response = download(url);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(PDF);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("application/pdf"));
    }

    @Test
    void getCharacterSheetById_ownSheet_downloadUrlServesThePdfAsAttachment() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");
        String url = json(mvc.get().uri("/sheets/{id}", id).exchange(), "$.downloadUrl");

        HttpResponse<byte[]> response = download(url);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(PDF);
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(disposition -> assertThat(disposition).startsWith("attachment").contains("sheet.pdf"));
    }

    @Test
    void getCharacterSheetById_sheetOfOtherUser_returnsNotFound() {
        UUID othersSheet = sheetOfOtherUser();

        assertThat(mvc.get().uri("/sheets/{id}", othersSheet)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getCharacterSheetById_unknownId_returnsNotFound() {
        createAppUser();

        assertThat(mvc.get().uri("/sheets/{id}", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- PUT /sheets/{id} ----

    @Test
    void updateCharacterSheet_validBody_updatesTitleAndSystemKeepingFileAndIncrementingVersion() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira, level 9", "D&D 5e");
        String keyBefore = sheetObjectKey(id);

        MvcTestResult result = mvc.put().uri("/sheets/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Alira, level 10\",\"system\":\"Pathfinder 2e\"}").exchange();

        assertThat(result).hasStatusOk();
        assertThat(jdbc.queryForMap("select title, game_system, version from character_sheet where id = ?", id))
                .containsEntry("title", "Alira, level 10").containsEntry("game_system", "Pathfinder 2e").containsEntry("version", 2L);
        assertThat(storedObjectKeys()).containsExactly(keyBefore);
        assertThat(sheetObjectKey(id)).isEqualTo(keyBefore);
    }

    @Test
    void updateCharacterSheet_blankSystem_removesSystem() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira", "D&D 5e");

        mvc.put().uri("/sheets/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Alira\",\"system\":\"\"}").exchange();

        assertThat(jdbc.queryForObject("select game_system from character_sheet where id = ?", String.class, id)).isNull();
    }

    @Test
    void updateCharacterSheet_invalidBody_returnsBadRequestAndKeepsSheet() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira", "D&D 5e");

        MvcTestResult result = mvc.put().uri("/sheets/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForMap("select title, game_system from character_sheet where id = ?", id))
                .containsEntry("title", "Alira").containsEntry("game_system", "D&D 5e");
    }

    @Test
    void updateCharacterSheet_sheetOfOtherUser_returnsNotFoundAndKeepsSheet() {
        UUID othersSheet = sheetOfOtherUser();

        MvcTestResult result = mvc.put().uri("/sheets/{id}", othersSheet).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hijacked\"}").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select title from character_sheet where id = ?", String.class, othersSheet)).isEqualTo("Secret");
    }

    // ---- PUT /sheets/{id}/file ----

    @Test
    void updateCharacterSheetFile_validPdf_replacesFileKeepingTitleAndSystem() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira, level 9", "D&D 5e");
        String oldKey = sheetObjectKey(id);

        MvcTestResult result = mvc.put().uri("/sheets/{id}/file", id).multipart().file(pdfFile("edited.pdf")).exchange();

        String newKey = sheetObjectKey(id);
        assertThat(result).hasStatusOk();
        assertThat(List.of(json(result, "$.fileName"), json(result, "$.title"), json(result, "$.system")))
                .containsExactly("edited.pdf", "Alira, level 9", "D&D 5e");
        assertThat(objectKeyOf(json(result, "$.url"))).isEqualTo(newKey);
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(storedObjectKeys()).containsExactly(newKey);
        assertThat(jdbc.queryForList("select name from document", String.class)).containsExactly("edited.pdf");
    }

    @Test
    void updateCharacterSheetFile_invalidContent_returnsBadRequestAndKeepsPreviousFile() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");
        String oldKey = sheetObjectKey(id);

        MvcTestResult result = mvc.put().uri("/sheets/{id}/file", id).multipart().file(fakePdf("edited.pdf")).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(storedObjectKeys()).containsExactly(oldKey);
        assertThat(sheetObjectKey(id)).isEqualTo(oldKey);
    }

    @Test
    void updateCharacterSheetFile_sheetOfOtherUser_returnsNotFoundAndKeepsFile() throws Exception {
        UUID othersSheet = sheetOfOtherUser();
        String key = sheetObjectKey(othersSheet);

        MvcTestResult result = mvc.put().uri("/sheets/{id}/file", othersSheet).multipart().file(pdfFile("edited.pdf")).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(storedObjectKeys()).containsExactly(key);
        assertThat(sheetObjectKey(othersSheet)).isEqualTo(key);
    }

    // ---- DELETE /sheets/{id} ----

    @Test
    void deleteCharacterSheet_ownSheet_removesOnlyThatSheetWithDocumentAndFile() throws Exception {
        UUID characterId = characterWithOwner("Alira");
        UUID first = createSheet(characterId, "First");
        UUID second = createSheet(characterId, "Second");
        String secondKey = sheetObjectKey(second);

        MvcTestResult result = mvc.delete().uri("/sheets/{id}", first).exchange();

        assertThat(result).hasStatusOk();
        assertThat(json(result, "$.message")).isEqualTo("Sheet deleted");
        assertThat(jdbc.queryForList("select id from character_sheet", UUID.class)).containsExactly(second);
        assertThat(countRows("document")).isEqualTo(1);
        assertThat(storedObjectKeys()).containsExactly(secondKey);
        assertThat(mvc.get().uri("/characters/{id}", characterId)).hasStatusOk();
        assertThat(mvc.get().uri("/sheets/{id}", first)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCharacterSheet_sheetOfOtherUser_returnsNotFoundAndKeepsEverything() throws Exception {
        UUID othersSheet = sheetOfOtherUser();

        MvcTestResult result = mvc.delete().uri("/sheets/{id}", othersSheet).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(1);
    }

    // ---- cascade ----

    @Test
    void deleteGameCharacter_withSheetsAvatarAndCover_removesItsFilesAndKeepsOtherCharacters() throws Exception {
        UUID alira = characterWithOwner("Alira");
        UUID borin = createGameCharacter("Borin");
        UUID diaryId = createDiary(alira, "Chapter one");
        createSheets(alira, 3);
        UUID borinSheet = createSheet(borin, "Borin");
        String borinKey = sheetObjectKey(borinSheet);
        mvc.put().uri("/characters/{id}/avatar", alira).multipart().file(pngFile("avatar.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("cover.png")).exchange();

        MvcTestResult result = mvc.delete().uri("/characters/{id}", alira).exchange();

        assertThat(result).hasStatusOk();
        assertThat(countRows("game_character")).isEqualTo(1);
        assertThat(jdbc.queryForList("select id from character_sheet", UUID.class)).containsExactly(borinSheet);
        assertThat(countRows("document")).isEqualTo(1);
        assertThat(storedObjectKeys()).containsExactly(borinKey);
    }

    @Test
    void deleteGameCharacter_sheetAddedBeforeCommit_returnsConflictAndKeepsCharacter() {
        UUID characterId = characterWithOwner("Alira");
        UUID documentId = UUID.randomUUID();

        try (BlockingTransaction concurrent = BlockingTransaction.begin(jdbc)
                .execute(INSERT_DOCUMENT, documentId)
                .execute(INSERT_SHEET, UUID.randomUUID(), characterId, documentId)) {
            CompletableFuture<MvcTestResult> delete = concurrent.startBlockedCall(
                    () -> mvc.delete().uri("/characters/{id}", characterId).with(currentUser()).exchange());
            concurrent.commit();

            assertThat(delete.join()).hasStatus(HttpStatus.CONFLICT);
        }
        assertThat(countRows("game_character")).isEqualTo(1);
    }

    @Test
    void deleteAppUser_userWithSheets_removesOwnSheetsDocumentsAndFilesKeepingOtherUsers() throws Exception {
        UUID othersSheet = sheetOfOtherUser();
        String othersKey = sheetObjectKey(othersSheet);
        UUID alira = createGameCharacter("Alira");
        UUID borin = createGameCharacter("Borin");
        createSheets(alira, 2);
        createSheet(borin, "Borin");

        MvcTestResult result = mvc.delete().uri("/me").exchange();

        assertThat(result).hasStatusOk();
        assertThat(List.of(countRows("app_user"), countRows("game_character"), countRows("document"))).containsOnly(1);
        assertThat(jdbc.queryForList("select id from character_sheet", UUID.class)).containsExactly(othersSheet);
        assertThat(storedObjectKeys()).containsExactly(othersKey);
    }
}
