package it.gdr.diary.dto.diary_page_tag;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Tags of a page of a diary")
public record DetailDiaryPageTagsDTO(
        @Schema(description = "Tags of the page, sorted alphabetically ignoring case")
        List<String> tags) {
}
