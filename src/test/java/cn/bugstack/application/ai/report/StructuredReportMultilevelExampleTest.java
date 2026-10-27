package cn.bugstack.application.ai.report;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证三级标题示例允许模型按提示词选择段落或表格。 */
class StructuredReportMultilevelExampleTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 使用相同模板验证多条记录和单条记录两种正文类型。 */
    @Test
    void choosesParagraphOrTableWithinThirdLevelHeading() throws Exception {
        JsonNode template = mapper.readTree(Files.readString(Path.of(
                "docs/examples/structured-report-multilevel-template.json")));
        JsonNode multiple = mapper.readTree(Files.readString(Path.of(
                "docs/examples/structured-report-multilevel-business-data.json")));

        StructuredAiClient model = request -> {
            String sectionId = request.getContext().path("sectionId").asText();
            ObjectNode response = mapper.createObjectNode();
            response.put("sectionId", sectionId);
            response.put("status", "READY");
            response.putArray("questions");
            ObjectNode block = response.putArray("blocks").addObject();
            String factRef = request.getContext().path("facts").fieldNames().next();
            block.putArray("factRefs").add(factRef);
            if ("function-evidence".equals(sectionId)) {
                assertTrue(request.getUserInstruction().contains("只有 1 条记录时"));
                assertEquals(2, request.getOutputSchema().path("properties").path("blocks")
                        .path("items").path("oneOf").size());
                JsonNode checks = request.getContext().path("facts").path("/checks");
                if (checks.size() == 1) {
                    block.put("type", "paragraph");
                    JsonNode check = checks.get(0);
                    block.put("text", check.path("name").asText() + "：" + check.path("result").asText()
                            + "；依据：" + check.path("evidence").asText());
                } else {
                    block.put("type", "table");
                    block.putArray("headers").add("检查项").add("结果").add("依据");
                    ArrayNode rows = block.putArray("rows");
                    for (JsonNode check : checks) {
                        rows.addArray().add(check.path("name").asText())
                                .add(check.path("result").asText())
                                .add(check.path("evidence").asText());
                    }
                }
            } else {
                block.put("type", "paragraph");
                block.put("text", "已依据本章节业务数据形成说明。");
            }
            return response.toString();
        };

        StructuredReportAiApplication app = new StructuredReportAiApplication(model);
        DocumentSpec tableReport = app.generate(template, multiple);
        assertEquals(3, tableReport.getSections().size());
        assertEquals("质量评估", tableReport.getSections().get(1).getTitle());
        SubsectionBlockSpec functional = assertInstanceOf(SubsectionBlockSpec.class,
                tableReport.getSections().get(1).getBlocks().get(0));
        assertEquals("功能质量", functional.getTitle());
        SubsectionBlockSpec evidence = assertInstanceOf(SubsectionBlockSpec.class, functional.getBlocks().get(0));
        assertEquals("功能验证结果", evidence.getTitle());
        TableBlockSpec table = assertInstanceOf(TableBlockSpec.class, evidence.getBlocks().get(0));
        assertEquals(3, table.getRows().size());

        JsonNode single = multiple.deepCopy();
        ArrayNode checks = (ArrayNode) single.path("checks");
        checks.remove(2);
        checks.remove(1);
        DocumentSpec paragraphReport = app.generate(template, single);
        SubsectionBlockSpec paragraphFunctional = (SubsectionBlockSpec)
                paragraphReport.getSections().get(1).getBlocks().get(0);
        SubsectionBlockSpec paragraphEvidence = (SubsectionBlockSpec) paragraphFunctional.getBlocks().get(0);
        assertInstanceOf(ParagraphBlockSpec.class, paragraphEvidence.getBlocks().get(0));
        assertFalse(paragraphEvidence.getBlocks().get(0) instanceof TableBlockSpec);
    }
}
