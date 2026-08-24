package cn.bugstack.protocol.document;

import cn.bugstack.protocol.document.block.BulletListBlockSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentSpecJsonCodecTest {

    private final DocumentSpecJsonCodec codec = new DocumentSpecJsonCodec();

    @Test
    void readsPublishedExamplesAndPreservesPolymorphicBlocks() {
        DocumentSpec simple = codec.read(resource("/document-spec/1.0/example-simple.json"));
        DocumentSpec complete = codec.read(resource("/document-spec/1.0/example-complete.json"));

        assertEquals(DocumentSpecVersion.V1, simple.getSchemaVersion());
        assertEquals("系统评估报告", simple.getMetadata().getTitle());
        assertTrue(simple.getSections().get(0).getBlocks().get(0) instanceof ParagraphBlockSpec);

        assertTrue(complete.getSections().get(0).getBlocks().get(1) instanceof BulletListBlockSpec);
        assertTrue(complete.getSections().get(0).getBlocks().get(3) instanceof TableBlockSpec);
        assertTrue(complete.getSections().get(0).getBlocks().get(4) instanceof SubsectionBlockSpec);
        TableBlockSpec table = (TableBlockSpec) complete.getSections().get(0).getBlocks().get(3);
        assertEquals("CENTER", table.getAlignment());
        assertEquals("ABOVE", table.getCaptionPosition());
        assertEquals("Arial", table.getHeaderTextStyle().getAsciiFontFamily());
        assertEquals("微软雅黑", table.getHeaderTextStyle().getFarEastFontFamily());
        assertEquals("Calibri", table.getBodyTextStyle().getAsciiFontFamily());
        assertEquals("仿宋", table.getBodyTextStyle().getFarEastFontFamily());
        assertEquals(1, table.getMerges().size());
        ParagraphBlockSpec richParagraph = (ParagraphBlockSpec) complete.getSections().get(0).getBlocks().get(0);
        assertEquals(4, richParagraph.getTextRanges().size());
        assertEquals(Boolean.TRUE, richParagraph.getTextRanges().get(0).getStyle().getBold());
        assertEquals(Boolean.TRUE, richParagraph.getTextRanges().get(2).getStyle().getUnderline());

        DocumentSpec roundTrip = codec.read(codec.write(complete));
        assertEquals(complete.getMetadata().getTitle(), roundTrip.getMetadata().getTitle());
        assertEquals(complete.getSections().get(0).getBlocks().size(),
                roundTrip.getSections().get(0).getBlocks().size());
        assertTrue(!codec.write(simple).contains(": null"));
    }

    @Test
    void rejectsUnknownProtocolProperties() {
        String json = "{\"schemaVersion\":\"1.0\",\"metadata\":{\"title\":\"T\"},"
                + "\"layout\":{},\"sections\":[],\"unexpected\":true}";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> codec.read(json));
        assertTrue(error.getMessage().contains("unexpected"));
    }

    @Test
    void rejectsMissingTopLevelSchemaPropertiesEvenWhenJavaHasDefaults() {
        String json = "{\"schemaVersion\":\"1.0\",\"metadata\":{\"title\":\"T\"},\"sections\":[]}";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> codec.read(json));

        assertTrue(error.getMessage().contains("layout"));
    }

    @Test
    void publishedSchemaAndCapabilitiesAreValidJsonDocuments() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schema = mapper.readTree(resource("/document-spec/1.0/schema.json"));
        JsonNode capabilities = mapper.readTree(resource("/document-spec/1.0/capabilities.json"));

        assertEquals("1.0", schema.path("properties").path("schemaVersion").path("const").asText());
        assertEquals("1.0", capabilities.path("schemaVersion").asText());
        assertEquals(8, capabilities.path("blocks").size());
    }

    private InputStream resource(String path) {
        InputStream input = getClass().getResourceAsStream(path);
        assertNotNull(input, "missing test resource: " + path);
        return input;
    }
}
