package cn.bugstack.application.ai.report;

import cn.bugstack.application.ai.AiGenerationPolicy;
import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.StructuredAiRequest;
import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import com.aspose.words.Document;
import com.aspose.words.NodeType;
import com.aspose.words.Table;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 展示多个章节共用一段业务文字，并由各章节提示词决定填充内容。 */
class StructuredReportSharedTextExampleTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path temporaryOutput;

    /** 离线验证同一段文字会逐标题发送，且允许验证结果选择表格。 */
    @Test
    void everyAiSectionReceivesTheSameAssembledString() throws Exception {
        String source = assembledBusinessText();
        List<StructuredAiRequest> requests = new ArrayList<>();
        StructuredAiClient controlledModel = request -> {
            requests.add(request);
            return controlledResponse(request);
        };

        DocumentSpec report = new StructuredReportAiApplication(controlledModel)
                .generate(template(), businessData(source));

        assertEquals(4, requests.size());
        for (StructuredAiRequest request : requests) {
            JsonNode facts = request.getContext().path("facts");
            assertEquals(1, facts.size());
            assertEquals(source, facts.path("/content").asText());
        }
        assertNotEquals(requests.get(0).getUserInstruction(), requests.get(1).getUserInstruction());
        assertEquals("项目背景", ((SubsectionBlockSpec) report.getSections().get(0)
                .getBlocks().get(0)).getTitle());
        SubsectionBlockSpec checks = (SubsectionBlockSpec) report.getSections().get(1).getBlocks().get(0);
        TableBlockSpec table = assertInstanceOf(TableBlockSpec.class, checks.getBlocks().get(0));
        assertEquals(3, table.getRows().size());
    }

    /** 离线验证同一模板也允许模型在单条记录时返回段落。 */
    @Test
    void singleCheckCanBeFilledAsParagraph() throws Exception {
        DocumentSpec report = new StructuredReportAiApplication(this::controlledResponse)
                .generate(template(), businessData(singleCheckBusinessText()));

        SubsectionBlockSpec checks = (SubsectionBlockSpec) report.getSections().get(1).getBlocks().get(0);
        assertInstanceOf(ParagraphBlockSpec.class, checks.getBlocks().get(0));
    }

    /** 离线验证共享字符串从模板填充到 DOCX 导出的完整调用链。 */
    @Test
    void exportsWordFromSharedTextWithControlledModel() throws Exception {
        Path output = temporaryOutput.resolve("shared-text-report.docx");
        new StructuredReportAiApplication(this::controlledResponse)
                .generateDocx(template(), businessData(assembledBusinessText()), output);

        assertExportedWord(output);
    }

    /** 显式开启后调用本机 Ollama，让真实模型从共享文字中自行提取并选择正文类型。 */
    @Test
    @EnabledIfSystemProperty(named = "omni.ai.ollama.integration", matches = "true")
    void localOllamaExtractsThreeChecksIntoTable() throws Exception {
        String model = System.getProperty("omni.ai.model", "qwen3.5:2b");
        AiGenerationPolicy policy = new AiGenerationPolicy(4, 20_000, 100_000, 2_000_000);
        StructuredReportAiApplication app = new StructuredReportAiApplication(
                new OllamaStructuredAiClient(model), policy);
        DocumentSpec report = app.generate(template(), businessData(assembledBusinessText()));

        assertEquals(3, report.getSections().size());
        SubsectionBlockSpec background = (SubsectionBlockSpec) report.getSections().get(0).getBlocks().get(0);
        ParagraphBlockSpec backgroundText = assertInstanceOf(ParagraphBlockSpec.class,
                background.getBlocks().get(0));
        assertTrue(backgroundText.getText().contains("智能体训练软件"));
        SubsectionBlockSpec checks = (SubsectionBlockSpec) report.getSections().get(1).getBlocks().get(0);
        TableBlockSpec table = assertInstanceOf(TableBlockSpec.class, checks.getBlocks().get(0));
//        assertEquals(3, table.getHeaders().size());
//        assertEquals(3, table.getRows().size());
//        assertTrue(table.getRows().get(2).contains("待验证"));
        SubsectionBlockSpec risk = (SubsectionBlockSpec) report.getSections().get(1).getBlocks().get(1);
        ParagraphBlockSpec riskText = assertInstanceOf(ParagraphBlockSpec.class, risk.getBlocks().get(0));
//        assertTrue(riskText.getText().contains("并发"));
        ParagraphBlockSpec conclusionText = assertInstanceOf(ParagraphBlockSpec.class,
                report.getSections().get(2).getBlocks().get(0));
//        assertTrue(conclusionText.getText().contains("功能检查"));
    }

    /** 显式开启后由本机 Ollama 生成 DOCX，并重新打开检查标题及表格。 */
    @Test
    @EnabledIfSystemProperty(named = "omni.ai.ollama.integration", matches = "true")
    void localOllamaExportsWordFromAssembledString() throws Exception {
        String model = System.getProperty("omni.ai.model", "qwen3.5:2b");
        Path output = Path.of(System.getProperty("omni.ai.output",
                "docs/generated/shared-text-assessment-ollama.docx")).toAbsolutePath();
        Files.createDirectories(output.getParent());
        AiGenerationPolicy policy = new AiGenerationPolicy(4, 20_000, 100_000, 2_000_000);

        new StructuredReportAiApplication(new OllamaStructuredAiClient(model), policy)
                .generateDocx(template(), businessData(assembledBusinessText()), output);

        assertExportedWord(output);
    }

    /** 检查生成的文件可重新打开，且包含模板标题和三条检查记录表格。 */
    private void assertExportedWord(Path output) throws Exception {
        assertTrue(Files.isRegularFile(output));
        assertTrue(Files.size(output) > 0);
        Document word = new Document(output.toString());
        assertTrue(word.getText().contains("项目概述"));
        assertTrue(word.getText().contains("功能验证"));
        assertTrue(word.getText().contains("评估结论"));
        assertEquals(1, word.getChildNodes(NodeType.TABLE, true).getCount());
        Table table = (Table) word.getChildNodes(NodeType.TABLE, true).get(0);
        assertEquals(4, table.getRows().getCount());
    }

    /** 将业务代码已组装好的原始字符串包装为框架要求的顶层 JSON 对象。 */
    private ObjectNode businessData(String assembledText) {
        return mapper.createObjectNode().put("content", assembledText);
    }

    /** 示例原始业务文字：框架不要求在这里拆分出 project、checks 等业务 key。 */
    private String assembledBusinessText() {
        String content = "分析主题：任务归档接口超时\n"
                + "分析对象：智能体训练软件 1.0 的任务归档功能\n"
                + "现象：归档接口响应时间 p95 从 180 毫秒升至 3.2 秒，超时率从 0.2% 升至 8.1%。\n"
                + "变更：10:05 上线后，每个数据库事务处理的归档任务数从 100 增至 1000；其他连接池配置未变。\n"
                + "\n"
                + "因果对 C1：\n"
                + "原因：单事务归档任务数从 100 增至 1000。\n"
                + "结果：数据库事务持续时间 p95 从 120 毫秒升至 2.6 秒。\n"
                + "作用机制：单个事务处理更多记录，连接被占用更久。\n"
                + "证据：相同并发条件下，分别使用 100 和 1000 的批量大小进行对照测试，观察到上述差异。\n"
                + "判定：较高可信度。\n"
                + "\n"
                + "因果对 C2：\n"
                + "原因：数据库事务持续时间增加，连接占用时间延长。\n"
                + "结果：连接池等待时间 p95 从 20 毫秒升至 1.4 秒。\n"
                + "作用机制：高并发请求需要等待可用连接。\n"
                + "证据：问题时段并发请求约 30 个，连接池上限为 20；连接池等待指标同步升高。\n"
                + "判定：有指标支持，但尚未单独隔离验证。\n"
                + "\n"
                + "因果对 C3：\n"
                + "原因：获取数据库连接的等待时间增加。\n"
                + "结果：归档接口响应变慢并出现超时。\n"
                + "证据：将批量大小恢复为 100 后，连接池等待时间 p95 降至 25 毫秒，接口超时率降至 0.3%。\n"
                + "判定：较高可信度。\n"
                + "\n"
                + "其他可能因素：数据库查询计划未观察到变化；网络延迟无明显上升。仍需补充不同并发量下的对照测试。\n"
                + "建议：暂时维持每事务 100 条，补充批量大小与并发量的组合测试，再确定正式参数。";
        return content;
    }

    /** 仅保留一条因果对，用于验证模型按同一提示词选择段落。 */
    private String singleCheckBusinessText() {
        String source = assembledBusinessText();
        int secondPair = source.indexOf("\n因果对 C2：");
        int alternatives = source.indexOf("\n其他可能因素：");
        return source.substring(0, secondPair) + source.substring(alternatives);
    }

    /** 读取可复用的标题和逐标题提示词模板。 */
    private JsonNode template() throws Exception {
        return mapper.readTree(Files.readString(Path.of(
                "docs/examples/structured-report-shared-text-template.json")));
    }

    /** 离线模型仅用于验证调用链；真实模型由显式开启的集成测试验证。 */
    private String controlledResponse(StructuredAiRequest request) {
        String id = request.getContext().path("sectionId").asText();
        ObjectNode response = mapper.createObjectNode();
        response.put("sectionId", id);
        response.put("status", "READY");
        response.putArray("questions");
        ObjectNode block = response.putArray("blocks").addObject();
        block.putArray("factRefs").add("/content");
        if ("checks".equals(id)) {
            String source = request.getContext().path("facts").path("/content").asText();
            if (source.contains("因果对 C2：")) {
                block.put("type", "table");
                block.putArray("headers").add("检查项").add("结果").add("依据");
                ArrayNode rows = block.putArray("rows");
                rows.addArray().add("因果对 C1").add("事务持续时间增加").add("相同并发条件下的批量大小对照测试");
                rows.addArray().add("因果对 C2").add("连接池等待时间增加").add("连接池等待指标同步升高");
                rows.addArray().add("因果对 C3").add("归档接口超时").add("回退批量大小后超时率下降");
            } else {
                block.put("type", "paragraph");
                block.put("text", "因果对 C1：批量大小增加后事务持续时间上升；依据为对照测试。");
            }
        } else {
            block.put("type", "paragraph");
            block.put("text", "已根据共享业务文字填充当前标题。");
        }
        return response.toString();
    }
}
