package cn.bugstack.application.artifact;

import cn.bugstack.protocol.diagram.DiagramTypeSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDiagramArtifactStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndResolvesFilesWithoutExposingPathsInReference() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("source"));
        Path vsdx = Files.write(source.resolve("source.vsdx"), new byte[]{1, 2, 3});
        Path png = Files.write(source.resolve("source.png"), new byte[]{4, 5});
        LocalDiagramArtifactStore store = new LocalDiagramArtifactStore(tempDir.resolve("artifacts"));

        DiagramArtifactReference reference = store.store(DiagramTypeSpec.FLOW, vsdx, png);
        ResolvedDiagramArtifact resolved = store.resolve(reference.getDiagramArtifactId());

        assertEquals(3, reference.getVsdx().getSize());
        assertEquals(2, reference.getPreview().getSize());
        assertFalse(reference.getVsdx().getArtifactId().contains(tempDir.toString()));
        assertTrue(Files.isRegularFile(resolved.getVsdxPath()));
        assertEquals(reference.getDiagramArtifactId(), resolved.getReference().getDiagramArtifactId());
    }

    @Test
    void rejectsPathTraversalAsArtifactId() {
        LocalDiagramArtifactStore store = new LocalDiagramArtifactStore(tempDir.resolve("artifacts"));
        assertThrows(IllegalArgumentException.class, () -> store.resolve("../../etc/passwd"));
    }
}
