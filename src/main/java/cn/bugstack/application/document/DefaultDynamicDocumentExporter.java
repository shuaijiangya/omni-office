package cn.bugstack.application.document;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.export.core.ReportDocumentRenderer;
import cn.bugstack.export.core.ReportDocumentValidator;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.docx.DocxReportCompiler;
import cn.bugstack.export.document.ReportDocument;
import cn.bugstack.protocol.document.DocumentSpec;

import java.nio.file.Path;
import java.util.List;

/**
 * DocumentSpec 校验、语义编译和 DOCX/PDF/HTML 渲染的默认应用服务。
 */
public final class DefaultDynamicDocumentExporter implements DynamicDocumentExporter {

    private final DocumentSpecValidator specValidator;
    private final DocumentSpecCompiler specCompiler;
    private final DocumentSpecBlueprintFactory blueprintFactory;
    private final ReportDocumentValidator documentValidator;
    private final ReportDocumentRenderer renderer;

    public DefaultDynamicDocumentExporter() {
        this(new DocumentSpecValidator(), new DefaultDocumentSpecCompiler(),
                new DocumentSpecBlueprintFactory(), new ReportDocumentValidator(), new DocxReportCompiler());
    }

    public DefaultDynamicDocumentExporter(DocumentSpecValidator specValidator,
                                          DocumentSpecCompiler specCompiler,
                                          DocumentSpecBlueprintFactory blueprintFactory,
                                          ReportDocumentValidator documentValidator,
                                          ReportDocumentRenderer renderer) {
        if (specValidator == null || specCompiler == null || blueprintFactory == null
                || documentValidator == null || renderer == null) {
            throw new IllegalArgumentException("dynamic document exporter dependencies must not be null");
        }
        this.specValidator = specValidator;
        this.specCompiler = specCompiler;
        this.blueprintFactory = blueprintFactory;
        this.documentValidator = documentValidator;
        this.renderer = renderer;
    }

    @Override
    public byte[] exportToBytes(DocumentSpec spec, ReportOutputFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("document output format must not be null");
        }
        PreparedDynamicDocument prepared = prepare(spec);
        return renderer.renderToBytes(prepared.document, prepared.blueprint, format);
    }

    @Override
    public void export(DocumentSpec spec, ReportOutputFormat format, Path outputPath) {
        if (format == null || outputPath == null) {
            throw new IllegalArgumentException("document output format and output path must not be null");
        }
        PreparedDynamicDocument prepared = prepare(spec);
        renderer.render(prepared.document, prepared.blueprint, format, outputPath);
    }

    private PreparedDynamicDocument prepare(DocumentSpec spec) {
        specValidator.validate(spec).throwIfInvalid();
        ReportDocument document = specCompiler.compile(spec);
        List<String> semanticErrors = documentValidator.validate(document);
        if (!semanticErrors.isEmpty()) {
            throw new IllegalArgumentException("compiled report document is invalid: "
                    + String.join("; ", semanticErrors));
        }
        return new PreparedDynamicDocument(document, blueprintFactory.create(spec));
    }

    private static final class PreparedDynamicDocument {

        private final ReportDocument document;
        private final ReportBlueprint blueprint;

        private PreparedDynamicDocument(ReportDocument document, ReportBlueprint blueprint) {
            this.document = document;
            this.blueprint = blueprint;
        }
    }
}
