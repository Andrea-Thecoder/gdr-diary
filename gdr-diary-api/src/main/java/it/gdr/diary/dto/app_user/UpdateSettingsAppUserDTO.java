package it.gdr.diary.dto.app_user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

@Schema(description = "Partial update of the logged-in user. Only the settings can be changed")
public record UpdateSettingsAppUserDTO(
        @Schema(description = "Settings to change, merged into the stored ones: a key replaces the stored value, "
                + "a key with a null value is removed, keys that are not sent are left untouched. "
                + "It must contain at least one key",
                examples = {"{\"font\":\"Lora\",\"fontSize\":16,\"darkMode\":true}"},
                minProperties = 1,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty Map<String, Object> settings) {
}
