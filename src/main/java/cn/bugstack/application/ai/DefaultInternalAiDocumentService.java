package cn.bugstack.application.ai;

import cn.bugstack.application.document.DocumentSpecValidationResult;
import cn.bugstack.application.document.DocumentSpecValidator;
import cn.bugstack.application.document.DocumentSpecViolation;
import cn.bugstack.application.document.DynamicDocumentExporter;
import cn.bugstack.application.template.DocumentTemplateMappingException;
import cn.bugstack.application.template.DocumentTemplateService;
import cn.bugstack.application.template.DocumentTemplateValidationException;
import cn.bugstack.application.template.DocumentTemplateViolation;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentSpecJsonCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 默认内部 AI 结构化文档服务。 */
public final class DefaultInternalAiDocumentService implements InternalAiDocumentService {

    private static final int MAX_FEEDBACK_ERRORS = 20;
    private static final int MAX_FEEDBACK_ERROR_LENGTH = 1_000;
    private final StructuredAiClient aiClient;
    private final AiGenerationPolicy policy;
    private final AiOutputSchemaProvider schemaProvider;
    private final AiPromptFactory promptFactory;
    private final DocumentSpecJsonCodec documentCodec;
    private final DocumentSpecValidator documentValidator;
    private final AiGeneratedDocumentSafetyValidator safetyValidator;
    private final DocumentTemplateService templateService;
    private final DynamicDocumentExporter documentExporter;
    private final ObjectMapper objectMapper;

    public DefaultInternalAiDocumentService(StructuredAiClient aiClient,
                                            DocumentSpecValidator documentValidator,
                                            DocumentTemplateService templateService,
                                            DynamicDocumentExporter documentExporter) {
        this(aiClient, AiGenerationPolicy.defaults(), new ClassPathAiOutputSchemaProvider(),
                new AiPromptFactory(), new DocumentSpecJsonCodec(), documentValidator,
                new AiGeneratedDocumentSafetyValidator(), templateService, documentExporter, new ObjectMapper());
    }

    public DefaultInternalAiDocumentService(StructuredAiClient aiClient,
                                            AiGenerationPolicy policy,
                                            AiOutputSchemaProvider schemaProvider,
                                            AiPromptFactory promptFactory,
                                            DocumentSpecJsonCodec documentCodec,
                                            DocumentSpecValidator documentValidator,
                                            AiGeneratedDocumentSafetyValidator safetyValidator,
                                            DocumentTemplateService templateService,
                                            DynamicDocumentExporter documentExporter,
                                            ObjectMapper objectMapper) {
        if (aiClient == null || policy == null || schemaProvider == null || promptFactory == null
                || documentCodec == null || documentValidator == null || safetyValidator == null
                || templateService == null || documentExporter == null || objectMapper == null) {
            throw new IllegalArgumentException("internal AI document service dependencies must not be null");
        }
        this.aiClient = aiClient;
        this.policy = policy;
        this.schemaProvider = schemaProvider;
        this.promptFactory = promptFactory;
        this.documentCodec = documentCodec;
        this.documentValidator = documentValidator;
        this.safetyValidator = safetyValidator;
        this.templateService = templateService;
        this.documentExporter = documentExporter;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
    }

    @Override
    public AiDocumentResult generateFreeform(String instruction, JsonNode context) {
        validateInput(instruction, context);
        List<String> feedback = Collections.emptyList();
        for (int attempt = 1; attempt <= policy.getMaxAttempts(); attempt++) {
            StructuredAiRequest request = new StructuredAiRequest(
                    "generate_document_spec",
                    promptFactory.freeformSystemInstruction(documentValidator.isDiagramEnabled()),
                    instruction,
                    context,
                    schemaProvider.documentSpecSchema(),
                    attempt,
                    feedback);
            String output = callModel(request, AiGenerationMode.FREEFORM_DOCUMENT, attempt);
            try {
                DocumentSpec document = documentCodec.read(requireObjectJson(output));
                List<String> errors = validateGeneratedDocument(document);
                if (errors.isEmpty()) {
                    return new AiDocumentResult(AiGenerationMode.FREEFORM_DOCUMENT,
                            document, null, null, null, attempt);
                }
                feedback = boundedFeedback(errors);
            } catch (IllegalArgumentException e) {
                feedback = boundedFeedback(Collections.singletonList("/ [INVALID_JSON] " + e.getMessage()));
            }
        }
        throw invalidOutput(AiGenerationMode.FREEFORM_DOCUMENT, policy.getMaxAttempts(), feedback);
    }

