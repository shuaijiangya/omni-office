package cn.bugstack.application.ai.evaluation;

import cn.bugstack.application.ai.AiDocumentResult;
import cn.bugstack.application.ai.AiGenerationMode;
import cn.bugstack.application.ai.InternalAiDocumentService;
import cn.bugstack.protocol.document.DocumentSpecJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** 运行结构、内容存在性和最大重试次数断言，不用主观文本相似度替代业务验收。 */
public final class AiEvaluationRunner {

    private final InternalAiDocumentService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final DocumentSpecJsonCodec codec = new DocumentSpecJsonCodec();
    private final Clock clock;

    public AiEvaluationRunner(InternalAiDocumentService service) {
        this(service, Clock.systemUTC());
    }

    AiEvaluationRunner(InternalAiDocumentService service, Clock clock) {
        if (service == null || clock == null) throw new IllegalArgumentException("evaluation dependencies are required");
        this.service = service;
        this.clock = clock;
    }

    public AiEvaluationReport run(List<AiEvaluationCase> cases) {
        if (cases == null) throw new IllegalArgumentException("evaluation cases are required");
        List<AiEvaluationReport.CaseResult> results = new ArrayList<>();
        for (AiEvaluationCase testCase : cases) results.add(runOne(testCase));
        return new AiEvaluationReport(clock.instant(), results);
    }

    private AiEvaluationReport.CaseResult runOne(AiEvaluationCase testCase) {
        long started = clock.millis();
        List<String> violations = new ArrayList<>();
        int attempts = 0;
        try {
            AiDocumentResult result = testCase.getMode() == AiGenerationMode.FREEFORM_DOCUMENT
                    ? service.generateFreeform(testCase.getInstruction(), testCase.getContext())
                    : service.generateFromTemplate(testCase.getTemplateId(), testCase.getTemplateVersion(),
                    testCase.getInstruction(), testCase.getContext());
            attempts = result.getAttempts();
            JsonNode json = mapper.readTree(codec.write(result.getDocumentSpec()));
            for (String pointer : testCase.getRequiredJsonPointers()) {
                if (json.at(pointer).isMissingNode() || json.at(pointer).isNull()) {
                    violations.add("missing required JSON pointer: " + pointer);
                }
            }
            String documentJson = json.toString();
            for (String text : testCase.getRequiredText()) {
                if (!documentJson.contains(text)) violations.add("required text is absent: " + text);
            }
            if (attempts > testCase.getMaximumAttempts()) {
                violations.add("attempts " + attempts + " exceed limit " + testCase.getMaximumAttempts());
            }
        } catch (Exception e) {
            violations.add("generation failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return new AiEvaluationReport.CaseResult(testCase.getId(), violations.isEmpty(), attempts,
                Math.max(0, clock.millis() - started), violations);
    }
}
