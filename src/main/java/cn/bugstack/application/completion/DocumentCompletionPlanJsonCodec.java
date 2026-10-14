package cn.bugstack.application.completion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 可供人工审核与跨请求传递的文档补全计划 JSON 编解码器。 */
public final class DocumentCompletionPlanJsonCodec {

    private static final int MAX_PLAN_CHARACTERS = 300_000;
    private final ObjectMapper mapper = new ObjectMapper();
    private final TableBindingInferer bindingInferer = new TableBindingInferer();
    private final DocumentCompletionPlanValidator validator = new DocumentCompletionPlanValidator();

    /** 将本地计划转换为可展示的 JSON，并附上规划时源文件摘要。 */
    public ObjectNode write(DocumentCompletionPlan plan) {
        if (plan == null) throw new IllegalArgumentException("completion plan is required");
        ObjectNode root = mapper.createObjectNode();
        root.put("status", plan.isReady() ? "READY" : "NEEDS_INPUT");
        root.put("sourceSha256", plan.getSourceSha256());
        root.put("confidence", plan.getConfidence());
        root.put("reason", plan.getReason());
        root.set("questions", mapper.valueToTree(plan.getQuestions()));
        ArrayNode paragraphs = root.putArray("paragraphPatches");
        for (ParagraphPatchPlan patch : plan.getParagraphs()) {
            ObjectNode item = paragraphs.addObject();
            item.put("paragraphId", patch.getParagraphId());
            item.put("operation", patch.getOperation().name());
            item.put("overwritePolicy", patch.getOverwritePolicy().name());
            item.set("texts", mapper.valueToTree(patch.getTexts()));
        }
        ArrayNode tables = root.putArray("tablePatches");
        for (TableCompletionPlanningResult table : plan.getTables()) {
            ObjectNode item = tables.addObject();
            item.put("tableId", table.getPatchPlan().getTableId());
            item.put("overwritePolicy", table.getPatchPlan().getOverwritePolicy().name());
            item.set("keyColumnIds", mapper.valueToTree(table.getBinding().getRowKeyColumnIds()));
            item.set("rows", mapper.valueToTree(table.getPatchPlan().getRows()));
        }
        ArrayNode cells = root.putArray("cellPatches");
        for (CellPatchPlan patch : plan.getCells()) {
            ObjectNode item = cells.addObject();
            item.put("tableId", patch.getTableId());
            item.put("rowIndex", patch.getRowIndex());
            item.put("columnIndex", patch.getColumnIndex());
            item.put("overwritePolicy", patch.getOverwritePolicy().name());
            item.put("text", patch.getText());
        }
        ArrayNode runs = root.putArray("runPatches");
        for (RunPatchPlan patch : plan.getRuns()) {
            ObjectNode item = runs.addObject();
            item.put("paragraphId", patch.getParagraphId());
            item.put("runIndex", patch.getRunIndex());
            item.put("expectedText", patch.getExpectedText());
            item.put("text", patch.getText());
            item.put("overwritePolicy", patch.getOverwritePolicy().name());
        }
        return root;
    }

    /** 解析模型输出；源文件摘要由可信的结构快照提供。 */
    public DocumentCompletionPlan readModel(JsonNode root, DocumentStructureSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("source snapshot is required");
        return read(root, snapshot);
    }

