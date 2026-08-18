package cn.bugstack.application.template;

import cn.bugstack.protocol.template.DocumentTemplateSpec;
import cn.bugstack.protocol.template.DocumentTemplateSpecJsonCodec;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryDocumentTemplateCatalogTest {

    @Test
    void requiresExplicitVersionRejectsDuplicatesAndProtectsSnapshot() throws Exception {
        InMemoryDocumentTemplateCatalog catalog = new InMemoryDocumentTemplateCatalog();
        DocumentTemplateSpec template = template();
        catalog.register(template);
        template.setName("调用方修改");

        assertEquals("系统评估数据模板", catalog.require("system.assessment", "1.0.0").getName());
        assertEquals(1, catalog.list().size());
        assertThrows(IllegalStateException.class, () -> catalog.register(catalog.require(
                "system.assessment", "1.0.0")));
        assertThrows(IllegalArgumentException.class, () -> catalog.require("system.assessment", null));
        assertThrows(IllegalArgumentException.class, () -> catalog.require("system.assessment", "2.0.0"));
    }

    @Test
    void rejectsRemoteSchemaReferencesAtRegistration() throws Exception {
        DocumentTemplateSpec template = template();
        ((ObjectNode) template.getDataSchema()).put("$ref", "https://example.com/external-schema.json");

        assertThrows(DocumentTemplateValidationException.class,
                () -> new InMemoryDocumentTemplateCatalog().register(template));
    }

    private DocumentTemplateSpec template() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/document-template/1.0/example-assessment-template.json")) {
            if (input == null) {
                throw new IllegalStateException("missing document template example");
            }
            return new DocumentTemplateSpecJsonCodec().read(input);
        }
    }
}
