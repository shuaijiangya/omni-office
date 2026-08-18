package cn.bugstack.application.external.storage;

import cn.bugstack.application.external.ExternalArtifactReference;
import cn.bugstack.application.external.ExternalArtifactStore;
import cn.bugstack.application.external.ResolvedExternalArtifact;
import cn.bugstack.application.external.security.ArtifactSecurityScanner;
import cn.bugstack.application.external.security.BasicArtifactSecurityScanner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 将受控工件存储到对象存储，并只在服务端缓存读取副本。 */
public final class ObjectStorageExternalArtifactStore implements ExternalArtifactStore {

    private static final String URI_PREFIX = "omni-office://artifacts/";
    private final ArtifactObjectStorage storage;
    private final Path cacheRoot;
    private final Duration retention;
    private final ArtifactSecurityScanner scanner;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ObjectStorageExternalArtifactStore(ArtifactObjectStorage storage, Path cacheRoot,
                                              Duration retention) {
        this(storage, cacheRoot, retention, new BasicArtifactSecurityScanner(), Clock.systemUTC());
    }

    ObjectStorageExternalArtifactStore(ArtifactObjectStorage storage, Path cacheRoot, Duration retention,
                                       ArtifactSecurityScanner scanner, Clock clock) {
        if (storage == null || cacheRoot == null || retention == null || retention.isNegative()
                || retention.isZero() || scanner == null || clock == null) {
            throw new IllegalArgumentException("object artifact store dependencies are required");
        }
        this.storage = storage;
        this.cacheRoot = cacheRoot.toAbsolutePath().normalize();
        this.retention = retention;
        this.scanner = scanner;
        this.clock = clock;
        try { Files.createDirectories(this.cacheRoot); }
        catch (IOException e) { throw new IllegalStateException("failed to create object cache", e); }
    }

    @Override
    public ExternalArtifactReference store(byte[] content, String fileName, String mediaType) {
        if (content == null || content.length == 0 || content.length > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("artifact content size is invalid");
        }
        if (fileName == null || !fileName.matches("[A-Za-z0-9._-]{1,128}")
                || mediaType == null || !mediaType.matches("[a-z0-9.+-]+/[a-zA-Z0-9.+-]+")) {
            throw new IllegalArgumentException("artifact metadata is invalid");
        }
        scanner.scan(content, fileName, mediaType);
        String id = UUID.randomUUID().toString();
        Instant created = clock.instant();
        Metadata metadata = new Metadata(id, fileName, mediaType, content.length, sha256(content),
                created, created.plus(retention));
        try {
            storage.put(contentKey(id), content, mediaType);
            storage.put(metadataKey(id), mapper.writeValueAsBytes(metadata), "application/json");
            return reference(metadata);
        } catch (IOException | RuntimeException e) {
            storage.delete(contentKey(id));
            storage.delete(metadataKey(id));
            throw new IllegalStateException("failed to store object artifact", e);
        }
    }

    @Override
    public ResolvedExternalArtifact resolve(String resourceUri) {
        String id = id(resourceUri);
        Metadata metadata = metadata(id);
        if (!metadata.expiresAt.isAfter(clock.instant())) {
            delete(resourceUri);
            throw new IllegalArgumentException("artifact expired");
        }
        byte[] content = storage.get(contentKey(id));
        if (content.length != metadata.size || !sha256(content).equals(metadata.sha256)) {
            throw new IllegalStateException("object artifact integrity check failed");
        }
        Path cache = cacheRoot.resolve(id + ".bin").normalize();
        if (!cacheRoot.equals(cache.getParent())) throw new IllegalStateException("object cache path escaped root");
        try { Files.write(cache, content); }
        catch (IOException e) { throw new IllegalStateException("failed to cache object artifact", e); }
        return new ResolvedExternalArtifact(reference(metadata), cache);
    }

    @Override
    public List<ExternalArtifactReference> list() {
        List<ExternalArtifactReference> result = new ArrayList<>();
        for (String key : storage.list("artifacts/")) {
            if (key.endsWith("/metadata.json")) {
                try {
                    Metadata value = mapper.readValue(storage.get(key), Metadata.class);
                    if (value.expiresAt.isAfter(clock.instant())) result.add(reference(value));
                } catch (IOException | RuntimeException ignored) { }
            }
        }
        result.sort(Comparator.comparing(ExternalArtifactReference::getArtifactId));
        return Collections.unmodifiableList(result);
    }

    @Override
    public boolean delete(String resourceUri) {
        String id = id(resourceUri);
        boolean deleted = storage.delete(contentKey(id));
        deleted |= storage.delete(metadataKey(id));
        try { Files.deleteIfExists(cacheRoot.resolve(id + ".bin")); } catch (IOException ignored) { }
        return deleted;
    }

    @Override
    public int purgeExpired(Instant now) {
        int count = 0;
        for (String key : storage.list("artifacts/")) {
            if (!key.endsWith("/metadata.json")) continue;
            try {
                Metadata value = mapper.readValue(storage.get(key), Metadata.class);
                if (!value.expiresAt.isAfter(now) && delete(URI_PREFIX + value.id)) count++;
            } catch (IOException | RuntimeException ignored) { }
        }
        return count;
    }

    private Metadata metadata(String id) {
        try { return mapper.readValue(storage.get(metadataKey(id)), Metadata.class); }
        catch (IOException e) { throw new IllegalStateException("invalid object artifact metadata", e); }
    }

    private ExternalArtifactReference reference(Metadata value) {
        return new ExternalArtifactReference(value.id, URI_PREFIX + value.id, value.fileName, value.mediaType,
                value.size, value.sha256, value.createdAt, value.expiresAt);
    }

    private String id(String uriValue) {
        try {
            URI uri = URI.create(uriValue);
            if (!"omni-office".equals(uri.getScheme()) || !"artifacts".equals(uri.getHost())
                    || uri.getPath() == null || uri.getPath().indexOf('/', 1) >= 0) throw new IllegalArgumentException();
            return UUID.fromString(uri.getPath().substring(1)).toString();
        } catch (RuntimeException e) { throw new IllegalArgumentException("invalid artifact resource URI", e); }
    }

    private String contentKey(String id) { return "artifacts/" + id + "/content.bin"; }
    private String metadataKey(String id) { return "artifacts/" + id + "/metadata.json"; }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException("SHA-256 is not available", e); }
    }

    public static final class Metadata {
        public String id;
        public String fileName;
        public String mediaType;
        public long size;
        public String sha256;
        public Instant createdAt;
        public Instant expiresAt;
        public Metadata() { }
        Metadata(String id, String fileName, String mediaType, long size, String sha256,
                 Instant createdAt, Instant expiresAt) {
            this.id = id; this.fileName = fileName; this.mediaType = mediaType; this.size = size;
            this.sha256 = sha256; this.createdAt = createdAt; this.expiresAt = expiresAt;
        }
    }
}
