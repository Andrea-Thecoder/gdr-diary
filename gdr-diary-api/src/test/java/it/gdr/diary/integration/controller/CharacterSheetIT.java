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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    // ---- POST /characters/{characterId}/sheets ----

    @Test
    void createCharacterSheet_validPdf_returnsCreatedWithDetail() {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = uploadSheet(characterId, "Alira, level 9", "D&D 5e", pdfFile("alira.pdf"), CURRENT_USER);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(List.of(json(result, "$.title"), json(result, "$.system"), json(result, "$.fileName"), json(result, "$.characterId")))
                .containsExactly("Alira, level 9", "D&D 5e", "alira.pdf", characterId.toString());
    }

    @Test
    void createCharacterSheet_validPdf_returnsSizeOfTheFile() {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = uploadSheet(characterId, "Alira");

        assertThat(json(result, "$.sizeBytes")).isEqualTo(String.valueOf(PDF.length));
    }

    @Test
    void createCharacterSheet_validPdf_storesSheetLinkedToCharacter() {
        UUID characterId = characterWithOwner("Alira");

        UUID id = createSheet(characterId, "Alira, level 9", "D&D 5e");

        Map<String, Object> row = jdbc.queryForMap("select character_id, title, game_system from character_sheet where id = ?", id);
        assertThat(row).containsEntry("character_id", characterId).containsEntry("title", "Alira, level 9")
                .containsEntry("game_system", "D&D 5e");
    }

    @Test
    void createCharacterSheet_validPdf_storesPdfDocumentAndOneFile() throws Exception {
        UUID characterId = characterWithOwner("Alira");

        UUID id = createSheet(characterId, "Alira");

        Map<String, Object> document = jdbc.queryForMap(
                "select d.mime_type, d.name, d.size from document d join character_sheet s on s.document_id = d.id where s.id = ?", id);
        assertThat(document).containsEntry("mime_type", "application/pdf").containsEntry("name", "sheet.pdf")
                .containsEntry("size", (long) PDF.length);
        assertThat(storedObjectKeys()).containsExactly(sheetObjectKey(id));
    }

    @Test
    void createCharacterSheet_validPdf_writesAuditWithSubject() {
        UUID characterId = characterWithOwner("Alira");

        UUID id = createSheet(characterId, "Alira");

        assertThat(jdbc.queryForObject("select created_by from character_sheet where id = ?", String.class, id))
                .isEqualTo(TestJwt.USER_SUBJECT);
    }

    @Test
    void createCharacterSheet_systemOmitted_storesNullSystem() {
        UUID characterId = characterWithOwner("Alira");

        UUID id = createSheet(characterId, "Alira");

        assertThat(jdbc.queryForObject("select game_system from character_sheet where id = ?", String.class, id)).isNull();
    }

    @Test
    void createCharacterSheet_titleAndSystemWithSpaces_storesTrimmedValues() {
        UUID characterId = characterWithOwner("Alira");

        UUID id = createSheet(characterId, "  Alira  ", "  D&D 5e ");

        Map<String, Object> row = jdbc.queryForMap("select title, game_system from character_sheet where id = ?", id);
        assertThat(row).containsEntry("title", "Alira").containsEntry("game_system", "D&D 5e");
    }

    @Test
    void createCharacterSheet_severalSheetsForTheSameCharacter_storesThemAllWithDistinctFiles() throws Exception {
        UUID characterId = characterWithOwner("Alira");

        createSheets(characterId, 3);

        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(3);
    }

    @Test
    void createCharacterSheet_contentNotAPdf_returnsBadRequest() {
        UUID characterId = characterWithOwner("Alira");

        assertThat(uploadSheet(characterId, "Alira", null, fakePdf("sheet.pdf"), CURRENT_USER)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCharacterSheet_contentNotAPdf_returnsReasonAndStoresNothing() throws Exception {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = uploadSheet(characterId, "Alira", null, fakePdf("sheet.pdf"), CURRENT_USER);

        assertThat(json(result, "$.violations[0].message")).isEqualTo("Unsupported file type");
        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(0);
    }

    @Test
    void createCharacterSheet_imageDeclaredAsPdf_storesNothing() throws Exception {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = uploadSheet(characterId, "Alira", null, pngAsPdf("sheet.pdf"), CURRENT_USER);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(0);
    }

    @Test
    void createCharacterSheet_imageWithImageType_isRejected() throws Exception {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = uploadSheet(characterId, "Alira", null, pngFile("sheet.png"), CURRENT_USER);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void createCharacterSheet_pdfWithWrongExtension_isRejected() throws Exception {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = uploadSheet(characterId, "Alira", null, pdfFile("sheet.png"), CURRENT_USER);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(storedObjectKeys()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createCharacterSheet_blankTitle_returnsBadRequestAndStoresNothing(String title) throws Exception {
        UUID characterId = characterWithOwner("Alira");

        MvcTestResult result = uploadSheet(characterId, title, null, pdfFile("sheet.pdf"), CURRENT_USER);

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
    void createCharacterSheet_unknownCharacter_returnsNotFound() {
        createAppUser();

        assertThat(uploadSheet(UUID.randomUUID(), "Alira")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createCharacterSheet_characterAtLimit_returnsBadRequestWithReason() {
        UUID characterId = characterWithOwner("Alira");
        int limit = sheetConfig.getMaxSheetsPerCharacter();
        createSheets(characterId, limit);

        MvcTestResult result = uploadSheet(characterId, "One too many");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(result, "$.violations[0].message")).isEqualTo("The character has too many sheets (" + limit + ")");
    }

    @Test
    void createCharacterSheet_characterAtLimit_storesNoFurtherRowNorFile() throws Exception {
        UUID characterId = characterWithOwner("Alira");
        int limit = sheetConfig.getMaxSheetsPerCharacter();
        createSheets(characterId, limit);

        uploadSheet(characterId, "One too many");

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

    @Test
    void findAllCharacterSheetsByCharacter_noSort_returnsOldestFirst() {
        UUID characterId = characterWithOwner("Alira");
        createSheet(characterId, "First");
        createSheet(characterId, "Second");
        createSheet(characterId, "Third");

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets", characterId).exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly("First", "Second", "Third");
    }

    @Test
    void findAllCharacterSheetsByCharacter_descending_returnsNewestFirst() {
        UUID characterId = characterWithOwner("Alira");
        createSheet(characterId, "First");
        createSheet(characterId, "Second");
        createSheet(characterId, "Third");

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets?descending=true", characterId).exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly("Third", "Second", "First");
    }

    @Test
    void findAllCharacterSheetsByCharacter_sortByTitle_ordersAlphabetically() {
        UUID characterId = characterWithOwner("Alira");
        createSheet(characterId, "Charlie");
        createSheet(characterId, "Alpha");
        createSheet(characterId, "Bravo");

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets?sort=title", characterId).exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly("Alpha", "Bravo", "Charlie");
    }

    @Test
    void findAllCharacterSheetsByCharacter_sortBySystem_ordersBySystem() {
        UUID characterId = characterWithOwner("Alira");
        createSheet(characterId, "One", "Pathfinder 2e");
        createSheet(characterId, "Two", "Call of Cthulhu");
        createSheet(characterId, "Three", "D&D 5e");

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets?sort=system", characterId).exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly("Two", "Three", "One");
    }

    @Test
    void findAllCharacterSheetsByCharacter_unknownSortKey_fallsBackToDefaultOrder() {
        UUID characterId = characterWithOwner("Alira");
        createSheet(characterId, "First");
        createSheet(characterId, "Second");

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets?sort=version", characterId).exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly("First", "Second");
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
    void findAllCharacterSheetsByCharacter_pagination_returnsRequestedPageAndTotals() {
        UUID characterId = characterWithOwner("Alira");
        createSheets(characterId, 5);

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets?page=2&size=2", characterId).exchange();

        assertThat(listAt(result, "$.list[*].title")).containsExactly("Sheet 3", "Sheet 4");
        assertThat(List.of(json(result, "$.totalRows"), json(result, "$.totalPages"))).containsExactly("5", "3");
    }

    @Test
    void findAllCharacterSheetsByCharacter_sheets_exposeUrlAndDownloadUrl() {
        UUID characterId = characterWithOwner("Alira");
        createSheet(characterId, "Alira");

        MvcTestResult result = mvc.get().uri("/characters/{id}/sheets", characterId).exchange();

        assertThat(List.of(json(result, "$.list[0].url"), json(result, "$.list[0].downloadUrl"))).allSatisfy(url -> assertThat(url).startsWith("http"));
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
    void getCharacterSheetById_ownSheet_returnsDetail() {
        UUID characterId = characterWithOwner("Alira");
        UUID id = createSheet(characterId, "Alira, level 9", "D&D 5e");

        MvcTestResult result = mvc.get().uri("/sheets/{id}", id).exchange();

        assertThat(List.of(json(result, "$.id"), json(result, "$.characterId"), json(result, "$.title"), json(result, "$.system"),
                json(result, "$.fileName"), json(result, "$.sizeBytes")))
                .containsExactly(id.toString(), characterId.toString(), "Alira, level 9", "D&D 5e", "sheet.pdf", String.valueOf(PDF.length));
    }

    @Test
    void getCharacterSheetById_ownSheet_returnsUrlOfStoredObjectKey() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");

        MvcTestResult result = mvc.get().uri("/sheets/{id}", id).exchange();

        assertThat(objectKeyOf(json(result, "$.url"))).isEqualTo(sheetObjectKey(id));
    }

    @Test
    void getCharacterSheetById_ownSheet_returnsDownloadUrlOfStoredObjectKey() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");

        MvcTestResult result = mvc.get().uri("/sheets/{id}", id).exchange();

        assertThat(objectKeyOf(json(result, "$.downloadUrl"))).isEqualTo(sheetObjectKey(id));
    }

    @Test
    void getCharacterSheetById_ownSheet_returnsUrlsExpiringAfterConfiguredSeconds() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");

        MvcTestResult result = mvc.get().uri("/sheets/{id}", id).exchange();

        String expected = String.valueOf(storageConfig.getUrlExpiry().toSeconds());
        assertThat(List.of(queryParam(json(result, "$.url"), "X-Amz-Expires"), queryParam(json(result, "$.downloadUrl"), "X-Amz-Expires")))
                .containsOnly(expected);
    }

    @Test
    void getCharacterSheetById_ownSheet_downloadUrlAsksForAttachmentWithOriginalName() {
        UUID characterId = characterWithOwner("Alira");
        UUID id = UUID.fromString(json(uploadSheet(characterId, "Alira", null, pdfFile("Scheda di Alira.pdf"), CURRENT_USER), "$.id"));

        MvcTestResult result = mvc.get().uri("/sheets/{id}", id).exchange();

        String disposition = URLDecoder.decode(queryParam(json(result, "$.downloadUrl"), "response-content-disposition"), StandardCharsets.UTF_8);
        assertThat(disposition).isEqualTo("attachment; filename*=UTF-8''Scheda%20di%20Alira.pdf");
    }

    @Test
    void getCharacterSheetById_ownSheet_urlHasNoContentDispositionOverride() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");

        MvcTestResult result = mvc.get().uri("/sheets/{id}", id).exchange();

        assertThat(queryParam(json(result, "$.url"), "response-content-disposition")).isNull();
    }

    @Test
    void getCharacterSheetById_ownSheet_urlServesThePdfBytes() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");
        String url = json(mvc.get().uri("/sheets/{id}", id).exchange(), "$.url");

        HttpResponse<byte[]> response = download(url);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(PDF);
    }

    @Test
    void getCharacterSheetById_ownSheet_urlServesPdfContentType() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");
        String url = json(mvc.get().uri("/sheets/{id}", id).exchange(), "$.url");

        HttpResponse<byte[]> response = download(url);

        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("application/pdf"));
    }

    @Test
    void getCharacterSheetById_ownSheet_downloadUrlServesTheFileAsAttachment() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");
        String url = json(mvc.get().uri("/sheets/{id}", id).exchange(), "$.downloadUrl");

        HttpResponse<byte[]> response = download(url);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(disposition -> assertThat(disposition).startsWith("attachment").contains("sheet.pdf"));
    }

    @Test
    void getCharacterSheetById_ownSheet_downloadUrlServesTheSameBytes() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");
        String url = json(mvc.get().uri("/sheets/{id}", id).exchange(), "$.downloadUrl");

        HttpResponse<byte[]> response = download(url);

        assertThat(response.body()).isEqualTo(PDF);
    }

    @Test
    void getCharacterSheetById_sheetOfOtherUser_returnsNotFound() {
        createAppUser();
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        UUID othersSheet = UUID.fromString(json(uploadSheet(othersCharacter, "Secret", null, pdfFile("s.pdf"), TestJwt.otherUser()), "$.id"));

        assertThat(mvc.get().uri("/sheets/{id}", othersSheet)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getCharacterSheetById_unknownId_returnsNotFound() {
        createAppUser();

        assertThat(mvc.get().uri("/sheets/{id}", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- PUT /sheets/{id} ----

    @Test
    void updateCharacterSheet_validBody_updatesTitleAndSystem() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira, level 9", "D&D 5e");

        MvcTestResult result = mvc.put().uri("/sheets/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Alira, level 10\",\"system\":\"Pathfinder 2e\"}").exchange();

        assertThat(result).hasStatusOk();
        Map<String, Object> row = jdbc.queryForMap("select title, game_system from character_sheet where id = ?", id);
        assertThat(row).containsEntry("title", "Alira, level 10").containsEntry("game_system", "Pathfinder 2e");
    }

    @Test
    void updateCharacterSheet_blankSystem_removesSystem() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira", "D&D 5e");

        mvc.put().uri("/sheets/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Alira\",\"system\":\"\"}").exchange();

        assertThat(jdbc.queryForObject("select game_system from character_sheet where id = ?", String.class, id)).isNull();
    }

    @Test
    void updateCharacterSheet_validBody_keepsFileAndDocument() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");
        String keyBefore = sheetObjectKey(id);

        mvc.put().uri("/sheets/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Renamed\"}").exchange();

        assertThat(storedObjectKeys()).containsExactly(keyBefore);
        assertThat(sheetObjectKey(id)).isEqualTo(keyBefore);
    }

    @Test
    void updateCharacterSheet_validBody_incrementsVersion() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");

        mvc.put().uri("/sheets/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Renamed\"}").exchange();

        assertThat(jdbc.queryForObject("select version from character_sheet where id = ?", Long.class, id)).isEqualTo(2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\"\"}", "{\"title\":\"   \"}", "{\"system\":\"D&D 5e\"}"})
    void updateCharacterSheet_invalidBody_returnsBadRequestAndKeepsSheet(String body) {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira", "D&D 5e");

        MvcTestResult result = mvc.put().uri("/sheets/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("select title from character_sheet where id = ?", String.class, id)).isEqualTo("Alira");
    }

    @Test
    void updateCharacterSheet_sheetOfOtherUser_returnsNotFoundAndKeepsSheet() {
        createAppUser();
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        UUID othersSheet = UUID.fromString(json(uploadSheet(othersCharacter, "Secret", null, pdfFile("s.pdf"), TestJwt.otherUser()), "$.id"));

        MvcTestResult result = mvc.put().uri("/sheets/{id}", othersSheet).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hijacked\"}").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select title from character_sheet where id = ?", String.class, othersSheet)).isEqualTo("Secret");
    }

    // ---- PUT /sheets/{id}/file ----

    @Test
    void updateCharacterSheetFile_validPdf_keepsOnlyNewFile() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");
        String oldKey = sheetObjectKey(id);

        MvcTestResult result = mvc.put().uri("/sheets/{id}/file", id).multipart().file(pdfFile("edited.pdf")).exchange();

        assertThat(result).hasStatusOk();
        String newKey = sheetObjectKey(id);
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(storedObjectKeys()).containsExactly(newKey);
    }

    @Test
    void updateCharacterSheetFile_validPdf_keepsOneDocumentRowWithNewName() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");

        mvc.put().uri("/sheets/{id}/file", id).multipart().file(pdfFile("edited.pdf")).exchange();

        assertThat(jdbc.queryForList("select name from document", String.class)).containsExactly("edited.pdf");
    }

    @Test
    void updateCharacterSheetFile_validPdf_returnsNewFileNameAndKeepsTitleAndSystem() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira, level 9", "D&D 5e");

        MvcTestResult result = mvc.put().uri("/sheets/{id}/file", id).multipart().file(pdfFile("edited.pdf")).exchange();

        assertThat(List.of(json(result, "$.fileName"), json(result, "$.title"), json(result, "$.system")))
                .containsExactly("edited.pdf", "Alira, level 9", "D&D 5e");
    }

    @Test
    void updateCharacterSheetFile_validPdf_returnsUrlOfNewObjectKey() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");

        MvcTestResult result = mvc.put().uri("/sheets/{id}/file", id).multipart().file(pdfFile("edited.pdf")).exchange();

        assertThat(objectKeyOf(json(result, "$.url"))).isEqualTo(sheetObjectKey(id));
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
    void updateCharacterSheetFile_sheetOfOtherUser_returnsNotFoundAndStoresNoNewFile() throws Exception {
        createAppUser();
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        UUID othersSheet = UUID.fromString(json(uploadSheet(othersCharacter, "Secret", null, pdfFile("s.pdf"), TestJwt.otherUser()), "$.id"));
        String key = sheetObjectKey(othersSheet);

        MvcTestResult result = mvc.put().uri("/sheets/{id}/file", othersSheet).multipart().file(pdfFile("edited.pdf")).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(storedObjectKeys()).containsExactly(key);
    }

    // ---- DELETE /sheets/{id} ----

    @Test
    void deleteCharacterSheet_ownSheet_removesRowDocumentAndFile() throws Exception {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");

        MvcTestResult result = mvc.delete().uri("/sheets/{id}", id).exchange();

        assertThat(result).hasStatusOk();
        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(0);
    }

    @Test
    void deleteCharacterSheet_ownSheet_returnsConfirmationMessage() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");

        assertThat(mvc.delete().uri("/sheets/{id}", id)).bodyJson().extractingPath("$.message").isEqualTo("Sheet deleted");
    }

    @Test
    void deleteCharacterSheet_ownSheet_keepsOtherSheetsOfTheCharacter() throws Exception {
        UUID characterId = characterWithOwner("Alira");
        UUID first = createSheet(characterId, "First");
        UUID second = createSheet(characterId, "Second");
        String secondKey = sheetObjectKey(second);

        mvc.delete().uri("/sheets/{id}", first).exchange();

        assertThat(jdbc.queryForList("select id from character_sheet", UUID.class)).containsExactly(second);
        assertThat(storedObjectKeys()).containsExactly(secondKey);
    }

    @Test
    void deleteCharacterSheet_ownSheet_keepsTheCharacter() {
        UUID characterId = characterWithOwner("Alira");
        UUID id = createSheet(characterId, "Alira");

        mvc.delete().uri("/sheets/{id}", id).exchange();

        assertThat(mvc.get().uri("/characters/{id}", characterId)).hasStatusOk();
    }

    @Test
    void deleteCharacterSheet_deletedSheet_isNotFoundAnymore() {
        UUID id = createSheet(characterWithOwner("Alira"), "Alira");
        mvc.delete().uri("/sheets/{id}", id).exchange();

        assertThat(mvc.get().uri("/sheets/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCharacterSheet_sheetOfOtherUser_returnsNotFoundAndKeepsEverything() throws Exception {
        createAppUser();
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        UUID othersSheet = UUID.fromString(json(uploadSheet(othersCharacter, "Secret", null, pdfFile("s.pdf"), TestJwt.otherUser()), "$.id"));

        MvcTestResult result = mvc.delete().uri("/sheets/{id}", othersSheet).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(List.of(countRows("character_sheet"), countRows("document"), storedObjectKeys().size())).containsOnly(1);
    }

    // ---- cascade ----

    @Test
    void deleteGameCharacter_withSheets_removesSheetsDocumentsAndFiles() throws Exception {
        UUID characterId = characterWithOwner("Alira");
        createSheets(characterId, 3);

        MvcTestResult result = mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(result).hasStatusOk();
        assertThat(List.of(countRows("game_character"), countRows("character_sheet"), countRows("document"), storedObjectKeys().size()))
                .containsOnly(0);
    }

    @Test
    void deleteGameCharacter_withSheetsAvatarAndCover_removesEveryFile() throws Exception {
        UUID characterId = characterWithOwner("Alira");
        UUID diaryId = createDiary(characterId, "Chapter one");
        createSheet(characterId, "Alira");
        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(pngFile("avatar.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("cover.png")).exchange();

        mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(List.of(countRows("document"), storedObjectKeys().size())).containsOnly(0);
    }

    @Test
    void deleteGameCharacter_withSheets_keepsSheetsOfOtherCharacters() throws Exception {
        UUID alira = characterWithOwner("Alira");
        UUID borin = createGameCharacter("Borin");
        createSheet(alira, "Alira");
        UUID borinSheet = createSheet(borin, "Borin");
        String borinKey = sheetObjectKey(borinSheet);

        mvc.delete().uri("/characters/{id}", alira).exchange();

        assertThat(jdbc.queryForList("select id from character_sheet", UUID.class)).containsExactly(borinSheet);
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
    void deleteAppUser_userWithSheets_removesSheetsDocumentsAndFiles() throws Exception {
        UUID alira = characterWithOwner("Alira");
        UUID borin = createGameCharacter("Borin");
        createSheets(alira, 2);
        createSheet(borin, "Borin");

        MvcTestResult result = mvc.delete().uri("/me").exchange();

        assertThat(result).hasStatusOk();
        assertThat(List.of(countRows("app_user"), countRows("game_character"), countRows("character_sheet"), countRows("document"),
                storedObjectKeys().size())).containsOnly(0);
    }

    @Test
    void deleteAppUser_otherUserWithSheets_keepsSheetsOfOtherUser() throws Exception {
        createAppUser();
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        UUID othersSheet = UUID.fromString(json(uploadSheet(othersCharacter, "Secret", null, pdfFile("s.pdf"), TestJwt.otherUser()), "$.id"));
        String key = sheetObjectKey(othersSheet);

        mvc.delete().uri("/me").exchange();

        assertThat(jdbc.queryForList("select id from character_sheet", UUID.class)).containsExactly(othersSheet);
        assertThat(storedObjectKeys()).containsExactly(key);
    }
}
