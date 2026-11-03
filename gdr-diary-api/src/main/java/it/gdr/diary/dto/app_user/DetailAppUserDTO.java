package it.gdr.diary.dto.app_user;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.AppUser;

import java.util.Map;

@Schema(description = "The logged-in user")
public record DetailAppUserDTO(
        @Schema(description = "Email of the user", examples = {"mario.rossi@example.com"}, nullable = true)
        String email,

        @Schema(description = "True if the user has a premium account. Read-only: it can only be changed by the backend", examples = {"false"})
        boolean premium,

        @Schema(description = "Free-form settings of the user. An empty object if none are stored: the client then uses its own defaults")
        Map<String, Object> settings,

        @Schema(description = "Temporary URL of the profile image, valid for a few minutes. Null if the user has no profile image", examples = {"http://localhost:8333/diary-documents/5f1c...?X-Amz-Signature=..."}, nullable = true)
        String avatarUrl) {

    public static DetailAppUserDTO from(AppUser user, String avatarUrl) {
        return new DetailAppUserDTO(user.getEmail(), user.isPremium(), user.getSettings() == null ? Map.of() : user.getSettings(), avatarUrl);
    }
}
