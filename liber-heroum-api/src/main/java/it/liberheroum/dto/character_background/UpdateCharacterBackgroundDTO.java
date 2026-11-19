package it.liberheroum.dto.character_background;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@Schema(description = "Data to write the background story of a character")
public record UpdateCharacterBackgroundDTO(
        @Schema(description = "New content of the background as a rich text document (JSON). Only the nodes and marks of the diary editor are accepted: doc, paragraph (with textAlign), blockquote, bulletList, orderedList (with start and type), listItem, horizontalRule, hardBreak and text, with the marks bold, italic, underline, title (with level 1 or 2) and textColor", examples = {"{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Born in the halls of Erebor.\"}]}]}"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty Map<String, Object> content,

        @Schema(description = "Version of the background the client last read, as returned by the server (0 if the character has no background yet). The update is rejected with 409 if the background has been modified since", examples = {"3"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long version) {
}
