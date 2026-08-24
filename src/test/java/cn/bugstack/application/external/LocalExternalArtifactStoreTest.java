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

    @Test
    void persistsPrincipalOwnershipAndRequiresExplicitAnyAccess() throws Exception {
        LocalExternalArtifactStore store = new LocalExternalArtifactStore(
                Files.createTempDirectory("external-artifact-owner"));
        ExternalArtifactReference reference = store.storeForPrincipal(new byte[]{1, 2}, "artifact.bin",
                "application/octet-stream", "alice");

        assertEquals("alice", reference.getOwnerPrincipalId());
        assertEquals("alice", store.resolveForPrincipal(reference.getResourceUri(), "alice", false)
                .getReference().getOwnerPrincipalId());
        assertThrows(IllegalArgumentException.class,
                () -> store.resolveForPrincipal(reference.getResourceUri(), "bob", false));
        assertEquals("alice", store.resolveForPrincipal(reference.getResourceUri(), "admin", true)
                .getReference().getOwnerPrincipalId());
    }

    @Test
    void storesFileBackedArtifactsThroughTheStreamingPath() throws Exception {
        Path root = Files.createTempDirectory("external-artifact-stream");
        Path input = Files.write(root.resolve("input.bin"), new byte[]{9, 8, 7, 6});
        LocalExternalArtifactStore store = new LocalExternalArtifactStore(root.resolve("store"));

        ExternalArtifactReference reference = store.storeForPrincipal(
                input, "artifact.bin", "application/octet-stream", "alice");

        assertEquals(4, reference.getSize());
        assertArrayEquals(new byte[]{9, 8, 7, 6}, Files.readAllBytes(
                store.resolveForPrincipal(reference.getResourceUri(), "alice", false).getContentPath()));
    }
}
