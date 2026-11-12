package it.gdr.diary.dto.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.Feedback;
import it.gdr.diary.model.FeedbackStatus;
import it.gdr.diary.model.FeedbackType;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Summary of a feedback, as shown in the list")
public record BaseFeedbackDTO(
        @Schema(description = "Feedback identifier", examples = {"7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10"})
        UUID id,
        @Schema(description = "Type of feedback", examples = {"IDEA"})
        FeedbackType type,
        @Schema(description = "Processing status of the feedback", examples = {"NEW"})
        FeedbackStatus status,
        @Schema(description = "Email address left by the sender", examples = {"alira@example.com"})
        String email,
        @Schema(description = "First characters of the text of the feedback", examples = {"The page tags are great, could I choose their color?"})
        String messagePreview,
        @Schema(description = "When the feedback was received", examples = {"2026-11-11T18:30:00Z"})
        Instant createdAt) {

    private static final int PREVIEW_LENGTH = 120;

    public static BaseFeedbackDTO from(Feedback feedback) {
        return new BaseFeedbackDTO(
                feedback.getId(),
                feedback.getType(),
                feedback.getStatus(),
                feedback.getEmail(),
                StringUtils.abbreviate(feedback.getMessage(), PREVIEW_LENGTH),
                feedback.getCreatedAt());
    }
}
