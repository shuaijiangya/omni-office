package cn.bugstack.application.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentTemplateExpanderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DocumentTemplateExpander expander = new DocumentTemplateExpander();

    @Test
    void expandsScalarLoopAndConditionalWithoutExecutingExpressions() throws Exception {
        JsonNode template = mapper.readTree("{\"title\":\"{{project}}报告\",\"enabled\":\"{{enabled}}\","
                + "\"rows\":[{\"$each\":\"items\",\"$as\":\"entry\","
                + "\"$template\":[\"{{entry.name}}\",\"{{entry.score}}\"]}],"
                + "\"optional\":[{\"$if\":\"include\",\"$template\":\"{{note}}\"}]}" );
        JsonNode data = mapper.readTree("{\"project\":\"系统\",\"enabled\":true,"
                + "\"items\":[{\"name\":\"A\",\"score\":90},{\"name\":\"B\",\"score\":80}],"
                + "\"include\":false}");

        JsonNode result = expander.expand(template, data);

        assertEquals("系统报告", result.path("title").asText());
        assertEquals(true, result.path("enabled").asBoolean());
        assertEquals(2, result.path("rows").size());
        assertEquals(90, result.path("rows").get(0).get(1).asInt());
        assertFalse(result.path("optional").elements().hasNext());
    }

    @Test
    void rejectsMissingValuesAndUnknownDirectiveProperties() throws Exception {
        JsonNode missing = mapper.readTree("{\"value\":\"{{missing}}\"}");
        JsonNode unknownDirective = mapper.readTree(
                "[{\"$each\":\"items\",\"$template\":\"{{item}}\",\"script\":\"x\"}]");
        JsonNode data = mapper.readTree("{\"items\":[\"A\"]}");

        assertThrows(DocumentTemplateMappingException.class, () -> expander.expand(missing, data));
        assertThrows(DocumentTemplateMappingException.class, () -> expander.expand(unknownDirective, data));
    }
}
