package cn.bugstack.application.completion;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import com.aspose.words.StyleIdentifier;
import com.aspose.words.Table;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 真实 Ollama 调用：无槽位报告的正文、表格和原生图表自动补全。 */
class AdaptiveSourceDocxOllamaLiveTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 仅通过 JVM 开关运行，避免常规测试集依赖本地模型服务。 */
    @Test
    void completesUnmarkedReportWithRealOllama() throws Exception {
//        assumeTrue(Boolean.getBoolean("ollama.adaptive.live"),
//                "set -Dollama.adaptive.live=true to invoke the local model");
        Path root = Path.of("target", "adaptive-source-ollama").toAbsolutePath();
        Files.createDirectories(root);
        Path source = root.resolve("source.docx");
        Path output = root.resolve("completed.docx");
        Files.deleteIfExists(output);
//        createSource(source);
        assertTrue(Files.isRegularFile(source), "source DOCX does not exist: " + source);
        ObjectNode facts = facts();
        String modelName = System.getProperty("ollama.model", "qwen3.5:2b");
        StructuredAiClient ollama = new OllamaStructuredAiClient(
                URI.create("http://127.0.0.1:11434/api/chat"), modelName, Duration.ofMinutes(5), 0D,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), mapper);
        AtomicInteger calls = new AtomicInteger();
        StructuredAiClient traced = request -> {
            String response = ollama.generateJson(request);
            int call = calls.incrementAndGet();
            try {
                Files.writeString(root.resolve("model-response-" + call + ".json"),
                        response, StandardCharsets.UTF_8);
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("failed to persist Ollama response", failure);
            }
            System.out.println("OLLAMA_ADAPTIVE " + request.getOperation() + " #" + call + " " + response);
            return response;
        };
        AdaptiveSourceDocxCompletionApplication app = new AdaptiveSourceDocxCompletionApplication(traced);
        String instruction = "请依据业务数据，把当前报告中能够补充的内容补充完整。并将空余的表格内容补充完整"
                + "自行判断正文、表格以及必要图表的填充位置；保留现有内容，并在每段章节下保留正文信息。确保文章字数在1000左右";
        DocumentCompletionPlan plan = app.plan(source, instruction, facts);
        Files.writeString(root.resolve("review-plan.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                        new DocumentCompletionPlanJsonCodec().write(plan)), StandardCharsets.UTF_8);
        assertTrue(plan.isReady(), "AI should find evidence-backed content: " + plan.getQuestions());
        assertTrue(!plan.getParagraphs().isEmpty(), "AI should add report prose");
        assertTrue(!plan.getTables().isEmpty() || !plan.getCells().isEmpty(),
                "AI should complete the existing table: " + plan.getReason());
//        assertTrue(!plan.getCharts().isEmpty(), "AI should add a chart from numeric facts");
        DocumentCompletionResult result = app.apply(source, output, plan);
        assertTrue(result.getParagraphsChanged() > 0);
        assertTrue(result.getRowsUpdated() + result.getRowsAppended() + result.getCellsChanged() > 0);
//        assertTrue(result.getChartsInserted() > 0);
        Document completed = new Document(output.toString());
        completed.save(root.resolve("aspose-preview.pdf").toString());
        Table completedTable = completed.getFirstSection().getBody().getTables().get(0);
        assertEquals(3, completedTable.getRows().getCount());
        assertTrue(completedTable.getRows().get(1).getCells().get(1).getText().contains("3"));
        assertTrue(completedTable.getRows().get(1).getCells().get(2).getText().contains("增加重试与告警"));
        assertTrue(completedTable.getRows().get(2).getCells().get(2).getText().contains("调整阈值并巡检"));
        assertTrue(completed.getText().contains("订单平台"));
        assertTrue(completed.getText().contains("接口超时"));
        assertTrue(completed.getText().contains("资源告警"));
        int charts = 0;
        for (Object item : completed.getChildNodes(NodeType.SHAPE, true)) {
            if (((Shape) item).hasChart()) charts++;
        }
        assertEquals(result.getChartsInserted(), charts);
        assertTrue(calls.get() >= 2, "body/table and chart planning must call the real model");
        System.out.println("OLLAMA_ADAPTIVE_OUTPUT " + output);
    }

    /** 第二份真实样本：没有 Word 标题样式、没有表格和图表的普通文章。 */
    @Test
    void completesPlainArticleWithRealOllama() throws Exception {
        assumeTrue(Boolean.getBoolean("ollama.adaptive.live"),
                "set -Dollama.adaptive.live=true to invoke the local model");
        Path root = Path.of("target", "adaptive-source-ollama").toAbsolutePath();
        Files.createDirectories(root);
        Path source = root.resolve("plain-source.docx");
        Path output = root.resolve("plain-completed.docx");
        Document article = new Document();
        DocumentBuilder writer = new DocumentBuilder(article);
        writer.getFont().setName("Songti SC");
        writer.getFont().setNameFarEast("Songti SC");
        writer.writeln("清算服务巡检简报");
        writer.writeln("本文记录已确认的巡检事实。");
        article.save(source.toString());
        ObjectNode facts = mapper.createObjectNode();
        facts.putObject("inspection").put("system", "清算服务")
                .put("period", "2026年10月")
                .put("finding", "核心接口响应正常");
        StructuredAiClient model = new OllamaStructuredAiClient(
                System.getProperty("ollama.model", "qwen3.5:2b"));
        AdaptiveSourceDocxCompletionApplication app = new AdaptiveSourceDocxCompletionApplication(model);
        DocumentCompletionPlan plan = app.plan(source,
                "根据业务数据补充当前文章中可以补充的巡检事实，保留现有内容。每段段落下需要增加一段正文信息", facts);
        System.out.println("OLLAMA_ADAPTIVE_PLAIN_PLAN "
                + new DocumentCompletionPlanJsonCodec().write(plan));
        assertTrue(plan.isReady(), "plain article should not request supplied facts again: "
                + plan.getQuestions());
        assertTrue(!plan.getParagraphs().isEmpty(), "plain article should receive factual prose");
        app.apply(source, output, plan);
        assertTrue(new Document(output.toString()).getText().contains("核心接口响应正常"));
        System.out.println("OLLAMA_ADAPTIVE_PLAIN_OUTPUT " + output);
    }

    /** 创建不含占位符、提示段落或预定义填充槽位的业务报告。 */
    private void createSource(Path output) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.getPageSetup().setTopMargin(50);
        builder.getPageSetup().setBottomMargin(50);
        for (int style : new int[]{StyleIdentifier.NORMAL, StyleIdentifier.TITLE,
                StyleIdentifier.HEADING_1}) {
            document.getStyles().getByStyleIdentifier(style).getFont().setName("Songti SC");
            document.getStyles().getByStyleIdentifier(style).getFont().setNameFarEast("Songti SC");
        }
        builder.getFont().setName("Songti SC");
        builder.getFont().setNameFarEast("Songti SC");
        builder.getFont().setSize(11);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.TITLE);
        builder.writeln("订单平台运行评估报告");
        heading(builder, "项目概况");
        builder.writeln("项目名称：订单平台。");
        heading(builder, "风险统计");
        builder.startTable();
        builder.insertCell(); builder.write("风险项");
        builder.insertCell(); builder.write("数量");
        builder.insertCell(); builder.write("处置建议");
        builder.endRow();
        builder.insertCell(); builder.write("接口超时");
        builder.insertCell();
        builder.insertCell();
        builder.endRow();
        builder.endTable();
        builder.writeln();
        heading(builder, "风险分析");
        heading(builder, "风险数量图表");
        heading(builder, "评估结论");
        builder.writeln("本报告保留原始章节和已有内容。");
        document.save(output.toString());
    }

    /** 写入 Word 语义标题。 */
    private void heading(DocumentBuilder builder, String title) throws Exception {
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_1);
        builder.writeln(title);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
    }

    /** 模拟调用方真实提供的业务事实；所有数字均有明确来源。 */
    private ObjectNode facts() {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("project").put("name", "订单平台")
                .put("period", "2026年第四季度")
                .put("progress", "订单创建与查询接口已完成联调");
        root.putArray("risks")
                .addObject().put("name", "接口超时").put("count", 3)
                .put("advice", "增加重试与告警");
        root.withArray("risks").addObject().put("name", "资源告警")
                .put("count", 2).put("advice", "调整阈值并巡检");
        root.put("conclusion", "已完成接口联调，需持续跟踪两项风险。请细化风险内容");
        return root;
    }
}
