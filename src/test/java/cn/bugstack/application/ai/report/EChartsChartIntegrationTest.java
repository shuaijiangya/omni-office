package cn.bugstack.application.ai.report;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.block.ChartBlockSpec;
import com.aspose.words.Document;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 可选 Node/ECharts 运行时的真实服务端渲染集成测试。 */
class EChartsChartIntegrationTest {

    @TempDir
    Path output;

    /** AI 图表数据经过 ECharts SSR 后作为静态 SVG 嵌入 Word。 */
    @Test
    @EnabledIfSystemProperty(named = "omni.office.echarts.integration", matches = "true")
    void embedsEChartsImageWithoutPretendingItIsEditable() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String template = "{\"schemaVersion\":\"1.0\",\"templateId\":\"echarts\","
                + "\"version\":\"1.0\",\"title\":\"图表报告\",\"sections\":[{"
                + "\"id\":\"metrics\",\"level\":1,\"title\":\"指标分析\","
                + "\"prompt\":\"用图展示趋势\",\"dataRefs\":[\"/metrics\"],"
                + "\"allowedBlocks\":[\"chart\"],\"chartRenderMode\":\"ECHARTS_IMAGE\","
                + "\"chartStylePreset\":\"BUSINESS_BLUE\"}]}";
        StructuredAiClient client = request -> "{\"sectionId\":\"metrics\",\"status\":\"READY\","
                + "\"blocks\":[{\"type\":\"chart\",\"chartType\":\"LINE\","
                + "\"title\":\"月度趋势\",\"categories\":[\"一月\",\"二月\"],"
                + "\"series\":[{\"name\":\"处理量\",\"values\":[12,18]}],"
                + "\"factRefs\":[\"/metrics\"]}],\"questions\":[]}";
        StructuredReportAiApplication app = new StructuredReportAiApplication(client);
        DocumentSpec spec = app.generate(mapper.readTree(template),
                mapper.readTree("{\"metrics\":{\"values\":[12,18]}}"));
        assertEquals("ECHARTS_IMAGE", ((ChartBlockSpec) spec.getSections().get(0)
                .getBlocks().get(0)).getRenderMode());
        Path target = output.resolve("echarts.docx");
        app.generateDocx(mapper.readTree(template), mapper.readTree("{\"metrics\":{\"values\":[12,18]}}"), target);
        assertTrue(Files.size(target) > 0);
        Shape shape = (Shape) new Document(target.toString()).getChildNodes(NodeType.SHAPE, true).get(0);
        assertFalse(shape.hasChart());
        assertTrue(shape.hasImage());
    }
}
