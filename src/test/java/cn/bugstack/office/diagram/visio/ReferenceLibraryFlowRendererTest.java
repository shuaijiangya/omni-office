package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramType;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.SaveFileFormat;
import com.aspose.diagram.Shape;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定“库管理”流程图的 JSON 到 Visio 与 PNG 测试。
 *
 * <p>与数据流图复用相同业务 JSON，仅图模板不同。输出保存在
 * {@code target/library-flow-template-example} 以便直接查看。</p>
 */
class ReferenceLibraryFlowRendererTest {

    /** 功能3及数据1至数据3的共享输入。 */
    private static final Path INPUT = Path.of(
            "src/test/resources/diagram/data-flow-function3-three-inputs.json");
    /** 可查看的固定流程图输出目录。 */
    private static final Path OUTPUT = Path.of("target/library-flow-template-example/功能3-库管理流程图.vsdx");

    /**
     * 验证菜单栏、功能3、三份数据、增删改查及结束按参考图生成可编辑 Visio。
     *
     * @throws Exception JSON、VSDX 或 PNG 处理失败时抛出
     */
    @Test
    void rendersFixedLibraryFlowFromTheSameThreeInputJson() throws Exception {
        VisioDiagramArtifact artifact = new ReferenceLibraryFlowRenderer().render(INPUT, OUTPUT);
        assertEquals(DiagramType.FLOW, artifact.getType());
        assertTrue(Files.size(artifact.getVsdxPath()) > 0L);
        assertTrue(Files.size(artifact.getPreviewPngPath()) > 0L);
        assertSavedNativeArrowCells(artifact.getVsdxPath(), 8);

        BufferedImage preview = ImageIO.read(artifact.getPreviewPngPath().toFile());
        assertEquals(1200, preview.getWidth());
        assertEquals(1150, preview.getHeight());
        assertEquals(Color.WHITE.getRGB(), preview.getRGB(20, 20));
        assertEquals(Color.BLACK.getRGB(), preview.getRGB(600, 365));

        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            Set<String> labels = new HashSet<>();
            Set<Double> operationEntryX = new HashSet<>();
            boolean hasData1ToOperations = false;
            boolean hasData3ToOperations = false;
            int functionToData = 0;
            int dataToOperations = 0;
            int operationCells = 0;
            int operationDividers = 0;
            int elbowConnectors = 0;
            int nativeArrows = 0;
            boolean correctFunctionBox = false;
            boolean nativeMenuConnector = false;
            boolean hasOperationsOuter = false;
            Shape operations = null;
            Shape end = null;
            Shape operationsToEnd = null;
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                Shape shape = loaded.getPages().getPage(0).getShapes().get(index);
                labels.add(shape.getPureText());
                String name = shape.getNameU();
                if (name.startsWith("LibraryLine_FunctionToData")) {
                    functionToData++;
                    assertTrue(hasNativeArrow(shape));
                }
                if (name.matches("LibraryLine_Data[0-9]+ToOperations_.*")) {
                    dataToOperations++;
                    operationEntryX.add(shape.getLine().getBeginArrow().getValue() == 4
                            ? shape.getXForm1D().getBeginX().getValue()
                            : shape.getXForm1D().getEndX().getValue());
                    assertTrue(hasNativeArrow(shape));
                }
                hasData1ToOperations |= name.startsWith("LibraryLine_Data1ToOperations_");
                hasData3ToOperations |= name.startsWith("LibraryLine_Data3ToOperations_");
                assertTrue(!name.contains("LibraryTrunk") && !name.contains("OperationsCollector")
                        && !name.contains("CollectorToOperations"),
                        "flowchart must use one connector per source/target pair, not shared buses");
                if (name.matches("LibraryBox_operation[0-3]")) {
                    operationCells++;
                    assertEquals(0, shape.getLine().getLinePattern().getValue(),
                            "operation cells must not double-draw their shared borders");
                }
                if (name.startsWith("LibraryLine_OperationDivider")) operationDividers++;
                if (name.equals("LibraryBox_operations")) {
                    hasOperationsOuter = true;
                    operations = shape;
                }
                if (name.equals("LibraryBox_end")) end = shape;
                if (name.startsWith("LibraryLine_OperationsToEnd_")) operationsToEnd = shape;
                if (name.startsWith("LibraryLine_")) {
                    assertTrue(shape.getOneD(), "flow lines must be editable one-dimensional Visio shapes");
                    if (shape.getGeoms().get(0).getCoordinateCol().getCount() > 2) elbowConnectors++;
                }
                assertTrue(!name.startsWith("LibrarySegment_"),
                        "a source-target pair must be one Visio connector, never several line segments");
                if (name.startsWith("LibraryLine_MenuToFunction_")) nativeMenuConnector = shape.getOneD();
                if (name.startsWith("LibraryLine_") && hasNativeArrow(shape)) nativeArrows++;
                if (name.equals("LibraryBox_function")) {
                    correctFunctionBox = Math.abs(shape.getXForm().getWidth().getValue() - 260D / 72D) < 0.001D
                            && Math.abs(shape.getXForm().getHeight().getValue() - 90D / 72D) < 0.001D;
                }
            }
            for (String label : new String[]{"菜单栏", "功能3", "数据1", "数据2", "数据3",
                    "增", "删", "改", "查", "结束"}) {
                assertTrue(labels.contains(label), "missing label: " + label);
            }
            assertTrue(hasData1ToOperations);
            assertTrue(hasData3ToOperations);
            assertEquals(3, functionToData);
            assertEquals(3, dataToOperations);
            assertEquals(3, operationEntryX.size(), "each data node needs its own operations entry port");
            assertEquals(4, operationCells);
            assertEquals(3, operationDividers);
            assertEquals(4, elbowConnectors, "four non-central routes each have two turns in one connector");
            assertTrue(hasOperationsOuter);
            assertTrue(nativeMenuConnector);
            assertEquals(16, loaded.getPages().getPage(0).getConnects().getCount(),
                    "every direct flow connector must be glued at both endpoints");
            assertEquals(8, nativeArrows);
            assertTrue(correctFunctionBox, "function rectangle must retain its intended Visio dimensions");
            assertTrue(operations != null && end != null && operationsToEnd != null);
            double center = operations.getXForm().getPinX().getValue();
            for (int index = 0; index < 3; index++) {
                assertTrue(operations.getConnections().get(index).getY().getValue()
                                > operations.getXForm().getHeight().getValue(),
                        "Visio must keep native arrowheads visible outside the operations border");
            }
            assertEquals(center, operationsToEnd.getXForm1D().getBeginX().getValue(), 0.001D,
                    "arrow out of operations must start at its horizontal center");
            assertEquals(end.getXForm().getPinX().getValue(),
                    operationsToEnd.getXForm1D().getEndX().getValue(), 0.001D,
                    "arrow into the end node must point to its horizontal center");
        } finally {
            loaded.dispose();
        }
    }

    /**
     * 用与用户截图一致的双输入结构回读真实 VSDX 渲染结果。
     *
     * @throws Exception 文件生成或 Visio 渲染失败时抛出
     */
    @Test
    void rendersTwoInputVisioWithCleanOperationsBorders() throws Exception {
        Path input = Path.of("src/test/resources/diagram/data-flow-function3.json");
        Path output = Path.of("target/library-flow-template-example/功能3-双数据流程图.vsdx");
        VisioDiagramArtifact artifact = new ReferenceLibraryFlowRenderer().render(input, output);
        assertSavedNativeArrowCells(artifact.getVsdxPath(), 6);
        Diagram diagram = new Diagram(artifact.getVsdxPath().toString());
        try {
            assertEquals(12, diagram.getPages().getPage(0).getConnects().getCount());
            diagram.save(output.resolveSibling("功能3-双数据流程图.visio.png").toString(), SaveFileFormat.PNG);
        } finally {
            diagram.dispose();
        }
    }

    /** 判断一根 Visio 连接线的流向箭头是否保存在其原生 Line 属性中。 */
    private boolean hasNativeArrow(Shape shape) {
        return shape.getLine().getBeginArrow().getValue() == 4
                || shape.getLine().getEndArrow().getValue() == 4;
    }

    /** 直接检查 VSDX 的 OOXML，确认箭头是 Visio 原生 Line 单元格而不是预览图效果。 */
    private void assertSavedNativeArrowCells(Path vsdx, int expected) throws Exception {
        try (ZipFile zip = new ZipFile(vsdx.toFile());
             InputStream content = zip.getInputStream(zip.getEntry("visio/pages/page1.xml"))) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            NodeList shapes = factory.newDocumentBuilder().parse(content)
                    .getElementsByTagNameNS("*", "Shape");
            int arrowCells = 0;
            for (int index = 0; index < shapes.getLength(); index++) {
                Element shape = (Element) shapes.item(index);
                if (!shape.getAttribute("NameU").startsWith("LibraryLine_")) continue;
                NodeList cells = shape.getElementsByTagNameNS("*", "Cell");
                for (int cellIndex = 0; cellIndex < cells.getLength(); cellIndex++) {
                    Element cell = (Element) cells.item(cellIndex);
                    if (("BeginArrow".equals(cell.getAttribute("N"))
                            || "EndArrow".equals(cell.getAttribute("N")))
                            && "4".equals(cell.getAttribute("V"))) {
                        arrowCells++;
                        break;
                    }
                }
            }
            assertEquals(expected, arrowCells, "VSDX must store native Visio arrow cells");
        }
    }
}