    @Override
    public AiDocumentResult generateFromTemplate(String templateId, String version,
                                                 String instruction, JsonNode context) {
        validateInput(instruction, context);
        JsonNode outputSchema = templateService.getDataSchema(templateId, version);
        List<String> feedback = Collections.emptyList();
        for (int attempt = 1; attempt <= policy.getMaxAttempts(); attempt++) {
            StructuredAiRequest request = new StructuredAiRequest(
                    "fill_document_template",
                    promptFactory.templateSystemInstruction(templateId, version),
                    instruction,
                    context,
                    outputSchema,
                    attempt,
                    feedback);
            String output = callModel(request, AiGenerationMode.DOCUMENT_TEMPLATE, attempt);
            try {
                JsonNode data = objectMapper.readTree(requireObjectJson(output));
                DocumentSpec document = templateService.renderSpec(templateId, version, data);
                return new AiDocumentResult(AiGenerationMode.DOCUMENT_TEMPLATE,
                        document, data, templateId, version, attempt);
            } catch (DocumentTemplateValidationException e) {
                feedback = templateFeedback(e.getViolations());
            } catch (DocumentTemplateMappingException e) {
                feedback = boundedFeedback(Collections.singletonList(
                        e.getTemplatePath() + " [TEMPLATE_MAPPING] " + e.getMessage()));
            } catch (JsonProcessingException | IllegalArgumentException e) {
                feedback = boundedFeedback(Collections.singletonList("/ [INVALID_JSON] " + e.getMessage()));
            }
        }
        throw invalidOutput(AiGenerationMode.DOCUMENT_TEMPLATE, policy.getMaxAttempts(), feedback);
    }

    @Override
    public byte[] exportToBytes(AiDocumentResult result, ReportOutputFormat format) {
        if (result == null || result.getDocumentSpec() == null || format == null) {
            throw new IllegalArgumentException("AI document result and output format must not be null");
        }
        return documentExporter.exportToBytes(result.getDocumentSpec(), format);
    }

    @Override
    public void export(AiDocumentResult result, ReportOutputFormat format, Path outputPath) {
        if (result == null || result.getDocumentSpec() == null || format == null || outputPath == null) {
            throw new IllegalArgumentException("AI document result, output format and path must not be null");
        }
        documentExporter.export(result.getDocumentSpec(), format, outputPath);
    }

    private List<String> validateGeneratedDocument(DocumentSpec document) {
        List<String> errors = new ArrayList<>();
        DocumentSpecValidationResult result = documentValidator.validate(document);
        for (DocumentSpecViolation violation : result.getViolations()) {
            errors.add(violation.toString());
        }
        errors.addAll(safetyValidator.validate(document));
        return errors;
    }

    private String callModel(StructuredAiRequest request, AiGenerationMode mode, int attempt) {
        final String output;
        try {
            output = aiClient.generateJson(request);
        } catch (RuntimeException e) {
            throw new AiGenerationException("internal AI call failed", mode, attempt,
                    request.getValidationFeedback(), e);
        }
        if (output == null || output.trim().isEmpty()) {
            return "";
        }
        if (output.length() > policy.getMaxOutputCharacters()) {
            throw new AiGenerationException("internal AI output exceeds configured limit", mode, attempt,
                    Collections.singletonList("/ [LIMIT_EXCEEDED] model output is too large"), null);
        }
        return output;
    }

    private String requireObjectJson(String output) {
        if (output == null || output.trim().isEmpty()) {
            throw new IllegalArgumentException("model returned an empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(output);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("model output root must be a JSON object");
            }
            return root.toString();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("model output must contain JSON only: " + e.getOriginalMessage(), e);
        }
    }

    private void validateInput(String instruction, JsonNode context) {
        if (instruction == null || instruction.trim().isEmpty()) {
            throw new IllegalArgumentException("AI generation instruction must not be blank");
        }
        if (instruction.length() > policy.getMaxInstructionCharacters()) {
            throw new IllegalArgumentException("AI generation instruction exceeds configured limit");
        }
        if (context != null && !context.isObject()) {
            throw new IllegalArgumentException("AI generation context must be a JSON object");
        }
        if (context != null && context.toString().length() > policy.getMaxContextCharacters()) {
            throw new IllegalArgumentException("AI generation context exceeds configured limit");
        }
    }

    private List<String> templateFeedback(List<DocumentTemplateViolation> violations) {
        List<String> errors = new ArrayList<>();
        for (DocumentTemplateViolation violation : violations) {
            errors.add(violation.toString());
        }
        return boundedFeedback(errors);
    }

    private List<String> boundedFeedback(List<String> errors) {
        List<String> bounded = new ArrayList<>();
        for (String error : errors) {
            if (bounded.size() >= MAX_FEEDBACK_ERRORS) {
                break;
            }
            String value = error == null ? "unknown validation error" : error;
            bounded.add(value.length() <= MAX_FEEDBACK_ERROR_LENGTH
                    ? value : value.substring(0, MAX_FEEDBACK_ERROR_LENGTH));
        }
        return Collections.unmodifiableList(bounded);
    }

    private AiGenerationException invalidOutput(AiGenerationMode mode, int attempts, List<String> feedback) {
        return new AiGenerationException("internal AI output remained invalid after " + attempts + " attempts",
                mode, attempts, feedback, null);
    }
}
