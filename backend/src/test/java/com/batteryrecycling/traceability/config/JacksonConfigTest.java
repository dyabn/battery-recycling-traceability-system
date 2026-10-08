package com.batteryrecycling.traceability.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class JacksonConfigTest {

    @Test
    void serializesLongIdsAsStringsForJavascriptSafety() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().longAsStringCustomizer().customize(builder);
        ObjectMapper objectMapper = builder.build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new LongIdPayload(9_007_199_254_740_993L, 9_007_199_254_740_994L)));

        Assertions.assertThat(json.get("id").isTextual()).isTrue();
        Assertions.assertThat(json.get("id").asText()).isEqualTo("9007199254740993");
        Assertions.assertThat(json.get("primitiveId").isTextual()).isTrue();
        Assertions.assertThat(json.get("primitiveId").asText()).isEqualTo("9007199254740994");
    }

    private record LongIdPayload(Long id, long primitiveId) {
    }
}
