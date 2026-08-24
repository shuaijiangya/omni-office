package cn.bugstack.application.template.governance;

import cn.bugstack.application.document.DynamicDocumentExporter;
import cn.bugstack.application.template.TemplateDocumentAssembler;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.template.DocumentTemplateSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 使用审核样例数据执行模板展开、DOCX 真实渲染和 OOXML 回读的发布门禁。 */
public final class TemplatePublicationGate {

    private final TemplateDocumentAssembler assembler;
    private final DynamicDocumentExporter exporter;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TemplatePublicationGate(DynamicDocumentExporter exporter) {
        this(new TemplateDocumentAssembler(), exporter, new ObjectMapper(), Clock.systemUTC());
    }

    TemplatePublicationGate(TemplateDocumentAssembler assembler, DynamicDocumentExporter exporter,
                            ObjectMapper mapper, Clock clock) {
        if (assembler == null || exporter == null || mapper == null || clock == null) {
            throw new IllegalArgumentException("template publication gate dependencies are required");
        }
        this.assembler = assembler;
        this.exporter = exporter;
        this.mapper = mapper.copy();
        this.clock = clock;
    }

    /** 执行发布门禁并返回可持久化验证证据。 */
    public TemplatePublicationEvidence validate(DocumentTemplateSpec template, JsonNode sampleData) {
        if (template == null || sampleData == null || !sampleData.isObject()) {
            throw new IllegalArgumentException("template publication requires object sampleData");
        }
        DocumentSpec document = assembler.assemble(template, sampleData);
        Path output = null;
        try {
            output = Files.createTempFile("omni-template-publication-", ".docx");
            exporter.export(document, ReportOutputFormat.DOCX, output);
            verifyDocx(output);
            return new TemplatePublicationEvidence(
                    sha256(mapper.writeValueAsBytes(sampleData)), sha256(output), Files.size(output), clock.instant());
        } catch (IOException e) {
            throw new IllegalStateException("template publication render verification failed", e);
        } finally {
            if (output != null) {
                try { Files.deleteIfExists(output); } catch (IOException ignored) { }
            }
        }
    }

    private void verifyDocx(Path output) throws IOException {
        Set<String> required = new HashSet<>();
        required.add("[Content_Types].xml");
        required.add("word/document.xml");
        try (InputStream input = Files.newInputStream(output); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) required.remove(entry.getName());
        }
        if (!required.isEmpty()) {
            throw new IllegalStateException("rendered template DOCX is incomplete: " + required);
        }
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String sha256(Path content) throws IOException {
        try (InputStream input = Files.newInputStream(content)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String hex(byte[] digest) {
        StringBuilder result = new StringBuilder(64);
        for (byte item : digest) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
}
