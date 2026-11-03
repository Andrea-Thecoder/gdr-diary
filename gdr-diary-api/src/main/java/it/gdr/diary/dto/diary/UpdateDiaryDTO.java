package it.gdr.diary.dto.diary;

import it.gdr.diary.validation.ValidText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Data to fully replace a diary")
public record UpdateDiaryDTO(
        @Schema(description = "Diary title", examples = {"Chronicles of the Lonely Mountain"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) @ValidText String title,

        @Schema(description = "Free text description of the diary", examples = {"Notes from the journey to Erebor"})
        @Size(max = 2000) @ValidText String description,

        @Schema(description = "Cover color in HEX format (#RRGGBB)", examples = {"#7B1E1E"})
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String coverColor,

        @Schema(description = "Font used to display the pages. If empty, the user setting is used", examples = {"Merriweather"})
        @Size(max = 100) @ValidText String font,

        @Schema(description = "Font size in points. If empty, the user setting is used", examples = {"16"})
        @Positive Short fontSize) {
}
