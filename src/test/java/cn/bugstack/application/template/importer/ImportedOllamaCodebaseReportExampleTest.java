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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 使用真实 Ollama 与当前仓库的 Java 文件统计，演示 DOCX 模板导入后的报告生成。
 *
 * <p>运行命令须显式设置 {@code -Domni.office.example.realOllama=true}。普通单元测试
 * 不会依赖本机模型服务；启用后每个正文、表格及图表槽位都会请求 Ollama。统计事实
 * 在测试运行时从源码目录采集；模型选择模块排列，表格和图表数值再从事实映射。</p>
 */
class ImportedOllamaCodebaseReportExampleTest {

    /** 导入用的源模板，不会被生成流程修改。 */
    private static final Path TEMPLATE = Path.of("target", "codebase-ollama-report-template.docx");
    /** 使用真实 Ollama 生成的新报告。 */
    private static final Path REPORT = Path.of("target", "codebase-ollama-report.docx");
    /** 模板表格的固定标题。 */
    private static final String[] HEADERS = {"模块", "主代码 Java 文件", "测试 Java 文件"};

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 导入模板、读取真实源码统计、调用本机 Ollama 并验证报告中的可编辑图表。
     *
     * @throws Exception 模板、模型请求或文档验证失败时抛出
     */
    @Test
    void importsTemplateAndGeneratesReportFromRealOllamaAndRepositoryFacts() throws Exception {
        assumeTrue(Boolean.getBoolean("omni.office.example.realOllama"),
                "Run explicitly with -Domni.office.example.realOllama=true");
        Files.createDirectories(TEMPLATE.getParent());
        createTemplate();
        Files.deleteIfExists(REPORT);

        DocxTemplateAnalysis analysis = new DocxTemplateAnalyzer().analyze(TEMPLATE, "codebase-ollama-report");
        ObjectNode template = analysis.getReportTemplate();
        configureTemplate(template);
        ObjectNode facts = repositoryFacts();
        AtomicInteger modelCalls = new AtomicInteger();
        StructuredAiClient client = new VerifiedOllamaClient(new OllamaStructuredAiClient("qwen3.5:2b"),
                facts, modelCalls);
        new DocxTemplateReportApplication(new StructuredReportAiApplication(client))
                .generate(TEMPLATE, template, analysis.getStyleBindings(), facts, REPORT);

        Document output = new Document(REPORT.toString());
        assertTrue(output.getText().contains("项目源码结构评估报告"));
        assertFalse(output.getText().contains("{{"));
        assertEquals(6, modelCalls.get(), "each non-heading slot must call the real model");
        assertEquals(5, output.getSections().get(0).getBody().getTables().get(0).getRows().getCount());
        int chartCount = 0;
        for (Object node : output.getChildNodes(NodeType.SHAPE, true)) {
            if (((Shape) node).hasChart()) chartCount++;
        }
        assertEquals(3, chartCount);
        assertTrue(new Document(TEMPLATE.toString()).getText().contains("{{chart:source}}"));
        System.out.println("Real repository facts: " + facts);
        System.out.println("Real Ollama calls: " + modelCalls.get());
        System.out.println("Imported template: " + TEMPLATE.toAbsolutePath());
        System.out.println("Generated report: " + REPORT.toAbsolutePath());
    }

