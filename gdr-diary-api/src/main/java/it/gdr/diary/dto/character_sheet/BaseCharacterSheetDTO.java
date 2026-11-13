package it.gdr.diary.dto.character_sheet;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.CharacterSheet;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Summary of a sheet of a character of the logged-in user, used in lists")
public record BaseCharacterSheetDTO(
        @Schema(description = "Sheet identifier", examples = {"4d0f6c1a-2b7e-4c1d-8e55-9a3b7c1d2e10"})
        UUID id,
        @Schema(description = "Identifier of the character the sheet belongs to", examples = {"0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10"})
        UUID characterId,
        @Schema(description = "Title of the sheet", examples = {"Alira, level 8"})
        String title,
        @Schema(description = "Game system of the sheet, null if not indicated", examples = {"D&D 5e"}, nullable = true)
        String system,
        @Schema(description = "Size of the PDF file in bytes", examples = {"1468006"})
        Long sizeBytes,
        @Schema(description = "When the sheet was uploaded", examples = {"2026-10-12T18:30:00Z"})
        Instant createdAt,
        @Schema(description = "Temporary URL to open the PDF in the browser", examples = {"http://localhost:8333/diary-documents/3f1c2a6e?X-Amz-Expires=300"})
        String url,
        @Schema(description = "Temporary URL that downloads the PDF as an attachment", examples = {"http://localhost:8333/diary-documents/3f1c2a6e?response-content-disposition=attachment&X-Amz-Expires=300"})
        String downloadUrl) {

    public static BaseCharacterSheetDTO from(CharacterSheet sheet, String url, String downloadUrl) {
        return new BaseCharacterSheetDTO(
                sheet.getId(),
                sheet.getCharacter().getId(),
                sheet.getTitle(),
                sheet.getGameSystem(),
                sheet.getDocument().getSize(),
                sheet.getCreatedAt(),
                url,
                downloadUrl);
    }
}
