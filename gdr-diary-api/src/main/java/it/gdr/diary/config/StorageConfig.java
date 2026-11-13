package it.gdr.diary.config;

import io.minio.MinioClient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.storage")
@Validated
public class StorageConfig {

    @NotBlank
    private String endpoint;
    @NotBlank
    private String accessKey;
    @NotBlank
    private String secretKey;
    @NotBlank
    private String bucket;
    @NotBlank
    private String region = "us-east-1";
    private boolean createBuckets = true;
    @NotNull
    @DurationMin(seconds = 1)
    @DurationMax(days = 7)
    private Duration urlExpiry = Duration.ofMinutes(5);
    @NotNull
    private DataSize maxFileSize = DataSize.ofMegabytes(5);
    @NotNull
    private DataSize maxPdfFileSize = DataSize.ofMegabytes(10);
    @NotEmpty
    private List<String> allowedMimeTypes = List.of("image/png", "image/jpeg", "image/webp", "application/pdf");

    @AssertTrue(message = "app.storage.max-file-size must be greater than zero")
    public boolean isMaxFileSizePositive() {
        return maxFileSize == null || maxFileSize.toBytes() > 0;
    }

    @AssertTrue(message = "app.storage.max-pdf-file-size must be greater than zero")
    public boolean isMaxPdfFileSizePositive() {
        return maxPdfFileSize == null || maxPdfFileSize.toBytes() > 0;
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .build();
    }
}
