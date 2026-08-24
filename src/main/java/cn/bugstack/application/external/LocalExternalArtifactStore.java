package cn.bugstack.application.external;

import cn.bugstack.application.external.security.ArtifactSecurityScanner;
import cn.bugstack.application.external.security.BasicArtifactSecurityScanner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Function Calling 与 MCP 共用的受控本地输出工件库。
 *
 * <p>外部仅获得 {@code omni-office://artifacts/{uuid}}，文件路径始终由服务端根目录、
 * UUID 和固定文件名派生，避免调用方提交路径或文件名造成越界写入。</p>
 */
public final class LocalExternalArtifactStore implements ExternalArtifactStore {

    private static final String URI_PREFIX = "omni-office://artifacts/";
    private static final String CONTENT_FILE = "content.bin";
    private static final String METADATA_FILE = "metadata.properties";
    private static final int MAX_ARTIFACT_BYTES = 100 * 1024 * 1024;
    private final Path root;
    private final Duration retention;
    private final ArtifactSecurityScanner scanner;
    private final Clock clock;

    public LocalExternalArtifactStore(Path root) {
        this(root, Duration.ofDays(30), new BasicArtifactSecurityScanner(), Clock.systemUTC());
    }

    public LocalExternalArtifactStore(Path root, Duration retention, ArtifactSecurityScanner scanner) {
        this(root, retention, scanner, Clock.systemUTC());
    }

    LocalExternalArtifactStore(Path root, Duration retention, ArtifactSecurityScanner scanner, Clock clock) {
        if (root == null) {
            throw new IllegalArgumentException("external artifact root must not be null");
        }
        if (retention == null || retention.isNegative() || retention.isZero() || scanner == null || clock == null) {
            throw new IllegalArgumentException("artifact retention, scanner and clock are required");
        }
        this.root = root.toAbsolutePath().normalize();
        this.retention = retention;
        this.scanner = scanner;
        this.clock = clock;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create external artifact root: " + this.root, e);
        }
    }

    @Override
    public ExternalArtifactReference store(byte[] content, String fileName, String mediaType) {
        return storeForPrincipal(content, fileName, mediaType, "system");
    }

    @Override
    public ExternalArtifactReference storeForPrincipal(byte[] content, String fileName, String mediaType,
                                                       String ownerPrincipalId) {
        if (content == null || content.length == 0 || content.length > MAX_ARTIFACT_BYTES) {
            throw new IllegalArgumentException("external artifact content must contain 1 to 104857600 bytes");
        }
        String owner = requireOwnerPrincipalId(ownerPrincipalId);
        String safeFileName = requireFileName(fileName);
        String safeMediaType = requireMediaType(mediaType);
        scanner.scan(content, safeFileName, safeMediaType);
        String id = UUID.randomUUID().toString();
        Path temporary = root.resolve("." + id + ".tmp");
        Path completed = root.resolve(id);
        try {
            Files.createDirectory(temporary);
            Files.write(temporary.resolve(CONTENT_FILE), content);
            String sha256 = sha256(content);
            Properties metadata = new Properties();
            metadata.setProperty("fileName", safeFileName);
            metadata.setProperty("mediaType", safeMediaType);
            metadata.setProperty("size", String.valueOf(content.length));
            metadata.setProperty("sha256", sha256);
            metadata.setProperty("ownerPrincipalId", owner);
            Instant createdAt = clock.instant();
            metadata.setProperty("createdAt", createdAt.toString());
            metadata.setProperty("expiresAt", createdAt.plus(retention).toString());
            try (OutputStream output = Files.newOutputStream(temporary.resolve(METADATA_FILE))) {
                metadata.store(output, "omni-office external artifact");
            }
            moveCompleted(temporary, completed);
            return reference(id, metadata);
        } catch (IOException e) {
            deleteTreeQuietly(temporary);
            throw new IllegalStateException("failed to store external artifact", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ExternalArtifactReference storeForPrincipal(Path contentPath, String fileName, String mediaType,
                                                       String ownerPrincipalId) {
        if (contentPath == null || !Files.isRegularFile(contentPath)) {
            throw new IllegalArgumentException("external artifact file is invalid");
        }
        final long size;
        try {
            size = Files.size(contentPath);
        } catch (IOException e) {
            throw new IllegalStateException("failed to inspect external artifact file", e);
        }
        if (size < 1 || size > MAX_ARTIFACT_BYTES) {
            throw new IllegalArgumentException("external artifact file size is invalid");
        }
        String owner = requireOwnerPrincipalId(ownerPrincipalId);
        String safeFileName = requireFileName(fileName);
        String safeMediaType = requireMediaType(mediaType);
        scanner.scan(contentPath, safeFileName, safeMediaType);
        String id = UUID.randomUUID().toString();
        Path temporary = root.resolve("." + id + ".tmp");
        Path completed = root.resolve(id);
        try {
            Files.createDirectory(temporary);
            Files.copy(contentPath, temporary.resolve(CONTENT_FILE));
            Properties metadata = new Properties();
            metadata.setProperty("fileName", safeFileName);
            metadata.setProperty("mediaType", safeMediaType);
            metadata.setProperty("size", String.valueOf(size));
            metadata.setProperty("sha256", sha256(contentPath));
            metadata.setProperty("ownerPrincipalId", owner);
            Instant createdAt = clock.instant();
            metadata.setProperty("createdAt", createdAt.toString());
            metadata.setProperty("expiresAt", createdAt.plus(retention).toString());
            try (OutputStream output = Files.newOutputStream(temporary.resolve(METADATA_FILE))) {
                metadata.store(output, "omni-office external artifact");
            }
            moveCompleted(temporary, completed);
            return reference(id, metadata);
        } catch (IOException | RuntimeException e) {
            deleteTreeQuietly(temporary);
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new IllegalStateException("failed to store external artifact file", e);
        }
    }

    @Override
    public ResolvedExternalArtifact resolve(String resourceUri) {
        String id = requireResourceId(resourceUri);
        Path directory = root.resolve(id).normalize();
        if (!root.equals(directory.getParent())) {
            throw new IllegalArgumentException("invalid external artifact resource URI");
        }
        Path content = directory.resolve(CONTENT_FILE);
        Path metadataPath = directory.resolve(METADATA_FILE);
        if (!Files.isRegularFile(content) || !Files.isRegularFile(metadataPath)) {
            throw new IllegalArgumentException("external artifact does not exist: " + id);
        }
        try (InputStream input = Files.newInputStream(metadataPath)) {
            Properties metadata = new Properties();
            metadata.load(input);
            ExternalArtifactReference reference = reference(id, metadata);
            if (reference.getExpiresAt() != null && !reference.getExpiresAt().isAfter(clock.instant())) {
                deleteTreeQuietly(directory);
                throw new IllegalArgumentException("external artifact has expired: " + id);
            }
            if (Files.size(content) != reference.getSize()) {
                throw new IllegalStateException("external artifact size does not match metadata: " + id);
            }
            return new ResolvedExternalArtifact(reference, content);
        } catch (IOException e) {
            throw new IllegalStateException("failed to resolve external artifact: " + id, e);
        }
    }

    public Path getRoot() {
        return root;
    }

    @Override
    public List<ExternalArtifactReference> list() {
        List<ExternalArtifactReference> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(Files::isDirectory).forEach(path -> {
                try {
                    result.add(resolve(URI_PREFIX + path.getFileName()).getReference());
                } catch (RuntimeException ignored) {
                    // Expired or incomplete entries are deliberately omitted.
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("failed to list external artifacts", e);
        }
        result.sort(Comparator.comparing(ExternalArtifactReference::getArtifactId));
        return Collections.unmodifiableList(result);
    }

    @Override
    public boolean delete(String resourceUri) {
        String id = requireResourceId(resourceUri);
        Path directory = root.resolve(id).normalize();
        if (!root.equals(directory.getParent()) || !Files.exists(directory)) {
            return false;
        }
        deleteTreeQuietly(directory);
        return !Files.exists(directory);
    }

    @Override
    public int purgeExpired(Instant now) {
        if (now == null) throw new IllegalArgumentException("artifact purge time is required");
        int purged = 0;
        List<ExternalArtifactReference> snapshots = listIncludingExpired();
        for (ExternalArtifactReference value : snapshots) {
            if (value.getExpiresAt() != null && !value.getExpiresAt().isAfter(now)
                    && delete(value.getResourceUri())) {
                purged++;
            }
        }
        return purged;
    }

    private ExternalArtifactReference reference(String id, Properties metadata) {
        String fileName = requireFileName(metadata.getProperty("fileName"));
        String mediaType = requireMediaType(metadata.getProperty("mediaType"));
        String sha256 = metadata.getProperty("sha256");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("invalid external artifact checksum metadata");
        }
        final long size;
        try {
            size = Long.parseLong(metadata.getProperty("size"));
        } catch (RuntimeException e) {
            throw new IllegalStateException("invalid external artifact size metadata", e);
        }
        Instant createdAt = instant(metadata.getProperty("createdAt"), null);
        Instant expiresAt = instant(metadata.getProperty("expiresAt"),
                createdAt == null ? null : createdAt.plus(retention));
        String ownerPrincipalId = metadata.getProperty("ownerPrincipalId");
        if (ownerPrincipalId != null) ownerPrincipalId = requireOwnerPrincipalId(ownerPrincipalId);
        return new ExternalArtifactReference(id, URI_PREFIX + id, fileName, mediaType, size, sha256,
                createdAt, expiresAt, ownerPrincipalId);
    }

    private List<ExternalArtifactReference> listIncludingExpired() {
        List<ExternalArtifactReference> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(Files::isDirectory).forEach(directory -> {
                Path metadataPath = directory.resolve(METADATA_FILE);
                if (!Files.isRegularFile(metadataPath)) return;
                try (InputStream input = Files.newInputStream(metadataPath)) {
                    Properties metadata = new Properties();
                    metadata.load(input);
                    result.add(reference(directory.getFileName().toString(), metadata));
                } catch (IOException | RuntimeException ignored) {
                    // Incomplete artifacts are not treated as published lifecycle entries.
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("failed to inspect external artifacts", e);
        }
        return result;
    }

    private Instant instant(String value, Instant fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            throw new IllegalStateException("invalid external artifact time metadata", e);
        }
    }

    private String requireResourceId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("external artifact resource URI must not be null");
        }
        try {
            URI uri = URI.create(value);
            if (!"omni-office".equals(uri.getScheme()) || !"artifacts".equals(uri.getHost())
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("invalid external artifact resource URI");
            }
            String path = uri.getPath();
            if (path == null || path.length() < 2 || path.indexOf('/', 1) >= 0) {
                throw new IllegalArgumentException("invalid external artifact resource URI");
            }
            return UUID.fromString(path.substring(1)).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid external artifact resource URI", e);
        }
    }

    private String requireFileName(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("external artifact file name is invalid");
        }
        return value;
    }

    private String requireMediaType(String value) {
        if (value == null || !value.matches("[a-z0-9.+-]+/[a-zA-Z0-9.+-]+")) {
            throw new IllegalArgumentException("external artifact media type is invalid");
        }
        return value;
    }

    private String requireOwnerPrincipalId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("external artifact owner principal id is invalid");
        }
        return value;
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String sha256(Path contentPath) throws IOException {
        try (InputStream input = Files.newInputStream(contentPath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
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
