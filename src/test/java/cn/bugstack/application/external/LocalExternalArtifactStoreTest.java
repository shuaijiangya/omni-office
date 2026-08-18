package cn.bugstack.application.external;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalExternalArtifactStoreTest {

    @Test
    void storesAndResolvesOpaqueResourceWithoutExposingPath() throws Exception {
        Path root = Files.createTempDirectory("external-artifacts");
        LocalExternalArtifactStore store = new LocalExternalArtifactStore(root);
        byte[] content = new byte[]{1, 2, 3, 4};

        ExternalArtifactReference reference = store.store(content, "artifact.bin",
                "application/octet-stream");
        ResolvedExternalArtifact resolved = store.resolve(reference.getResourceUri());

        assertTrue(reference.getResourceUri().matches("omni-office://artifacts/[0-9a-f-]{36}"));
        assertEquals(64, reference.getSha256().length());
        assertArrayEquals(content, Files.readAllBytes(resolved.getContentPath()));
        assertTrue(resolved.getContentPath().startsWith(root.toAbsolutePath()));
    }

    @Test
    void rejectsUntrustedNamesAndResourceUris() throws Exception {
        LocalExternalArtifactStore store = new LocalExternalArtifactStore(
                Files.createTempDirectory("external-artifact-boundary"));

        assertThrows(IllegalArgumentException.class,
                () -> store.store(new byte[]{1}, "../secret.docx", "application/octet-stream"));
        assertThrows(IllegalArgumentException.class,
                () -> store.resolve("file:///tmp/secret.docx"));
        assertThrows(IllegalArgumentException.class,
                () -> store.resolve("omni-office://artifacts/../../secret"));
    }
}
