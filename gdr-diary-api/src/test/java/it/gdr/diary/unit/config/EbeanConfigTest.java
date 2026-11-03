package it.gdr.diary.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.config.EbeanConfig;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EbeanConfigTest {

    private final ObjectMapper objectMapper = new EbeanConfig().objectMapper();

    @Test
    void objectMapper_instant_writesIsoString() {
        String json = objectMapper.writeValueAsString(Map.of("at", Instant.parse("2026-10-26T10:15:30Z")));

        assertThat(json).isEqualTo("{\"at\":\"2026-10-26T10:15:30Z\"}");
    }

    @Test
    void objectMapper_nullValue_omitsProperty() {
        String json = objectMapper.writeValueAsString(new Holder("Lora", null));

        assertThat(json).isEqualTo("{\"font\":\"Lora\"}");
    }

    record Holder(String font, String color) {
    }
}
