package cn.bugstack.application.template;

import cn.bugstack.application.document.DefaultDynamicDocumentExporter;
import cn.bugstack.application.document.DocumentGenerationApplication;
import cn.bugstack.application.document.DynamicDocumentExporter;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.template.DocumentTemplateSpec;
import cn.bugstack.protocol.template.DocumentTemplateSpecJsonCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * M3 推荐门面。模板目录只包含 DocumentTemplate，不扫描或接管 export 业务报告。
 */
public final class DocumentTemplateApplication implements DocumentTemplateService {

    private final DocumentTemplateCatalog catalog;
    private final DocumentTemplateService service;
    private final DocumentTemplateSpecJsonCodec templateCodec = new DocumentTemplateSpecJsonCodec();
    private final ObjectMapper dataMapper = new ObjectMapper();

    /** 创建不启用图工件的普通数据模板应用。 */
    public DocumentTemplateApplication() {
        this(new DefaultDynamicDocumentExporter());
    }

    /** 创建共享受控图工件目录、支持模板内 DiagramSpec 的应用。 */
    public DocumentTemplateApplication(Path artifactRoot) {
        this(new DocumentGenerationApplication(artifactRoot));
    }

    /** 创建使用调用方指定 DocumentSpec 导出器的模板应用。 */
    public DocumentTemplateApplication(DynamicDocumentExporter documentExporter) {
        this(new InMemoryDocumentTemplateCatalog(), documentExporter);
    }

    /** 创建使用调用方指定目录与 DocumentSpec 导出器的模板应用。 */
    public DocumentTemplateApplication(DocumentTemplateCatalog catalog,
                                       DynamicDocumentExporter documentExporter) {
        if (catalog == null || documentExporter == null) {
            throw new IllegalArgumentException("document template catalog and exporter must not be null");
        }
        this.catalog = catalog;
        this.service = new DefaultDocumentTemplateService(catalog, documentExporter);
    }

    public void register(DocumentTemplateSpec template) {
        catalog.register(template);
    }

    public void register(InputStream templateJson) {
        register(templateCodec.read(templateJson));
    }

    public void register(String templateJson) {
        register(templateCodec.read(templateJson));
    }

    @Override
    public DocumentSpec renderSpec(String templateId, String version, JsonNode data) {
        return service.renderSpec(templateId, version, data);
    }

    public DocumentSpec renderSpec(String templateId, String version, String dataJson) {
        return renderSpec(templateId, version, readData(dataJson));
    }

    @Override
    public byte[] exportToBytes(String templateId, String version, JsonNode data, ReportOutputFormat format) {
        return service.exportToBytes(templateId, version, data, format);
    }

    public byte[] exportToBytes(String templateId, String version, String dataJson, ReportOutputFormat format) {
        return exportToBytes(templateId, version, readData(dataJson), format);
    }

    @Override
    public void export(String templateId, String version, JsonNode data,
                       ReportOutputFormat format, Path outputPath) {
        service.export(templateId, version, data, format, outputPath);
    }

    @Override
    public JsonNode getDataSchema(String templateId, String version) {
        return service.getDataSchema(templateId, version);
    }

    @Override
    public List<DocumentTemplateDescriptor> listTemplates() {
        return service.listTemplates();
    }

    private JsonNode readData(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("template data json must not be blank");
        }
        try {
            JsonNode data = dataMapper.readTree(json);
            if (data == null || !data.isObject()) {
                throw new IllegalArgumentException("template data json root must be an object");
            }
            return data;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid template data json: " + e.getOriginalMessage(), e);
        }
    }
}
