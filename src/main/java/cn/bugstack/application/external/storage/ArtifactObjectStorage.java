package cn.bugstack.application.external.storage;

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

    /**
     * 读取对象。
     *
     * @param key 受控对象键
     * @return 对象内容
     */
    byte[] get(String key);

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
