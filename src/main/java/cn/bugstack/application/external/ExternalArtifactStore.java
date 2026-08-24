package cn.bugstack.application.external;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * 保存并绑定到指定调用主体。自定义旧实现可继续使用三参数方法，但生产实现应覆盖本方法并持久化所有者。
     *
     * @param content 工件内容
     * @param fileName 下载文件名
     * @param mediaType MIME 类型
     * @param ownerPrincipalId 所属主体 ID
     * @return 工件引用
     */
    default ExternalArtifactReference storeForPrincipal(byte[] content, String fileName, String mediaType,
                                                        String ownerPrincipalId) {
        return store(content, fileName, mediaType);
    }

    /** 保存文件型工件；生产实现应覆盖本方法以避免大文件整体进入堆内存。 */
    default ExternalArtifactReference storeForPrincipal(Path contentPath, String fileName, String mediaType,
                                                        String ownerPrincipalId) {
        try {
            return storeForPrincipal(Files.readAllBytes(contentPath), fileName, mediaType, ownerPrincipalId);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read artifact file", e);
        }
    }

    /**
     * 解析受控资源 URI。
     *
     * @param resourceUri 工件资源 URI
     * @return 已校验完整性的工件
     */
    ResolvedExternalArtifact resolve(String resourceUri);

    /**
     * 按主体授权解析工件。没有所有者元数据的历史工件只允许跨主体管理员读取。
     *
     * @param resourceUri 工件资源 URI
     * @param principalId 当前主体 ID
     * @param allowAny 是否具有租户内跨主体读取权限
     * @return 已授权工件
     */
    default ResolvedExternalArtifact resolveForPrincipal(String resourceUri, String principalId,
                                                         boolean allowAny) {
        if (principalId == null || !principalId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("artifact principal id is invalid");
        }
        ResolvedExternalArtifact artifact = resolve(resourceUri);
        String owner = artifact.getReference().getOwnerPrincipalId();
        if (!allowAny && (owner == null || !principalId.equals(owner))) {
            throw new IllegalArgumentException("external artifact does not exist");
        }
        return artifact;
    }

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
