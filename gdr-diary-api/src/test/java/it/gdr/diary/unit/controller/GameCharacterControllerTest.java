package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
class GameCharacterControllerTest {

    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final String VALID_INSERT = """
            {"name":"Aragorn","kind":"PLAYER","description":"Ranger","campaignName":"Lost Mine","backgroundColor":"#1A2B3C"}
            """;
    private static final String VALID_UPDATE = """
            {"name":"Strider","description":null,"campaignName":"Curse of Strahd","backgroundColor":"#000000"}
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
        return new DetailGameCharacterDTO(CHARACTER_ID, name, GameCharacterKind.PLAYER, "Ranger", "Lost Mine", "#1A2B3C", null, null);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
    }

    @Test
    void findAllGameCharacters_noParameters_returnsOk() {
        when(service.findAllGameCharacters(any())).thenReturn(PagedResultDTO.of(List.of(), 0, 1, 20));

        assertThat(mvc.get().uri("/characters")).hasStatusOk();
    }

    @Test
    void findAllGameCharacters_charactersFound_returnsPagedList() {
        BaseGameCharacterDTO character = new BaseGameCharacterDTO(CHARACTER_ID, "Aragorn", GameCharacterKind.PLAYER, "Lost Mine", null, null);
        when(service.findAllGameCharacters(any())).thenReturn(PagedResultDTO.of(List.of(character), 1, 1, 20));

        assertThat(mvc.get().uri("/characters")).bodyJson().extractingPath("$.list[0].name").isEqualTo("Aragorn");
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
    @ValueSource(strings = {"page=0", "size=0", "size=101"})
    void findAllGameCharacters_paginationOutOfRange_returnsBadRequest(String query) {
        assertThat(mvc.get().uri("/characters?" + query)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllGameCharacters_sizeOutOfRange_returnsViolationWithFieldName() {
        assertThat(mvc.get().uri("/characters?size=101"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("size");
    }

    @Test
    void findAllGameCharacters_pageNotANumber_returnsReadableViolation() {
        assertThat(mvc.get().uri("/characters?page=abc"))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Invalid value");
    }

    @Test
    void findAllGameCharacters_paginationOutOfRange_doesNotCallService() {
        mvc.get().uri("/characters?size=101").exchange();

        verifyNoInteractions(service);
    }

    @Test
    void getGameCharacterById_ownedCharacter_returnsDetail() {
        when(service.getGameCharacterById(CHARACTER_ID)).thenReturn(detail("Aragorn"));

        assertThat(mvc.get().uri("/characters/{id}", CHARACTER_ID)).bodyJson().extractingPath("$.name").isEqualTo("Aragorn");
    }

    @Test
    void getGameCharacterById_notFound_returnsNotFound() {
        when(service.getGameCharacterById(CHARACTER_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));

        assertThat(mvc.get().uri("/characters/{id}", CHARACTER_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getGameCharacterById_notFound_returnsReasonAsViolation() {
        when(service.getGameCharacterById(CHARACTER_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));

        assertThat(mvc.get().uri("/characters/{id}", CHARACTER_ID)).bodyJson()
                .extractingPath("$.violations[0].message").isEqualTo("Character not found");
    }

    @Test
    void getGameCharacterById_idNotUuid_returnsBadRequest() {
        assertThat(mvc.get().uri("/characters/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_validBody_returnsCreated() {
        when(service.createGameCharacter(any())).thenReturn(detail("Aragorn"));

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(VALID_INSERT))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createGameCharacter_validBody_passesBodyToService() {
        when(service.createGameCharacter(any())).thenReturn(detail("Aragorn"));

        mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(VALID_INSERT).exchange();

        verify(service).createGameCharacter(new InsertGameCharacterDTO("Aragorn", GameCharacterKind.PLAYER, "Ranger", "Lost Mine", "#1A2B3C"));
    }

    @Test
    void createGameCharacter_validBody_returnsDetail() {
        when(service.createGameCharacter(any())).thenReturn(detail("Aragorn"));

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(VALID_INSERT))
                .bodyJson().extractingPath("$.id").isEqualTo(CHARACTER_ID.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"kind\":\"PLAYER\"}",
            "{\"name\":\" \",\"kind\":\"PLAYER\"}",
            "{\"name\":\"Aragorn\"}",
            "{\"name\":\"Aragorn\",\"kind\":\"PLAYER\",\"backgroundColor\":\"red\"}",
            "{\"name\":\"Aragorn\",\"kind\":\"PLAYER\",\"backgroundColor\":\"#12345\"}"})
    void createGameCharacter_invalidBody_returnsBadRequest(String body) {
        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_nameTooLong_returnsBadRequest() {
        String body = "{\"name\":\"" + "a".repeat(256) + "\",\"kind\":\"PLAYER\"}";

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_invalidBody_doesNotCallService() {
        mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"PLAYER\"}").exchange();

        verifyNoInteractions(service);
    }

    @Test
    void createGameCharacter_unknownKind_returnsBadRequest() {
        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Aragorn\",\"kind\":\"WIZARD\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_malformedJson_returnsBadRequest() {
        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_userMissing_returnsNotFound() {
        when(service.createGameCharacter(any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(VALID_INSERT))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateGameCharacter_validBody_returnsDetail() {
        when(service.updateGameCharacter(eq(CHARACTER_ID), any())).thenReturn(detail("Strider"));

        assertThat(mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .bodyJson().extractingPath("$.name").isEqualTo("Strider");
    }

    @Test
    void updateGameCharacter_validBody_passesIdAndBodyToService() {
        when(service.updateGameCharacter(eq(CHARACTER_ID), any())).thenReturn(detail("Strider"));

        mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE).exchange();

        verify(service).updateGameCharacter(CHARACTER_ID, new UpdateGameCharacterDTO("Strider", null, "Curse of Strahd", "#000000"));
    }

    @Test
    void updateGameCharacter_blankName_returnsBadRequest() {
        assertThat(mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteGameCharacter_ownedCharacter_returnsConfirmationMessage() {
        assertThat(mvc.delete().uri("/characters/{id}", CHARACTER_ID)).bodyJson()
                .extractingPath("$.message").isEqualTo("Character deleted");
    }

    @Test
    void deleteGameCharacter_ownedCharacter_deletesThroughService() {
        mvc.delete().uri("/characters/{id}", CHARACTER_ID).exchange();

        verify(service).deleteGameCharacter(CHARACTER_ID);
    }

    @Test
    void deleteGameCharacter_notFound_returnsNotFound() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found")).when(service).deleteGameCharacter(CHARACTER_ID);

        assertThat(mvc.delete().uri("/characters/{id}", CHARACTER_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateGameCharacterAvatar_imagePart_returnsDetail() {
        when(service.updateGameCharacterAvatar(eq(CHARACTER_ID), any())).thenReturn(detail("Aragorn"));

        assertThat(mvc.put().uri("/characters/{id}/avatar", CHARACTER_ID).multipart().file(image()))
                .bodyJson().extractingPath("$.id").isEqualTo(CHARACTER_ID.toString());
    }

    @Test
    void updateGameCharacterAvatar_imagePart_passesFileToService() {
        when(service.updateGameCharacterAvatar(eq(CHARACTER_ID), any())).thenReturn(detail("Aragorn"));
        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);

        mvc.put().uri("/characters/{id}/avatar", CHARACTER_ID).multipart().file(image()).exchange();

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
    void deleteGameCharacterAvatar_ownedCharacter_returnsConfirmationMessage() {
        assertThat(mvc.delete().uri("/characters/{id}/avatar", CHARACTER_ID)).bodyJson()
                .extractingPath("$.message").isEqualTo("Avatar removed");
    }

    @Test
    void deleteGameCharacterAvatar_ownedCharacter_deletesThroughService() {
        mvc.delete().uri("/characters/{id}/avatar", CHARACTER_ID).exchange();

        verify(service).deleteGameCharacterAvatar(CHARACTER_ID);
    }

    @Test
    void createGameCharacter_nullKind_returnsBadRequest() {
        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Aragorn\",\"kind\":null}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_campaignNameTooLong_returnsBadRequest() {
        String body = "{\"name\":\"Aragorn\",\"kind\":\"PLAYER\",\"campaignName\":\"" + "a".repeat(256) + "\"}";

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_descriptionTooLong_returnsBadRequest() {
        String body = "{\"name\":\"Aragorn\",\"kind\":\"PLAYER\",\"description\":\"" + "a".repeat(2001) + "\"}";

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_nameOf255Chars_returnsCreated() {
        when(service.createGameCharacter(any())).thenReturn(detail("a".repeat(255)));
        String body = "{\"name\":\"" + "a".repeat(255) + "\",\"kind\":\"PLAYER\"}";

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.CREATED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"Aragorn\",\"backgroundColor\":\"red\"}",
            "{\"name\":\"Aragorn\",\"backgroundColor\":\"#12345\"}"})
    void updateGameCharacter_invalidBackgroundColor_returnsBadRequest(String body) {
        assertThat(mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateGameCharacter_campaignNameTooLong_returnsBadRequest() {
        String body = "{\"name\":\"Aragorn\",\"campaignName\":\"" + "a".repeat(256) + "\"}";

        assertThat(mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateGameCharacter_descriptionTooLong_returnsBadRequest() {
        String body = "{\"name\":\"Aragorn\",\"description\":\"" + "a".repeat(2001) + "\"}";

        assertThat(mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateGameCharacter_invalidBody_doesNotCallService() {
        mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Aragorn\",\"backgroundColor\":\"red\"}").exchange();

        verifyNoInteractions(service);
    }

    @Test
    void createGameCharacter_blankName_returnsBadRequestTitle() {
        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \",\"kind\":\"PLAYER\"}"))
                .bodyJson().extractingPath("$.title").isEqualTo("BAD_REQUEST");
    }

    @Test
    void createGameCharacter_blankName_returnsViolationWithFieldName() {
        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \",\"kind\":\"PLAYER\"}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("name");
    }

    @Test
    void createGameCharacter_invalidBackgroundColor_returnsViolationOnBackgroundColorField() {
        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Aragorn\",\"kind\":\"PLAYER\",\"backgroundColor\":\"red\"}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("backgroundColor");
    }

    @Test
    void updateGameCharacter_invalidBackgroundColor_returnsViolationOnBackgroundColorField() {
        assertThat(mvc.put().uri("/characters/{id}", CHARACTER_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Aragorn\",\"backgroundColor\":\"red\"}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("backgroundColor");
    }

    @Test
    void createGameCharacter_nullKind_returnsViolationOnKindField() {
        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Aragorn\",\"kind\":null}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("kind");
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
