package com.batteryrecycling.traceability.contract;

import java.io.InputStream;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class FirstSliceOpenApiContractTest {

    @Test
    @SuppressWarnings("unchecked")
    void i2RuntimeErrorContractIsDeclaredInOpenApi() {
        java.nio.file.Path contract = java.nio.file.Path.of("contracts/api/openapi-first-slice.yaml");
        if (!java.nio.file.Files.exists(contract)) {
            contract = java.nio.file.Path.of("../contracts/api/openapi-first-slice.yaml");
        }
        try (InputStream input = java.nio.file.Files.newInputStream(contract)) {
            Map<String, Object> openApi = new Yaml().load(input);
            Map<String, Object> paths = (Map<String, Object>) openApi.get("paths");
            Map<String, Object> submitPost = (Map<String, Object>) ((Map<String, Object>) paths.get("/recycle-batches/{id}/submit")).get("post");
            Map<String, Object> submitResponses = (Map<String, Object>) submitPost.get("responses");
            Assertions.assertThat(submitResponses).containsKeys("200", "400", "401", "403", "404", "409");

            Map<String, Object> components = (Map<String, Object>) openApi.get("components");
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            Map<String, Object> duplicateCheck = (Map<String, Object>) schemas.get("DuplicateCheckRequest");
            Map<String, Object> properties = (Map<String, Object>) duplicateCheck.get("properties");
            Map<String, Object> originalCode = (Map<String, Object>) properties.get("originalCode");
            Assertions.assertThat(originalCode).containsEntry("maxLength", 100);
        } catch (Exception exception) {
            throw new AssertionError("OpenAPI contract could not be parsed", exception);
        }
    }
}
