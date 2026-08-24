package cn.bugstack.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 在正式拆分 Maven 模块前锁定底层包的依赖方向。 */
class ModuleBoundaryTest {

    private static final Path SOURCES = Path.of("src/main/java/cn/bugstack");

    @Test
    void protocolRemainsAStandaloneContractLayer() throws Exception {
        assertNoImports("protocol", "cn.bugstack.application.", "cn.bugstack.export.",
                "cn.bugstack.office.");
    }

    @Test
    void officeRemainsIndependentFromProtocolExportAndApplications() throws Exception {
        assertNoImports("office", "cn.bugstack.application.", "cn.bugstack.export.",
                "cn.bugstack.protocol.");
    }

    @Test
    void exportDependsOnlyOnOfficeAmongProjectLayers() throws Exception {
        assertNoImports("export", "cn.bugstack.application.", "cn.bugstack.protocol.");
    }

    private void assertNoImports(String layer, String... forbiddenPrefixes) throws IOException {
        List<String> violations = new ArrayList<>();
        Path root = SOURCES.resolve(layer);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                        String value = line.trim();
                        if (!value.startsWith("import ")) continue;
                        Arrays.stream(forbiddenPrefixes).filter(value::contains).forEach(prefix ->
                                violations.add(path + ": " + value));
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("failed to inspect module boundary", e);
                }
            });
        }
        assertTrue(violations.isEmpty(), () -> "invalid " + layer + " dependencies:\n"
                + String.join("\n", violations));
    }
}
