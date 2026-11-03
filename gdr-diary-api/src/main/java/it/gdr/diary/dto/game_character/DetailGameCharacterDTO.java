package it.gdr.diary.dto.game_character;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.model.GameCharacterKind;

import java.util.UUID;

@Schema(description = "A character of the logged-in user")
public record DetailGameCharacterDTO(
        @Schema(description = "Character identifier", examples = {"0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10"})
        UUID id,

        @Schema(description = "Character name", examples = {"Thorin Oakenshield"})
        String name,

        @Schema(description = "Kind of character: PLAYER or GAME_MASTER", examples = {"PLAYER"})
        GameCharacterKind kind,

        @Schema(description = "Free text description of the character", examples = {"Dwarf fighter, exiled king of Erebor"}, nullable = true)
        String description,

        @Schema(description = "Name of the campaign the character plays in", examples = {"The Lost Mine"}, nullable = true)
        String campaignName,

        @Schema(description = "Background color in HEX format (#RRGGBB)", examples = {"#3B2F2F"}, nullable = true)
        String backgroundColor,

        @Schema(description = "Identifier of the avatar document. Null if the character has no avatar", examples = {"9a3f6c52-1d7b-4e0a-8c35-6b2d9e4f7a10"}, nullable = true)
        UUID avatarDocumentId,

        @Schema(description = "Temporary URL of the avatar image, valid for a few minutes. Null if the character has no avatar", examples = {"http://localhost:8333/diary-documents/5f1c...?X-Amz-Signature=..."}, nullable = true)
        String avatarUrl) {

    public static DetailGameCharacterDTO from(GameCharacter character, String avatarUrl) {
        return new DetailGameCharacterDTO(
                character.getId(),
                character.getName(),
                character.getKind(),
                character.getDescription(),
                character.getCampaignName(),
                character.getBackgroundColor(),
                character.getAvatarDocument() == null ? null : character.getAvatarDocument().getId(),
                avatarUrl);
    }
}
