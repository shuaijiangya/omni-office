package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramType;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.ArrowSizeValue;
import com.aspose.diagram.SaveFileFormat;
import com.aspose.diagram.Shape;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证两种 Visio 模板的输入数量由业务 JSON 决定。 */
class ReferenceDynamicInputsTest {

    /** 可直接查看生成效果的测试产物目录。 */
    private final Path output = Path.of("target/dynamic-inputs-example");

    /** 一个输入节点不应触发原有的固定数量限制。 */
    @Test
    void rendersOneInputInBothTemplates() throws Exception {
        Path input = input(1);
        verify(new ReferenceDataFlowRenderer().render(input, output.resolve("one-data-flow.vsdx")), 1);
        verify(new ReferenceLibraryFlowRenderer().render(input, output.resolve("one-library-flow.vsdx")), 1);
    }

    /** 超过四个输入时应扩展画布，保留每个独立节点和原生箭头。 */
    @Test
    void rendersNineInputsInBothTemplates() throws Exception {
        Path input = input(9);
        VisioDiagramArtifact dataFlow = new ReferenceDataFlowRenderer()
                .render(input, output.resolve("nine-data-flow.vsdx"));
        VisioDiagramArtifact libraryFlow = new ReferenceLibraryFlowRenderer()
                .render(input, output.resolve("nine-library-flow.vsdx"));
        verify(dataFlow, 9);
        verify(libraryFlow, 9);
        BufferedImage dataPreview = ImageIO.read(dataFlow.getPreviewPngPath().toFile());
        BufferedImage libraryPreview = ImageIO.read(libraryFlow.getPreviewPngPath().toFile());
        assertEquals(1500, dataPreview.getWidth());
        assertTrue(dataPreview.getHeight() > 1000);
        assertTrue(libraryPreview.getWidth() > 1500);
        assertEquals(1150, libraryPreview.getHeight());
        BufferedImage actualVisio = ImageIO.read(libraryFlow.getVsdxPath().resolveSibling(
                libraryFlow.getVsdxPath().getFileName() + ".visio.png").toFile());
        int firstCenter = 195;
        int lastCenter = firstCenter + 8 * 370;
        int firstPort = libraryPreview.getWidth() / 2 - 130 + 260 / 10;
        int lastPort = libraryPreview.getWidth() / 2 - 130 + 9 * 260 / 10;
        for (int y : new int[]{320, 560}) {
            assertDarkNear(actualVisio, (firstCenter + firstPort) / 2D, y);
            assertDarkNear(actualVisio, (lastCenter + lastPort) / 2D, y);
        }
    }

    /** 写入仅包含业务字段的输入 JSON。 */
    private Path input(int count) throws Exception {
        Files.createDirectories(output);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("function", "库管理");
        ArrayNode names = root.putArray("inputs");
        for (int index = 1; index <= count; index++) names.add("数据" + index);
        Path path = output.resolve("input-" + count + ".json");
        mapper.writeValue(path.toFile(), root);
        return path;
    }

    /** 确认真正的 VSDX 回读图中，长距离直角路线的水平段没有被截断。 */
    private void assertDarkNear(BufferedImage image, double x, double y) {
        int centerX = (int) Math.round(x);
        int centerY = (int) Math.round(y);
        for (int yy = centerY - 4; yy <= centerY + 4; yy++) {
            for (int xx = centerX - 4; xx <= centerX + 4; xx++) {
                int rgb = image.getRGB(xx, yy);
                if (((rgb >> 16) & 0xFF) < 100 && ((rgb >> 8) & 0xFF) < 100
                        && (rgb & 0xFF) < 100) return;
            }
        }
        assertTrue(false, "native Visio route is missing around pixel " + centerX + "," + centerY);
    }

    /** 回读 VSDX，确认数据节点、尺寸和箭头确实保存在 Visio 文件中。 */
    private void verify(VisioDiagramArtifact artifact, int count) throws Exception {
        assertTrue(Files.size(artifact.getVsdxPath()) > 0);
        assertTrue(Files.size(artifact.getPreviewPngPath()) > 0);
        Diagram diagram = new Diagram(artifact.getVsdxPath().toString());
        try {
            if (count == 9) {
                diagram.save(artifact.getVsdxPath().resolveSibling(
                        artifact.getVsdxPath().getFileName() + ".visio.png").toString(), SaveFileFormat.PNG);
            }
            Set<String> labels = new HashSet<>();
            int arrows = 0;
            double[] inputX = new double[count];
            double[] inputY = new double[count];
            for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
                Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
                labels.add(shape.getPureText());
                String name = shape.getNameU();
                assertFalse(name.contains("Background"), "the page must not contain a selectable background shape");
                assertFalse(name.startsWith("LibraryText_"), "flowchart text must belong to its node");
                assertFalse(name.matches("data[0-9]+_Label_.*"), "data-flow text must belong to its node");
                if (name.matches("data[0-9]+_.*")
                        || name.matches("LibraryBox_data[0-9]+")) {
                    String number = name.startsWith("LibraryBox_")
                            ? name.substring("LibraryBox_data".length())
                            : name.substring(4, name.indexOf('_'));
                    int inputIndex = Integer.parseInt(number) - 1;
                    assertEquals("数据" + (inputIndex + 1), shape.getPureText());
                    inputX[inputIndex] = shape.getXForm().getPinX().getValue();
                    inputY[inputIndex] = shape.getXForm().getPinY().getValue();
                }
                if (shape.getNameU().startsWith("Flow_") || shape.getNameU().startsWith("LibraryLine_")) {
                    boolean beginArrow = shape.getLine().getBeginArrow().getValue() == 4;
                    if (beginArrow || shape.getLine().getEndArrow().getValue() == 4) {
                        assertEquals(ArrowSizeValue.LARGE, beginArrow
                                ? shape.getLine().getBeginArrowSize().getValue()
                                : shape.getLine().getEndArrowSize().getValue());
                        arrows++;
                    }
                }
                assertFalse(shape.getNameU().contains("Arrow_"), "arrowhead must not be manually drawn");
            }
            assertTrue(labels.contains("库管理"));
            for (int index = 1; index <= count; index++) assertTrue(labels.contains("数据" + index));
            if (artifact.getType() == DiagramType.DATA_FLOW) {
                assertEquals(0, arrows, "data-flow lines must not have arrowheads");
                for (int index = 1; index < count; index++) {
                    assertEquals(inputX[0], inputX[index], 0.001D, "data-flow inputs must stay at left");
                    assertTrue(inputY[index] < inputY[index - 1], "data-flow inputs must run top to bottom");
                }
            } else {
                assertEquals(2 * count + 2, arrows,
                        "every function/data and data/operations pair requires its own native arrow");
                assertEquals(4 * count + 4, diagram.getPages().getPage(0).getConnects().getCount(),
                        "every direct connector must be attached at both endpoints");
                for (int index = 1; index < count; index++) {
                    assertTrue(inputX[index] > inputX[index - 1], "flowchart inputs must run left to right");
                    assertEquals(inputY[0], inputY[index], 0.001D, "flowchart inputs must stay on one row");
                }
            }
        } finally {
            diagram.dispose();
        }
    }
}
