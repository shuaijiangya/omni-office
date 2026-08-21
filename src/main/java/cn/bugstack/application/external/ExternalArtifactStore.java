package cn.bugstack.application.external;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** 外部生成结果的受控工件存储 SPI。 */
public interface ExternalArtifactStore {

    /**
     * 保存经过安全扫描的工件。
     *
     * @param content 工件内容
     * @param fileName 下载文件名
     * @param mediaType MIME 类型
     * @return 不包含服务端路径的工件引用
     */
    ExternalArtifactReference store(byte[] content, String fileName, String mediaType);

    /**
     * 解析受控资源 URI。
     *
     * @param resourceUri 工件资源 URI
     * @return 已校验完整性的工件
     */
    ResolvedExternalArtifact resolve(String resourceUri);

    /** @return 当前工件库中尚未过期的工件引用 */
    default List<ExternalArtifactReference> list() {
        return Collections.emptyList();
    }

    /**
     * 删除工件。
     *
     * @param resourceUri 工件资源 URI
     * @return 工件存在并删除成功时返回 {@code true}
     */
    default boolean delete(String resourceUri) {
        throw new UnsupportedOperationException("artifact deletion is not supported");
    }

    /**
     * 清理过期工件。
     *
     * @param now 当前时刻
     * @return 删除的工件数量
     */
    default int purgeExpired(Instant now) {
        return 0;
    }
}
