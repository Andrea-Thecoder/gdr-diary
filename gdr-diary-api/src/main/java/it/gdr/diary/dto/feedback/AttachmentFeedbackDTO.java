package it.gdr.diary.dto.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.Document;

@Schema(description = "An image attached to a feedback")
public record AttachmentFeedbackDTO(
        @Schema(description = "Original name of the uploaded file", examples = {"screenshot.png"})
        String fileName,
        @Schema(description = "Type of the file", examples = {"image/png"})
        String mimeType,
        @Schema(description = "Size of the file in bytes", examples = {"482113"})
        Long sizeBytes,
        @Schema(description = "Temporary URL to open the image", examples = {"http://localhost:8333/feedback-documents/3f1c2a6e?X-Amz-Expires=300"})
        String url) {

    public static AttachmentFeedbackDTO from(Document document, String url) {
        return new AttachmentFeedbackDTO(document.getName(), document.getMimeType(), document.getSize(), url);
    }
}
