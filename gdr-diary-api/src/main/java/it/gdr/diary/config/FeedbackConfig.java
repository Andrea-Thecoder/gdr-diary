package it.gdr.diary.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.feedback")
@Validated
public class FeedbackConfig {

    @NotBlank
    private String bucket = "feedback-documents";
    @Positive
    private int maxFiles = 3;
    @NotNull
    private DataSize maxTotalFileSize = DataSize.ofMegabytes(5);
    @NotNull
    private DataSize maxTechnicalInfoSize = DataSize.ofKilobytes(2);
    @NotNull
    private ZoneId timezone = ZoneId.of("Europe/Rome");
    @Positive
    private int rateLimitMaxRequests = 5;
    @NotNull
    @DurationMin(seconds = 1)
    private Duration rateLimitWindow = Duration.ofHours(1);

    @AssertTrue(message = "app.feedback.max-total-file-size must be greater than zero")
    public boolean isMaxTotalFileSizePositive() {
        return maxTotalFileSize == null || maxTotalFileSize.toBytes() > 0;
    }

    @AssertTrue(message = "app.feedback.max-technical-info-size must be greater than zero")
    public boolean isMaxTechnicalInfoSizePositive() {
        return maxTechnicalInfoSize == null || maxTechnicalInfoSize.toBytes() > 0;
    }
}
