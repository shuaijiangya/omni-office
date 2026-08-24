package cn.bugstack.application.external.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

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
        if ("image/jpeg".equals(mediaType)) requirePrefix(content,
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        if (mediaType.contains("openxmlformats") || mediaType.contains("visio")) scanZip(content);
        if ("text/html".equals(mediaType)) {
            String prefix = new String(content, 0, Math.min(content.length, 256), StandardCharsets.UTF_8)
                    .trim().toLowerCase();
            if (!prefix.startsWith("<!doctype html") && !prefix.startsWith("<html")) {
                throw new ArtifactSecurityException("HTML artifact has an invalid signature");
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void scan(Path contentPath, String fileName, String mediaType) {
        if (contentPath == null || !Files.isRegularFile(contentPath)) {
            throw new ArtifactSecurityException("artifact file is invalid");
        }
        try {
            if ("application/pdf".equals(mediaType)) requireFilePrefix(contentPath,
                    "%PDF-".getBytes(StandardCharsets.US_ASCII));
            if ("image/png".equals(mediaType)) requireFilePrefix(contentPath,
                    new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
            if ("image/jpeg".equals(mediaType)) requireFilePrefix(contentPath,
                    new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            if (mediaType.contains("openxmlformats") || mediaType.contains("visio")) {
                try (java.io.InputStream input = Files.newInputStream(contentPath)) {
                    scanZip(input);
                }
            }
            if ("text/html".equals(mediaType)) {
                byte[] prefixBytes = new byte[256];
                int read;
                try (java.io.InputStream input = Files.newInputStream(contentPath)) {
                    read = input.read(prefixBytes);
                }
                String prefix = new String(prefixBytes, 0, Math.max(0, read), StandardCharsets.UTF_8)
                        .trim().toLowerCase();
                if (!prefix.startsWith("<!doctype html") && !prefix.startsWith("<html")) {
                    throw new ArtifactSecurityException("HTML artifact has an invalid signature");
                }
            }
        } catch (IOException e) {
            throw new ArtifactSecurityException("artifact file cannot be inspected", e);
        }
    }

    private void scanZip(byte[] content) {
        requirePrefix(content, new byte[]{'P', 'K'});
        scanZip(new ByteArrayInputStream(content));
    }

    private void scanZip(java.io.InputStream input) {
        Set<String> names = new HashSet<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
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
                    if (relationship != null) {
                        if (relationship.size() + read > 2_000_000) {
                            throw new ArtifactSecurityException("OOXML relationship part is too large");
                        }
                        relationship.write(buffer, 0, read);
                    }
                }
                if (relationship != null) scanRelationships(relationship.toByteArray());
            }
        } catch (IOException e) {
            throw new ArtifactSecurityException("artifact ZIP cannot be inspected", e);
        }
    }

    private void scanRelationships(byte[] content) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(content));
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT
                        || !"Relationship".equals(reader.getLocalName())) continue;
                String mode = reader.getAttributeValue(null, "TargetMode");
                if (!"External".equalsIgnoreCase(mode)) continue;
                String type = reader.getAttributeValue(null, "Type");
                String target = reader.getAttributeValue(null, "Target");
                if (!isAllowedExternalHyperlink(type, target)) {
                    throw new ArtifactSecurityException("OOXML artifact contains an unsafe external relationship");
                }
            }
            reader.close();
        } catch (ArtifactSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new ArtifactSecurityException("OOXML relationship part cannot be inspected", e);
        }
    }

    private boolean isAllowedExternalHyperlink(String type, String target) {
        if (type == null || !type.endsWith("/hyperlink") || target == null) return false;
        try {
            String scheme = URI.create(target.trim()).getScheme();
            return "http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme)
                    || "mailto".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void requireFilePrefix(Path contentPath, byte[] prefix) throws IOException {
        byte[] actual = new byte[prefix.length];
        int read;
        try (java.io.InputStream input = Files.newInputStream(contentPath)) {
            read = input.read(actual);
        }
        if (read != prefix.length) throw new ArtifactSecurityException("artifact signature is invalid");
        requirePrefix(actual, prefix);
    }

    private void requirePrefix(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) throw new ArtifactSecurityException("artifact signature is invalid");
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) throw new ArtifactSecurityException("artifact signature is invalid");
        }
    }
}
