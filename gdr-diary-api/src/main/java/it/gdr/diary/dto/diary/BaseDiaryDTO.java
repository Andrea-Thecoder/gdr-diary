package it.gdr.diary.dto.diary;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.Diary;

import java.util.UUID;

@Schema(description = "Summary of a diary of a character, used in lists")
public record BaseDiaryDTO(
        @Schema(description = "Diary identifier", examples = {"5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4"})
        UUID id,

        @Schema(description = "Identifier of the character the diary belongs to", examples = {"0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10"})
        UUID characterId,

        @Schema(description = "Diary title", examples = {"Chronicles of the Lonely Mountain"})
        String title,

        @Schema(description = "Cover color in HEX format (#RRGGBB)", examples = {"#7B1E1E"}, nullable = true)
        String coverColor,

        @Schema(description = "Temporary URL of the cover image, valid for a few minutes. Null if the diary has no cover", examples = {"http://localhost:8333/diary-documents/5f1c...?X-Amz-Signature=..."}, nullable = true)
        String coverUrl) {

    public static BaseDiaryDTO from(Diary diary, String coverUrl) {
        return new BaseDiaryDTO(
                diary.getId(),
                diary.getCharacter().getId(),
                diary.getTitle(),
                diary.getCoverColor(),
                coverUrl);
    }
}
