package it.liberheroum.unit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import it.liberheroum.dto.character_background.DetailCharacterBackgroundDTO;
import it.liberheroum.dto.game_character.DetailGameCharacterDTO;
import it.liberheroum.model.CharacterBackground;
import it.liberheroum.model.GameCharacter;
import it.liberheroum.model.GameCharacterKind;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CharacterBackgroundDtoTest {

    private static final Map<String, Object> CONTENT = Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph")));

    private GameCharacter character() {
        GameCharacter character = new GameCharacter();
        character.setId(UUID.randomUUID());
        character.setName("Aragorn");
        character.setKind(GameCharacterKind.PLAYER);
        return character;
    }

    @Test
    void from_background_carriesContentVersionAndLength() {
        CharacterBackground background = new CharacterBackground();
        background.setContent(CONTENT);
        background.setVersion(4L);
        background.setTextLength(120);

        DetailCharacterBackgroundDTO dto = DetailCharacterBackgroundDTO.from(background);

        assertThat(dto).isEqualTo(new DetailCharacterBackgroundDTO(CONTENT, 4L, 120));
    }

    @Test
    void empty_noBackground_isAnEmptyDocumentAtVersionZero() {
        DetailCharacterBackgroundDTO dto = DetailCharacterBackgroundDTO.empty();

        assertThat(dto).isEqualTo(new DetailCharacterBackgroundDTO(CONTENT, 0L, 0));
    }

    @Test
    void detailGameCharacterFrom_withoutPreview_hasNoBackgroundPreview() {
        DetailGameCharacterDTO dto = DetailGameCharacterDTO.from(character(), null);

        assertThat(dto.backgroundPreview()).isNull();
    }

    @Test
    void detailGameCharacterFrom_withPreview_carriesIt() {
        DetailGameCharacterDTO dto = DetailGameCharacterDTO.from(character(), null, "Born in Erebor");

        assertThat(dto.backgroundPreview()).isEqualTo("Born in Erebor");
    }
}
