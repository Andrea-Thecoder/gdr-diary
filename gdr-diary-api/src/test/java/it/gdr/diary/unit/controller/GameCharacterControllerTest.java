package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.ExpressionList;
import it.gdr.diary.controller.GameCharacterController;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.game_character.BaseGameCharacterDTO;
import it.gdr.diary.dto.game_character.DetailGameCharacterDTO;
import it.gdr.diary.dto.game_character.InsertGameCharacterDTO;
import it.gdr.diary.dto.game_character.UpdateGameCharacterDTO;
import it.gdr.diary.dto.search.GameCharacterSearchRequest;
import it.gdr.diary.exception.ExceptionMapper;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.GameCharacterKind;
import it.gdr.diary.services.GameCharacterService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class GameCharacterControllerTest {

    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final String VALID_INSERT = """
            {"name":"Aragorn","kind":"PLAYER","campaignName":"Lost Mine","backgroundColor":"#1A2B3C"}
            """;
    private static final String VALID_UPDATE = """
            {"name":"Strider","campaignName":"Curse of Strahd","backgroundColor":"#000000"}
            """;

    @Mock
    private GameCharacterService service;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new GameCharacterController(service))
                .setControllerAdvice(new ExceptionMapper())
                .build());
    }

    private DetailGameCharacterDTO detail(String name) {
        return new DetailGameCharacterDTO(CHARACTER_ID, name, GameCharacterKind.PLAYER, "Lost Mine", "#1A2B3C", null, null, null);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
    }

    private static String insertBody(String fields) {
        return "{\"name\":\"Aragorn\",\"kind\":\"PLAYER\"" + fields + "}";
    }

    static Stream<String> invalidInsertBodies() {
        return Stream.of(
                "{\"kind\":\"PLAYER\"}",
                "{\"name\":\" \",\"kind\":\"PLAYER\"}",
                "{\"name\":\"a\\u0000b\",\"kind\":\"PLAYER\"}",
                "{\"name\":\"" + "a".repeat(256) + "\",\"kind\":\"PLAYER\"}",
                "{\"name\":\"Aragorn\"}",
                "{\"name\":\"Aragorn\",\"kind\":null}",
                "{\"name\":\"Aragorn\",\"kind\":\"WIZARD\"}",
                insertBody(",\"backgroundColor\":\"red\""),
                insertBody(",\"backgroundColor\":\"#12345\""),
                insertBody(",\"campaignName\":\"" + "a".repeat(256) + "\""),
                "{\"name\":");
    }

    static Stream<String> invalidUpdateBodies() {
        return Stream.of(
                "{\"name\":\"\"}",
                "{\"name\":\"Aragorn\",\"backgroundColor\":\"red\"}",
                "{\"name\":\"Aragorn\",\"backgroundColor\":\"#12345\"}",
                "{\"name\":\"Aragorn\",\"campaignName\":\"" + "a".repeat(256) + "\"}");
    }

    @Test
    void findAllGameCharacters_charactersFound_returnsPagedList() {
        BaseGameCharacterDTO character = new BaseGameCharacterDTO(CHARACTER_ID, "Aragorn", GameCharacterKind.PLAYER, "Lost Mine", null, null);
        when(service.findAllGameCharacters(any())).thenReturn(PagedResultDTO.of(List.of(character), 1, 1, 20));

        MvcTestResult result = mvc.get().uri("/characters").exchange();

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.list[0].name").isEqualTo("Aragorn");
    }

    @Test
    void findAllGameCharacters_queryParameters_bindsSearchRequest() {
        when(service.findAllGameCharacters(any())).thenReturn(PagedResultDTO.of(List.of(), 0, 2, 5));
        ArgumentCaptor<GameCharacterSearchRequest> request = ArgumentCaptor.forClass(GameCharacterSearchRequest.class);

        mvc.get().uri("/characters?name=ara&campaignName=mine&page=2&size=5&sort=campaignName&descending=true").exchange();

        verify(service).findAllGameCharacters(request.capture());
        assertThat(request.getValue())
                .extracting(GameCharacterSearchRequest::getName, GameCharacterSearchRequest::getCampaignName,
                        GameCharacterSearchRequest::getPage, GameCharacterSearchRequest::getSize,
                        GameCharacterSearchRequest::getSort, GameCharacterSearchRequest::isDescending)
                .containsExactly("ara", "mine", 2, 5, "campaignName", true);
    }

    @ParameterizedTest
    @CsvSource({"page=0,page", "size=0,size", "size=101,size"})
    void findAllGameCharacters_paginationOutOfRange_returnsViolationOnFieldAndDoesNotCallService(String query, String field) {
        MvcTestResult result = mvc.get().uri("/characters?" + query).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.violations[0].field").isEqualTo(field);
        verifyNoInteractions(service);
    }

    @Test
    void findAllGameCharacters_pageNotANumber_returnsReadableViolation() {
        assertThat(mvc.get().uri("/characters?page=abc"))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Invalid value");
    }

    @Test
    void getGameCharacterById_ownedCharacter_returnsDetail() {
        when(service.getGameCharacterById(CHARACTER_ID)).thenReturn(detail("Aragorn"));

        assertThat(mvc.get().uri("/characters/{id}", CHARACTER_ID)).bodyJson().extractingPath("$.name").isEqualTo("Aragorn");
    }

    @Test
    void getGameCharacterById_notFound_returnsNotFoundWithReasonAsViolation() {
        when(service.getGameCharacterById(CHARACTER_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));

        MvcTestResult result = mvc.get().uri("/characters/{id}", CHARACTER_ID).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                .extractingPath("$.violations[0].message").isEqualTo("Character not found");
    }

    @Test
    void getGameCharacterById_idNotUuid_returnsBadRequest() {
        assertThat(mvc.get().uri("/characters/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_validBody_returnsCreatedDetailAndPassesBodyToService() {
        when(service.createGameCharacter(any())).thenReturn(detail("Aragorn"));

        MvcTestResult result = mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(VALID_INSERT).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.id").isEqualTo(CHARACTER_ID.toString());
        verify(service).createGameCharacter(new InsertGameCharacterDTO("Aragorn", GameCharacterKind.PLAYER, "Lost Mine", "#1A2B3C"));
    }

    @Test
    void createGameCharacter_nameOf255Chars_returnsCreated() {
        when(service.createGameCharacter(any())).thenReturn(detail("a".repeat(255)));
        String body = "{\"name\":\"" + "a".repeat(255) + "\",\"kind\":\"PLAYER\"}";

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.CREATED);
    }

    @ParameterizedTest
    @MethodSource("invalidInsertBodies")
    void createGameCharacter_invalidBody_returnsBadRequestAndDoesNotCallService(String body) {
        MvcTestResult result = mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"name\":\" \",\"kind\":\"PLAYER\"}|name",
            "{\"name\":\"Aragorn\",\"kind\":null}|kind",
            "{\"name\":\"Aragorn\",\"kind\":\"PLAYER\",\"backgroundColor\":\"red\"}|backgroundColor"})
    void createGameCharacter_invalidField_returnsBadRequestTitleAndViolationOnThatField(String body, String field) {
        MvcTestResult result = mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("BAD_REQUEST");
        assertThat(result).bodyJson().extractingPath("$.violations[0].field").isEqualTo(field);
    }

    @Test
    void updateGameCharacter_validBody_returnsDetailAndPassesIdAndBodyToService() {
        when(service.updateGameCharacter(eq(CHARACTER_ID), any())).thenReturn(detail("Strider"));

        MvcTestResult result = mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE).exchange();

        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Strider");
        verify(service).updateGameCharacter(CHARACTER_ID, new UpdateGameCharacterDTO("Strider", "Curse of Strahd", "#000000"));
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateBodies")
    void updateGameCharacter_invalidBody_returnsBadRequestAndDoesNotCallService(String body) {
        MvcTestResult result = mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void updateGameCharacter_invalidBackgroundColor_returnsViolationOnBackgroundColorField() {
        assertThat(mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Aragorn\",\"backgroundColor\":\"red\"}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("backgroundColor");
    }

    @Test
    void deleteGameCharacter_ownedCharacter_returnsConfirmationMessageAndDeletesThroughService() {
        MvcTestResult result = mvc.delete().uri("/characters/{id}", CHARACTER_ID).exchange();

        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Character deleted");
        verify(service).deleteGameCharacter(CHARACTER_ID);
    }

    @Test
    void updateGameCharacterAvatar_imagePart_returnsDetailAndPassesFileToService() {
        when(service.updateGameCharacterAvatar(eq(CHARACTER_ID), any())).thenReturn(detail("Aragorn"));
        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);

        MvcTestResult result = mvc.put().uri("/characters/{id}/avatar", CHARACTER_ID).multipart().file(image()).exchange();

        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(CHARACTER_ID.toString());
        verify(service).updateGameCharacterAvatar(eq(CHARACTER_ID), file.capture());
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("avatar.png");
    }

    @Test
    void updateGameCharacterAvatar_invalidFile_returnsBadRequestWithReason() {
        when(service.updateGameCharacterAvatar(eq(CHARACTER_ID), any())).thenThrow(new ServiceException("Unsupported file type"));

        assertThat(mvc.put().uri("/characters/{id}/avatar", CHARACTER_ID).multipart().file(image()))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Unsupported file type");
    }

    @Test
    void updateGameCharacterAvatar_missingFilePart_returnsBadRequest() {
        MockMultipartFile wrongPart = new MockMultipartFile("image", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1});

        assertThat(mvc.put().uri("/characters/{id}/avatar", CHARACTER_ID).multipart().file(wrongPart))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteGameCharacterAvatar_ownedCharacter_returnsConfirmationMessageAndDeletesThroughService() {
        MvcTestResult result = mvc.delete().uri("/characters/{id}/avatar", CHARACTER_ID).exchange();

        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Avatar removed");
        verify(service).deleteGameCharacterAvatar(CHARACTER_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllGameCharacters_injectedSortFields_sortsByDefaultField() {
        when(service.findAllGameCharacters(any())).thenReturn(PagedResultDTO.of(List.of(), 0, 1, 20));
        ArgumentCaptor<GameCharacterSearchRequest> request = ArgumentCaptor.forClass(GameCharacterSearchRequest.class);
        ExpressionList<Object> query = mock(ExpressionList.class, RETURNS_SELF);

        mvc.get().uri("/characters?SORT_FIELDS[owner.email]=owner.email&sortFields[owner.email]=owner.email&sort=owner.email").exchange();

        verify(service).findAllGameCharacters(request.capture());
        request.getValue().applySortAndPagination(query, "name");
        verify(query).orderBy("name asc, id asc");
    }
}
