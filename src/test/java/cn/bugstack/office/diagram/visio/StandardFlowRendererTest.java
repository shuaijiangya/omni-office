package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.Shape;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用 Visio 作者提供的原生母版验证标准流程图。 */
class StandardFlowRendererTest {

    /** 两数据输入应保留增删改查，并形成六根独立动态连接线。 */
    @Test
    void rendersTwoInputsWithAuthoredMasters() throws Exception {
        Path input = Path.of("src/test/resources/diagram/data-flow-function3.json");
        Path output = Path.of("target/standard-flow-template-example/功能3-双数据标准流程图.vsdx");
        verify(new StandardFlowRenderer().render(input, output), 2);
    }

    /** 四数据输入应在同一排形成十根独立动态连接线。 */
    @Test
    void rendersFourInputsWithOneConnectorPerRelationship() throws Exception {
        Path input = Path.of("src/test/resources/diagram/data-flow-function3-four-inputs.json");
        Path output = Path.of("target/standard-flow-template-example/功能3-四数据标准流程图.vsdx");
        verify(new StandardFlowRenderer().render(input, output), 4);
    }

    /** 单输入保持直连，九输入仍生成每对节点单独的原生连接器。 */
    @Test
    void rendersOneAndNineInputs() throws Exception {
        for (int count : new int[]{1, 9}) {
            Path directory = Path.of("target/standard-flow-template-example");
            Files.createDirectories(directory);
            ObjectNode data = new ObjectMapper().createObjectNode();
            data.put("function", "功能3");
            ArrayNode inputs = data.putArray("inputs");
            for (int index = 1; index <= count; index++) inputs.add("数据" + index);
            Path json = directory.resolve("标准流程图-" + count + "输入.json");
            new ObjectMapper().writeValue(json.toFile(), data);
            verify(new StandardFlowRenderer().render(json,
                    directory.resolve("功能3-" + count + "数据标准流程图.vsdx")), count);
        }
    }

