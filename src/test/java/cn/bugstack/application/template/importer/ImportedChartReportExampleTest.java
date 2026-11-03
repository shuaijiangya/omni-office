package cn.bugstack.application.template.importer;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.StructuredAiRequest;
import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import cn.bugstack.application.ai.report.StructuredReportAiApplication;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import com.aspose.words.StyleIdentifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 展示 DOCX 导入分析、槽位审核、业务数据填充与原生图表写回的完整示例。
 *
 * <p>默认使用可重复的结构化响应；设置 {@code -Domni.office.example.ollama=true}
 * 时，摘要和结论由本机 {@code qwen3.5:2b} 生成，数值表格与图表仍直接取业务事实，
 * 避免模型改写统计数据。模板和报告写在 {@code target}，不会覆盖用户文档。</p>
 */
class ImportedChartReportExampleTest {

    /** 源模板输出位置。 */
    static final Path TEMPLATE = Path.of("target", "imported-chart-report-template.docx");
    /** 新报告输出位置。 */
    static final Path REPORT = Path.of("target", "imported-chart-report.docx");

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 从磁盘重新导入模板，填充段落、动态表格和五类 Word 图表。
     *
     * @throws Exception 模板创建、生成或断言失败时抛出
     */
    @Test
    void generatesNewReportFromImportedTemplateWithFiveEditableCharts() throws Exception {
        Files.createDirectories(TEMPLATE.getParent());
        createTemplate(TEMPLATE);
        Files.deleteIfExists(REPORT);

        DocxTemplateAnalysis analysis = new DocxTemplateAnalyzer().analyze(TEMPLATE, "operations-chart-report");
        ObjectNode template = analysis.getReportTemplate();
        ObjectNode bindings = analysis.getStyleBindings();
        reviewTemplate(template);
        JsonNode data = businessData();
        StructuredAiClient client = responseClient(Boolean.getBoolean("omni.office.example.ollama"));
        new DocxTemplateReportApplication(new StructuredReportAiApplication(client))
                .generate(TEMPLATE, template, bindings, data, REPORT);

        Document generated = new Document(REPORT.toString());
        assertTrue(generated.getText().contains("业务运行评估报告"));
        assertTrue(generated.getText().contains("累计处理任务"));
        assertFalse(generated.getText().contains("{{"));
        assertEquals(4, generated.getSections().get(0).getBody().getTables().get(0).getRows().getCount());
        int charts = 0;
        for (Object item : generated.getChildNodes(NodeType.SHAPE, true)) {
            if (((Shape) item).hasChart()) charts++;
        }
        assertEquals(5, charts);
        assertTrue(new Document(TEMPLATE.toString()).getText().contains("{{chart:quarter}}"));
        System.out.println("Imported report template: " + TEMPLATE.toAbsolutePath());
        System.out.println("New report with five charts: " + REPORT.toAbsolutePath());
    }

