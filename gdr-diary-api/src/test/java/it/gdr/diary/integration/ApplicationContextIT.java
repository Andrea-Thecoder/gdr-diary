package it.gdr.diary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class ApplicationContextIT extends AbstractIntegrationTest {

    @Test
    void contextLoads_containersRunning_appliesFlywayMigrations() {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);

        assertThat(applied).isEqualTo(2);
    }
}
