package cn.bugstack.application.external.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 产物发布前的安全扫描 SPI；发现问题应抛出 ArtifactSecurityException。 */
public interface ArtifactSecurityScanner {

    void scan(byte[] content, String fileName, String mediaType);

    /** 扫描文件型工件；旧实现默认回退到字节扫描。 */
    default void scan(Path contentPath, String fileName, String mediaType) {
        try {
            scan(Files.readAllBytes(contentPath), fileName, mediaType);
        } catch (IOException e) {
            throw new ArtifactSecurityException("artifact file cannot be scanned", e);
        }
    }
}
