package cn.bugstack.application.external.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 校验文件签名、ZIP 解压规模、重复条目、路径穿越和 OOXML 外部关系。 */
public final class BasicArtifactSecurityScanner implements ArtifactSecurityScanner {

    private static final long MAX_UNCOMPRESSED_BYTES = 300L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 20_000;

    @Override
    public void scan(byte[] content, String fileName, String mediaType) {
        if (content == null || content.length == 0) throw new ArtifactSecurityException("artifact is empty");
        if ("application/pdf".equals(mediaType)) requirePrefix(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
        if ("image/png".equals(mediaType)) requirePrefix(content,
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
        if (mediaType.contains("openxmlformats") || mediaType.contains("visio")) scanZip(content);
        if ("text/html".equals(mediaType)) {
            String prefix = new String(content, 0, Math.min(content.length, 256), StandardCharsets.UTF_8)
                    .trim().toLowerCase();
            if (!prefix.startsWith("<!doctype html") && !prefix.startsWith("<html")) {
                throw new ArtifactSecurityException("HTML artifact has an invalid signature");
            }
        }
    }

    private void scanZip(byte[] content) {
        requirePrefix(content, new byte[]{'P', 'K'});
        Set<String> names = new HashSet<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                count++;
                String name = entry.getName();
                if (count > MAX_ZIP_ENTRIES || name.startsWith("/") || name.contains("../") || !names.add(name)) {
                    throw new ArtifactSecurityException("unsafe ZIP artifact structure");
                }
                java.io.ByteArrayOutputStream relationship = name.endsWith(".rels")
                        ? new java.io.ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED_BYTES) {
                        throw new ArtifactSecurityException("artifact exceeds safe uncompressed size");
                    }
                    if (relationship != null && relationship.size() < 2_000_000) relationship.write(buffer, 0, read);
                }
                if (relationship != null && relationship.toString(StandardCharsets.UTF_8)
                        .contains("TargetMode=\"External\"")) {
                    throw new ArtifactSecurityException("OOXML artifact contains an external relationship");
                }
            }
        } catch (IOException e) {
            throw new ArtifactSecurityException("artifact ZIP cannot be inspected", e);
        }
    }

    private void requirePrefix(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) throw new ArtifactSecurityException("artifact signature is invalid");
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) throw new ArtifactSecurityException("artifact signature is invalid");
        }
    }
}
