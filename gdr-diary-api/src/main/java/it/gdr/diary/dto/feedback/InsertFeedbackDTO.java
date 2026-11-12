package it.gdr.diary.dto.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.validation.ValidText;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Data of a feedback, sent as form fields together with the optional image parts")
public record InsertFeedbackDTO(
        @Schema(description = "Type of feedback, compared ignoring case", examples = {"idea"},
                allowableValues = {"idea", "bug", "love"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Pattern(regexp = "(?i)idea|bug|love", message = "must be idea, bug or love")
        String type,
        @Schema(description = "Text of the feedback", examples = {"The page tags are great, could I choose their color?"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 2000)
        @ValidText
        String message,
        @Schema(description = "Email address of the sender, to answer to", examples = {"alira@example.com"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Email
        @Size(max = 254)
        @ValidText
        String email,
        @Schema(description = "Technical information of the sender (browser, screen size, page), as the text of a JSON object", examples = {"{\"browser\":\"Firefox 130\",\"screen\":\"1360x768\"}"}, nullable = true)
        @ValidText
        String technicalInfo,
        @Schema(description = "Acceptance of the privacy policy: it must be true", examples = {"true"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @AssertTrue(message = "the privacy policy must be accepted")
        Boolean consent,
        @Schema(description = "Trap field for bots: it must be empty or missing, a real user never fills it", examples = {""}, nullable = true)
        @Size(max = 500)
        String website) {
}
