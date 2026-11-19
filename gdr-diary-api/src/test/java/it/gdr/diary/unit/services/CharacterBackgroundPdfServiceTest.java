package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import it.gdr.diary.model.CharacterBackground;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.model.GameCharacterKind;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.services.BackgroundPdfRenderer;
import it.gdr.diary.services.OwnedResources;
import it.gdr.diary.services.CharacterBackgroundPdfService;
import it.gdr.diary.unit.support.QueryStub;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CharacterBackgroundPdfServiceTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final Map<String, Object> CONTENT = Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph")));
    private static final byte[] PDF = {37, 80, 68, 70};

    @Mock
    private Database database;
    @Mock
    private JwtInspector jwtInspector;
    @Mock
    private BackgroundPdfRenderer renderer;

    private CharacterBackgroundPdfService service;

    @BeforeEach
    void setUp() {
        service = new CharacterBackgroundPdfService(database, jwtInspector, renderer, new OwnedResources(database, jwtInspector));
    }

    private GameCharacter character(String name, String campaign, GameCharacterKind kind) {
        GameCharacter character = new GameCharacter();
        character.setId(CHARACTER_ID);
        character.setName(name);
        character.setCampaignName(campaign);
        character.setKind(kind);
        return character;
    }

    private void givenCharacter(GameCharacter character) {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<GameCharacter> characters = stubFind(database, GameCharacter.class);
        when(characters.where().findOne()).thenReturn(character);
    }

    private void givenBackground(CharacterBackground background) {
        QueryStub<CharacterBackground> backgrounds = stubFind(database, CharacterBackground.class);
        when(backgrounds.where().findOne()).thenReturn(background);
    }

    private CharacterBackground background() {
        CharacterBackground background = new CharacterBackground();
        background.setContent(CONTENT);
        return background;
    }

    @Test
    void getCharacterBackgroundPdf_existing_returnsRenderedBytesFromTitleCampaignAndContent() {
        givenCharacter(character("Alira", "Il Nord", GameCharacterKind.PLAYER));
        givenBackground(background());
        when(renderer.render(anyString(), any(), any())).thenReturn(PDF);

        byte[] result = service.getCharacterBackgroundPdf(CHARACTER_ID).content();

        assertThat(result).isEqualTo(PDF);
        verify(renderer).render("Il Background di Alira", "Il Nord", CONTENT);
    }

    @Test
    void getCharacterBackgroundPdf_blankCampaign_rendersWithoutSubtitle() {
        givenCharacter(character("Alira", "  ", GameCharacterKind.PLAYER));
        givenBackground(background());
        when(renderer.render(anyString(), any(), any())).thenReturn(PDF);

        service.getCharacterBackgroundPdf(CHARACTER_ID);

        verify(renderer).render(eq("Il Background di Alira"), isNull(), eq(CONTENT));
    }

    @ParameterizedTest
    @CsvSource({
            "Alira dei Salici, background-alira-dei-salici.pdf",
            "Thorin Scudodiquercia!, background-thorin-scudodiquercia.pdf",
            "Élodie l'Ombra, background-elodie-l-ombra.pdf",
            "龍, background-personaggio.pdf"
    })
    void getCharacterBackgroundPdf_name_givesASafeFileName(String name, String fileName) {
        givenCharacter(character(name, null, GameCharacterKind.PLAYER));
        givenBackground(background());
        when(renderer.render(anyString(), any(), any())).thenReturn(PDF);

        assertThat(service.getCharacterBackgroundPdf(CHARACTER_ID).fileName()).isEqualTo(fileName);
    }

    @Test
    void getCharacterBackgroundPdf_veryLongName_keepsTheFileNameShort() {
        givenCharacter(character("a".repeat(200), null, GameCharacterKind.PLAYER));
        givenBackground(background());
        when(renderer.render(anyString(), any(), any())).thenReturn(PDF);

        assertThat(service.getCharacterBackgroundPdf(CHARACTER_ID).fileName()).isEqualTo("background-" + "a".repeat(60) + ".pdf");
    }
}
