package cn.bugstack.application.template;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.block.AbstractListBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import com.aspose.words.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTemplateApplicationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsVersionedBusinessDataToDocumentSpec() throws Exception {
        DocumentTemplateApplication application = application();

        DocumentSpec document = application.renderSpec("system.assessment", "1.0.0", data());

        assertEquals("无人系统评估报告", document.getMetadata().getTitle());
        assertEquals(4, document.getSections().size());
        AbstractListBlockSpec capabilities = (AbstractListBlockSpec) document.getSections().get(1).getBlocks().get(0);
        assertEquals(3, capabilities.getItems().size());
        TableBlockSpec risks = (TableBlockSpec) document.getSections().get(2).getBlocks().get(0);
        assertEquals(2, risks.getRows().size());
        assertEquals("高", risks.getRows().get(0).get(0));
        assertEquals(1, document.getSections().get(3).getBlocks().size());
    }

    @Test
    void validatesDataBeforeMapping() throws Exception {
        DocumentTemplateApplication application = application();
        JsonNode invalid = data();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("risks").get(0)).put("level", "严重");

        DocumentTemplateValidationException exception = assertThrows(DocumentTemplateValidationException.class,
                () -> application.renderSpec("system.assessment", "1.0.0", invalid));

        assertFalse(exception.getViolations().isEmpty());
    }

    @Test
    void exportsTemplateToDocxAndPdfWithoutBusinessReportFallback() throws Exception {
        DocumentTemplateApplication application = application();

        byte[] docx = application.exportToBytes(
                "system.assessment", "1.0.0", data(), ReportOutputFormat.DOCX);
        byte[] pdf = application.exportToBytes(
                "system.assessment", "1.0.0", data(), ReportOutputFormat.PDF);
        Document word = new Document(new ByteArrayInputStream(docx));

        assertTrue(word.getText().contains("无人系统评估报告"));
        assertTrue(word.getText().contains("态势感知"));
        assertTrue(word.getText().contains("复杂电磁环境"));
        assertTrue(word.getText().contains("联系人：张三"));
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    @Test
    void omitsConditionalBlockWhenFlagIsFalse() throws Exception {
        DocumentTemplateApplication application = application();
        com.fasterxml.jackson.databind.node.ObjectNode data = (com.fasterxml.jackson.databind.node.ObjectNode) data();
        data.put("includeAppendix", false);
        data.remove("contact");

        DocumentSpec document = application.renderSpec("system.assessment", "1.0.0", data);

        assertTrue(document.getSections().get(3).getBlocks().isEmpty());
    }

    private DocumentTemplateApplication application() throws Exception {
        DocumentTemplateApplication application = new DocumentTemplateApplication();
        try (InputStream input = resource("/document-template/1.0/example-assessment-template.json")) {
            application.register(input);
        }
        return application;
    }

    private JsonNode data() throws Exception {
        try (InputStream input = resource("/document-template/1.0/example-assessment-data.json")) {
            return mapper.readTree(input);
        }
    }

    private InputStream resource(String path) {
        InputStream input = getClass().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("missing resource: " + path);
        }
        return input;
    }
}
