package it.gdr.diary.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import io.ebean.Database;
import io.ebean.config.CurrentUserProvider;
import io.ebean.config.DatabaseConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;


@Configuration
public class EbeanConfig {

    static final String JSON_MAX_STRING_PROPERTY = "jsonb.parserMaxStringBuffer";
    static final int JSON_MAX_STRING_CHARS = 1_048_576;

    @Bean
    public Database database(DataSource dataSource, CurrentUserProvider currentUserProvider) {
        allowLongJsonStrings();
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setDataSource(dataSource);
        dbConfig.setCurrentUserProvider(currentUserProvider);
        dbConfig.setIdGeneratorAutomatic(false);
        dbConfig.setDefaultServer(true);
        return dbConfig.build();
    }

    static void allowLongJsonStrings() {
        if (System.getProperty(JSON_MAX_STRING_PROPERTY) == null) {
            System.setProperty(JSON_MAX_STRING_PROPERTY, String.valueOf(JSON_MAX_STRING_CHARS));
        }
    }

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }
}