    /** 创建带有正文、重复表格行和三个图表物理槽位的 Word 模板。 */
    private void createTemplate() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.getPageSetup().setTopMargin(52);
        builder.getPageSetup().setBottomMargin(52);
        builder.getPageSetup().setLeftMargin(58);
        builder.getPageSetup().setRightMargin(58);
        builder.getFont().setName("Times New Roman");
        builder.getFont().setNameFarEast("宋体");
        builder.getFont().setSize(11);
        builder.getFont().setColor(Color.BLACK);

        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.TITLE);
        builder.getFont().setNameFarEast("黑体");
        builder.getFont().setSize(22);
        builder.getFont().setBold(true);
        builder.writeln("项目源码结构评估报告");
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.getFont().setNameFarEast("宋体");
        builder.getFont().setSize(11);
        builder.getFont().setBold(false);
        builder.writeln("本报告统计当前仓库四个核心包的 Java 源文件和测试文件，展示代码分布与测试文件分布。");

        heading(builder, StyleIdentifier.HEADING_1, "统计摘要");
        builder.writeln("{{summary}}");
        heading(builder, StyleIdentifier.HEADING_1, "模块文件统计");
        builder.startTable();
        builder.getCellFormat().getShading().setBackgroundPatternColor(new Color(230, 237, 245));
        builder.getFont().setNameFarEast("黑体");
        for (String header : HEADERS) {
            builder.insertCell();
            builder.write(header);
        }
        builder.endRow();
        builder.getCellFormat().getShading().setBackgroundPatternColor(Color.WHITE);
        builder.getFont().setNameFarEast("宋体");
        builder.insertCell(); builder.write("{{table:modules}}");
        builder.insertCell(); builder.write("待填充");
        builder.insertCell(); builder.write("待填充");
        builder.endRow();
        builder.endTable();
        builder.writeln();

        heading(builder, StyleIdentifier.HEADING_1, "图表分析");
        chartSlot(builder, "主代码文件分布", "source");
        chartSlot(builder, "测试文件分布", "tests");
        chartSlot(builder, "主代码构成", "share");
        heading(builder, StyleIdentifier.HEADING_1, "评估结论");
        builder.write("{{conclusion}}");
        document.save(TEMPLATE.toString());
    }

    /** 按模板原有标题样式写入章节。 */
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

    /** 图表标记独占一个段落，以便分析器精确定位。 */
    private void chartSlot(DocumentBuilder builder, String title, String id) throws Exception {
        heading(builder, StyleIdentifier.HEADING_2, title);
        builder.writeln("{{chart:" + id + "}}");
    }

    /** 将模板分析结果绑定到真实源码统计，而不是预先编造的业务 JSON。 */
    private void configureTemplate(ObjectNode template) {
        String[] refs = {"/modules", "/modules", null, "/modules", "/modules", "/modules", "/modules"};
        String[] instructions = {
                "基于四个模块的真实文件数写一段客观摘要，说明统计范围，不推断质量结论。",
                "只返回四行模块文件统计，数值必须与 facts 完全一致。",
                "",
                "按模块绘制主代码 Java 文件数柱状图，类别和数值与 facts 一致。",
                "按模块绘制测试 Java 文件数横向条形图，类别和数值与 facts 一致。",
                "按模块绘制主代码 Java 文件构成饼图，数值直接使用文件数，不计算百分比。",
                "仅根据文件数对代码分布做简短结论，不推断测试覆盖率或软件质量。"
        };
        ArrayNode sections = template.withArray("sections");
        assertEquals(refs.length, sections.size());
        for (int i = 0; i < sections.size(); i++) {
            if (refs[i] == null) continue;
            ObjectNode section = (ObjectNode) sections.get(i);
            section.put("prompt", instructions[i]);
            section.withArray("dataRefs").removeAll().add(refs[i]);
            if (i >= 3 && i <= 5) {
                section.put("chartStylePreset", "BUSINESS_BLUE");
                section.put("editableRequired", true);
            }
        }
    }

    /** 从四个实际源码包逐项统计主代码和测试 Java 文件。 */
    private ObjectNode repositoryFacts() throws IOException {
        ObjectNode facts = mapper.createObjectNode();
        ArrayNode modules = facts.putArray("modules");
        addModule(modules, "应用编排", "application");
        addModule(modules, "报告导出", "export");
        addModule(modules, "Word 渲染", "office");
        addModule(modules, "协议模型", "protocol");
        return facts;
    }

    /** 读取一个顶级包及其对应测试包的文件数。 */
    private void addModule(ArrayNode modules, String label, String packageName) throws IOException {
        ObjectNode module = modules.addObject();
        module.put("name", label);
        module.put("sourceFiles", javaCount(Path.of("src/main/java/cn/bugstack", packageName)));
        module.put("testFiles", javaCount(Path.of("src/test/java/cn/bugstack", packageName)));
    }

    /** 只统计存在的目录内以 .java 结尾的真实文件。 */
    private long javaCount(Path root) throws IOException {
        if (!Files.isDirectory(root)) return 0;
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).count();
        }
    }

    /**
     * 对所有填充槽位调用真实 Ollama；模型撰写正文并决定排列，数值来自实时统计。
     */
    private final class VerifiedOllamaClient implements StructuredAiClient {
        private final StructuredAiClient ollama;
        private final JsonNode facts;
        private final AtomicInteger calls;

        /** 保存模型客户端及数值校验依据。 */
        private VerifiedOllamaClient(StructuredAiClient ollama, JsonNode facts, AtomicInteger calls) {
            this.ollama = ollama;
            this.facts = facts;
            this.calls = calls;
        }

        /** 请求模型生成单个槽位，并按原始源码统计校验结构化结果。 */
        @Override
        public String generateJson(StructuredAiRequest request) {
            calls.incrementAndGet();
            String id = request.getContext().path("sectionId").asText();
            String ref = "/modules";
            JsonNode result = invokeModel(id, request.getContext().path("facts").path(ref));
            System.out.println("Ollama response for " + id + ": " + result);
            ObjectNode response = mapper.createObjectNode();
            response.put("sectionId", id).put("status", "READY");
            response.putArray("questions");
            ObjectNode block = response.putArray("blocks").addObject();
            block.put("slotId", "slot-001");
            block.putArray("factRefs").add(ref);
            if ("section-001".equals(id) || "section-007".equals(id)) {
                String text = result.path("text").asText();
                verifyNarrative(text);
                block.put("type", "paragraph").put("text", text);
            } else if ("section-002".equals(id)) {
                block.put("type", "table");
                ArrayNode headers = block.putArray("headers");
                for (String header : HEADERS) headers.add(header);
                ArrayNode rows = block.putArray("rows");
                for (JsonNode module : orderedModules(result.path("order"))) {
                    rows.addArray().add(module.path("name").asText())
                            .add(module.path("sourceFiles").asText())
                            .add(module.path("testFiles").asText());
                }
            } else {
                block.put("type", "chart");
                block.put("chartType", chartType(id));
                block.put("title", request.getContext().path("title").asText());
                ArrayNode categories = block.putArray("categories");
                ArrayNode series = block.putArray("series");
                ObjectNode one = series.addObject();
                one.put("name", "Java 文件数");
                ArrayNode values = one.putArray("values");
                String field = "section-005".equals(id) ? "testFiles" : "sourceFiles";
                for (JsonNode module : orderedModules(result.path("order"))) {
                    categories.add(module.path("name").asText());
                    values.add(module.path(field).asLong());
                }
            }
            return response.toString();
        }

        /** 为本地小模型选择简单的单槽位 JSON Schema。 */
        private JsonNode invokeModel(String id, JsonNode moduleFacts) {
            ObjectNode schema = mapper.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = schema.putArray("required");
            String instruction;
            if ("section-001".equals(id) || "section-007".equals(id)) {
                properties.putObject("text").put("type", "string");
                required.add("text");
                instruction = "section-001".equals(id)
                        ? "写一段不超过 80 字的报告摘要。只说明本报告用表格和图表展示"
                        + "应用编排、报告导出、Word 渲染、协议模型的 Java 源文件及测试文件分布。"
                        + "不要写数量、比较、排名、覆盖率或质量判断。"
                        : "写一段不超过 80 字的报告结论。只说明文件数体现代码文件分布，"
                        + "不能单凭文件数判断测试覆盖率或软件质量。不要写数量、比较或排名。";
            } else if ("section-002".equals(id)) {
                orderSchema(properties, required, moduleFacts);
                instruction = "输出 order，包含 modules 中全部四个 name，任意顺序且不得重复。"
                        + "只复制模块名称，不复制或计算数字。";
            } else {
                orderSchema(properties, required, moduleFacts);
                instruction = "输出 order，包含 modules 中全部四个 name，任意顺序且不得重复。"
                        + "图表类型和标题已由导入的 Word 模板固定。";
            }
            schema.put("additionalProperties", false);
            ObjectNode context = mapper.createObjectNode();
            if ("section-001".equals(id) || "section-007".equals(id)) {
                ArrayNode names = context.putArray("moduleNames");
                for (JsonNode module : moduleFacts) names.add(module.path("name").asText());
            } else {
                context.set("modules", moduleFacts.deepCopy());
            }
            StructuredAiRequest smallRequest = new StructuredAiRequest("fill_imported_docx_slot",
                    "你是报告模板填充器。只能依据输入事实输出符合 JSON Schema 的 JSON，不能编造数据。",
                    instruction, context, schema, 1, java.util.Collections.emptyList());
            try {
                return mapper.readTree(ollama.generateJson(smallRequest));
            } catch (Exception failure) {
                throw new IllegalStateException("real Ollama request failed for " + id, failure);
            }
        }

        /** 限定模型只能输出已存在的模块名，不允许它生成统计数值。 */
        private void orderSchema(ObjectNode properties, ArrayNode required, JsonNode moduleFacts) {
            ObjectNode order = properties.putObject("order");
            order.put("type", "array").put("minItems", moduleFacts.size()).put("maxItems", moduleFacts.size());
            ArrayNode names = order.putObject("items").put("type", "string").putArray("enum");
            for (JsonNode module : moduleFacts) names.add(module.path("name").asText());
            required.add("order");
        }

        /** 将模型选择的完整排列映射回实时统计，防止重复或缺失模块。 */
        private List<JsonNode> orderedModules(JsonNode order) {
            JsonNode modules = facts.path("modules");
            if (!order.isArray() || order.size() != modules.size()) {
                throw new IllegalStateException("Ollama module order is incomplete");
            }
            List<JsonNode> arranged = new java.util.ArrayList<>();
            java.util.Set<String> used = new java.util.HashSet<>();
            for (JsonNode item : order) {
                String name = item.asText();
                if (!used.add(name)) throw new IllegalStateException("Ollama repeated module: " + name);
                JsonNode match = null;
                for (JsonNode module : modules) {
                    if (name.equals(module.path("name").asText())) match = module;
                }
                if (match == null) throw new IllegalStateException("Ollama invented module: " + name);
                arranged.add(match);
            }
            return arranged;
        }

        /** 禁止小模型在概括性段落中加入未经校验的数字或排名。 */
        private void verifyNarrative(String text) {
            if (text.trim().isEmpty()) throw new IllegalStateException("Ollama returned empty paragraph");
            if (java.util.regex.Pattern.compile("[0-9]|最多|最少|最高|最低|较多|较少|第一|最后")
                    .matcher(text).find()) {
                throw new IllegalStateException("Ollama narrative contains unsupported quantitative claim");
            }
        }

        /** 模板确定的图表类型，模型负责生成图表内容。 */
        private String chartType(String id) {
            switch (id) {
                case "section-004": return "COLUMN";
                case "section-005": return "BAR";
                case "section-006": return "PIE";
                default: throw new IllegalArgumentException("unexpected chart section: " + id);
            }
        }
    }
}
