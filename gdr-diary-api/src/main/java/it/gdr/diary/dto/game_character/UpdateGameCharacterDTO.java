package it.gdr.diary.dto.game_character;

import it.gdr.diary.validation.ValidText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Data to fully replace a character")
public record UpdateGameCharacterDTO(
        @Schema(description = "Character name", examples = {"Thorin Oakenshield"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) @ValidText String name,

        @Schema(description = "Free text description of the character", examples = {"Dwarf fighter, exiled king of Erebor"})
        @Size(max = 2000) @ValidText String description,

        @Schema(description = "Name of the campaign the character plays in", examples = {"The Lost Mine"})
        @Size(max = 255) @ValidText String campaignName,

        @Schema(description = "Background color in HEX format (#RRGGBB)", examples = {"#3B2F2F"})
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String backgroundColor) {
}
