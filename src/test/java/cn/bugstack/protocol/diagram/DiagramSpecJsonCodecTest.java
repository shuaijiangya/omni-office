package cn.bugstack.protocol.diagram;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagramSpecJsonCodecTest {

    @Test
    void readsAndWritesPublishedFlowExample() throws IOException {
        DiagramSpecJsonCodec codec = new DiagramSpecJsonCodec();
        DiagramSpec spec = codec.read(resource("/diagram-spec/1.0/example-flow.json"));

        assertEquals(DiagramTypeSpec.FLOW, spec.getType());
        assertEquals(5, spec.getNodes().size());
        assertEquals("文档生成流程", codec.read(codec.write(spec)).getTitle());
    }

    @Test
    void rejectsUnknownProperties() {
        String json = "{\"schemaVersion\":\"1.0\",\"type\":\"FLOW\",\"title\":\"x\","
                + "\"nodes\":[{\"id\":\"a\",\"label\":\"A\",\"type\":\"START\",\"unknown\":1}]}";

        assertThrows(IllegalArgumentException.class, () -> new DiagramSpecJsonCodec().read(json));
    }

    private String resource(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("missing test resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
