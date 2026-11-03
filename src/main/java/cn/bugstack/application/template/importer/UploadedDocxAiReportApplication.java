package cn.bugstack.application.template.importer;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.StructuredAiRequest;
import cn.bugstack.application.ai.report.StructuredReportAiApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上传 DOCX 模板后自动识别占位符，逐槽位调用 AI，并将结果写回源版式。
 *
 * <p>标题、表头、槽位类型和样式由模板决定；正文、表格数据行、图表类型及图表
 * 数据全部来自模型响应。应用只负责结构转换、事实数值校验与 Word 组装，不合成
 * 业务数据。模板中没有明确占位符的内容保持不变。</p>
 */
public final class UploadedDocxAiReportApplication {

    private static final int MAX_TEMPLATE_BYTES = 25 * 1024 * 1024;
    private static final Pattern NUMBER = Pattern.compile("(?<![0-9])(?:[0-9]+(?:\\.[0-9]+)?)(?![0-9])");
    private final StructuredAiClient model;
    private final ObjectMapper mapper = new ObjectMapper();

    /** @param model 真实模型客户端，例如 {@code OllamaStructuredAiClient} */
    public UploadedDocxAiReportApplication(StructuredAiClient model) {
        if (model == null) throw new IllegalArgumentException("AI client is required");
        this.model = model;
    }

