package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramType;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.Shape;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参考图数据流图 JSON 到可编辑 Visio 与 PNG 的端到端测试。
 *
 * <p>运行此测试后可在 {@code target/data-flow-template-example} 查看生成结果。</p>
 */
class ReferenceDataFlowRendererTest {

    /** 仅含两份输入名称和中间功能名称的业务 JSON。 */
    private static final Path JSON = Path.of("src/test/resources/diagram/data-flow-reference.json");
    /** 中间功能变更为功能3的业务 JSON。 */
    private static final Path FUNCTION3_JSON = Path.of("src/test/resources/diagram/data-flow-function3.json");
    /** 功能3与三份输入的业务 JSON。 */
    private static final Path THREE_INPUTS_JSON = Path.of(
            "src/test/resources/diagram/data-flow-function3-three-inputs.json");
    /** 可直接查看的测试输出目录。 */
    private static final Path OUTPUT = Path.of("target/data-flow-template-example");

    /** 隔离动态输入名称的测试文件。 */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证 JSON 定义生成的波浪文档、处理框、圆柱存储和渐变图标可编辑且预览一致。
     *
     * @throws Exception JSON、VSDX 或 PNG 处理失败时抛出
     */
    @Test
    void rendersReferenceImageAsEditableVisioFromJson() throws Exception {
        JsonNode spec = new ObjectMapper().readTree(JSON.toFile());
        assertEquals(2, spec.size());
        assertEquals("数据1", spec.get("inputs").get(0).asText());
        assertEquals("数据2", spec.get("inputs").get(1).asText());
        assertEquals("功能1", spec.get("function").asText());

        VisioDiagramArtifact artifact = new ReferenceDataFlowRenderer()
                .render(JSON, OUTPUT.resolve("功能1数据流图.vsdx"));

        assertEquals(DiagramType.DATA_FLOW, artifact.getType());
        assertTrue(Files.size(artifact.getVsdxPath()) > 0L);
        assertTrue(Files.size(artifact.getPreviewPngPath()) > 0L);
        BufferedImage preview = ImageIO.read(artifact.getPreviewPngPath().toFile());
        assertEquals(1500, preview.getWidth());
        assertEquals(800, preview.getHeight());
        assertEquals(Color.WHITE.getRGB(), preview.getRGB(10, 10));
        assertTrue(new Color(preview.getRGB(680, 502)).getRed()
                > new Color(preview.getRGB(680, 502)).getGreen());

        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            Set<String> labels = new HashSet<>();
            boolean documentOutline = false;
            boolean gradientSquare = false;
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                Shape shape = loaded.getPages().getPage(0).getShapes().get(index);
                labels.add(shape.getPureText());
                documentOutline |= shape.getNameU().startsWith("data1_");
                gradientSquare |= shape.getNameU().startsWith("control_Gradient_");
            }
            assertTrue(labels.contains("数据1"));
            assertTrue(labels.contains("数据2"));
            assertTrue(labels.contains("功能1"));
            assertTrue(labels.contains("数据库"));
            assertTrue(labels.contains("持久化"));
            assertTrue(labels.contains("图 5-3 功能1数据流图"));
            assertTrue(documentOutline);
            assertTrue(gradientSquare);
        } finally {
            loaded.dispose();
        }
    }

    /**
     * 保留原功能1示例，额外生成中间处理框为功能3的 Visio 与预览图。
     *
     * @throws Exception JSON、VSDX 或 PNG 处理失败时抛出
     */
    @Test
    void rendersFunction3Example() throws Exception {
        VisioDiagramArtifact artifact = new ReferenceDataFlowRenderer()
                .render(FUNCTION3_JSON, OUTPUT.resolve("功能3数据流图.vsdx"));
        assertTrue(Files.size(artifact.getVsdxPath()) > 0L);
        assertTrue(Files.size(artifact.getPreviewPngPath()) > 0L);
        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            Set<String> labels = new HashSet<>();
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                labels.add(loaded.getPages().getPage(0).getShapes().get(index).getPureText());
            }
            assertTrue(labels.contains("功能3"));
            assertTrue(labels.contains("图 5-3 功能3数据流图"));
            assertTrue(labels.contains("持久化"));
            assertTrue(labels.contains("数据库"));
        } finally {
            loaded.dispose();
        }
    }

    /**
     * 在功能3图中新增数据3，验证第三份文档及其独立连线均保存到新 VSDX。
     *
     * @throws Exception JSON、VSDX 或 PNG 处理失败时抛出
     */
    @Test
    void rendersThirdInputForFunction3() throws Exception {
        VisioDiagramArtifact artifact = new ReferenceDataFlowRenderer()
                .render(THREE_INPUTS_JSON, OUTPUT.resolve("功能3-三数据输入数据流图.vsdx"));
        assertTrue(Files.size(artifact.getVsdxPath()) > 0L);
        assertTrue(Files.size(artifact.getPreviewPngPath()) > 0L);
        BufferedImage preview = ImageIO.read(artifact.getPreviewPngPath().toFile());
        assertEquals(Color.BLACK.getRGB(), preview.getRGB(440, 330));
        assertEquals(Color.WHITE.getRGB(), preview.getRGB(405, 330));
        assertEquals(Color.WHITE.getRGB(), preview.getRGB(460, 330));
        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            Set<String> labels = new HashSet<>();
            boolean thirdInputEdge = false;
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                Shape shape = loaded.getPages().getPage(0).getShapes().get(index);
                labels.add(shape.getPureText());
                thirdInputEdge |= shape.getNameU().startsWith("Flow_data3_function_");
            }
            assertTrue(labels.contains("数据1"));
            assertTrue(labels.contains("数据2"));
            assertTrue(labels.contains("数据3"));
            assertTrue(labels.contains("功能3"));
            assertTrue(labels.contains("数据库"));
            assertTrue(thirdInputEdge);
        } finally {
            loaded.dispose();
        }
    }

    /**
     * 验证输入和中间功能可更换，持久化、数据库和样式不被改动。
     *
     * @throws Exception 临时 JSON 或 Visio 文件处理失败时抛出
     */
    @Test
    void changesInputsAndFunctionButKeepsStorageAndStyle() throws Exception {
        Path input = temporaryDirectory.resolve("inputs.json");
        Files.writeString(input, "{\"inputs\":[\"需求文档\",\"接口数据\"],\"function\":\"成果汇总\"}");
        VisioDiagramArtifact artifact = new ReferenceDataFlowRenderer()
                .render(input, temporaryDirectory.resolve("custom-inputs.vsdx"));
        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            Set<String> labels = new HashSet<>();
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                labels.add(loaded.getPages().getPage(0).getShapes().get(index).getPureText());
            }
            assertTrue(labels.contains("需求文档"));
            assertTrue(labels.contains("接口数据"));
            assertTrue(labels.contains("成果汇总"));
            assertTrue(labels.contains("数据库"));
            assertTrue(labels.contains("持久化"));
            assertTrue(labels.contains("图 5-3 成果汇总数据流图"));
        } finally {
            loaded.dispose();
        }
        Files.writeString(input, "{\"inputs\":[\"数据1\",\"数据2\"],\"function\":\"功能1\",\"background\":\"#000000\"}");
        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceDataFlowRenderer().render(input, temporaryDirectory.resolve("invalid.vsdx")));
        Files.writeString(input, "{\"inputs\":[\"这是一个放不进固定宽度文档图形的过长输入名称\",\"数据2\"],\"function\":\"功能1\"}");
        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceDataFlowRenderer().render(input, temporaryDirectory.resolve("too-long.vsdx")));
        Files.writeString(input, "{\"inputs\":[\"数据1\",\"数据2\"],\"function\":\"" + "超长功能名称".repeat(6) + "\"}");
        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceDataFlowRenderer().render(input, temporaryDirectory.resolve("function-too-long.vsdx")));
    }
}
