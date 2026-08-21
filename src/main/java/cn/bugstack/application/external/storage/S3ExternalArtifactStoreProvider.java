package cn.bugstack.application.external.storage;

import cn.bugstack.application.external.ExternalArtifactStore;
import cn.bugstack.application.external.ExternalArtifactStoreProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/** 共享 S3 客户端，并为每个租户建立独立键前缀和本地读取缓存。 */
public final class S3ExternalArtifactStoreProvider implements ExternalArtifactStoreProvider {

    private final S3Client client;
    private final String bucket;
    private final String rootPrefix;
    private final Duration retention;

    /**
     * 创建 S3/MinIO 工件库提供器。
     *
     * @param bucket Bucket 名称
     * @param region AWS 区域
     * @param rootPrefix 服务级对象键前缀
     * @param endpoint 可选的 S3 兼容端点
     * @param accessKey 可选静态访问密钥；为空时使用 AWS 默认凭证链
     * @param secretKey 与访问密钥配套的秘密密钥
     * @param pathStyle 是否启用 Path-style 访问
     * @param retention 工件保留时间
     */
    public S3ExternalArtifactStoreProvider(String bucket, String region, String rootPrefix,
                                           URI endpoint, String accessKey, String secretKey,
                                           boolean pathStyle, Duration retention) {
        if (bucket == null || !bucket.matches("[A-Za-z0-9.-]{3,255}")
                || region == null || region.isBlank() || retention == null
                || retention.isZero() || retention.isNegative()
                || ((accessKey == null) != (secretKey == null))
                || (accessKey != null && (accessKey.isBlank() || secretKey.isBlank()))) {
            throw new IllegalArgumentException("S3 artifact provider configuration is invalid");
        }
        validateEndpoint(endpoint);
        software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(3)).build())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .chunkedEncodingEnabled(false).build());
        if (endpoint != null) builder.endpointOverride(endpoint);
        if (accessKey != null) builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)));
        this.client = builder.build();
        this.bucket = bucket;
        this.rootPrefix = rootPrefix == null ? "omni-office" : rootPrefix;
        this.retention = retention;
    }

    /** {@inheritDoc} */
    @Override
    public ExternalArtifactStore store(String tenantId, Path tenantRoot) {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("tenant id is invalid");
        }
        S3ArtifactObjectStorage storage = new S3ArtifactObjectStorage(
                client, bucket, rootPrefix + "/tenants/" + tenantId);
        return new ObjectStorageExternalArtifactStore(storage,
                tenantRoot.resolve("object-cache"), retention);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReady() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        client.close();
    }

    private static void validateEndpoint(URI endpoint) {
        if (endpoint == null) return;
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("S3 endpoint is invalid");
        }
        if ("https".equalsIgnoreCase(endpoint.getScheme())) return;
        String host = endpoint.getHost();
        boolean loopback = "http".equalsIgnoreCase(endpoint.getScheme())
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "minio".equalsIgnoreCase(host));
        if (!loopback) throw new IllegalArgumentException("S3 endpoint must use HTTPS");
    }
}
