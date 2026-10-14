package cn.bugstack.application.completion;

import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.Document;
import com.aspose.words.Paragraph;
import com.aspose.words.Run;
import com.aspose.words.Table;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 将复杂待补全样本与已确认业务事实组合，展示文档级审核补丁的完整写回效果。 */
class ComplexSourceDocxCompletedExampleTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void completesAllKnownFactsAndPreservesSourceStructure() throws Exception {
        Path source = Path.of("target/completion-complex-sample/complex-source.docx").toAbsolutePath();
        Assumptions.assumeTrue(Files.exists(source),
                "first generate the sample with scripts/generate-complex-source-docx.py");
        Path output = source.resolveSibling("complex-completed.docx");
        JsonNode facts = mapper.readTree(Path.of("docs/samples/complex-source-docx-facts.json").toFile());
        SourceDocxCompletionApplication application = new SourceDocxCompletionApplication(request -> {
            throw new AssertionError("This reviewed-plan example must not call an LLM");
        });
        DocumentStructureSnapshot snapshot = application.analyze(source);
        ObjectNode plan = createReviewedPlan(snapshot, facts);
        Files.writeString(source.resolveSibling("complex-reviewed-plan.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(plan));

        DocumentCompletionResult result = application.applyReviewedPlan(source, output, plan);
        assertEquals(12, result.getParagraphsChanged());
        assertEquals(3, result.getRowsUpdated());
        assertEquals(1, result.getRowsAppended());
        assertEquals(30, result.getCellsChanged());
        assertEquals(2, result.getRunsChanged());

        AsposeWordsLicenseLoader.applyConfiguredLicense();
        Document completed = new Document(output.toString());
        assertEquals(6, completed.getFirstSection().getBody().getTables().getCount());
        Table risk = completed.getFirstSection().getBody().getTables().get(4);
        assertEquals(1, risk.getRows().get(0).getCells().getCount(),
                "the risk table's merged title row must stay merged");
        Table apis = completed.getFirstSection().getBody().getTables().get(2);
        assertEquals(5, apis.getRows().getCount(), "the GET /orders API row must be appended");
        assertEquals("/orders", clean(apis.getRows().get(4).getCells().get(0).getText()));
        assertEquals("GET", clean(apis.getRows().get(4).getCells().get(1).getText()));
        String text = completed.getText();
        assertFalse(text.contains("待补充"));
        assertFalse(text.contains("待填写"));
        assertTrue(text.contains("星河订单服务概要设计文档"));
        assertTrue(text.contains(facts.path("scope").path("goal").asText()));
        assertTrue(text.contains(facts.path("reviewConclusion").asText()));
        for (Paragraph paragraph : completed.getFirstSection().getBody().getParagraphs()) {
            if (paragraph.getText().contains("评审结论：")) {
                assertEquals(2, paragraph.getRuns().getCount());
                Run value = paragraph.getRuns().get(1);
                assertEquals("有条件通过", value.getText());
                assertTrue(value.getFont().getBold());
                assertEquals(new Color(192, 0, 0), value.getFont().getColor());
            }
        }
        System.out.println("COMPLEX_COMPLETION_OUTPUT " + output + " filled=47 appendedApiRows=1");
    }

    private ObjectNode createReviewedPlan(DocumentStructureSnapshot snapshot, JsonNode facts) {
        ObjectNode plan = mapper.createObjectNode();
        plan.put("status", "READY");
        plan.put("sourceSha256", snapshot.getSha256());
        plan.put("confidence", 1.0);
        plan.put("reason", "已确认业务事实逐项映射到源文档，并经人工规则审核");
        plan.putArray("questions");
        ArrayNode paragraphs = plan.putArray("paragraphPatches");
        ArrayNode tables = plan.putArray("tablePatches");
        ArrayNode cells = plan.putArray("cellPatches");
        ArrayNode runs = plan.putArray("runPatches");

        Map<String, String> paragraphFacts = new HashMap<>();
        paragraphFacts.put("建设目标：", facts.path("scope").path("goal").asText());
        paragraphFacts.put("不包含范围：", facts.path("scope").path("excluded").asText());
        paragraphFacts.put("部署拓扑：", facts.path("architecture").path("topology").asText());
        paragraphFacts.put("故障隔离策略：", facts.path("architecture").path("isolation").asText());
        paragraphFacts.put("请求幂等策略：", facts.path("apis").path("idempotency").asText());
        paragraphFacts.put("订单状态流转：", facts.path("dataModel").path("statusFlow").asText());
        paragraphFacts.put("发布步骤：", facts.path("rollout").path("steps").asText());
        paragraphFacts.put("回滚触发条件：", facts.path("rollout").path("rollbackTrigger").asText());
        paragraphFacts.put("回滚操作：", facts.path("rollout").path("rollbackAction").asText());
        paragraphFacts.put("实施日期：", facts.path("rollout").path("implementationDate").asText());
        for (SourceParagraphSnapshot paragraph : snapshot.getParagraphs()) {
            String original = paragraph.getText();
            if (original.equals("星河订单服务概要设计待补全文档")) {
                paragraph(paragraphs, paragraph, "星河订单服务概要设计文档");
            } else if (original.startsWith("本文档记录订单服务的范围、接口、数据和运行要求")) {
                paragraph(paragraphs, paragraph,
                        "本文档确定订单服务的范围、接口、数据和运行要求，供研发、测试及运维人员实施与验收。");
            } else if (original.endsWith("待补充")) {
                String label = original.substring(0, original.length() - "待补充".length());
                String value = paragraphFacts.get(label);
                if (value == null) throw new IllegalStateException("unmapped paragraph: " + original);
                paragraph(paragraphs, paragraph, label + value);
            } else if (original.endsWith("待填写")) {
                String value = original.startsWith("计划上线窗口：")
                        ? facts.path("scope").path("releaseWindow").asText()
                        : facts.path("reviewConclusion").asText();
                ObjectNode patch = runs.addObject();
                patch.put("paragraphId", paragraph.getParagraphId());
                patch.put("runIndex", 1);
                patch.put("expectedText", "待填写");
                patch.put("text", value);
                patch.put("overwritePolicy", "OVERWRITE_ALL");
            }
        }

        List<SourceTableSnapshot> sourceTables = snapshot.getTables();
        SourceTableSnapshot metadata = sourceTables.get(0);
        cell(cells, metadata, 2, 1, facts.path("document").path("owner").asText());
        cell(cells, metadata, 3, 1, facts.path("document").path("reviewer").asText());
        cell(cells, metadata, 4, 1, facts.path("document").path("implementationDate").asText());

        SourceTableSnapshot components = sourceTables.get(1);
        for (int row = 1; row <= 4; row++) {
            String name = components.getRows().get(row - 1).get(0);
            JsonNode item = find(facts.path("architecture").path("components"), "name", name);
            for (int column = 1; column <= 3; column++) {
                if (components.getRows().get(row - 1).get(column).isEmpty()) {
                    String field = List.of("", "responsibility", "deployment", "owner").get(column);
                    cell(cells, components, row, column, item.path(field).asText());
                }
            }
        }

        SourceTableSnapshot apiTable = sourceTables.get(2);
        ObjectNode apiPatch = tables.addObject();
        apiPatch.put("tableId", apiTable.getTableId());
        apiPatch.put("overwritePolicy", "BLANKS_ONLY");
        apiPatch.putArray("keyColumnIds").add("column_0").add("column_1");
        ArrayNode apiRows = apiPatch.putArray("rows");
        for (JsonNode item : facts.path("apis").path("rows")) {
            ObjectNode row = apiRows.addObject();
            row.put("column_0", item.path("path").asText());
            row.put("column_1", item.path("method").asText());
            row.put("column_2", item.path("purpose").asText());
            row.put("column_3", item.path("timeoutMs").asText());
        }

        SourceTableSnapshot fields = sourceTables.get(3);
        for (int row = 1; row <= fields.getRows().size(); row++) {
            List<String> values = fields.getRows().get(row - 1);
            if (values.get(3).isEmpty()) {
                cell(cells, fields, row, 3,
                        facts.path("dataModel").path("fields").path(values.get(0)).asText());
            }
        }

        SourceTableSnapshot risk = sourceTables.get(4);
        for (int row = 2; row <= 4; row++) {
            String name = risk.getRows().get(row - 1).get(0);
            JsonNode item = find(facts.path("risks"), "name", name);
            cell(cells, risk, row, 1, item.path("impact").asText());
            cell(cells, risk, row, 2, item.path("mitigation").asText());
        }

        SourceTableSnapshot quality = sourceTables.get(5);
        for (int row = 1; row <= 4; row++) {
            String metric = quality.getRows().get(row - 1).get(0);
            JsonNode item = find(facts.path("quality"), "metric", metric);
            cell(cells, quality, row, 1, item.path("target").asText());
            cell(cells, quality, row, 2, item.path("verification").asText());
        }
        assertEquals(12, paragraphs.size());
        assertEquals(1, tables.size());
        assertEquals(30, cells.size());
        assertEquals(2, runs.size());
        return plan;
    }

    private void paragraph(ArrayNode patches, SourceParagraphSnapshot source, String text) {
        ObjectNode patch = patches.addObject();
        patch.put("paragraphId", source.getParagraphId());
        patch.put("operation", "REPLACE_TEXT");
        patch.put("overwritePolicy", "OVERWRITE_ALL");
        patch.putArray("texts").add(text);
    }

    private void cell(ArrayNode patches, SourceTableSnapshot table, int row, int column, String value) {
        if (value.isBlank()) throw new IllegalStateException("missing fact for " + table.getCaption());
        ObjectNode patch = patches.addObject();
        patch.put("tableId", table.getTableId());
        patch.put("rowIndex", row);
        patch.put("columnIndex", column);
        patch.put("overwritePolicy", "BLANKS_ONLY");
        patch.put("text", value);
    }

    private JsonNode find(JsonNode array, String key, String value) {
        for (JsonNode item : array) if (value.equals(item.path(key).asText())) return item;
        throw new IllegalStateException("missing source fact: " + key + "=" + value);
    }

    private String clean(String text) {
        return text.replace("\u0007", "").trim();
    }
}
