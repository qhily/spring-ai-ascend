package com.huawei.ascend.examples.a2a.scenarios;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Materializes the YAML agent templates under {@code src/test/resources/scenarios/}
 * into per-run files: tokens like {@code @SUFFIX@} and {@code @STUB_BASE_URL@}
 * are replaced before the file is written, because agent and tool names become
 * keys in openJiuwen's JVM-global resource registry (a repeated registration of
 * the same name is rejected) and stub endpoints get an ephemeral port per test.
 */
final class ScenarioYamls {

    private ScenarioYamls() {
    }

    static Path materialize(String resourceName, Map<String, String> tokens) {
        try (InputStream in = ScenarioYamls.class.getResourceAsStream("/scenarios/" + resourceName)) {
            if (in == null) {
                throw new IllegalArgumentException("No scenario YAML resource: " + resourceName);
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> token : tokens.entrySet()) {
                content = content.replace(token.getKey(), token.getValue());
            }
            Path dir = Files.createDirectories(
                    Path.of("target", "scenario-yamls", UUID.randomUUID().toString()));
            Path yaml = dir.resolve(resourceName);
            Files.writeString(yaml, content);
            return yaml;
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to materialize scenario YAML: " + resourceName, error);
        }
    }

    /** Unique, YAML-name-safe suffix so each run registers fresh global agent/tool keys. */
    static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
