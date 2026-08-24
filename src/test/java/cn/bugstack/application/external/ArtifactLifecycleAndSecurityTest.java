package cn.bugstack.application.external;

import cn.bugstack.application.external.security.ArtifactSecurityException;
import cn.bugstack.application.external.security.BasicArtifactSecurityScanner;
import cn.bugstack.application.external.storage.InMemoryArtifactObjectStorage;
import cn.bugstack.application.external.storage.ObjectStorageExternalArtifactStore;
import cn.bugstack.application.artifact.LocalDiagramArtifactStore;
import cn.bugstack.protocol.diagram.DiagramTypeSpec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        ExternalArtifactReference artifact = store.storeForPrincipal(html, "document.html", "text/html", "alice");
        assertTrue(artifact.getResourceUri().startsWith("omni-office://artifacts/"));
        assertEquals("alice", artifact.getOwnerPrincipalId());
        assertArrayEquals(html, Files.readAllBytes(store.resolveForPrincipal(
                artifact.getResourceUri(), "alice", false).getContentPath()));
        assertThrows(IllegalArgumentException.class,
                () -> store.resolveForPrincipal(artifact.getResourceUri(), "bob", false));
        assertEquals(1, store.list().size());
        assertTrue(store.delete(artifact.getResourceUri()));
    }

    @Test
    void localProviderAndDiagramArtifactsShareConfiguredRetention() throws Exception {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        LocalExternalArtifactStoreProvider provider = new LocalExternalArtifactStoreProvider(Duration.ofDays(30));
        assertEquals(Duration.ofDays(30), provider.retention());

        java.nio.file.Path root = Files.createTempDirectory("diagram-lifecycle");
        java.nio.file.Path vsdx = Files.write(root.resolve("input.vsdx"), new byte[]{1});
        java.nio.file.Path preview = Files.write(root.resolve("input.png"), new byte[]{2});
        LocalDiagramArtifactStore diagrams = new LocalDiagramArtifactStore(root.resolve("store"),
                Duration.ofSeconds(1), Clock.fixed(now, ZoneOffset.UTC));
        String id = diagrams.store(DiagramTypeSpec.FLOW, vsdx, preview).getDiagramArtifactId();
        assertEquals(1, diagrams.purgeExpired(now.plusSeconds(2)));
        assertThrows(IllegalArgumentException.class, () -> diagrams.resolve(id));
    }

    @Test
    void allowsWebHyperlinksButRejectsExternalEmbeddedContentInOoxml() throws Exception {
        BasicArtifactSecurityScanner scanner = new BasicArtifactSecurityScanner();
        String hyperlink = "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" "
                + "Target=\"https://example.com/docs\" TargetMode=\"External\"/></Relationships>";
        scanner.scan(ooxmlWithRelationships(hyperlink), "safe.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        String externalImage = "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
                + "Target=\"https://example.com/tracker.png\" TargetMode=\"External\"/></Relationships>";
        assertThrows(ArtifactSecurityException.class, () -> scanner.scan(ooxmlWithRelationships(externalImage),
                "unsafe.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    private byte[] ooxmlWithRelationships(String relationships) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write(relationships.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
