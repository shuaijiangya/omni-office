package cn.bugstack.application.external.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** S3、OSS、MinIO 等对象存储的最小适配接口。 */
public interface ArtifactObjectStorage {

    /**
     * 写入对象。
     *
     * @param key 受控对象键
     * @param content 对象内容
     * @param mediaType MIME 类型
     */
    void put(String key, byte[] content, String mediaType);

    /** 文件型上传；远程实现应覆盖以使用 SDK 的文件 RequestBody。 */
    default void put(String key, Path contentPath, String mediaType) {
        try {
            put(key, Files.readAllBytes(contentPath), mediaType);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read object content file", e);
        }
    }

    /**
     * 读取对象。
     *
     * @param key 受控对象键
     * @return 对象内容
     */
    byte[] get(String key);

    /** 将对象读取到受控文件；远程实现应覆盖以避免完整对象进入堆内存。 */
    default void get(String key, Path destination) {
        try {
            Files.write(destination, get(key));
        } catch (IOException e) {
            throw new IllegalStateException("failed to write object content file", e);
        }
    }

    /**
     * 删除对象。
     *
     * @param key 受控对象键
     * @return 对象存在并成功删除时返回 {@code true}
     */
    boolean delete(String key);

    /**
     * 列出指定前缀下的对象键。
     *
     * @param prefix 对象键前缀
     * @return 匹配的对象键
     */
    List<String> list(String prefix);
}
