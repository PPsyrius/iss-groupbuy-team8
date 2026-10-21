package com.blocbuy.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /internal/**} serves service-to-service calls only and is never part of the public
 * contract (backend ADR 0008). The {@code contract-check} CI job proves the committed contract
 * equals the one exported from the verified JAR, so checking the committed file is enough.
 */
class InternalPathsContractTest {

    /** Surefire runs with the module directory as working directory. */
    private static final Path CONTRACT = Path.of("..", "..", "contracts", "openapi.json");

    @Test
    void contractExportsNoInternalPaths() throws Exception {
        JsonNode paths =
                JsonMapper.builder().build().readTree(Files.readString(CONTRACT)).get("paths");
        List<String> names =
                StreamSupport.stream(paths.propertyNames().spliterator(), false).toList();

        assertThat(names)
                .isNotEmpty()
                .allMatch(path -> path.startsWith("/api/"))
                .noneMatch(path -> path.contains("/internal"));
    }
}
