package it.gdr.diary.dto.diary_page_tag;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tag of a page of a diary")
public record DetailDiaryPageTagDTO(
        @Schema(description = "Tag of the page, null if the page has no tag", nullable = true, examples = {"combat"})
        String tag) {
}
