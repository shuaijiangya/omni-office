package cn.bugstack.application.ai.review;

import cn.bugstack.application.ai.AiDocumentResult;
import cn.bugstack.application.document.DynamicDocumentExporter;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentSpecJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** 四眼原则人工审核库；仅 APPROVED 草稿可由此服务导出。 */
public final class FileAiDraftReviewService {

    private final Path root;
    private final DynamicDocumentExporter exporter;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final DocumentSpecJsonCodec codec = new DocumentSpecJsonCodec();
    private final Clock clock;

    public FileAiDraftReviewService(Path root, DynamicDocumentExporter exporter) {
        this(root, exporter, Clock.systemUTC());
    }

    FileAiDraftReviewService(Path root, DynamicDocumentExporter exporter, Clock clock) {
        if (root == null || exporter == null || clock == null) {
            throw new IllegalArgumentException("AI draft review dependencies are required");
        }
        this.root = root.toAbsolutePath().normalize();
        this.exporter = exporter;
        this.clock = clock;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create AI draft review directory", e);
        }
    }

    public synchronized AiDraftRecord submit(AiDocumentResult result, String requestedBy) {
        if (result == null || result.getDocumentSpec() == null) {
            throw new IllegalArgumentException("AI document result is required");
        }
        AiDraftRecord record = new AiDraftRecord();
        record.setDraftId(UUID.randomUUID().toString());
        record.setMode(result.getMode().name());
        record.setTemplateId(result.getTemplateId());
        record.setTemplateVersion(result.getTemplateVersion());
        record.setAttempts(result.getAttempts());
        try {
            record.setDocumentSpec(mapper.readTree(codec.write(result.getDocumentSpec())));
        } catch (IOException e) {
            throw new IllegalStateException("failed to snapshot AI document", e);
        }
        record.setStatus(AiDraftStatus.PENDING_REVIEW);
        record.setRequestedBy(requireActor(requestedBy));
        record.setCreatedAt(clock.instant());
        write(record, true);
        return copy(record);
    }

    public synchronized AiDraftRecord approve(String draftId, String reviewer, String comment) {
        AiDraftRecord record = pending(draftId);
        String actor = requireActor(reviewer);
        if (actor.equals(record.getRequestedBy())) {
            throw new IllegalArgumentException("draft creator cannot approve their own AI document");
        }
        record.setStatus(AiDraftStatus.APPROVED);
        record.setReviewedBy(actor);
        record.setReviewComment(comment(comment, false));
        record.setReviewedAt(clock.instant());
        write(record, false);
        return copy(record);
    }

    public synchronized AiDraftRecord reject(String draftId, String reviewer, String comment) {
        AiDraftRecord record = pending(draftId);
        record.setStatus(AiDraftStatus.REJECTED);
        record.setReviewedBy(requireActor(reviewer));
        record.setReviewComment(comment(comment, true));
        record.setReviewedAt(clock.instant());
        write(record, false);
        return copy(record);
    }

    public synchronized byte[] exportApproved(String draftId, ReportOutputFormat format) {
        AiDraftRecord record = read(draftId);
        if (record.getStatus() != AiDraftStatus.APPROVED) {
            throw new IllegalStateException("AI document must be approved before export");
        }
        DocumentSpec spec = codec.read(record.getDocumentSpec().toString());
        return exporter.exportToBytes(spec, format);
    }

    public synchronized AiDraftRecord get(String draftId) {
        return copy(read(draftId));
    }

    public synchronized List<AiDraftRecord> list() {
        List<AiDraftRecord> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> result.add(copy(readPath(path))));
        } catch (IOException e) {
            throw new IllegalStateException("failed to list AI drafts", e);
        }
        result.sort(Comparator.comparing(AiDraftRecord::getCreatedAt));
        return result;
    }

    private AiDraftRecord pending(String draftId) {
        AiDraftRecord record = read(draftId);
        if (record.getStatus() != AiDraftStatus.PENDING_REVIEW) {
            throw new IllegalStateException("AI draft is already in a terminal review state");
        }
        return record;
    }

    private AiDraftRecord read(String draftId) {
        if (draftId == null || !draftId.matches("[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("AI draft id is invalid");
        }
        Path path = root.resolve(draftId + ".json").normalize();
        if (!root.equals(path.getParent()) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("AI draft does not exist");
        }
        return readPath(path);
    }

    private AiDraftRecord readPath(Path path) {
        try {
            AiDraftRecord value = mapper.readValue(path.toFile(), AiDraftRecord.class);
            if (value.getDocumentSpec() == null || value.getStatus() == null) {
                throw new IllegalStateException("AI draft record is incomplete");
            }
            return value;
        } catch (IOException e) {
            throw new IllegalStateException("failed to read AI draft", e);
        }
    }

    private void write(AiDraftRecord record, boolean create) {
        Path target = root.resolve(record.getDraftId() + ".json").normalize();
        if (!root.equals(target.getParent()) || create && Files.exists(target)) {
            throw new IllegalStateException("AI draft path collision");
        }
        try {
            Path temporary = Files.createTempFile(root, ".draft-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), record);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist AI draft", e);
        }
    }

    private String requireActor(String actor) {
        if (actor == null || !actor.matches("[A-Za-z0-9@._-]{1,128}")) {
            throw new IllegalArgumentException("AI review actor is invalid");
        }
        return actor;
    }

    private String comment(String value, boolean required) {
        String result = value == null ? "" : value.trim();
        if (required && result.isEmpty()) throw new IllegalArgumentException("rejection comment is required");
        if (result.length() > 2_000) throw new IllegalArgumentException("review comment exceeds 2000 characters");
        return result.isEmpty() ? null : result;
    }

    private AiDraftRecord copy(AiDraftRecord value) {
        return mapper.convertValue(mapper.valueToTree(value), AiDraftRecord.class);
    }
}
