package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.gdr.diary.controller.CharacterSheetController;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.character_sheet.BaseCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.DetailCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.InsertCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.UpdateCharacterSheetDTO;
import it.gdr.diary.dto.search.CharacterSheetSearchRequest;
import it.gdr.diary.exception.ExceptionMapper;
import it.gdr.diary.services.CharacterSheetService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CharacterSheetControllerTest {

    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final UUID SHEET_ID = UUID.fromString("7d1e5a52-9a0c-4b57-8c64-0f7a1d9a4e21");
    private static final String VALID_UPDATE = """
            {"title":"Alira, level 10","system":"Pathfinder 2e"}
            """;

    @Mock
    private CharacterSheetService service;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new CharacterSheetController(service))
                .setControllerAdvice(new ExceptionMapper())
                .build());
    }

    private static Stream<String> invalidSearchQueries() {
        return Stream.of("page=0", "size=0", "size=101", "title=" + "a".repeat(101));
    }

    private static Stream<Arguments> invalidUploads() {
        return Stream.of(
                Arguments.of(Map.of("title", ""), "file"),
                Arguments.of(Map.of("title", "   "), "file"),
                Arguments.of(Map.of("system", "D&D 5e"), "file"),
                Arguments.of(Map.of("title", "a".repeat(101)), "file"),
                Arguments.of(Map.of("title", "Alira", "system", "a".repeat(101)), "file"),
                Arguments.of(Map.of("title", "Alira"), "pdf"));
    }

    private static Stream<String> invalidUpdateBodies() {
        return Stream.of(
                "{}",
                "{\"title\":\"\"}",
                "{\"title\":\"   \"}",
                "{\"title\":null}",
                "{\"system\":\"D&D 5e\"}",
                "{\"title\":\"" + "a".repeat(101) + "\"}",
                "{\"title\":\"Alira\",\"system\":\"" + "a".repeat(101) + "\"}",
                "{\"title\":");
    }

    private static Stream<Arguments> notUuidRequests() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/characters/not-a-uuid/sheets"),
                Arguments.of(HttpMethod.GET, "/sheets/not-a-uuid"),
                Arguments.of(HttpMethod.DELETE, "/sheets/not-a-uuid"));
    }

    private DetailCharacterSheetDTO detail(String title) {
        return new DetailCharacterSheetDTO(SHEET_ID, CHARACTER_ID, title, "D&D 5e", "sheet.pdf", 2048L, null,
                "https://storage.example/view", "https://storage.example/download");
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "sheet.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{'%', 'P', 'D', 'F', '-'});
    }

    private MvcTestResult upload(Map<String, String> fields, String partName) {
        MockMultipartFile part = new MockMultipartFile(partName, "sheet.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1});
        var request = mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(part);
        fields.forEach(request::param);
        return request.exchange();
    }

    // ---- GET /characters/{characterId}/sheets ----

    @Test
    void findAllCharacterSheetsByCharacter_sheetsFound_returnsOkWithPagedListAndUrls() {
        BaseCharacterSheetDTO sheet = new BaseCharacterSheetDTO(SHEET_ID, CHARACTER_ID, "Alira, level 9", "D&D 5e", 2048L, null,
                "https://storage.example/view", "https://storage.example/download");
        when(service.findAllCharacterSheetsByCharacter(eq(CHARACTER_ID), any())).thenReturn(PagedResultDTO.of(List.of(sheet), 1, 1, 20));

        MvcTestResult result = mvc.get().uri("/characters/{characterId}/sheets", CHARACTER_ID).exchange();

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.list[0].downloadUrl").isEqualTo("https://storage.example/download");
    }

    @Test
    void findAllCharacterSheetsByCharacter_queryParameters_bindsSearchRequest() {
        when(service.findAllCharacterSheetsByCharacter(eq(CHARACTER_ID), any())).thenReturn(PagedResultDTO.of(List.of(), 0, 2, 5));
        ArgumentCaptor<CharacterSheetSearchRequest> request = ArgumentCaptor.forClass(CharacterSheetSearchRequest.class);

        mvc.get().uri("/characters/{characterId}/sheets?title=ali&system=Pathfinder&page=2&size=5&sort=system&descending=true", CHARACTER_ID)
                .exchange();

        verify(service).findAllCharacterSheetsByCharacter(eq(CHARACTER_ID), request.capture());
        assertThat(request.getValue())
                .extracting(CharacterSheetSearchRequest::getTitle, CharacterSheetSearchRequest::getSystem,
                        CharacterSheetSearchRequest::getPage, CharacterSheetSearchRequest::getSize,
                        CharacterSheetSearchRequest::getSort, CharacterSheetSearchRequest::isDescending)
                .containsExactly("ali", "Pathfinder", 2, 5, "system", true);
    }

    @ParameterizedTest
    @MethodSource("invalidSearchQueries")
    void findAllCharacterSheetsByCharacter_invalidSearchParameters_returnsBadRequestWithoutCallingService(String query) {
        MvcTestResult result = mvc.get().uri("/characters/{characterId}/sheets?" + query, CHARACTER_ID).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    // ---- POST /characters/{characterId}/sheets ----

    @Test
    void createCharacterSheet_validMultipart_returnsCreatedDetailBuiltFromFields() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), eq(new InsertCharacterSheetDTO("Alira, level 9", "D&D 5e")),
                argThat(file -> "sheet.pdf".equals(file.getOriginalFilename())))).thenReturn(detail("Alira, level 9"));

        MvcTestResult result = mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf())
                .param("title", "Alira, level 9").param("system", "D&D 5e").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.id").isEqualTo(SHEET_ID.toString());
    }

    @Test
    void createCharacterSheet_systemOmitted_passesNullSystem() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), eq(new InsertCharacterSheetDTO("Alira", null)), any()))
                .thenReturn(detail("Alira"));

        MvcTestResult result = mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf())
                .param("title", "Alira").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createCharacterSheet_titleOf100Chars_returnsCreated() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), any(), any())).thenReturn(detail("a".repeat(100)));

        MvcTestResult result = mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf())
                .param("title", "a".repeat(100)).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @ParameterizedTest
    @MethodSource("invalidUploads")
    void createCharacterSheet_invalidFieldsOrMissingFilePart_returnsBadRequestWithoutCallingService(Map<String, String> fields,
                                                                                                    String partName) {
        MvcTestResult result = upload(fields, partName);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    // ---- GET /sheets/{id} ----

    @Test
    void getCharacterSheetById_ownedSheet_returnsDetailWithBothUrls() {
        when(service.getCharacterSheetById(SHEET_ID)).thenReturn(detail("Alira, level 9"));

        MvcTestResult result = mvc.get().uri("/sheets/{id}", SHEET_ID).exchange();

        assertThat(result).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.title", title -> title.assertThat().isEqualTo("Alira, level 9"))
                .hasPathSatisfying("$.url", url -> url.assertThat().isEqualTo("https://storage.example/view"))
                .hasPathSatisfying("$.downloadUrl", url -> url.assertThat().isEqualTo("https://storage.example/download"));
    }

    @Test
    void getCharacterSheetById_notFound_returnsNotFoundWithReason() {
        when(service.getCharacterSheetById(SHEET_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sheet not found"));

        MvcTestResult result = mvc.get().uri("/sheets/{id}", SHEET_ID).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.violations[0].message").isEqualTo("Sheet not found");
    }

    // ---- PUT /sheets/{id} ----

    @Test
    void updateCharacterSheet_validBody_returnsDetailBuiltFromBody() {
        when(service.updateCharacterSheet(SHEET_ID, new UpdateCharacterSheetDTO("Alira, level 10", "Pathfinder 2e")))
                .thenReturn(detail("Alira, level 10"));

        MvcTestResult result = mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE).exchange();

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.title").isEqualTo("Alira, level 10");
    }

    @Test
    void updateCharacterSheet_systemNull_isAccepted() {
        when(service.updateCharacterSheet(eq(SHEET_ID), any())).thenReturn(detail("Alira"));

        MvcTestResult result = mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Alira\",\"system\":null}").exchange();

        assertThat(result).hasStatusOk();
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateBodies")
    void updateCharacterSheet_invalidBody_returnsBadRequestWithoutCallingService(String body) {
        MvcTestResult result = mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    // ---- PUT /sheets/{id}/file ----

    @Test
    void updateCharacterSheetFile_pdfPart_returnsDetailForUploadedFile() {
        when(service.updateCharacterSheetFile(eq(SHEET_ID), argThat(file -> "sheet.pdf".equals(file.getOriginalFilename()))))
                .thenReturn(detail("Alira, level 9"));

        MvcTestResult result = mvc.put().uri("/sheets/{id}/file", SHEET_ID).multipart().file(pdf()).exchange();

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.id").isEqualTo(SHEET_ID.toString());
    }

    @Test
    void updateCharacterSheetFile_missingFilePart_returnsBadRequestWithoutCallingService() {
        MockMultipartFile wrongPart = new MockMultipartFile("pdf", "sheet.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1});

        MvcTestResult result = mvc.put().uri("/sheets/{id}/file", SHEET_ID).multipart().file(wrongPart).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    // ---- DELETE /sheets/{id} ----

    @Test
    void deleteCharacterSheet_ownedSheet_deletesThroughServiceAndReturnsConfirmationMessage() {
        MvcTestResult result = mvc.delete().uri("/sheets/{id}", SHEET_ID).exchange();

        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Sheet deleted");
        verify(service).deleteCharacterSheet(SHEET_ID);
    }

    // ---- identifiers ----

    @ParameterizedTest
    @MethodSource("notUuidRequests")
    void anyEndpoint_identifierNotUuid_returnsBadRequestWithoutCallingService(HttpMethod method, String path) {
        MvcTestResult result = mvc.method(method).uri(path).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }
}
