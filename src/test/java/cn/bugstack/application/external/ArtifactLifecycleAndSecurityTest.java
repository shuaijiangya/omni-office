package cn.bugstack.application.external;

import cn.bugstack.application.external.security.ArtifactSecurityException;
import cn.bugstack.application.external.security.BasicArtifactSecurityScanner;
import cn.bugstack.application.external.storage.InMemoryArtifactObjectStorage;
import cn.bugstack.application.external.storage.ObjectStorageExternalArtifactStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactLifecycleAndSecurityTest {

    @Test
    void expiresPurgesAndRejectsSpoofedOfficeArtifacts() throws Exception {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        LocalExternalArtifactStore store = new LocalExternalArtifactStore(
                Files.createTempDirectory("artifact-lifecycle"), Duration.ofSeconds(1),
                new BasicArtifactSecurityScanner(), Clock.fixed(now, ZoneOffset.UTC));
        ExternalArtifactReference artifact = store.store("payload".getBytes(StandardCharsets.UTF_8),
                "payload.bin", "application/octet-stream");
        assertEquals(now.plusSeconds(1), artifact.getExpiresAt());
        assertEquals(1, store.purgeExpired(now.plusSeconds(2)));
        assertThrows(IllegalArgumentException.class, () -> store.resolve(artifact.getResourceUri()));
        assertThrows(ArtifactSecurityException.class, () -> store.store(new byte[]{1, 2, 3},
                "fake.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void objectStorageAdapterKeepsOpaqueUrisAndIntegrityMetadata() throws Exception {
        ObjectStorageExternalArtifactStore store = new ObjectStorageExternalArtifactStore(
                new InMemoryArtifactObjectStorage(), Files.createTempDirectory("object-cache"), Duration.ofHours(1));
        byte[] html = "<!doctype html><html><body>ok</body></html>".getBytes(StandardCharsets.UTF_8);
        ExternalArtifactReference artifact = store.store(html, "document.html", "text/html");
        assertTrue(artifact.getResourceUri().startsWith("omni-office://artifacts/"));
        assertArrayEquals(html, Files.readAllBytes(store.resolve(artifact.getResourceUri()).getContentPath()));
        assertEquals(1, store.list().size());
        assertTrue(store.delete(artifact.getResourceUri()));
    }
}
