package cn.bugstack.application.template.governance;

import cn.bugstack.application.template.DocumentTemplateCatalog;
import cn.bugstack.application.template.DocumentTemplateDescriptor;
import cn.bugstack.application.template.DocumentTemplateSpecValidator;
import cn.bugstack.protocol.template.DocumentTemplateSpec;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 原子落盘、显式审核且发布版本不可覆盖的模板目录。
 * {@link #register(DocumentTemplateSpec)} 仅用于受信任的启动期内置模板；业务模板应使用工作流方法。
 */
public final class FileDocumentTemplateCatalog implements DocumentTemplateCatalog {

    private final Path root;
    private final ObjectMapper mapper;
    private final DocumentTemplateSpecValidator validator;
    private final Clock clock;

    public FileDocumentTemplateCatalog(Path root) {
        this(root, Clock.systemUTC());
    }

    FileDocumentTemplateCatalog(Path root, Clock clock) {
        if (root == null || clock == null) {
            throw new IllegalArgumentException("template catalog root and clock are required");
        }
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
        this.validator = new DocumentTemplateSpecValidator();
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create template catalog", e);
        }
    }

    @Override
    public synchronized void register(DocumentTemplateSpec template) {
        create(template, "system", TemplateLifecycleStatus.PUBLISHED);
    }

    public synchronized TemplateRevision createDraft(DocumentTemplateSpec template, String actor) {
        return create(template, requireActor(actor), TemplateLifecycleStatus.DRAFT);
    }

    public synchronized TemplateRevision submit(String templateId, String version, String actor) {
        TemplateRevision value = read(templateId, version);
        if (value.getStatus() != TemplateLifecycleStatus.DRAFT
                && value.getStatus() != TemplateLifecycleStatus.REJECTED) {
            throw new IllegalStateException("only draft or rejected template versions can be submitted");
        }
        value.setStatus(TemplateLifecycleStatus.IN_REVIEW);
        value.setReviewedBy(null);
        value.setReviewComment(null);
        value.setUpdatedAt(clock.instant());
        write(value, path(templateId, version), false);
        return copy(value);
    }

    public synchronized TemplateRevision approve(String templateId, String version,
                                                  String reviewer, String comment) {
        TemplateRevision value = requireInReview(templateId, version);
        value.setStatus(TemplateLifecycleStatus.PUBLISHED);
        value.setReviewedBy(requireActor(reviewer));
        value.setReviewComment(boundedComment(comment));
        value.setUpdatedAt(clock.instant());
        write(value, path(templateId, version), false);
        return copy(value);
    }

    public synchronized TemplateRevision reject(String templateId, String version,
                                                 String reviewer, String comment) {
        TemplateRevision value = requireInReview(templateId, version);
        if (comment == null || comment.trim().isEmpty()) {
            throw new IllegalArgumentException("template rejection requires a review comment");
        }
        value.setStatus(TemplateLifecycleStatus.REJECTED);
        value.setReviewedBy(requireActor(reviewer));
        value.setReviewComment(boundedComment(comment));
        value.setUpdatedAt(clock.instant());
        write(value, path(templateId, version), false);
        return copy(value);
    }

    @Override
    public synchronized DocumentTemplateSpec require(String templateId, String version) {
        TemplateRevision value = read(templateId, version);
        if (value.getStatus() != TemplateLifecycleStatus.PUBLISHED) {
            throw new IllegalArgumentException("document template is not published: " + templateId + "@" + version);
        }
        return copySpec(value.getTemplate());
    }

    public synchronized TemplateRevision getRevision(String templateId, String version) {
        return copy(read(templateId, version));
    }

    public synchronized List<TemplateRevision> listRevisions() {
        List<TemplateRevision> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().forEach(path -> result.add(copy(readPath(path))));
        } catch (IOException e) {
            throw new IllegalStateException("failed to list template catalog", e);
        }
        result.sort(Comparator.comparing(item -> item.getTemplate().getTemplateId()
                + "@" + item.getTemplate().getVersion()));
        return result;
    }

    @Override
    public synchronized List<DocumentTemplateDescriptor> list() {
        List<DocumentTemplateDescriptor> result = new ArrayList<>();
        for (TemplateRevision revision : listRevisions()) {
            if (revision.getStatus() == TemplateLifecycleStatus.PUBLISHED) {
                DocumentTemplateSpec template = revision.getTemplate();
                result.add(new DocumentTemplateDescriptor(template.getTemplateId(), template.getVersion(),
                        template.getName(), template.getDescription()));
            }
        }
        return result;
    }

    private TemplateRevision create(DocumentTemplateSpec template, String actor,
                                    TemplateLifecycleStatus status) {
        validator.validateOrThrow(template);
        Path path = path(template.getTemplateId(), template.getVersion());
        if (Files.exists(path)) {
            throw new IllegalStateException("document template version already exists: "
                    + template.getTemplateId() + "@" + template.getVersion());
        }
        Instant now = clock.instant();
        TemplateRevision value = new TemplateRevision();
        value.setTemplate(copySpec(template));
        value.setStatus(status);
        value.setCreatedBy(actor);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        write(value, path, true);
        return copy(value);
    }

    private TemplateRevision requireInReview(String templateId, String version) {
        TemplateRevision value = read(templateId, version);
        if (value.getStatus() != TemplateLifecycleStatus.IN_REVIEW) {
            throw new IllegalStateException("only an in-review template version can be reviewed");
        }
        return value;
    }

    private TemplateRevision read(String templateId, String version) {
        Path path = path(templateId, version);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("document template does not exist: " + templateId + "@" + version);
        }
        return readPath(path);
    }

    private TemplateRevision readPath(Path path) {
        try {
            TemplateRevision value = mapper.readValue(path.toFile(), TemplateRevision.class);
            validator.validateOrThrow(value.getTemplate());
            if (value.getStatus() == null || value.getCreatedAt() == null || value.getUpdatedAt() == null) {
                throw new IllegalStateException("template revision metadata is incomplete");
            }
            return value;
        } catch (IOException e) {
            throw new IllegalStateException("failed to read template revision", e);
        }
    }

    private void write(TemplateRevision value, Path target, boolean newVersion) {
        Path parent = target.getParent();
        try {
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, ".template-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
                if (newVersion && Files.exists(target)) {
                    throw new IllegalStateException("document template version already exists");
                }
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
            throw new IllegalStateException("failed to persist template revision", e);
        }
    }

    private Path path(String templateId, String version) {
        validator.validateOrThrow(minimalForPath(templateId, version));
        Path directory = root.resolve(templateId).normalize();
        Path value = directory.resolve(version + ".json").normalize();
        if (!value.startsWith(root) || !directory.equals(value.getParent())) {
            throw new IllegalArgumentException("invalid template catalog path");
        }
        return value;
    }

    private DocumentTemplateSpec minimalForPath(String templateId, String version) {
        DocumentTemplateSpec value = new DocumentTemplateSpec();
        value.setTemplateId(templateId);
        value.setVersion(version);
        value.setName("path-validation");
        com.fasterxml.jackson.databind.node.ObjectNode schema = mapper.createObjectNode();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        value.setDataSchema(schema);
        com.fasterxml.jackson.databind.node.ObjectNode document = mapper.createObjectNode();
        document.put("schemaVersion", "1.0");
        document.set("metadata", mapper.createObjectNode());
        document.set("layout", mapper.createObjectNode());
        document.set("sections", mapper.createArrayNode());
        value.setDocumentTemplate(document);
        return value;
    }

    private String requireActor(String actor) {
        if (actor == null || !actor.matches("[A-Za-z0-9@._-]{1,128}")) {
            throw new IllegalArgumentException("template workflow actor is invalid");
        }
        return actor;
    }

    private String boundedComment(String comment) {
        if (comment == null) {
            return null;
        }
        String value = comment.trim();
        if (value.length() > 2_000) {
            throw new IllegalArgumentException("review comment exceeds 2000 characters");
        }
        return value.isEmpty() ? null : value;
    }

    private TemplateRevision copy(TemplateRevision source) {
        return mapper.convertValue(mapper.valueToTree(source), TemplateRevision.class);
    }

    private DocumentTemplateSpec copySpec(DocumentTemplateSpec source) {
        return mapper.convertValue(mapper.valueToTree(source), DocumentTemplateSpec.class);
    }
}
