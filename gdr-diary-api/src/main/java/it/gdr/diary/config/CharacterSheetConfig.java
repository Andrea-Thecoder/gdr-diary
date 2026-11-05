package it.gdr.diary.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.character-sheet")
@Validated
public class CharacterSheetConfig {

    @Positive
    private int maxSheetsPerCharacter = 10;
}