    /** 创建具有固定版式和明确填充槽位的源 DOCX。 */
    private void createTemplate(Path path) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.getPageSetup().setTopMargin(52);
        builder.getPageSetup().setBottomMargin(52);
        builder.getPageSetup().setLeftMargin(58);
        builder.getPageSetup().setRightMargin(58);
        builder.getFont().setName("宋体");
        builder.getFont().setNameFarEast("宋体");
        builder.getFont().setSize(11);
        builder.getFont().setColor(Color.BLACK);

        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.TITLE);
        builder.getFont().setNameFarEast("黑体");
        builder.getFont().setSize(22);
        builder.getFont().setBold(true);
        builder.writeln("业务运行评估报告");
        builder.getFont().setNameFarEast("宋体");
        builder.getFont().setSize(11);
        builder.getFont().setBold(false);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.writeln("本报告汇总 2026 年业务运行指标，涵盖任务量、完成率、产品构成和产品能力评价。");

        heading(builder, StyleIdentifier.HEADING_1, "报告摘要");
        builder.writeln("{{summary}}");
        heading(builder, StyleIdentifier.HEADING_1, "核心指标");
        builder.startTable();
        builder.getCellFormat().getShading().setBackgroundPatternColor(new Color(230, 237, 245));
        builder.getFont().setNameFarEast("黑体");
        builder.insertCell(); builder.write("指标");
        builder.insertCell(); builder.write("2025年");
        builder.insertCell(); builder.write("2026年");
        builder.endRow();
        builder.getFont().setNameFarEast("宋体");
        builder.getCellFormat().getShading().setBackgroundPatternColor(Color.WHITE);
        builder.insertCell(); builder.write("{{table:metrics}}");
        builder.insertCell(); builder.write("待填充");
        builder.insertCell(); builder.write("待填充");
        builder.endRow();
        builder.endTable();
        builder.writeln();

        heading(builder, StyleIdentifier.HEADING_1, "图表分析");
        chartSlot(builder, "季度处理任务", "quarter");
        chartSlot(builder, "产品构成", "mix");
        chartSlot(builder, "年度指标对比", "compare");
        chartSlot(builder, "月度用户趋势", "trend");
        chartSlot(builder, "产品能力评估", "radar");
        heading(builder, StyleIdentifier.HEADING_1, "评估结论");
        builder.write("{{conclusion}}");
        document.save(path.toString());
    }

    /** 写入黑色的语义标题。 */
    private void heading(DocumentBuilder builder, int style, String title) throws Exception {
        builder.getParagraphFormat().setStyleIdentifier(style);
        builder.getFont().setNameFarEast("黑体");
        builder.getFont().setBold(true);
        builder.getFont().setColor(Color.BLACK);
        builder.writeln(title);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.getFont().setNameFarEast("宋体");
        builder.getFont().setBold(false);
    }

    /** 每个图表占位符独占一个段落，供导入分析器精确定位。 */
    private void chartSlot(DocumentBuilder builder, String title, String marker) throws Exception {
        heading(builder, StyleIdentifier.HEADING_2, title);
        builder.writeln("{{chart:" + marker + "}}");
    }

    /** 为分析得到的章节明确指定数据来源与图表的可编辑约束。 */
    private void reviewTemplate(ObjectNode template) {
        String[] pointers = {"/summary", "/metrics", null,
                "/charts/quarter", "/charts/mix", "/charts/compare",
                "/charts/trend", "/charts/radar", "/conclusion"};
        String[] prompts = {"根据事实简述本年度业务变化，保留原始数字。",
                "严格按业务事实填充固定三列表格，不改变表头或数值。", "",
                "根据业务事实绘制季度处理任务柱状图。", "根据业务事实绘制产品构成饼图。",
                "根据业务事实绘制年度指标横向对比图。", "根据业务事实绘制月度用户折线图。",
                "根据业务事实绘制产品能力雷达图。", "根据事实给出简短结论，不补造原因。"};
        ArrayNode sections = template.withArray("sections");
        assertEquals(pointers.length, sections.size());
        for (int index = 0; index < sections.size(); index++) {
            if (pointers[index] == null) continue;
            ObjectNode section = (ObjectNode) sections.get(index);
            section.put("prompt", prompts[index]);
            section.withArray("dataRefs").removeAll().add(pointers[index]);
            if (index >= 3 && index <= 7) {
                section.put("chartStylePreset", "BUSINESS_BLUE");
                section.put("editableRequired", true);
            }
        }
    }

    /** 样本业务事实；图表数值与表格数值均从这里读取。 */
    private JsonNode businessData() {
        ObjectNode data = mapper.createObjectNode();
        data.put("summary", "2026 年累计处理任务 68 万，2025 年为 51 万；按期完成率由 82% 提升至 91%。");
        data.put("conclusion", "处理任务量和按期完成率均较上年提高。当前产品能力得分仍低于目标值，需关注性能与安全性差距。");
        ArrayNode metrics = data.putArray("metrics");
        metrics.addArray().add("累计处理任务（万）").add("51").add("68");
        metrics.addArray().add("按期完成率").add("82%").add("91%");
        metrics.addArray().add("自动化处理率").add("64%").add("79%");
        ObjectNode charts = data.putObject("charts");
        chart(charts, "quarter", "季度处理任务数（万）",
                new String[]{"第一季度", "第二季度", "第三季度", "第四季度"},
                new String[]{"2026年"}, new double[][]{{11, 15, 19, 23}});
        chart(charts, "mix", "产品业务构成（%）",
                new String[]{"文档生成", "图形生成", "模板服务", "外部工具"},
                new String[]{"占比"}, new double[][]{{42, 27, 19, 12}});
        chart(charts, "compare", "年度业务指标对比（%）",
                new String[]{"按期完成率", "自动化处理率", "一次通过率"},
                new String[]{"2025年", "2026年"}, new double[][]{{82, 64, 85}, {91, 79, 92}});
        chart(charts, "trend", "月度活跃用户（千人）",
                new String[]{"1月", "2月", "3月", "4月", "5月", "6月"},
                new String[]{"2025年", "2026年"},
                new double[][]{{42, 48, 53, 61, 67, 75}, {55, 63, 72, 84, 96, 112}});
        chart(charts, "radar", "产品能力评分",
                new String[]{"易用性", "稳定性", "性能", "扩展性", "安全性"},
                new String[]{"当前版本", "目标版本"},
                new double[][]{{82, 88, 84, 91, 86}, {92, 94, 92, 96, 95}});
        return data;
    }

    /** 创建一个图表事实对象。 */
    private void chart(ObjectNode charts, String key, String title, String[] categories,
                       String[] names, double[][] values) {
        ObjectNode chart = charts.putObject(key);
        chart.put("title", title);
        ArrayNode labels = chart.putArray("categories");
        for (String category : categories) labels.add(category);
        ArrayNode series = chart.putArray("series");
        for (int index = 0; index < names.length; index++) {
            ObjectNode item = series.addObject();
            item.put("name", names[index]);
            ArrayNode numbers = item.putArray("values");
            for (double value : values[index]) numbers.add(value);
        }
    }

    /** 正文可切换真实 Ollama；表格和图表保持数据确定性。 */
    private StructuredAiClient responseClient(boolean useOllama) {
        StructuredAiClient ollama = useOllama ? new OllamaStructuredAiClient("qwen3.5:2b") : null;
        return request -> {
            String id = request.getContext().path("sectionId").asText();
            ObjectNode root = mapper.createObjectNode();
            root.put("sectionId", id).put("status", "READY");
            ArrayNode blocks = root.putArray("blocks");
            root.putArray("questions");
            JsonNode facts = request.getContext().path("facts");
            String ref = facts.fieldNames().next();
            JsonNode value = facts.path(ref);
            ObjectNode block = blocks.addObject();
            block.put("slotId", "slot-001");
            block.putArray("factRefs").add(ref);
            if ("section-001".equals(id) || "section-009".equals(id)) {
                String narrative = useOllama ? generateNarrative(ollama, request, ref) : value.asText();
                block.put("type", "paragraph").put("text", narrative);
            } else if ("section-002".equals(id)) {
                block.put("type", "table");
                block.putArray("headers").add("指标").add("2025年").add("2026年");
                block.set("rows", value.deepCopy());
            } else {
                block.put("type", "chart");
                block.put("chartType", chartType(id));
                block.put("title", value.path("title").asText());
                block.set("categories", value.path("categories").deepCopy());
                block.set("series", value.path("series").deepCopy());
            }
            return root.toString();
        };
    }

    /** 用简化的 JSON Schema 让小模型只负责文字，不让它重建槽位协议。 */
    private String generateNarrative(StructuredAiClient ollama, StructuredAiRequest original, String ref) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("text").put("type", "string");
        schema.putArray("required").add("text");
        schema.put("additionalProperties", false);
        StructuredAiRequest narrativeRequest = new StructuredAiRequest(
                "write_report_paragraph",
                "你是中文业务报告撰写器。只能改写给定事实，不增加数字、原因或结论。只返回 JSON 对象。",
                "将 facts 中 " + ref + " 的内容写成一段客观、通顺的报告正文；保留原有数值与比较关系，不超过 120 字。",
                original.getContext(), schema, original.getAttempt(), original.getValidationFeedback());
        try {
            JsonNode result = mapper.readTree(ollama.generateJson(narrativeRequest));
            String text = result.path("text").asText().trim();
            if (text.isEmpty()) throw new IllegalStateException("Ollama did not return paragraph text");
            return text;
        } catch (Exception e) {
            throw new IllegalStateException("failed to generate narrative with local Ollama", e);
        }
    }

    /** 将图表标题所在章节映射到受支持的原生图表类型。 */
    private String chartType(String id) {
        switch (id) {
            case "section-004": return "COLUMN";
            case "section-005": return "PIE";
            case "section-006": return "BAR";
            case "section-007": return "LINE";
            case "section-008": return "RADAR";
            default: throw new IllegalArgumentException("unexpected chart section: " + id);
        }
    }
}
