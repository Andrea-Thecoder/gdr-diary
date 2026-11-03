package it.gdr.diary.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.diary")
@Validated
public class DiaryPageConfig {

    @NotNull
    private DataSize maxPageContentSize = DataSize.ofKilobytes(50);

    @Positive
    private int maxPageContentDepth = 50;

    @AssertTrue(message = "app.diary.max-page-content-size must be greater than zero")
    public boolean isMaxPageContentSizePositive() {
        return maxPageContentSize == null || maxPageContentSize.toBytes() > 0;
    }
}
