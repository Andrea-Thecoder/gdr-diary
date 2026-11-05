package it.gdr.diary.dto.diary;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Page of a diary to put the bookmark on")
public record UpdateBookmarkDiaryDTO(
        @Schema(description = "Identifier of a page of the same diary", example = "7e2b4d10-5a3c-4c8e-9f61-2a0b6d3e8c45")
        @NotNull
        UUID pageId) {
}
