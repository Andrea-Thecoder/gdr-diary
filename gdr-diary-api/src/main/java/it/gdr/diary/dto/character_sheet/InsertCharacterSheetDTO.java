package it.gdr.diary.dto.character_sheet;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.validation.ValidText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Data to create a sheet, sent as form fields together with the PDF file part")
public record InsertCharacterSheetDTO(
        @Schema(description = "Title of the sheet", examples = {"Alira, level 8"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 100)
        @ValidText
        String title,
        @Schema(description = "Game system of the sheet", examples = {"D&D 5e"}, nullable = true)
        @Size(max = 100)
        @ValidText
        String system) {
}
