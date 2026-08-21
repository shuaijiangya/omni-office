package cn.bugstack.application.document;

import cn.bugstack.application.artifact.DiagramArtifactReference;
import cn.bugstack.application.artifact.DiagramArtifactStore;
import cn.bugstack.application.artifact.LocalDiagramArtifactStore;
import cn.bugstack.application.diagram.DefaultDiagramGenerationService;
import cn.bugstack.application.diagram.DiagramGenerationService;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.export.core.ReportDocumentValidator;
import cn.bugstack.export.docx.DocxReportCompiler;
import cn.bugstack.protocol.diagram.DiagramSpec;
import cn.bugstack.protocol.document.DocumentSpec;

import java.nio.file.Path;

/**
 * M2 推荐应用门面：共享同一个受控工件库，统一提供图生成与 DocumentSpec 导出。
 */
public final class DocumentGenerationApplication implements DynamicDocumentExporter {

    private final DiagramGenerationService diagramService;
    private final DefaultDynamicDocumentExporter documentExporter;
    private final DocumentSpecValidator documentValidator;

    public DocumentGenerationApplication(Path artifactRoot) {
        this(new LocalDiagramArtifactStore(artifactRoot));
    }

    /** 创建使用调用方提供受控图工件存储的应用，便于外部协议层共享工件解析能力。 */
    public DocumentGenerationApplication(DiagramArtifactStore store) {
        if (store == null) {
            throw new IllegalArgumentException("diagram artifact store must not be null");
        }
        this.diagramService = new DefaultDiagramGenerationService(store);
        DiagramBlockResolver resolver = new DefaultDiagramBlockResolver(diagramService, store);
        this.documentValidator = new DocumentSpecValidator(DocumentSpecLimits.defaults(), true);
        this.documentExporter = new DefaultDynamicDocumentExporter(
                documentValidator,
                new DefaultDocumentSpecCompiler(resolver),
                new DocumentSpecBlueprintFactory(),
                new ReportDocumentValidator(),
                new DocxReportCompiler());
    }

    public DiagramArtifactReference generateDiagram(DiagramSpec spec) {
        return diagramService.generate(spec);
    }

    /** 执行无副作用的协议与语义约束校验；不会解析或生成图工件。 */
    public void validate(DocumentSpec spec) {
        documentValidator.validate(spec).throwIfInvalid();
    }

    @Override
    public byte[] exportToBytes(DocumentSpec spec, ReportOutputFormat format) {
        return documentExporter.exportToBytes(spec, format);
    }

    @Override
    public void export(DocumentSpec spec, ReportOutputFormat format, Path outputPath) {
        documentExporter.export(spec, format, outputPath);
    }
}