    /**
     * 接收 HTTP 上传得到的 DOCX 字节，并生成一个新文件。
     *
     * @param uploadedTemplate 上传的 DOCX 字节
     * @param templateId 本次模板标识
     * @param businessText 供模型提取和组织的原始业务文字
     * @param output 不存在的输出 DOCX 路径
     */
    public void generate(byte[] uploadedTemplate, String templateId, String businessText, Path output) {
        if (uploadedTemplate == null || uploadedTemplate.length == 0
                || uploadedTemplate.length > MAX_TEMPLATE_BYTES) {
            throw new IllegalArgumentException("uploaded DOCX must be 1 byte to 25 MiB");
        }
        Path temporary;
        try {
            temporary = Files.createTempFile("uploaded-report-template-", ".docx");
            Files.write(temporary, uploadedTemplate);
        } catch (IOException e) {
            throw new IllegalStateException("failed to stage uploaded DOCX", e);
        }
        try {
            generate(temporary, templateId, businessText, output);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException e) {
                throw new IllegalStateException("failed to remove staged uploaded DOCX", e);
            }
        }
    }

    /**
     * 从已有的 DOCX 路径执行同一流程；上传入口可直接委托此方法。
     *
     * @param source 已上传并保存的源模板
     * @param templateId 本次模板标识
     * @param businessText 原始业务文字
     * @param output 不存在的输出 DOCX 路径
     */
    public void generate(Path source, String templateId, String businessText, Path output) {
        if (source == null || output == null || businessText == null || businessText.trim().isEmpty()) {
            throw new IllegalArgumentException("source, business text and output are required");
        }
        if (businessText.length() > 50_000) {
            throw new IllegalArgumentException("business text exceeds 50000 characters");
        }
        DocxTemplateAnalysis analysis = new DocxTemplateAnalyzer().analyze(source, templateId);
        rejectUnreviewableTemplate(analysis.getStructure());
        ObjectNode reportTemplate = analysis.getReportTemplate();
        Map<String, Map<String, List<String>>> tableHeaders = tableHeaders(analysis);
        for (JsonNode entry : reportTemplate.withArray("sections")) {
            ObjectNode section = (ObjectNode) entry;
            if (!"AI_WRITE".equals(section.path("mode").asText())) continue;
            section.put("prompt", "按照源 DOCX 的标题和槽位含义，从 /content 提取所需内容。"
                    + "表格数据行和图表数据全部由模型返回；不得编造业务事实。");
            section.withArray("dataRefs").removeAll().add("/content");
            section.put("maxChars", 20_000);
            if (hasSlotType(section, "chart")) {
                section.put("editableRequired", true);
                section.put("chartStylePreset", "BUSINESS_BLUE");
            }
        }
        ObjectNode facts = mapper.createObjectNode().put("content", businessText);
        StructuredAiClient slotClient = new SlotClient(model, businessText, tableHeaders);
        new DocxTemplateReportApplication(new StructuredReportAiApplication(slotClient))
                .generate(source, reportTemplate, analysis.getStyleBindings(), facts, output);
    }

    /** 含歧义样例或缺失标题层级的模板不得被自动生成器静默猜测。 */
    private void rejectUnreviewableTemplate(JsonNode structure) {
        for (JsonNode issue : structure.path("reviewIssues")) {
            String code = issue.path("code").asText();
            if ("AMBIGUOUS_BLOCK_SAMPLE".equals(code) || "HEADING_LEVEL_GAP".equals(code)
                    || "NO_HEADINGS".equals(code)) {
                throw new IllegalArgumentException("uploaded template needs review: " + code);
            }
        }
    }

    /** 判断章节是否包含指定类型的填充槽位。 */
    private boolean hasSlotType(JsonNode section, String type) {
        for (JsonNode slot : section.path("slots")) {
            if (type.equals(slot.path("type").asText())) return true;
        }
        return false;
    }

    /** 从模板中读取固定表头，并关联到每个表格填充槽位。 */
    private Map<String, Map<String, List<String>>> tableHeaders(DocxTemplateAnalysis analysis) {
        Map<String, Map<String, List<String>>> result = new HashMap<>();
        for (JsonNode section : analysis.getStructure().path("sections")) {
            String sectionId = section.path("id").asText();
            Map<String, List<String>> slots = new HashMap<>();
            for (JsonNode placeholder : section.path("placeholders")) {
                if (!placeholder.path("name").asText().startsWith("table:")) continue;
                String anchor = placeholder.path("anchorId").asText();
                int row = anchor.indexOf("-r");
                if (row < 0) throw new IllegalArgumentException("table marker must be inside a prototype row");
                String tableAnchor = anchor.substring(0, row);
                List<String> headers = null;
                for (JsonNode block : section.path("blocks")) {
                    if (!"table".equals(block.path("type").asText())
                            || !tableAnchor.equals(block.path("anchorId").asText())) continue;
                    JsonNode headerRow = block.path("rowLayouts").path(0).path("cells");
                    List<String> found = new ArrayList<>();
                    for (JsonNode cell : headerRow) found.add(cell.path("text").asText());
                    headers = found;
                }
                if (headers == null || headers.isEmpty()) {
                    throw new IllegalArgumentException("table marker has no fixed header row");
                }
                slots.put(placeholder.path("slotId").asText(), headers);
            }
            result.put(sectionId, slots);
        }
        return result;
    }

    /** 逐槽位调用模型并将返回内容包装为结构化报告协议。 */
    private final class SlotClient implements StructuredAiClient {
        private final StructuredAiClient delegate;
        private final String businessText;
        private final Map<String, Map<String, List<String>>> headers;
        private final Set<BigDecimal> sourceNumbers;
        private final List<TableFacts> generatedTables = new ArrayList<>();

        /** 初始化模型与原文证据边界。 */
        private SlotClient(StructuredAiClient delegate, String businessText,
                           Map<String, Map<String, List<String>>> headers) {
            this.delegate = delegate;
            this.businessText = businessText;
            this.headers = headers;
            this.sourceNumbers = numbers(businessText);
        }

        /** 每个槽位要求一个完整模型响应，缺失或非法数据直接终止生成。 */
        @Override
        public String generateJson(StructuredAiRequest request) {
            String sectionId = request.getContext().path("sectionId").asText();
            String heading = request.getContext().path("title").asText();
            JsonNode slots = request.getContext().path("slots");
            if (!slots.isArray() || slots.size() == 0) {
                throw new IllegalArgumentException("uploaded template section has no fill slots");
            }
            ObjectNode response = mapper.createObjectNode();
            response.put("sectionId", sectionId).put("status", "READY");
            response.putArray("questions");
            ArrayNode blocks = response.putArray("blocks");
            for (JsonNode slot : slots) {
                String slotId = slot.path("id").asText();
                String type = slot.path("type").asText();
                List<String> fixedHeaders = headers.getOrDefault(sectionId, Collections.emptyMap())
                        .getOrDefault(slotId, Collections.emptyList());
                JsonNode generated = askModel(heading, slotId, type, fixedHeaders, request);
                ObjectNode block = blocks.addObject();
                block.put("slotId", slotId).put("type", type);
                block.putArray("factRefs").add("/content");
                if ("paragraph".equals(type)) {
                    String text = generated.path("text").asText("").trim();
                    if (text.isEmpty()) throw new IllegalArgumentException("AI paragraph is empty");
                    verifyNumbers(text);
                    block.put("text", text);
                } else if ("table".equals(type)) {
                    copyTable(generated, fixedHeaders, block);
                    generatedTables.add(new TableFacts(fixedHeaders, generated.path("rows")));
                } else if ("chart".equals(type)) {
                    copyChart(generated, block, heading);
                } else {
                    throw new IllegalArgumentException("unsupported template slot type: " + type);
                }
            }
            return response.toString();
        }

        /** 用较小的 JSON Schema 请求模型，失败可带反馈再试，但绝不合成缺失结果。 */
        private JsonNode askModel(String heading, String slotId, String type,
                                  List<String> fixedHeaders, StructuredAiRequest parent) {
            ObjectNode schema = schema(type);
            List<String> feedback = new ArrayList<>(parent.getValidationFeedback());
            String instruction = "填充上传 Word 模板的标题「" + heading + "」下的 " + type
                    + " 槽位 " + slotId + "。";
            if ("table".equals(type)) {
                instruction += "固定表头为 " + fixedHeaders + "。只返回数据行 rows，"
                        + "每行单元格顺序必须与表头一致，所有数据从原文提取，不要返回表头行。";
            } else if ("chart".equals(type)) {
                instruction += "只返回一个指标的图表：chartType、title、categories、seriesName、values。"
                        + "categories 是模块名称数组；values 是标题所指指标在各模块的数值数组，"
                        + "与 categories 一一对应且长度相同。不要把主代码数和测试数放在同一个数组，"
                        + "不要返回嵌套 series。图表类型按原文要求选择，数值不能计算或编造。";
                TableFacts related = relatedTable(heading);
                if (related != null) {
                    String metric = matchingMetric(heading, related.headers);
                    instruction += "之前 AI 返回的表格为 " + related.asJson()
                            + "。图表类别必须覆盖该表全部行，数值必须逐行对应正确指标列。";
                    if (metric != null) instruction += "当前标题对应表格指标列「" + metric + "」。";
                }
                String expectedType = requestedChartType(heading);
                if (expectedType != null) instruction += "原文明确指定图表类型为 " + expectedType + "。";
            } else {
                instruction += "依据原文生成对应的一个 paragraph，不能加入原文没有的数字。";
            }
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    ObjectNode context = mapper.createObjectNode().put("content", businessText);
                    String raw = delegate.generateJson(new StructuredAiRequest("fill_uploaded_docx_slot",
                            "你是 Word 报告模板填充器。只返回 JSON；所有业务内容和数值必须来自 content。",
                            instruction, context, schema, attempt, feedback));
                    JsonNode result = mapper.readTree(raw);
                    if (result == null || !result.isObject()) throw new IllegalArgumentException("model did not return JSON object");
                    if ("paragraph".equals(type)) {
                        String text = result.path("text").asText("").trim();
                        if (text.isEmpty()) throw new IllegalArgumentException("AI paragraph is empty");
                        verifyNumbers(text);
                    } else if ("table".equals(type)) {
                        copyTable(result, fixedHeaders, mapper.createObjectNode());
                    } else {
                        copyChart(result, mapper.createObjectNode(), heading);
                    }
                    return result;
                } catch (Exception failure) {
                    feedback = Collections.singletonList(failure.getMessage() == null
                            ? "invalid model JSON" : failure.getMessage());
                    if (attempt == 3) {
                        throw new IllegalStateException("AI failed to fill " + heading + "/" + slotId, failure);
                    }
                }
            }
            throw new IllegalStateException("AI retry loop ended unexpectedly");
        }

        /** 为单个槽位构造不含外层章节协议的输出 Schema。 */
        private ObjectNode schema(String type) {
            ObjectNode root = mapper.createObjectNode();
            root.put("type", "object");
            ObjectNode properties = root.putObject("properties");
            ArrayNode required = root.putArray("required");
            if ("paragraph".equals(type)) {
                properties.putObject("text").put("type", "string");
                required.add("text");
            } else if ("table".equals(type)) {
                ObjectNode rows = properties.putObject("rows");
                rows.put("type", "array");
                rows.putObject("items").put("type", "array")
                        .putObject("items").put("type", "string");
                required.add("rows");
            } else if ("chart".equals(type)) {
                ObjectNode chartType = properties.putObject("chartType");
                chartType.put("type", "string").putArray("enum")
                        .add("COLUMN").add("BAR").add("PIE").add("LINE").add("RADAR");
                properties.putObject("title").put("type", "string");
                properties.putObject("categories").put("type", "array")
                        .putObject("items").put("type", "string");
                properties.putObject("seriesName").put("type", "string");
                properties.putObject("values").put("type", "array")
                        .putObject("items").put("type", "number");
                required.add("chartType").add("title").add("categories")
                        .add("seriesName").add("values");
            } else {
                throw new IllegalArgumentException("unsupported template slot type: " + type);
            }
            root.put("additionalProperties", false);
            return root;
        }

        /** 表头保持模板原值，表格的所有数据行必须是模型返回的。 */
        private void copyTable(JsonNode generated, List<String> fixedHeaders, ObjectNode block) {
            JsonNode rows = generated.path("rows");
            if (fixedHeaders.isEmpty() || !rows.isArray() || rows.size() == 0) {
                throw new IllegalArgumentException("AI table requires nonempty rows and source headers");
            }
            ArrayNode headersNode = block.putArray("headers");
            for (String header : fixedHeaders) headersNode.add(header);
            ArrayNode outputRows = block.putArray("rows");
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() != fixedHeaders.size()) {
                    throw new IllegalArgumentException("AI table row width differs from source header");
                }
                ArrayNode outputRow = outputRows.addArray();
                for (JsonNode value : row) {
                    if (!value.isTextual() || value.asText().trim().isEmpty()) {
                        throw new IllegalArgumentException("AI table cell must be nonblank text");
                    }
                    verifyNumbers(value.asText());
                    outputRow.add(value.asText());
                }
            }
        }

        /** 图表类型、标题、类别、系列名与数值全部来自模型响应。 */
        private void copyChart(JsonNode generated, ObjectNode block, String heading) {
            String type = generated.path("chartType").asText();
            if (!java.util.Arrays.asList("COLUMN", "BAR", "PIE", "LINE", "RADAR").contains(type)) {
                throw new IllegalArgumentException("AI chart type is unsupported: " + type);
            }
            String expectedType = requestedChartType(heading);
            if (expectedType != null && !expectedType.equals(type)) {
                throw new IllegalArgumentException("AI chart type must be " + expectedType + " for " + heading);
            }
            JsonNode categories = generated.path("categories");
            JsonNode values = generated.path("values");
            String title = generated.path("title").asText("").trim();
            String name = generated.path("seriesName").asText("").trim();
            if (!categories.isArray() || categories.size() == 0
                    || !values.isArray() || values.size() != categories.size()
                    || title.isEmpty() || name.isEmpty()) {
                throw new IllegalArgumentException("AI chart requires a title and one aligned series");
            }
            verifyNumbers(title);
            verifyNumbers(name);
            for (JsonNode category : categories) {
                if (!category.isTextual() || category.asText().trim().isEmpty()) {
                    throw new IllegalArgumentException("AI chart category is blank");
                }
                verifyNumbers(category.asText());
            }
            for (JsonNode value : values) {
                if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
                    throw new IllegalArgumentException("AI chart value is not finite number");
                }
                verifyNumber(value.decimalValue());
            }
            verifyChartAgainstTable(heading, categories, values);
            block.put("chartType", type);
            block.put("title", title);
            block.set("categories", categories.deepCopy());
            ObjectNode oneSeries = block.putArray("series").addObject();
            oneSeries.put("name", name);
            oneSeries.set("values", values.deepCopy());
        }

        /** 根据业务文字中与图表标题紧邻的明确要求识别图表类型。 */
        private String requestedChartType(String heading) {
            Matcher instruction = Pattern.compile(Pattern.quote(heading)
                    + "\\s*使用\\s*(横向条形图|柱状图|饼图|折线图|雷达图)").matcher(businessText);
            if (!instruction.find()) return null;
            switch (instruction.group(1)) {
                case "横向条形图": return "BAR";
                case "柱状图": return "COLUMN";
                case "饼图": return "PIE";
                case "折线图": return "LINE";
                case "雷达图": return "RADAR";
                default: return null;
            }
        }

        /** 图表标题与已生成表头匹配时，选择与标题最相关的事实表。 */
        private TableFacts relatedTable(String heading) {
            TableFacts best = null;
            int bestLength = 0;
            for (TableFacts table : generatedTables) {
                String metric = matchingMetric(heading, table.headers);
                if (metric != null && metric.length() > bestLength) {
                    best = table;
                    bestLength = metric.length();
                }
            }
            return best;
        }

        /** 去除通用后缀后，以表头中的指标词匹配图表标题。 */
        private String matchingMetric(String heading, List<String> headers) {
            String found = null;
            for (int index = 1; index < headers.size(); index++) {
                String header = headers.get(index);
                String core = header.replace("Java", "").replace("文件", "")
                        .replace("数量", "").replace("数", "").replaceAll("\\s+", "");
                if (!core.isEmpty() && heading.contains(core)
                        && (found == null || core.length() > found.length())) found = header;
            }
            return found;
        }

        /** 核对每个 AI 图表点与 AI 表格中的同名行、对应指标列一致。 */
        private void verifyChartAgainstTable(String heading, JsonNode categories, JsonNode values) {
            TableFacts table = relatedTable(heading);
            if (table == null) return;
            String metric = matchingMetric(heading, table.headers);
            int valueColumn = table.headers.indexOf(metric);
            if (valueColumn < 1 || categories.size() != table.rows.size()) {
                throw new IllegalArgumentException("AI chart must cover all related table rows");
            }
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < categories.size(); i++) {
                String category = categories.get(i).asText();
                if (!seen.add(category)) throw new IllegalArgumentException("AI chart repeats category: " + category);
                JsonNode row = table.row(category);
                if (row == null || row.size() <= valueColumn) {
                    throw new IllegalArgumentException("AI chart category is absent from related table: " + category);
                }
                Matcher number = NUMBER.matcher(row.get(valueColumn).asText());
                if (!number.find() || new BigDecimal(number.group()).compareTo(values.get(i).decimalValue()) != 0) {
                    throw new IllegalArgumentException("AI chart value differs from table column " + metric
                            + " for " + category);
                }
            }
        }

        /** 拒绝正文或单元格中原文未出现的阿拉伯数字。 */
        private void verifyNumbers(String value) {
            Matcher matcher = NUMBER.matcher(value);
            while (matcher.find()) verifyNumber(new BigDecimal(matcher.group()));
        }

        /** 比较规范化数字，允许模型以 210.0 表示原文中的 210。 */
        private void verifyNumber(BigDecimal value) {
            if (!sourceNumbers.contains(value.stripTrailingZeros())) {
                throw new IllegalArgumentException("AI introduced a number not present in business text: " + value);
            }
        }

        /** 提取原文中所有可直接引用的数字。 */
        private Set<BigDecimal> numbers(String text) {
            Set<BigDecimal> values = new HashSet<>();
            Matcher matcher = NUMBER.matcher(text);
            while (matcher.find()) values.add(new BigDecimal(matcher.group()).stripTrailingZeros());
            return values;
        }

        /** 模型已经生成的表格，用作后续图表的可追溯校验依据。 */
        private final class TableFacts {
            private final List<String> headers;
            private final JsonNode rows;

            /** 保存模板固定表头和 AI 原样返回的数据行。 */
            private TableFacts(List<String> headers, JsonNode rows) {
                this.headers = new ArrayList<>(headers);
                this.rows = rows.deepCopy();
            }

            /** 返回可加入后续模型上下文的表格事实。 */
            private String asJson() {
                ObjectNode object = mapper.createObjectNode();
                ArrayNode names = object.putArray("headers");
                for (String header : headers) names.add(header);
                object.set("rows", rows);
                return object.toString();
            }

            /** 通过第一列类别名找到对应行。 */
            private JsonNode row(String category) {
                for (JsonNode row : rows) {
                    if (row.isArray() && row.size() > 0 && category.equals(row.get(0).asText())) return row;
                }
                return null;
            }
        }
    }
}
