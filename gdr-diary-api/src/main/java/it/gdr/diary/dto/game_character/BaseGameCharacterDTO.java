package it.gdr.diary.dto.game_character;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.model.GameCharacterKind;

import java.util.UUID;

@Schema(description = "Summary of a character of the logged-in user, used in lists")
public record BaseGameCharacterDTO(
        @Schema(description = "Character identifier", examples = {"0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10"})
        UUID id,

        @Schema(description = "Character name", examples = {"Thorin Oakenshield"})
        String name,

        @Schema(description = "Kind of character: PLAYER or GAME_MASTER", examples = {"PLAYER"})
        GameCharacterKind kind,

        @Schema(description = "Name of the campaign the character plays in", examples = {"The Lost Mine"}, nullable = true)
        String campaignName,

        @Schema(description = "Background color in HEX format (#RRGGBB)", examples = {"#3B2F2F"}, nullable = true)
        String backgroundColor,

        @Schema(description = "Temporary URL of the avatar image, valid for a few minutes. Null if the character has no avatar", examples = {"http://localhost:8333/diary-documents/5f1c...?X-Amz-Signature=..."}, nullable = true)
        String avatarUrl) {

    public static BaseGameCharacterDTO from(GameCharacter character, String avatarUrl) {
        return new BaseGameCharacterDTO(
                character.getId(),
                character.getName(),
                character.getKind(),
                character.getCampaignName(),
                character.getBackgroundColor(),
                avatarUrl);
    }
}
