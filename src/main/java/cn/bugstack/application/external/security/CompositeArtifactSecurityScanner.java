package cn.bugstack.application.external.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 顺序执行全部产物扫描器。 */
public final class CompositeArtifactSecurityScanner implements ArtifactSecurityScanner {

    private final List<ArtifactSecurityScanner> scanners;

    public CompositeArtifactSecurityScanner(ArtifactSecurityScanner... scanners) {
        this.scanners = new ArrayList<>(Arrays.asList(scanners));
        if (this.scanners.isEmpty() || this.scanners.contains(null)) {
            throw new IllegalArgumentException("at least one non-null artifact scanner is required");
        }
    }

    @Override
    public void scan(byte[] content, String fileName, String mediaType) {
        scanners.forEach(scanner -> scanner.scan(content, fileName, mediaType));
    }
}
