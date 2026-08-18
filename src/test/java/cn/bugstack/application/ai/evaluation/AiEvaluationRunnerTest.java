package cn.bugstack.application.ai.evaluation;

import cn.bugstack.application.ai.AiDocumentResult;
import cn.bugstack.application.ai.AiGenerationMode;
import cn.bugstack.application.ai.InternalAiDocumentService;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentSpecJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiEvaluationRunnerTest {

    @Test
    void evaluatesStructuralPointersTextAndAttempts() throws Exception {
        DocumentSpec spec;
        try (InputStream input = getClass().getResourceAsStream("/document-spec/1.0/example-simple.json")) {
            spec = new DocumentSpecJsonCodec().read(input);
        }
        AiDocumentResult generated = new AiDocumentResult(AiGenerationMode.FREEFORM_DOCUMENT,
                spec, null, null, null, 1);
        InternalAiDocumentService service = new StubService(generated);
        AiEvaluationCase passing = new AiEvaluationCase("pass", AiGenerationMode.FREEFORM_DOCUMENT,
                null, null, "generate", null, Arrays.asList("/metadata/title", "/sections/0/title"),
                Arrays.asList("系统评估报告"), 2);
        AiEvaluationCase failing = new AiEvaluationCase("fail", AiGenerationMode.FREEFORM_DOCUMENT,
                null, null, "generate", null, Arrays.asList("/missing"), Arrays.asList("absent-value"), 1);
        AiEvaluationReport report = new AiEvaluationRunner(service).run(Arrays.asList(passing, failing));
        assertEquals(1, report.getPassed());
        assertEquals(0.5, report.getPassRate());
    }

    private static final class StubService implements InternalAiDocumentService {
        private final AiDocumentResult result;
        private StubService(AiDocumentResult result) { this.result = result; }
        public AiDocumentResult generateFreeform(String instruction, JsonNode context) { return result; }
        public AiDocumentResult generateFromTemplate(String templateId, String version, String instruction, JsonNode context) { return result; }
        public byte[] exportToBytes(AiDocumentResult result, ReportOutputFormat format) { return new byte[0]; }
        public void export(AiDocumentResult result, ReportOutputFormat format, Path outputPath) { }
    }
}
