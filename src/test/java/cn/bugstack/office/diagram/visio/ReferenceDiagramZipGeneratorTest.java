package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import com.aspose.diagram.Diagram;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证按“功能模块/功能/JSON”组织的 ZIP 可批量生成两种 Visio 图。
 *
 * <p>完整示例输出保存在 {@code target/reference-diagram-zip-example}。</p>
 */
class ReferenceDiagramZipGeneratorTest {

    /** 非示例用 ZIP 和输出目录的隔离位置。 */
    @TempDir
    Path temporaryDirectory;

    /**
     * 一份 ZIP 中的两个功能分别生成数据流图与库管理流程图，并按源目录落盘。
     *
     * @throws Exception ZIP、Visio 或 PNG 读写失败时抛出
     */
    @Test
    void generatesBothDiagramsForEveryFeatureInZip() throws Exception {
        Path example = Path.of("target/reference-diagram-zip-example");
        Path zip = example.resolve("功能图输入.zip");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("任务管理模块/功能3/diagram.json",
                "{\"function\":\"功能3\",\"inputs\":[\"数据1\",\"数据2\",\"数据3\",\"数据4\"],"
                        + "\"persistenceLabel\":\"写入数据库\"}");
        entries.put("库管理模块/功能4/config.json",
                "{\"function\":\"功能4\",\"inputs\":[\"条件库\",\"原子动作库\",\"感知器库\"]}");
        writeZip(zip, entries);

        Path output = example.resolve("generated");
        List<VisioDiagramArtifact> artifacts = new ReferenceDiagramZipGenerator().generate(zip, output);

        assertEquals(4, artifacts.size());
        assertFeature(output, "任务管理模块", "功能3");
        assertFeature(output, "库管理模块", "功能4");
        for (VisioDiagramArtifact artifact : artifacts) {
            assertTrue(Files.size(artifact.getVsdxPath()) > 0L);
            assertTrue(Files.size(artifact.getPreviewPngPath()) > 0L);
        }
        Diagram loaded = new Diagram(output.resolve("任务管理模块/功能3/visio/功能3-库管理流程图.vsdx")
                .toString());
        try {
            boolean hasData4 = false;
            boolean hasPersistenceLabel = false;
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                String text = loaded.getPages().getPage(0).getShapes().get(index).getPureText();
                hasData4 |= "数据4".equals(text);
                hasPersistenceLabel |= "写入数据库".equals(text);
            }
            assertTrue(hasData4);
            assertFalse(hasPersistenceLabel);
        } finally {
            loaded.dispose();
        }
        loaded = new Diagram(output.resolve("任务管理模块/功能3/visio/功能3-数据流图.vsdx").toString());
        try {
            boolean hasCustomLabel = false;
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                hasCustomLabel |= "写入数据库".equals(
                        loaded.getPages().getPage(0).getShapes().get(index).getPureText());
            }
            assertTrue(hasCustomLabel);
        } finally {
            loaded.dispose();
        }
    }

    /**
     * ZIP 批量入口也应接受五个以上数据节点，与直接调用渲染器保持一致。
     *
     * @throws Exception ZIP 与 Visio 输出失败时抛出
     */
    @Test
    void acceptsDynamicInputCountInZip() throws Exception {
        Path zip = temporaryDirectory.resolve("many-inputs.zip");
        Path output = temporaryDirectory.resolve("many-inputs-output");
        writeZip(zip, Map.of("模块/库管理/diagram.json",
                "{\"function\":\"库管理\",\"inputs\":[\"数据1\",\"数据2\",\"数据3\","
                        + "\"数据4\",\"数据5\",\"数据6\"]}"));
        List<VisioDiagramArtifact> artifacts = new ReferenceDiagramZipGenerator().generate(zip, output);
        assertEquals(2, artifacts.size());
        assertFeature(output, "模块", "库管理");
    }

    /** 同一 ZIP 可显式选用标准 Visio 母版，保留数据流图及增删改查阶段。 */
    @Test
    void generatesStandardFlowFromJsonZip() throws Exception {
        Path example = Path.of("target/reference-diagram-zip-standard-example");
        Path zip = example.resolve("标准流程图输入.zip");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("任务管理模块/功能3/diagram.json",
                "{\"function\":\"功能3\",\"inputs\":[\"数据1\",\"数据2\",\"数据3\",\"数据4\"]}");
        entries.put("库管理模块/功能4/diagram.json",
                "{\"function\":\"功能4\",\"inputs\":[\"条件库\"]}");
        writeZip(zip, entries);

        Path output = example.resolve("generated");
        List<VisioDiagramArtifact> artifacts = new ReferenceDiagramZipGenerator()
                .generateWithStandardFlow(zip, output);

        assertEquals(4, artifacts.size());
        assertStandardFeature(output, "任务管理模块", "功能3");
        assertStandardFeature(output, "库管理模块", "功能4");
        Diagram diagram = new Diagram(output.resolve("任务管理模块/功能3/visio/功能3-标准流程图.vsdx")
                .toString());
        try {
            boolean operations = false;
            boolean fourthData = false;
            int nativeConnectors = 0;
            for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
                com.aspose.diagram.Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
                operations |= "StandardFlow_Operation4".equals(shape.getNameU())
                        && "查".equals(shape.getPureText());
                fourthData |= "StandardFlow_Data4".equals(shape.getNameU())
                        && "数据4".equals(shape.getPureText().trim());
                nativeConnectors += shape.getMaster() != null
                        && "Dynamic connector".equals(shape.getMaster().getNameU()) ? 1 : 0;
            }
            assertTrue(operations);
            assertTrue(fourthData);
            assertEquals(10, nativeConnectors);
        } finally {
            diagram.dispose();
        }
    }

    /**
     * 校验目录穿越、同一功能多个 JSON 和目录/内容名称不一致均在落盘前拒绝。
     *
     * @throws Exception ZIP 写入失败时抛出
     */
    @Test
    void rejectsUnsafeOrAmbiguousZipBeforePublishing() throws Exception {
        Path zip = temporaryDirectory.resolve("invalid.zip");
        Path output = temporaryDirectory.resolve("generated");
        String validJson = "{\"function\":\"功能3\",\"inputs\":[\"数据1\",\"数据2\",\"数据3\"]}";

        writeZip(zip, Map.of("../功能3/diagram.json", validJson));
        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceDiagramZipGenerator().generate(zip, output));
        assertFalse(Files.exists(output));

        Map<String, String> duplicate = new LinkedHashMap<>();
        duplicate.put("模块/功能3/one.json", validJson);
        duplicate.put("模块/功能3/two.json", validJson);
        writeZip(zip, duplicate);
        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceDiagramZipGenerator().generate(zip, output));
        assertFalse(Files.exists(output));

        writeZip(zip, Map.of("模块/功能4/diagram.json", validJson));
        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceDiagramZipGenerator().generate(zip, output));
        assertFalse(Files.exists(output));
    }

    /**
     * 如果后续功能绘制失败，已经绘制完成的前一个功能也不应提前落盘。
     *
     * @throws Exception ZIP 写入失败时抛出
     */
    @Test
    void stagesEveryDiagramBeforePublishingAnyOutput() throws Exception {
        Path zip = temporaryDirectory.resolve("late-failure.zip");
        Path output = temporaryDirectory.resolve("generated");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("模块/功能3/diagram.json",
                "{\"function\":\"功能3\",\"inputs\":[\"数据1\",\"数据2\",\"数据3\"]}");
        entries.put("模块/功能4/diagram.json",
                "{\"function\":\"功能4\",\"inputs\":[\"" + "超长数据名称".repeat(6)
                        + "\",\"数据2\",\"数据3\"]}");
        writeZip(zip, entries);

        assertThrows(IOException.class, () -> new ReferenceDiagramZipGenerator().generate(zip, output));
        assertFalse(Files.exists(output));
    }

    /** 确认一个功能目录只含两张 VSDX 及各自的预览 PNG。 */
    private void assertFeature(Path output, String module, String function) throws IOException {
        Path directory = output.resolve(module).resolve(function).resolve("visio");
        assertTrue(Files.isRegularFile(directory.resolve(function + "-数据流图.vsdx")));
        assertTrue(Files.isRegularFile(directory.resolve(function + "-数据流图.preview.png")));
        assertTrue(Files.isRegularFile(directory.resolve(function + "-库管理流程图.vsdx")));
        assertTrue(Files.isRegularFile(directory.resolve(function + "-库管理流程图.preview.png")));
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            assertEquals(4L, files.count());
        }
    }

    /** 确认显式标准流程图模式的两张图及预览按功能落盘。 */
    private void assertStandardFeature(Path output, String module, String function) throws IOException {
        Path directory = output.resolve(module).resolve(function).resolve("visio");
        assertTrue(Files.isRegularFile(directory.resolve(function + "-数据流图.vsdx")));
        assertTrue(Files.isRegularFile(directory.resolve(function + "-数据流图.preview.png")));
        assertTrue(Files.isRegularFile(directory.resolve(function + "-标准流程图.vsdx")));
        assertTrue(Files.isRegularFile(directory.resolve(function + "-标准流程图.preview.png")));
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            assertEquals(4L, files.count());
        }
    }

    /** 用实际 ZIP 结构构造可重复执行的测试输入。 */
    private void writeZip(Path path, Map<String, String> entries) throws IOException {
        Files.createDirectories(path.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }
}
