package cn.bugstack.application.artifact;

import cn.bugstack.protocol.diagram.DiagramTypeSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 受控本地目录图工件存储。
 *
 * <p>调用方仅持有 UUID 形式的组标识；所有路径均由存储根目录和固定文件名派生，
 * 从而避免通过工件标识进行路径穿越。</p>
 */
public final class LocalDiagramArtifactStore implements DiagramArtifactStore {

    private static final String VSDX_FILE = "diagram.vsdx";
    private static final String PREVIEW_FILE = "preview.png";
    private static final String METADATA_FILE = "metadata.properties";
    private final Path root;

    public LocalDiagramArtifactStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("artifact store root must not be null");
        }
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create artifact store root: " + this.root, e);
        }
    }

    @Override
    public DiagramArtifactReference store(DiagramTypeSpec type, Path vsdxSource, Path previewSource) {
        if (type == null || !isRegularFile(vsdxSource) || !isRegularFile(previewSource)) {
            throw new IllegalArgumentException("diagram type, VSDX and preview files are required");
        }
        String id = UUID.randomUUID().toString();
        Path temporary = root.resolve("." + id + ".tmp");
        Path completed = root.resolve(id);
        try {
            Files.createDirectory(temporary);
            Files.copy(vsdxSource, temporary.resolve(VSDX_FILE), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(previewSource, temporary.resolve(PREVIEW_FILE), StandardCopyOption.REPLACE_EXISTING);
            Properties metadata = new Properties();
            metadata.setProperty("type", type.name());
            try (OutputStream output = Files.newOutputStream(temporary.resolve(METADATA_FILE))) {
                metadata.store(output, "omni-office diagram artifact");
            }
            moveCompleted(temporary, completed);
            return reference(id, type, completed.resolve(VSDX_FILE), completed.resolve(PREVIEW_FILE));
        } catch (IOException e) {
            deleteTreeQuietly(temporary);
            throw new IllegalStateException("failed to store diagram artifact", e);
        }
    }

    @Override
    public ResolvedDiagramArtifact resolve(String diagramArtifactId) {
        String id = requireSafeId(diagramArtifactId);
        Path directory = root.resolve(id).normalize();
        if (!directory.getParent().equals(root)) {
            throw new IllegalArgumentException("invalid diagram artifact id");
        }
        Path vsdx = directory.resolve(VSDX_FILE);
        Path preview = directory.resolve(PREVIEW_FILE);
        Path metadataPath = directory.resolve(METADATA_FILE);
        if (!Files.isRegularFile(vsdx) || !Files.isRegularFile(preview) || !Files.isRegularFile(metadataPath)) {
            throw new IllegalArgumentException("diagram artifact does not exist: " + id);
        }
        try (InputStream input = Files.newInputStream(metadataPath)) {
            Properties metadata = new Properties();
            metadata.load(input);
            DiagramTypeSpec type = DiagramTypeSpec.valueOf(metadata.getProperty("type"));
            DiagramArtifactReference reference = reference(id, type, vsdx, preview);
            return new ResolvedDiagramArtifact(reference, vsdx, preview);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("failed to resolve diagram artifact: " + id, e);
        }
    }

    public Path getRoot() {
        return root;
    }

    private DiagramArtifactReference reference(String id, DiagramTypeSpec type, Path vsdx, Path preview)
            throws IOException {
        return new DiagramArtifactReference(id, type,
                new ArtifactReference(id + ":vsdx", VSDX_FILE,
                        "application/vnd.ms-visio.drawing", Files.size(vsdx)),
                new ArtifactReference(id + ":preview", PREVIEW_FILE, "image/png", Files.size(preview)));
    }

    private String requireSafeId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("diagram artifact id must not be null");
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("diagram artifact id must be a UUID", e);
        }
    }

    private boolean isRegularFile(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    private void moveCompleted(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private void deleteTreeQuietly(Path directory) {
        if (directory == null || !directory.normalize().startsWith(root) || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup of an unpublished temporary artifact.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup of an unpublished temporary artifact.
        }
    }
}
