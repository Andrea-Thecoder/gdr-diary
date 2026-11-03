package it.gdr.diary.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.user")
@Validated
public class AppUserConfig {

    @NotNull
    private DataSize maxSettingsSize = DataSize.ofKilobytes(10);
    @NotNull
    @DurationMin(seconds = 1)
    private Duration deleteMaxAuthAge = Duration.ofMinutes(5);

    @AssertTrue(message = "app.user.max-settings-size must be greater than zero")
    public boolean isMaxSettingsSizePositive() {
        return maxSettingsSize == null || maxSettingsSize.toBytes() > 0;
    }
}
