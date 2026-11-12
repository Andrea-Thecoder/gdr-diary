package it.gdr.diary.dto.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.Feedback;
import it.gdr.diary.model.FeedbackStatus;
import it.gdr.diary.model.FeedbackType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "A feedback with its text, technical information and attached images")
public record DetailFeedbackDTO(
        @Schema(description = "Feedback identifier", examples = {"7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10"})
        UUID id,
        @Schema(description = "Type of feedback", examples = {"IDEA"})
        FeedbackType type,
        @Schema(description = "Processing status of the feedback", examples = {"NEW"})
        FeedbackStatus status,
        @Schema(description = "Email address left by the sender", examples = {"alira@example.com"})
        String email,
        @Schema(description = "Text of the feedback", examples = {"The page tags are great, could I choose their color?"})
        String message,
        @Schema(description = "Technical information sent with the feedback, null if not sent", nullable = true)
        Map<String, Object> technicalInfo,
        @Schema(description = "Whether the sender accepted the privacy policy (always true)", examples = {"true"})
        boolean privacyAccepted,
        @Schema(description = "When the feedback was received", examples = {"2026-11-11T18:30:00Z"})
        Instant createdAt,
        @Schema(description = "Images attached to the feedback (empty list if none)")
        List<AttachmentFeedbackDTO> attachments) {

    public static DetailFeedbackDTO from(Feedback feedback, List<AttachmentFeedbackDTO> attachments) {
        return new DetailFeedbackDTO(
                feedback.getId(),
                feedback.getType(),
                feedback.getStatus(),
                feedback.getEmail(),
                feedback.getMessage(),
                feedback.getTechnicalInfo(),
                feedback.isPrivacyAccepted(),
                feedback.getCreatedAt(),
                attachments);
    }
}
