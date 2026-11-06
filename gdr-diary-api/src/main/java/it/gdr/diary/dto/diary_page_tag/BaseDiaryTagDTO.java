package it.gdr.diary.dto.diary_page_tag;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A tag of a diary with the pages that have it, used in the list of the tags of a diary")
public record BaseDiaryTagDTO(
        @Schema(description = "Tag as written on the first page that has it. Tags that differ only by case are grouped", examples = {"combat"})
        String name,

        @Schema(description = "Numbers of the pages of the diary that have the tag, in ascending order")
        List<Short> pageNumbers) {
}
