package it.gdr.diary.dto.diary;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.Diary;

import java.util.UUID;

@Schema(description = "A diary of a character of the logged-in user")
public record DetailDiaryDTO(
        @Schema(description = "Diary identifier", examples = {"5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4"})
        UUID id,

        @Schema(description = "Identifier of the character the diary belongs to", examples = {"0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10"})
        UUID characterId,

        @Schema(description = "Diary title", examples = {"Chronicles of the Lonely Mountain"})
        String title,

        @Schema(description = "Free text description of the diary", examples = {"Notes from the journey to Erebor"}, nullable = true)
        String description,

        @Schema(description = "Cover color in HEX format (#RRGGBB)", examples = {"#7B1E1E"}, nullable = true)
        String coverColor,

        @Schema(description = "Font used to display the pages. Null means the user setting is used", examples = {"Merriweather"}, nullable = true)
        String font,

        @Schema(description = "Font size in points. Null means the user setting is used", examples = {"16"}, nullable = true)
        Short fontSize,

        @Schema(description = "Identifier of the cover document. Null if the diary has no cover", examples = {"9a3f6c52-1d7b-4e0a-8c35-6b2d9e4f7a10"}, nullable = true)
        UUID coverDocumentId,

        @Schema(description = "Temporary URL of the cover image, valid for a few minutes. Null if the diary has no cover", examples = {"http://localhost:8333/diary-documents/5f1c...?X-Amz-Signature=..."}, nullable = true)
        String coverUrl) {

    public static DetailDiaryDTO from(Diary diary, String coverUrl) {
        return new DetailDiaryDTO(
                diary.getId(),
                diary.getCharacter().getId(),
                diary.getTitle(),
                diary.getDescription(),
                diary.getCoverColor(),
                diary.getFont(),
                diary.getFontSize(),
                diary.getCoverDocument() == null ? null : diary.getCoverDocument().getId(),
                coverUrl);
    }
}
