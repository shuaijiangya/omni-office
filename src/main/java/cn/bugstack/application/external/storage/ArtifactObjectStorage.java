package cn.bugstack.application.external.storage;

import java.util.List;

/** S3、OSS、MinIO 等对象存储的最小适配接口。 */
public interface ArtifactObjectStorage {

    void put(String key, byte[] content, String mediaType);
    byte[] get(String key);
    boolean delete(String key);
    List<String> list(String prefix);
}
