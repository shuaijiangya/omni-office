package cn.bugstack.application.ai;

import cn.bugstack.application.document.DefaultDynamicDocumentExporter;
import cn.bugstack.application.document.DocumentGenerationApplication;
import cn.bugstack.application.document.DocumentSpecLimits;
import cn.bugstack.application.document.DocumentSpecValidator;
import cn.bugstack.application.document.DynamicDocumentExporter;
import cn.bugstack.application.template.DocumentTemplateApplication;
import cn.bugstack.application.template.DocumentTemplateDescriptor;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.template.DocumentTemplateSpec;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * M4 推荐门面。调用方必须注入内部模型适配器，并显式选择自由文档或模板数据模式。
 */
public final class InternalAiDocumentApplication implements InternalAiDocumentService {

    private final DocumentTemplateApplication templates;
    private final InternalAiDocumentService service;

    /** 创建不允许自由 AI 生成图工件的应用。 */
    public InternalAiDocumentApplication(StructuredAiClient aiClient) {
        this(aiClient, new DefaultDynamicDocumentExporter(),
                new DocumentSpecValidator(DocumentSpecLimits.defaults(), false));
    }

    /** 创建支持自由 AI 内联 DiagramSpec 的应用。 */
    public InternalAiDocumentApplication(StructuredAiClient aiClient, Path artifactRoot) {
        this(aiClient, new DocumentGenerationApplication(artifactRoot),
                new DocumentSpecValidator(DocumentSpecLimits.defaults(), true));
    }

    public InternalAiDocumentApplication(StructuredAiClient aiClient,
                                         DynamicDocumentExporter documentExporter,
                                         DocumentSpecValidator documentValidator) {
        if (aiClient == null || documentExporter == null || documentValidator == null) {
            throw new IllegalArgumentException("internal AI application dependencies must not be null");
        }
        this.templates = new DocumentTemplateApplication(documentExporter);
        this.service = new DefaultInternalAiDocumentService(aiClient, documentValidator,
                templates, documentExporter);
    }

    public void registerTemplate(DocumentTemplateSpec template) {
        templates.register(template);
    }

    public void registerTemplate(InputStream templateJson) {
        templates.register(templateJson);
    }

    public void registerTemplate(String templateJson) {
        templates.register(templateJson);
    }

    public List<DocumentTemplateDescriptor> listTemplates() {
        return templates.listTemplates();
    }

    public JsonNode getTemplateDataSchema(String templateId, String version) {
        return templates.getDataSchema(templateId, version);
    }

    @Override
    public AiDocumentResult generateFreeform(String instruction, JsonNode context) {
        return service.generateFreeform(instruction, context);
    }

    public AiDocumentResult generateFreeform(String instruction) {
        return generateFreeform(instruction, null);
    }

    @Override
    public AiDocumentResult generateFromTemplate(String templateId, String version,
                                                 String instruction, JsonNode context) {
        return service.generateFromTemplate(templateId, version, instruction, context);
    }

    public AiDocumentResult generateFromTemplate(String templateId, String version, String instruction) {
        return generateFromTemplate(templateId, version, instruction, null);
    }

    @Override
    public byte[] exportToBytes(AiDocumentResult result, ReportOutputFormat format) {
        return service.exportToBytes(result, format);
    }

    @Override
    public void export(AiDocumentResult result, ReportOutputFormat format, Path outputPath) {
        service.export(result, format, outputPath);
    }
}
