package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.services.CharacterSheetService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;
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

    private DetailCharacterSheetDTO detail(String title) {
        return new DetailCharacterSheetDTO(SHEET_ID, CHARACTER_ID, title, "D&D 5e", "sheet.pdf", 2048L, null,
                "https://storage.example/view", "https://storage.example/download");
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "sheet.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{'%', 'P', 'D', 'F', '-'});
    }

    // ---- GET /characters/{characterId}/sheets ----

    @Test
    void findAllCharacterSheetsByCharacter_noParameters_returnsOk() {
        when(service.findAllCharacterSheetsByCharacter(eq(CHARACTER_ID), any())).thenReturn(PagedResultDTO.of(List.of(), 0, 1, 20));

        assertThat(mvc.get().uri("/characters/{characterId}/sheets", CHARACTER_ID)).hasStatusOk();
    }

    @Test
    void findAllCharacterSheetsByCharacter_sheetsFound_returnsPagedListWithUrls() {
        BaseCharacterSheetDTO sheet = new BaseCharacterSheetDTO(SHEET_ID, CHARACTER_ID, "Alira, level 9", "D&D 5e", 2048L, null,
                "https://storage.example/view", "https://storage.example/download");
        when(service.findAllCharacterSheetsByCharacter(eq(CHARACTER_ID), any())).thenReturn(PagedResultDTO.of(List.of(sheet), 1, 1, 20));

        assertThat(mvc.get().uri("/characters/{characterId}/sheets", CHARACTER_ID)).bodyJson()
                .extractingPath("$.list[0].downloadUrl").isEqualTo("https://storage.example/download");
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
    @ValueSource(strings = {"page=0", "size=0", "size=101"})
    void findAllCharacterSheetsByCharacter_paginationOutOfRange_returnsBadRequestWithoutCallingService(String query) {
        assertThat(mvc.get().uri("/characters/{characterId}/sheets?" + query, CHARACTER_ID)).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void findAllCharacterSheetsByCharacter_titleTooLong_returnsBadRequest() {
        assertThat(mvc.get().uri("/characters/{characterId}/sheets?title=" + "a".repeat(101), CHARACTER_ID))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllCharacterSheetsByCharacter_characterNotFound_returnsNotFound() {
        when(service.findAllCharacterSheetsByCharacter(eq(CHARACTER_ID), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));

        assertThat(mvc.get().uri("/characters/{characterId}/sheets", CHARACTER_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void findAllCharacterSheetsByCharacter_characterIdNotUuid_returnsBadRequest() {
        assertThat(mvc.get().uri("/characters/not-a-uuid/sheets")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ---- POST /characters/{characterId}/sheets ----

    @Test
    void createCharacterSheet_validMultipart_returnsCreated() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), any(), any())).thenReturn(detail("Alira, level 9"));

        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf())
                .param("title", "Alira, level 9").param("system", "D&D 5e"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createCharacterSheet_validMultipart_returnsDetail() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), any(), any())).thenReturn(detail("Alira, level 9"));

        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf())
                .param("title", "Alira, level 9"))
                .bodyJson().extractingPath("$.id").isEqualTo(SHEET_ID.toString());
    }

    @Test
    void createCharacterSheet_validMultipart_passesFieldsAndFileToService() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), any(), any())).thenReturn(detail("Alira, level 9"));
        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);

        mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf())
                .param("title", "Alira, level 9").param("system", "D&D 5e").exchange();

        verify(service).createCharacterSheet(eq(CHARACTER_ID), eq(new InsertCharacterSheetDTO("Alira, level 9", "D&D 5e")), file.capture());
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("sheet.pdf");
    }

    @Test
    void createCharacterSheet_systemOmitted_passesNullSystem() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), any(), any())).thenReturn(detail("Alira"));

        mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf())
                .param("title", "Alira").exchange();

        verify(service).createCharacterSheet(eq(CHARACTER_ID), eq(new InsertCharacterSheetDTO("Alira", null)), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createCharacterSheet_blankTitle_returnsBadRequestWithoutCallingService(String title) {
        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf()).param("title", title))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void createCharacterSheet_titleMissing_returnsBadRequest() {
        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf()))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCharacterSheet_titleTooLong_returnsBadRequest() {
        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf())
                .param("title", "a".repeat(101)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCharacterSheet_titleOf100Chars_returnsCreated() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), any(), any())).thenReturn(detail("a".repeat(100)));

        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf())
                .param("title", "a".repeat(100)))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createCharacterSheet_systemTooLong_returnsBadRequest() {
        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf())
                .param("title", "Alira").param("system", "a".repeat(101)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCharacterSheet_missingFilePart_returnsBadRequest() {
        MockMultipartFile wrongPart = new MockMultipartFile("pdf", "sheet.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1});

        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(wrongPart)
                .param("title", "Alira"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCharacterSheet_missingFilePart_doesNotCallService() {
        MockMultipartFile wrongPart = new MockMultipartFile("pdf", "sheet.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1});

        mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(wrongPart).param("title", "Alira").exchange();

        verifyNoInteractions(service);
    }

    @Test
    void createCharacterSheet_invalidFile_returnsBadRequestWithReason() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), any(), any())).thenThrow(new ServiceException("Unsupported file type"));

        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf()).param("title", "Alira"))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Unsupported file type");
    }

    @Test
    void createCharacterSheet_limitReached_returnsBadRequestWithReason() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), any(), any()))
                .thenThrow(new ServiceException("The character has too many sheets (10)"));

        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf()).param("title", "Alira"))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("The character has too many sheets (10)");
    }

    @Test
    void createCharacterSheet_characterNotFound_returnsNotFound() {
        when(service.createCharacterSheet(eq(CHARACTER_ID), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));

        assertThat(mvc.post().uri("/characters/{characterId}/sheets", CHARACTER_ID).multipart().file(pdf()).param("title", "Alira"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- GET /sheets/{id} ----

    @Test
    void getCharacterSheetById_ownedSheet_returnsDetail() {
        when(service.getCharacterSheetById(SHEET_ID)).thenReturn(detail("Alira, level 9"));

        assertThat(mvc.get().uri("/sheets/{id}", SHEET_ID)).bodyJson().extractingPath("$.title").isEqualTo("Alira, level 9");
    }

    @Test
    void getCharacterSheetById_ownedSheet_exposesBothUrls() {
        when(service.getCharacterSheetById(SHEET_ID)).thenReturn(detail("Alira, level 9"));

        assertThat(mvc.get().uri("/sheets/{id}", SHEET_ID)).bodyJson()
                .extractingPath("$.url").isEqualTo("https://storage.example/view");
        assertThat(mvc.get().uri("/sheets/{id}", SHEET_ID)).bodyJson()
                .extractingPath("$.downloadUrl").isEqualTo("https://storage.example/download");
    }

    @Test
    void getCharacterSheetById_notFound_returnsNotFoundWithReason() {
        when(service.getCharacterSheetById(SHEET_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sheet not found"));

        assertThat(mvc.get().uri("/sheets/{id}", SHEET_ID)).bodyJson()
                .extractingPath("$.violations[0].message").isEqualTo("Sheet not found");
    }

    @Test
    void getCharacterSheetById_idNotUuid_returnsBadRequest() {
        assertThat(mvc.get().uri("/sheets/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ---- PUT /sheets/{id} ----

    @Test
    void updateCharacterSheet_validBody_returnsDetail() {
        when(service.updateCharacterSheet(eq(SHEET_ID), any())).thenReturn(detail("Alira, level 10"));

        assertThat(mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .bodyJson().extractingPath("$.title").isEqualTo("Alira, level 10");
    }

    @Test
    void updateCharacterSheet_validBody_passesIdAndBodyToService() {
        when(service.updateCharacterSheet(eq(SHEET_ID), any())).thenReturn(detail("Alira, level 10"));

        mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE).exchange();

        verify(service).updateCharacterSheet(SHEET_ID, new UpdateCharacterSheetDTO("Alira, level 10", "Pathfinder 2e"));
    }

    @Test
    void updateCharacterSheet_systemNull_isAccepted() {
        when(service.updateCharacterSheet(eq(SHEET_ID), any())).thenReturn(detail("Alira"));

        assertThat(mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Alira\",\"system\":null}"))
                .hasStatusOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"title\":\"\"}",
            "{\"title\":\"   \"}",
            "{\"title\":null}",
            "{\"system\":\"D&D 5e\"}"})
    void updateCharacterSheet_invalidBody_returnsBadRequestWithoutCallingService(String body) {
        assertThat(mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void updateCharacterSheet_titleTooLong_returnsBadRequest() {
        String body = "{\"title\":\"" + "a".repeat(101) + "\"}";

        assertThat(mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateCharacterSheet_systemTooLong_returnsBadRequest() {
        String body = "{\"title\":\"Alira\",\"system\":\"" + "a".repeat(101) + "\"}";

        assertThat(mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateCharacterSheet_malformedJson_returnsBadRequest() {
        assertThat(mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON).content("{\"title\":"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateCharacterSheet_notFound_returnsNotFound() {
        when(service.updateCharacterSheet(eq(SHEET_ID), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sheet not found"));

        assertThat(mvc.put().uri("/sheets/{id}", SHEET_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- PUT /sheets/{id}/file ----

    @Test
    void updateCharacterSheetFile_pdfPart_returnsDetail() {
        when(service.updateCharacterSheetFile(eq(SHEET_ID), any())).thenReturn(detail("Alira, level 9"));

        assertThat(mvc.put().uri("/sheets/{id}/file", SHEET_ID).multipart().file(pdf()))
                .bodyJson().extractingPath("$.id").isEqualTo(SHEET_ID.toString());
    }

    @Test
    void updateCharacterSheetFile_pdfPart_passesFileToService() {
        when(service.updateCharacterSheetFile(eq(SHEET_ID), any())).thenReturn(detail("Alira, level 9"));
        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);

        mvc.put().uri("/sheets/{id}/file", SHEET_ID).multipart().file(pdf()).exchange();

        verify(service).updateCharacterSheetFile(eq(SHEET_ID), file.capture());
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("sheet.pdf");
    }

    @Test
    void updateCharacterSheetFile_invalidFile_returnsBadRequestWithReason() {
        when(service.updateCharacterSheetFile(eq(SHEET_ID), any())).thenThrow(new ServiceException("Unsupported file type"));

        assertThat(mvc.put().uri("/sheets/{id}/file", SHEET_ID).multipart().file(pdf()))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Unsupported file type");
    }

    @Test
    void updateCharacterSheetFile_missingFilePart_returnsBadRequestWithoutCallingService() {
        MockMultipartFile wrongPart = new MockMultipartFile("pdf", "sheet.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1});

        assertThat(mvc.put().uri("/sheets/{id}/file", SHEET_ID).multipart().file(wrongPart)).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void updateCharacterSheetFile_notFound_returnsNotFound() {
        when(service.updateCharacterSheetFile(eq(SHEET_ID), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sheet not found"));

        assertThat(mvc.put().uri("/sheets/{id}/file", SHEET_ID).multipart().file(pdf())).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- DELETE /sheets/{id} ----

    @Test
    void deleteCharacterSheet_ownedSheet_returnsConfirmationMessage() {
        assertThat(mvc.delete().uri("/sheets/{id}", SHEET_ID)).bodyJson()
                .extractingPath("$.message").isEqualTo("Sheet deleted");
    }

    @Test
    void deleteCharacterSheet_ownedSheet_deletesThroughService() {
        mvc.delete().uri("/sheets/{id}", SHEET_ID).exchange();

        verify(service).deleteCharacterSheet(SHEET_ID);
    }

    @Test
    void deleteCharacterSheet_notFound_returnsNotFound() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sheet not found")).when(service).deleteCharacterSheet(SHEET_ID);

        assertThat(mvc.delete().uri("/sheets/{id}", SHEET_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCharacterSheet_idNotUuid_returnsBadRequest() {
        assertThat(mvc.delete().uri("/sheets/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
