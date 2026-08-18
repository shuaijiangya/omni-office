package cn.bugstack.application.template;

import cn.bugstack.application.document.DynamicDocumentExporter;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.template.DocumentTemplateSpec;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;

/** 默认 DocumentTemplate 应用服务，不调用或自动回退到现有 BusinessReport。 */
public final class DefaultDocumentTemplateService implements DocumentTemplateService {

    private final DocumentTemplateCatalog catalog;
    private final TemplateDocumentAssembler assembler;
    private final DynamicDocumentExporter documentExporter;

    public DefaultDocumentTemplateService(DocumentTemplateCatalog catalog,
                                          DynamicDocumentExporter documentExporter) {
        this(catalog, new TemplateDocumentAssembler(), documentExporter);
    }

    public DefaultDocumentTemplateService(DocumentTemplateCatalog catalog,
                                          TemplateDocumentAssembler assembler,
                                          DynamicDocumentExporter documentExporter) {
        if (catalog == null || assembler == null || documentExporter == null) {
            throw new IllegalArgumentException("document template service dependencies must not be null");
        }
        this.catalog = catalog;
        this.assembler = assembler;
        this.documentExporter = documentExporter;
    }

    @Override
    public DocumentSpec renderSpec(String templateId, String version, JsonNode data) {
        DocumentTemplateSpec template = catalog.require(templateId, version);
        return assembler.assemble(template, data);
    }

    @Override
    public byte[] exportToBytes(String templateId, String version, JsonNode data, ReportOutputFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("document output format must not be null");
        }
        return documentExporter.exportToBytes(renderSpec(templateId, version, data), format);
    }

    @Override
    public void export(String templateId, String version, JsonNode data,
                       ReportOutputFormat format, Path outputPath) {
        if (format == null || outputPath == null) {
            throw new IllegalArgumentException("document output format and path must not be null");
        }
        documentExporter.export(renderSpec(templateId, version, data), format, outputPath);
    }

    @Override
    public JsonNode getDataSchema(String templateId, String version) {
        JsonNode schema = catalog.require(templateId, version).getDataSchema();
        return schema == null ? null : schema.deepCopy();
    }

    @Override
    public List<DocumentTemplateDescriptor> listTemplates() {
        return catalog.list();
    }
}