    /** 解析经过审核的外部计划，并重新校验其源文件摘要及每个操作。 */
    public DocumentCompletionPlan readReviewed(JsonNode root, DocumentStructureSnapshot snapshot) {
        if (snapshot == null || root == null || !root.isObject()
                || !snapshot.getSha256().equals(root.path("sourceSha256").asText())) {
            throw new IllegalArgumentException("reviewed plan source SHA-256 does not match the current DOCX");
        }
        DocumentCompletionPlan plan = read(root, snapshot);
        List<String> errors = validator.validate(snapshot, plan);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("reviewed completion plan is invalid: "
                    + String.join("; ", errors));
        }
        return plan;
    }

    private DocumentCompletionPlan read(JsonNode root, DocumentStructureSnapshot snapshot) {
        if (root == null || !root.isObject() || root.toString().length() > MAX_PLAN_CHARACTERS) {
            throw new IllegalArgumentException("completion plan must be a bounded JSON object");
        }
        String status = required(root, "status");
        if (!"READY".equals(status) && !"NEEDS_INPUT".equals(status)) {
            throw new IllegalArgumentException("status must be READY or NEEDS_INPUT");
        }
        double confidence = root.path("confidence").asDouble(-1D);
        if (!Double.isFinite(confidence) || confidence < 0D || confidence > 1D) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        String reason = required(root, "reason");
        List<String> questions = strings(root.path("questions"), "questions");
        if ("NEEDS_INPUT".equals(status)) {
            if (questions.isEmpty()) throw new IllegalArgumentException("NEEDS_INPUT requires a question");
            return new DocumentCompletionPlan(snapshot.getSha256(), Collections.emptyList(),
                    Collections.emptyList(), confidence, reason, questions);
        }
        List<ParagraphPatchPlan> paragraphs = new ArrayList<>();
        JsonNode paragraphNodes = root.path("paragraphPatches");
        if (!paragraphNodes.isArray()) throw new IllegalArgumentException("paragraphPatches must be an array");
        for (JsonNode item : paragraphNodes) {
            paragraphs.add(new ParagraphPatchPlan(required(item, "paragraphId"),
                    ParagraphPatchOperation.valueOf(required(item, "operation")),
                    TableOverwritePolicy.valueOf(required(item, "overwritePolicy")),
                    strings(item.path("texts"), "texts")));
        }
        List<TableCompletionPlanningResult> tables = new ArrayList<>();
        JsonNode tableNodes = root.path("tablePatches");
        if (!tableNodes.isArray()) throw new IllegalArgumentException("tablePatches must be an array");
        for (JsonNode item : tableNodes) {
            String tableId = required(item, "tableId");
            SourceTableSnapshot table = findTable(snapshot, tableId);
            TableBindingSpec binding = bindingInferer.infer(table, snapshot.getSha256())
                    .withRowKeyColumnIds(strings(item.path("keyColumnIds"), "keyColumnIds"));
            TablePatchPlan patch = new TablePatchPlan(tableId,
                    TableOverwritePolicy.valueOf(required(item, "overwritePolicy")),
                    rows(item.path("rows")));
            tables.add(new TableCompletionPlanningResult(TableCompletionStatus.READY,
                    binding, patch, confidence, reason, Collections.emptyList(), 1));
        }
        List<CellPatchPlan> cells = new ArrayList<>();
        JsonNode cellNodes = root.path("cellPatches");
        if (!cellNodes.isMissingNode() && !cellNodes.isArray()) {
            throw new IllegalArgumentException("cellPatches must be an array");
        }
        if (cellNodes.isArray()) {
            for (JsonNode item : cellNodes) {
                cells.add(new CellPatchPlan(required(item, "tableId"),
                        requiredIndex(item, "rowIndex"), requiredIndex(item, "columnIndex"),
                        TableOverwritePolicy.valueOf(required(item, "overwritePolicy")),
                        required(item, "text")));
            }
        }
        List<RunPatchPlan> runs = new ArrayList<>();
        JsonNode runNodes = root.path("runPatches");
        if (!runNodes.isMissingNode() && !runNodes.isArray()) {
            throw new IllegalArgumentException("runPatches must be an array");
        }
        if (runNodes.isArray()) {
            for (JsonNode item : runNodes) {
                runs.add(new RunPatchPlan(required(item, "paragraphId"),
                        requiredIndex(item, "runIndex"), textValue(item, "expectedText"),
                        required(item, "text"),
                        TableOverwritePolicy.valueOf(required(item, "overwritePolicy"))));
            }
        }
        return new DocumentCompletionPlan(snapshot.getSha256(), paragraphs, tables, cells, runs,
                confidence, reason, questions);
    }

    private String textValue(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isTextual()) throw new IllegalArgumentException(name + " must be a string");
        return value.asText();
    }

    private int requiredIndex(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return value.intValue();
    }

    private SourceTableSnapshot findTable(DocumentStructureSnapshot snapshot, String id) {
        for (SourceTableSnapshot table : snapshot.getTables()) {
            if (table.getTableId().equals(id)) return table;
        }
        throw new IllegalArgumentException("unknown tableId: " + id);
    }

    private List<Map<String, String>> rows(JsonNode value) {
        if (!value.isArray()) throw new IllegalArgumentException("rows must be an array");
        List<Map<String, String>> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isObject()) throw new IllegalArgumentException("table row must be an object");
            Map<String, String> row = new LinkedHashMap<>();
            item.fields().forEachRemaining(field -> {
                if (!field.getValue().isTextual()) {
                    throw new IllegalArgumentException("table cell values must be strings");
                }
                row.put(field.getKey(), field.getValue().asText());
            });
            result.add(row);
        }
        return result;
    }

    private String required(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isTextual() || value.asText().trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.asText().trim();
    }

    private List<String> strings(JsonNode node, String name) {
        if (!node.isArray()) throw new IllegalArgumentException(name + " must be an array");
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) throw new IllegalArgumentException(name + " must contain strings");
            result.add(value.asText());
        }
        return result;
    }
}
