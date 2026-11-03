package it.gdr.diary.dto.diary_page;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

@Schema(description = "Data to create a page of a diary. The page number is assigned by the server")
public record InsertDiaryPageDTO(
        @Schema(description = "Content of the page as a rich text document (JSON). Only the nodes and marks of the diary editor are accepted: doc, paragraph (with textAlign), blockquote, bulletList, orderedList (with start and type), listItem, horizontalRule, hardBreak and text, with the marks bold, italic, underline and title (with level 1 or 2)", examples = {"{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Day one on the road to Erebor.\"}]}]}"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty Map<String, Object> content) {
}
