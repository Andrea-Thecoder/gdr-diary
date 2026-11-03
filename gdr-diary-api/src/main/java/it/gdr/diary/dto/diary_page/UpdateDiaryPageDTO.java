package it.gdr.diary.dto.diary_page;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@Schema(description = "Data to update a page of a diary")
public record UpdateDiaryPageDTO(
        @Schema(description = "New content of the page as a rich text document (JSON). Only the nodes and marks of the diary editor are accepted: doc, paragraph (with textAlign), blockquote, bulletList, orderedList (with start and type), listItem, horizontalRule, hardBreak and text, with the marks bold, italic, underline and title (with level 1 or 2)", examples = {"{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Day one on the road to Erebor.\"}]}]}"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty Map<String, Object> content,

        @Schema(description = "Version of the page the client last read, as returned by the server. The update is rejected with 409 if the page has been modified since", examples = {"3"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long version) {
}
