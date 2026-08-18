package cn.bugstack.application.external.security;

/** 产物发布前的安全扫描 SPI；发现问题应抛出 ArtifactSecurityException。 */
public interface ArtifactSecurityScanner {

    void scan(byte[] content, String fileName, String mediaType);
}
