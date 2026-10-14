package cn.bugstack.application.completion;

import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.CellMerge;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.Paragraph;
import com.aspose.words.Run;
import com.aspose.words.StyleIdentifier;
import com.aspose.words.Table;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 手动启用的真实 Ollama 多样本验收示例；默认测试集不会调用外部模型。 */
class SourceDocxOllamaLiveExampleTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void plansAndAppliesMultipleSourceDocumentShapes() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("ollama.live"),
                "set -Dollama.live=true to run the locally installed model");
        AsposeWordsLicenseLoader.applyConfiguredLicense();
        Path root = Path.of("target", "ollama-completion-samples").toAbsolutePath();
        Files.createDirectories(root);
        String model = System.getProperty("ollama.model", "qwen3.5:2b");
        double temperature = Double.parseDouble(System.getProperty("ollama.temperature", "0.0"));
        StructuredAiClient localModel = new OllamaStructuredAiClient(
                URI.create("http://127.0.0.1:11434/api/chat"), model, Duration.ofMinutes(5), temperature,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), mapper);
        List<String> outcomes = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        String selected = System.getProperty("ollama.case", "");
        for (String id : List.of("paragraph", "paragraph-explicit", "table",
                "merged-cell", "merged-cell-explicit", "styled-run", "styled-run-explicit", "needs-input")) {
            if (!selected.isBlank() && !selected.equals(id)) continue;
            try {
                StructuredAiClient traced = request -> {
                    String raw = localModel.generateJson(request);
                    try {
                        Files.writeString(root.resolve(id + "-attempt-" + request.getAttempt() + ".json"),
                                raw, StandardCharsets.UTF_8);
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("failed to save local model trace", e);
                    }
                    return raw;
                };
                SourceDocxCompletionApplication application = new SourceDocxCompletionApplication(traced);
                runCase(root, application, id, outcomes);
            } catch (Exception | AssertionError error) {
                failures.add(id + ": " + error.getClass().getSimpleName() + " - " + error.getMessage());
                System.out.println("OLLAMA_SAMPLE_FAIL " + id + " " + error);
            }
        }
        System.out.println("OLLAMA_SAMPLE_RESULTS " + String.join(" | ", outcomes));
        assertTrue(failures.isEmpty(), String.join(" | ", failures));
    }

    private void runCase(Path root, SourceDocxCompletionApplication application, String id,
                         List<String> outcomes) throws Exception {
        Path source = root.resolve(id + "-source.docx");
        Path output = root.resolve(id + "-completed.docx");
        Files.deleteIfExists(output);
        String type = id.replace("-explicit", "");
        createSource(type, source);
        Files.writeString(root.resolve(id + "-structure.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(application.analyze(source)),
                StandardCharsets.UTF_8);
        ObjectNode facts = mapper.createObjectNode();
        String instruction;
        String expected;
        switch (id) {
            case "paragraph":
            case "paragraph-explicit":
                facts.put("scope", "本系统仅处理订单创建和订单查询，不处理支付。");
                instruction = id.endsWith("-explicit")
                        ? "只用 paragraphPatches 将『待补充范围说明』替换为『"
                        + facts.path("scope").asText() + "』。texts 中必须是新文字，不能是原占位符。"
                        : "只把正文中的『待补充范围说明』替换成 businessFacts.scope，不修改标题或其他内容。";
                expected = facts.path("scope").asText();
                break;
            case "table":
                facts.put("api", "POST /orders");
                facts.put("description", "创建订单");
                instruction = "只补全接口表中 POST /orders 这一行的空白说明格。用接口列作为行主键，"
                        + "保持其他行不变，不新增行。";
                expected = facts.path("description").asText();
                break;
            case "merged-cell":
            case "merged-cell-explicit":
                facts.put("owner", "王工");
                instruction = id.endsWith("-explicit")
                        ? "只用 cellPatches 对该表 tableId 的 rowIndex=1、columnIndex=1 填入『王工』。"
                        + "tablePatches 必须为空数组，不修改首行合并区域。"
                        : "这是固定格式表，首行是横向合并的标题。只用 cellPatches 把『负责人』右侧"
                        + "的普通空白物理单元格填成 businessFacts.owner，不修改合并区域。";
                expected = facts.path("owner").asText();
                break;
            case "styled-run":
            case "styled-run-explicit":
                facts.put("conclusion", "通过");
                instruction = id.endsWith("-explicit")
                        ? "只能使用 runPatches，paragraphPatches 必须为空数组。找到『评审结论：待填写』"
                        + "段落，对 runIndex=1 且 expectedText='待填写' 的 Run 写入新 text='通过'，"
                        + "overwritePolicy='OVERWRITE_ALL'。"
                        : "结论段落有两个不同样式的 Run。只使用 runPatches 把『待填写』这个 Run"
                        + "替换成 businessFacts.conclusion，保留其他 Run 和原样式。";
                expected = facts.path("conclusion").asText();
                break;
            case "needs-input":
                instruction = "填写实施日期。用户尚未提供日期，不得猜测；请提出明确问题。";
                expected = null;
                break;
            default:
                throw new IllegalArgumentException("unknown sample: " + id);
        }
        DocumentCompletionPlan plan = application.planDocument(source, instruction, facts);
        ObjectNode reviewJson = application.reviewablePlan(plan);
        Files.writeString(root.resolve(id + "-plan.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(reviewJson), StandardCharsets.UTF_8);
        if (expected == null) {
            assertTrue(!plan.isReady() && !plan.getQuestions().isEmpty(),
                    "model must request a missing date instead of inventing one");
            outcomes.add(id + "=NEEDS_INPUT");
            System.out.println("OLLAMA_SAMPLE " + id + " NEEDS_INPUT " + plan.getQuestions());
            return;
        }
        assertTrue(plan.isReady(), "model did not produce a READY plan: " + plan.getQuestions());
        assertTrue(containsPatchValue(reviewJson, expected),
                "READY plan does not contain the supplied fact; do not apply it: " + expected);
        DocumentCompletionResult result = application.applyReviewedPlan(source, output, reviewJson);
        Document completedDocument = new Document(output.toString());
        String completed = completedDocument.getText();
        assertTrue(completed.contains(expected), "completed DOCX is missing expected fact: " + expected);
        if ("styled-run".equals(type)) {
            Paragraph paragraph = findParagraph(completedDocument, "评审结论");
            assertTrue(paragraph != null && paragraph.getRuns().getCount() == 2,
                    "independently styled Run structure must be retained");
            Run edited = paragraph.getRuns().get(1);
            assertTrue(Color.RED.equals(edited.getFont().getColor()) && edited.getFont().getBold(),
                    "target Run must retain red bold styling");
        }
        if ("merged-cell".equals(type)) {
            assertTrue(completedDocument.getFirstSection().getBody().getTables().get(0)
                    .getRows().get(0).getCells().getCount() == 1,
                    "merged title row must remain merged");
        }
        outcomes.add(id + "=READY");
        System.out.println("OLLAMA_SAMPLE " + id + " READY " + output + " changed="
                + result.getParagraphsChanged() + "/" + result.getRowsUpdated() + "/"
                + result.getCellsChanged() + "/" + result.getRunsChanged());
    }

    private void createSource(String id, Path output) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.TITLE);
        builder.writeln("订单服务文档补全样本");
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_1);
        builder.writeln("1 " + id);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        switch (id) {
            case "paragraph":
                builder.writeln("待补充范围说明");
                break;
            case "table":
                builder.startTable();
                builder.insertCell(); builder.write("接口");
                builder.insertCell(); builder.write("说明");
                builder.endRow();
                builder.insertCell(); builder.write("POST /orders");
                builder.insertCell();
                builder.endRow();
                builder.insertCell(); builder.write("GET /orders/{id}");
                builder.insertCell(); builder.write("查询订单");
                builder.endRow();
                builder.endTable();
                break;
            case "merged-cell":
                Table table = builder.startTable();
                builder.insertCell(); builder.write("项目基本信息");
                builder.insertCell();
                builder.endRow();
                builder.insertCell(); builder.write("负责人");
                builder.insertCell();
                builder.endRow();
                builder.endTable();
                table.getRows().get(0).getCells().get(0).getCellFormat().setHorizontalMerge(CellMerge.FIRST);
                table.getRows().get(0).getCells().get(1).getCellFormat().setHorizontalMerge(CellMerge.PREVIOUS);
                break;
            case "styled-run":
                Paragraph paragraph = new Paragraph(document);
                Run label = new Run(document, "评审结论：");
                Run value = new Run(document, "待填写");
                label.getFont().setColor(Color.BLACK);
                value.getFont().setColor(Color.RED);
                value.getFont().setBold(true);
                paragraph.appendChild(label);
                paragraph.appendChild(value);
                document.getFirstSection().getBody().appendChild(paragraph);
                break;
            case "needs-input":
                builder.writeln("实施日期：待补充");
                break;
            default:
                throw new IllegalArgumentException("unknown sample: " + id);
        }
        document.save(output.toString());
    }

    private Paragraph findParagraph(Document document, String text) throws Exception {
        for (Paragraph paragraph : document.getFirstSection().getBody().getParagraphs()) {
            if (paragraph.getText().contains(text)) return paragraph;
        }
        return null;
    }

    private boolean containsPatchValue(JsonNode plan, String expected) {
        for (JsonNode patch : plan.path("paragraphPatches")) {
            for (JsonNode text : patch.path("texts")) {
                if (text.asText().contains(expected)) return true;
            }
        }
        for (JsonNode patch : plan.path("tablePatches")) {
            for (JsonNode row : patch.path("rows")) {
                java.util.Iterator<JsonNode> values = row.elements();
                while (values.hasNext()) {
                    if (values.next().asText().contains(expected)) return true;
                }
            }
        }
        for (JsonNode patch : plan.path("cellPatches")) {
            if (patch.path("text").asText().contains(expected)) return true;
        }
        for (JsonNode patch : plan.path("runPatches")) {
            if (patch.path("text").asText().contains(expected)) return true;
        }
        return false;
    }
}
