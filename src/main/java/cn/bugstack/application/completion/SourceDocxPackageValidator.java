package cn.bugstack.application.completion;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 在 Aspose 加载上传文件前限制 OOXML ZIP 的解压规模和外部关系。 */
final class SourceDocxPackageValidator {

    private static final long MAX_UNCOMPRESSED_BYTES = 300L * 1024 * 1024;
    private static final int MAX_ENTRIES = 20_000;
    private static final int MAX_RELATIONSHIP_BYTES = 2_000_000;

    /** 拒绝伪装扩展名、压缩炸弹、路径穿越和危险外部关系。 */
    void validate(Path source) {
        Set<String> names = new HashSet<>();
        long total = 0;
        int count = 0;
        try (InputStream input = Files.newInputStream(source);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                count++;
                String name = entry.getName();
                if (count > MAX_ENTRIES || !safeEntryName(name)
                        || !names.add(name.replace('\\', '/'))) {
                    throw new IllegalArgumentException("unsafe DOCX ZIP structure");
                }
                ByteArrayOutputStream relationships = name.endsWith(".rels")
                        ? new ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED_BYTES) {
                        throw new IllegalArgumentException("DOCX exceeds safe uncompressed size");
                    }
                    if (relationships != null) {
                        if (relationships.size() + read > MAX_RELATIONSHIP_BYTES) {
                            throw new IllegalArgumentException("DOCX relationship part is too large");
                        }
                        relationships.write(buffer, 0, read);
                    }
                }
                if (relationships != null) validateRelationships(relationships.toByteArray());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("DOCX ZIP cannot be inspected", e);
        }
        if (!names.contains("[Content_Types].xml") || !names.contains("word/document.xml")) {
            throw new IllegalArgumentException("file is not a DOCX package");
        }
    }

    private boolean safeEntryName(String name) {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.indexOf('\0') >= 0) return false;
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/")) return false;
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment) || ".".equals(segment)) return false;
        }
        return true;
    }

    private void validateRelationships(byte[] xml) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            try {
                while (reader.hasNext()) {
                    if (reader.next() != XMLStreamConstants.START_ELEMENT
                            || !"Relationship".equals(reader.getLocalName())) continue;
                    String mode = reader.getAttributeValue(null, "TargetMode");
                    if (!"External".equalsIgnoreCase(mode)) continue;
                    String type = reader.getAttributeValue(null, "Type");
                    String target = reader.getAttributeValue(null, "Target");
                    if (!safeExternalHyperlink(type, target)) {
                        throw new IllegalArgumentException("DOCX contains an unsafe external relationship");
                    }
                }
            } finally {
                reader.close();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("DOCX relationship part cannot be inspected", e);
        }
    }

    private boolean safeExternalHyperlink(String type, String target) {
        if (type == null || !type.endsWith("/hyperlink") || target == null) return false;
        try {
            String scheme = URI.create(target.trim()).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                    || "mailto".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
