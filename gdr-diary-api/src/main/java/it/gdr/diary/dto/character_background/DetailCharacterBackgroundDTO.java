package it.gdr.diary.dto.character_background;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.CharacterBackground;

import java.util.List;
import java.util.Map;

@Schema(description = "The background story of a character of the logged-in user")
public record DetailCharacterBackgroundDTO(
        @Schema(description = "Content of the background as a rich text document (JSON). For a character without a background it is an empty document", examples = {"{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Born in the halls of Erebor.\"}]}]}"})
        Map<String, Object> content,

        @Schema(description = "Version of the background: to be sent back when it is updated, so that changes made elsewhere in the meantime are detected. It is 0 for a character that has no background yet", examples = {"3"})
        Long version,

        @Schema(description = "Number of characters of the text of the background, spaces included", examples = {"1250"})
        Integer textLength) {

    public static DetailCharacterBackgroundDTO from(CharacterBackground background) {
        return new DetailCharacterBackgroundDTO(background.getContent(), background.getVersion(), background.getTextLength());
    }

    public static DetailCharacterBackgroundDTO empty() {
        return new DetailCharacterBackgroundDTO(
                Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph"))), 0L, 0);
    }
}
