package cn.bugstack.application.external.storage;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.ArrayList;
import java.util.List;

/** AWS S3、MinIO 及兼容服务的生产对象适配器。 */
public final class S3ArtifactObjectStorage implements ArtifactObjectStorage {

    private final S3Client client;
    private final String bucket;
    private final String prefix;

    /**
     * 创建 S3 对象存储适配器。
     *
     * @param client 已配置的 S3 客户端
     * @param bucket Bucket 名称
     * @param prefix 所有对象使用的受控键前缀
     */
    public S3ArtifactObjectStorage(S3Client client, String bucket, String prefix) {
        if (client == null || bucket == null || !bucket.matches("[A-Za-z0-9.-]{3,255}")) {
            throw new IllegalArgumentException("S3 artifact storage configuration is invalid");
        }
        this.client = client;
        this.bucket = bucket;
        this.prefix = normalizePrefix(prefix);
    }

    /** {@inheritDoc} */
    @Override
    public void put(String key, byte[] content, String mediaType) {
        if (content == null || mediaType == null) throw new IllegalArgumentException("S3 object is invalid");
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(fullKey(key))
                        .contentType(mediaType).contentLength((long) content.length).build(),
                RequestBody.fromBytes(content));
    }

    /** {@inheritDoc} */
    @Override
    public byte[] get(String key) {
        ResponseBytes<GetObjectResponse> value = client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(fullKey(key)).build(),
                ResponseTransformer.toBytes());
        return value.asByteArray();
    }

    /** {@inheritDoc} */
    @Override
    public boolean delete(String key) {
        String objectKey = fullKey(key);
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> list(String keyPrefix) {
        String requestedPrefix = fullKey(keyPrefix);
        List<String> result = new ArrayList<>();
        client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket)
                        .prefix(requestedPrefix).build())
                .contents().forEach(item -> {
                    String key = item.key();
                    if (key.startsWith(prefix)) result.add(key.substring(prefix.length()));
                });
        return result;
    }

    private String fullKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")
                || !key.matches("[A-Za-z0-9._/-]{1,512}")) {
            throw new IllegalArgumentException("S3 artifact key is invalid");
        }
        return prefix + key;
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) return "";
        String prefix = value.trim().replaceAll("^/+|/+$", "");
        if (prefix.isEmpty()) return "";
        if (prefix.contains("..") || !prefix.matches("[A-Za-z0-9._/-]{1,384}")) {
            throw new IllegalArgumentException("S3 artifact prefix is invalid");
        }
        return prefix + "/";
    }
}
