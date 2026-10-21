package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.SaveFileFormat;
import com.aspose.diagram.Shape;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用同一份四输入业务 JSON 同时生成数据流图和固定库管理流程图。
 *
 * <p>执行测试后，两张可编辑 VSDX 及其预览图位于 {@code target/four-inputs-example}。</p>
 */
class ReferenceFourInputsDiagramsTest {

    /** 功能3及数据1至数据4的共享业务定义。 */
    private static final Path INPUT = Path.of(
            "src/test/resources/diagram/data-flow-function3-four-inputs.json");
    /** 两张图的共同输出目录。 */
    private static final Path OUTPUT = Path.of("target/four-inputs-example");

    /**
     * 确认四个数据节点在两张图中均有独立连线，且模板固定元素不变。
     *
     * @throws Exception 输入解析或图形生成失败时抛出
     */
    @Test
    void rendersBothDiagramsFromOneFourInputJson() throws Exception {
        VisioDiagramArtifact dataFlow = new ReferenceDataFlowRenderer()
                .render(INPUT, OUTPUT.resolve("功能3-四数据输入数据流图.vsdx"));
        VisioDiagramArtifact libraryFlow = new ReferenceLibraryFlowRenderer()
                .render(INPUT, OUTPUT.resolve("功能3-四数据输入库管理流程图.vsdx"));

        assertPreview(dataFlow, 1500, 836);
        assertPreview(libraryFlow, 1500, 1150);
        assertDiagram(dataFlow, "Flow_data4_function_", "数据库", "持久化");
        assertDiagram(libraryFlow, "LibraryLine_Data4ToOperations_", "菜单栏", "结束");
        assertIndependentOperationsEntries(libraryFlow);
    }

    /** 验证输出文件存在且预览画布没有裁剪。 */
    private void assertPreview(VisioDiagramArtifact artifact, int width, int height) throws Exception {
        assertTrue(Files.size(artifact.getVsdxPath()) > 0L);
        assertTrue(Files.size(artifact.getPreviewPngPath()) > 0L);
        BufferedImage preview = ImageIO.read(artifact.getPreviewPngPath().toFile());
        assertEquals(width, preview.getWidth());
        assertEquals(height, preview.getHeight());
    }

    /** 验证业务文字与第四条连接线均保存为可编辑 Visio 对象。 */
    private void assertDiagram(VisioDiagramArtifact artifact, String fourthEdge, String... fixedLabels)
            throws Exception {
        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            loaded.save(artifact.getVsdxPath().resolveSibling(
                    artifact.getVsdxPath().getFileName() + ".visio.png").toString(), SaveFileFormat.PNG);
            Set<String> labels = new HashSet<>();
            boolean hasFourthEdge = false;
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                Shape shape = loaded.getPages().getPage(0).getShapes().get(index);
                labels.add(shape.getPureText());
                hasFourthEdge |= shape.getNameU().startsWith(fourthEdge);
                if (shape.getPureText() != null && !shape.getPureText().isBlank()) {
                    assertTrue(shape.getChars().getCount() > 0);
                    assertEquals("黑体", shape.getChars().get(0).getFontName().getValue());
                    assertEquals("黑体", shape.getChars().get(0).getAsianFontName().getValue());
                    assertEquals(12D / 72D, shape.getChars().get(0).getSize().getValue(), 0.0001D);
                }
            }
            for (String expected : new String[]{"功能3", "数据1", "数据2", "数据3", "数据4"}) {
                assertTrue(labels.contains(expected), "missing label: " + expected);
            }
            for (String expected : fixedLabels) assertTrue(labels.contains(expected));
            assertTrue(hasFourthEdge, "missing fourth input edge: " + fourthEdge);
        } finally {
            loaded.dispose();
        }
    }

    /** 四个数据节点各用一根直角连接线与独立箭头进入“增删改查”。 */
    private void assertIndependentOperationsEntries(VisioDiagramArtifact artifact) throws Exception {
        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            int individualNativeArrows = 0;
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                Shape shape = loaded.getPages().getPage(0).getShapes().get(index);
                String name = shape.getNameU();
                if (name.matches("LibraryLine_Data[1-4]ToOperations_.*")
                        && (shape.getLine().getBeginArrow().getValue() == 4
                        || shape.getLine().getEndArrow().getValue() == 4)) individualNativeArrows++;
                assertTrue(!name.contains("Collector") && !name.contains("Trunk"),
                        "flowchart must not contain a shared bus");
            }
            assertEquals(4, individualNativeArrows);
        } finally {
            loaded.dispose();
        }
    }
}