    /** JSON 中较长的节点名称应自动扩大节点，而不是挤到外框之外。 */
    @Test
    void expandsNodesForLongJsonLabels() throws Exception {
        Path directory = Path.of("target/standard-flow-template-example");
        Files.createDirectories(directory);
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put("function", "多源异构数据接入清洗与质量校验规则配置");
        data.putArray("inputs")
                .add("条件库任务执行状态查询与审核")
                .add("原子动作规则编排与发布");
        Path json = directory.resolve("长名称标准流程图.json");
        new ObjectMapper().writeValue(json.toFile(), data);
        VisioDiagramArtifact artifact = new StandardFlowRenderer().render(json,
                directory.resolve("长名称标准流程图.vsdx"));
        assertTrue(Files.size(artifact.getPreviewPngPath()) > 0);
        Diagram diagram = new Diagram(artifact.getVsdxPath().toString());
        try {
            boolean expanded = false;
            for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
                Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
                if ("StandardFlow_Function".equals(shape.getNameU())) {
                    expanded = shape.getXForm().getWidth().getValue() > 3.2D
                            && shape.getXForm().getHeight().getValue() >= 0.65D - 0.0001D;
                }
            }
            assertTrue(expanded, "long JSON label should use a larger editable Visio process node");
        } finally {
            diagram.dispose();
        }
    }

    /** 回读 VSDX，同时检查母版引用、箭头、连接器数量和业务文字。 */
    private void verify(VisioDiagramArtifact artifact, int inputs) throws Exception {
        assertTrue(Files.size(artifact.getVsdxPath()) > 0);
        assertTrue(Files.size(artifact.getPreviewPngPath()) > 0);
        try (ZipFile zip = new ZipFile(artifact.getVsdxPath().toFile());
             InputStream xml = zip.getInputStream(zip.getEntry("visio/pages/page1.xml"))) {
            assertTrue(zip.getEntry("docProps/thumbnail.emf") == null,
                    "new VSDX must not reuse the original document's stale thumbnail");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            NodeList shapes = factory.newDocumentBuilder().parse(xml).getElementsByTagNameNS("*", "Shape");
            int connectors = 0;
            Set<String> names = new HashSet<>();
            Map<String, String> texts = new HashMap<>();
            for (int index = 0; index < shapes.getLength(); index++) {
                Element shape = (Element) shapes.item(index);
                String name = shape.getAttribute("NameU");
                names.add(name);
                NodeList textNodes = shape.getElementsByTagNameNS("*", "Text");
                if (textNodes.getLength() != 0) {
                    texts.put(name, textNodes.item(0).getTextContent().trim());
                }
                if (!shape.getAttribute("NameU").startsWith("StandardFlow_")) continue;
                if (!"2".equals(shape.getAttribute("Master"))) continue;
                connectors++;
                int arrows = 0;
                NodeList cells = shape.getElementsByTagNameNS("*", "Cell");
                for (int c = 0; c < cells.getLength(); c++) {
                    Element cell = (Element) cells.item(c);
                    if ("EndArrow".equals(cell.getAttribute("N")) && "13".equals(cell.getAttribute("V"))) arrows++;
                }
                assertEquals(1, arrows, "each Visio connector needs its own native arrow");
            }
            assertEquals(2 * inputs + 2, connectors);
            assertTrue(names.contains("StandardFlow_Start"));
            assertTrue(names.contains("StandardFlow_Function"));
            assertTrue(names.contains("StandardFlow_Operations"));
            for (int index = 1; index <= 4; index++) {
                assertTrue(names.contains("StandardFlow_Operation" + index));
            }
            assertTrue(names.contains("StandardFlow_End"));
            assertTrue(names.contains("StandardFlow_OperationsToEnd"));
            assertEquals("菜单栏", texts.get("StandardFlow_Start"));
            assertEquals("功能3", texts.get("StandardFlow_Function"));
            assertEquals("结束", texts.get("StandardFlow_End"));
            for (int index = 1; index <= 4; index++) {
                assertEquals("增删改查".substring(index - 1, index),
                        texts.get("StandardFlow_Operation" + index));
            }
            for (int index = 1; index <= inputs; index++) {
                assertTrue(names.contains("StandardFlow_Data" + index));
                assertEquals("数据" + index, texts.get("StandardFlow_Data" + index));
                assertTrue(names.contains("StandardFlow_FunctionToData" + index));
                assertTrue(names.contains("StandardFlow_Data" + index + "ToOperations"));
            }
        }
        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            int connectorCount = 0;
            boolean screenshotSizedOperations = false;
            boolean textSizedFunction = false;
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                Shape shape = loaded.getPages().getPage(0).getShapes().get(index);
                if ("StandardFlow_Operations".equals(shape.getNameU())) {
                    screenshotSizedOperations = Math.abs(shape.getXForm().getWidth().getValue() - 2.7D) < 0.001D
                            && Math.abs(shape.getXForm().getHeight().getValue() - 2.9D) < 0.001D;
                }
                if ("StandardFlow_Function".equals(shape.getNameU())) {
                    textSizedFunction = Math.abs(shape.getXForm().getWidth().getValue() - 1.6D) < 0.001D
                            && Math.abs(shape.getXForm().getHeight().getValue() - 0.65D) < 0.001D;
                }
                if (shape.getNameU().startsWith("StandardFlow_")
                        && shape.getMaster() != null
                        && "Dynamic connector".equals(shape.getMaster().getNameU())) {
                    connectorCount++;
                    assertTrue(shape.getOneD(), "relationship must be a selectable single Visio connector");
                }
            }
            assertTrue(screenshotSizedOperations, "CRUD frame must keep the tall reference proportions");
            assertTrue(textSizedFunction, "short function text should not use an oversized process box");
            assertEquals(2 * inputs + 2, connectorCount);
            assertEquals((2 * inputs + 2) * 2, loaded.getPages().getPage(0).getConnects().getCount());
        } finally {
            loaded.dispose();
        }
    }
}
