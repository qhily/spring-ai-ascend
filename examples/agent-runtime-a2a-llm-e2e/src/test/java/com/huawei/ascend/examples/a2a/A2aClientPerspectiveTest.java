package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class A2aClientPerspectiveTest {

    @Test
    void exampleClientUsesPlatformSdkInsteadOfHandWrittenHttpOrTaskPolling() throws IOException {
        List<Path> javaFiles = javaFiles(Path.of("src/main/java/com/huawei/ascend/examples/a2a"));
        javaFiles.addAll(javaFiles(Path.of("src/test/java/com/huawei/ascend/examples/a2a")));

        String source = readAll(javaFiles);

        assertThat(source)
                .doesNotContain("java.net." + "http")
                .doesNotContain("Http" + "Client")
                .doesNotContain("Http" + "Request")
                .doesNotContain("Http" + "Response")
                .doesNotContain("tasks" + "/get")
                .doesNotContain("get" + "Task(")
                .doesNotContain("message" + "/send");
        // The example consumes the supported client SDK facade (which itself
        // rides the A2A SDK transport), never a hand-rolled protocol client.
        assertThat(source)
                .contains("Ascend" + "A2aClient")
                .contains("stream" + "Text")
                .contains("agent" + "Card()");
    }

    private static List<Path> javaFiles(Path source) throws IOException {
        try (Stream<Path> files = Files.walk(source)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("gateway"))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        }
    }

    private static String readAll(List<Path> javaFiles) throws IOException {
        StringBuilder source = new StringBuilder();
        for (Path javaFile : javaFiles) {
            source.append(Files.readString(javaFile)).append('\n');
        }
        return source.toString();
    }
}
