package cn.bugstack.protocol.template;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTemplateSpecJsonCodecTest {

    @Test
    void readsAndWritesPublishedTemplate() throws Exception {
        DocumentTemplateSpecJsonCodec codec = new DocumentTemplateSpecJsonCodec();
        DocumentTemplateSpec template;
        try (InputStream input = resource()) {
            template = codec.read(input);
        }

        assertEquals("system.assessment", template.getTemplateId());
        assertEquals("1.0.0", template.getVersion());
        assertTrue(template.getDataSchema().isObject());
        assertEquals("system.assessment", codec.read(codec.write(template)).getTemplateId());
    }

    @Test
    void rejectsUnknownDefinitionProperty() {
        String json = "{\"schemaVersion\":\"1.0\",\"templateId\":\"x\",\"version\":\"1.0.0\","
                + "\"name\":\"X\",\"dataSchema\":{},\"documentTemplate\":{},\"unknown\":true}";
        assertThrows(IllegalArgumentException.class, () -> new DocumentTemplateSpecJsonCodec().read(json));
    }

    private InputStream resource() {
        InputStream input = getClass().getResourceAsStream(
                "/document-template/1.0/example-assessment-template.json");
        if (input == null) {
            throw new IllegalStateException("missing document template example");
        }
        return input;
    }
}
