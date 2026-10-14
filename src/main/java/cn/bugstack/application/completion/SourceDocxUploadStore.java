package cn.bugstack.application.completion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/** 保存上传源 DOCX 的最小文件存储；业务层负责认证，并通过 ownerId 隔离文档。 */
public final class SourceDocxUploadStore {

    private static final long MAX_BYTES = 100L * 1024L * 1024L;
    private final Path root;
    private final SourceDocxStructureExtractor extractor = new SourceDocxStructureExtractor();

    /** 创建文件存储。root 应为应用专用且不能被普通用户直接写入。 */
    public SourceDocxUploadStore(Path root) {
        if (root == null) throw new IllegalArgumentException("upload root is required");
        this.root = root.toAbsolutePath().normalize();
    }

    /** 上传一份 DOCX，返回不可猜测的文档 ID。 */
    public String upload(String ownerId, String originalFilename, InputStream input) {
        requireOwner(ownerId);
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".docx")
                || input == null) {
            throw new IllegalArgumentException("a DOCX filename and input stream are required");
        }
        String id = UUID.randomUUID().toString();
        Path temporary = null;
        Path document = root.resolve(id + ".docx");
        Path ownerFile = root.resolve(id + ".owner");
        try {
            Files.createDirectories(root);
            temporary = Files.createTempFile(root, ".incoming-", ".docx");
            try (InputStream stream = input) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                try (java.io.OutputStream out = Files.newOutputStream(temporary)) {
                    while ((count = stream.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_BYTES) throw new IllegalArgumentException("source DOCX exceeds 100 MiB");
                        out.write(buffer, 0, count);
                    }
                }
                if (total == 0) throw new IllegalArgumentException("source DOCX is empty");
            }
            extractor.extract(temporary);
            Files.write(ownerFile, ownerId.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, document, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, document);
            }
            return id;
        } catch (Exception e) {
            try {
                Files.deleteIfExists(ownerFile);
                if (temporary != null) Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw new IllegalArgumentException("failed to store uploaded DOCX", e);
        }
    }

    /** 查询当前 owner 的源文件路径；禁止通过 ID 访问其他用户的文档。 */
    public Path resolve(String ownerId, String documentId) {
        requireOwner(ownerId);
        if (documentId == null || !documentId.matches("[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("invalid document ID");
        }
        Path ownerFile = root.resolve(documentId + ".owner");
        Path document = root.resolve(documentId + ".docx");
        try {
            if (!Files.isRegularFile(ownerFile) || !Files.isRegularFile(document)
                    || !ownerId.equals(new String(Files.readAllBytes(ownerFile), StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("uploaded DOCX was not found for this owner");
            }
            return document;
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to resolve uploaded DOCX", e);
        }
    }

    private void requireOwner(String ownerId) {
        if (ownerId == null || ownerId.trim().isEmpty() || ownerId.length() > 256) {
            throw new IllegalArgumentException("ownerId is required and must be at most 256 characters");
        }
    }
}
