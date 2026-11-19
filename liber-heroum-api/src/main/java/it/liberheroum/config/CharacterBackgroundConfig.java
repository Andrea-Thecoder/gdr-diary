package it.liberheroum.config;

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
@ConfigurationProperties(prefix = "app.character-background")
@Validated
public class CharacterBackgroundConfig {

    @Positive
    private int maxTextLength = 50_000;

    @NotNull
    private DataSize maxContentSize = DataSize.ofKilobytes(300);

    @AssertTrue(message = "app.character-background.max-content-size must be greater than zero")
    public boolean isMaxContentSizePositive() {
        return maxContentSize == null || maxContentSize.toBytes() > 0;
    }
}
