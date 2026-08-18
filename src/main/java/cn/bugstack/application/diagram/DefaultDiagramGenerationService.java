package cn.bugstack.application.diagram;

import cn.bugstack.application.artifact.DiagramArtifactReference;
import cn.bugstack.application.artifact.DiagramArtifactStore;
import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.api.VisioDiagramRenderer;
import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.visio.VsdxDiagramRenderer;
import cn.bugstack.protocol.diagram.DiagramSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 默认图工件生成服务。 */
public final class DefaultDiagramGenerationService implements DiagramGenerationService {

    private final DiagramSpecValidator validator;
    private final DiagramSpecCompiler compiler;
    private final VisioDiagramRenderer renderer;
    private final DiagramArtifactStore artifactStore;

    public DefaultDiagramGenerationService(DiagramArtifactStore artifactStore) {
        this(new DiagramSpecValidator(), new DiagramSpecCompiler(), new VsdxDiagramRenderer(), artifactStore);
    }

    public DefaultDiagramGenerationService(DiagramSpecValidator validator, DiagramSpecCompiler compiler,
                                           VisioDiagramRenderer renderer, DiagramArtifactStore artifactStore) {
        if (validator == null || compiler == null || renderer == null || artifactStore == null) {
            throw new IllegalArgumentException("diagram generation dependencies must not be null");
        }
        this.validator = validator;
        this.compiler = compiler;
        this.renderer = renderer;
        this.artifactStore = artifactStore;
    }

    @Override
    public DiagramArtifactReference generate(DiagramSpec spec) {
        validator.validate(spec).throwIfInvalid();
        DiagramDefinition definition = compiler.compile(spec);
        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory("omni-office-diagram-");
            VisioDiagramArtifact generated = renderer.render(definition, temporaryDirectory.resolve("diagram.vsdx"),
                    temporaryDirectory.resolve("preview.png"));
            return artifactStore.store(spec.getType(), generated.getVsdxPath(), generated.getPreviewPngPath());
        } catch (IOException e) {
            throw new IllegalStateException("failed to generate diagram artifact", e);
        } finally {
            deleteIfExists(temporaryDirectory == null ? null : temporaryDirectory.resolve("diagram.vsdx"));
            deleteIfExists(temporaryDirectory == null ? null : temporaryDirectory.resolve("preview.png"));
            deleteIfExists(temporaryDirectory);
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary generation output is best-effort cleanup only.
        }
    }
}
