package cn.bugstack.application.external.http;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.template.governance.FileDocumentTemplateCatalog;
import cn.bugstack.protocol.template.DocumentTemplateSpec;
import cn.bugstack.protocol.template.DocumentTemplateSpecJsonCodec;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.Instant;
import java.nio.file.Files;
import java.io.IOException;

/** 每个租户拥有物理隔离的工件根目录和独立模板目录。 */
public final class TenantApplicationRegistry {

    private final Path tenantsRoot;
    private final ConcurrentMap<String, ExternalDocumentToolApplication> applications = new ConcurrentHashMap<>();

    public TenantApplicationRegistry(Path dataRoot) {
        if (dataRoot == null) {
            throw new IllegalArgumentException("tenant data root is required");
        }
        this.tenantsRoot = dataRoot.toAbsolutePath().normalize().resolve("tenants");
    }

    public ExternalDocumentToolApplication require(String tenantId) {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("tenant id is invalid");
        }
        return applications.computeIfAbsent(tenantId, this::create);
    }

    public Path tenantRoot(String tenantId) {
        Path value = tenantsRoot.resolve(tenantId).normalize();
        if (!tenantsRoot.equals(value.getParent())) {
            throw new IllegalArgumentException("tenant root escapes configured data directory");
        }
        return value;
    }

    public int purgeExpiredArtifacts(Instant now) {
        return applications.values().stream().mapToInt(application -> application.purgeExpiredArtifacts(now)).sum();
    }

    public boolean isReady() {
        try {
            Files.createDirectories(tenantsRoot);
            return Files.isDirectory(tenantsRoot) && Files.isWritable(tenantsRoot);
        } catch (IOException e) {
            return false;
        }
    }

    private ExternalDocumentToolApplication create(String tenantId) {
        Path root = tenantRoot(tenantId);
        FileDocumentTemplateCatalog catalog = new FileDocumentTemplateCatalog(root.resolve("templates"));
        ExternalDocumentToolApplication application = new ExternalDocumentToolApplication(root, catalog);
        registerClasspathTemplate(application, catalog,
                "/document-template/1.0/example-assessment-template.json");
        registerClasspathTemplate(application, catalog,
                "/internal-ai/1.0/omni-office-demo-template.json");
        return application;
    }

    private void registerClasspathTemplate(ExternalDocumentToolApplication application,
                                           FileDocumentTemplateCatalog catalog, String resource) {
        try (InputStream input = TenantApplicationRegistry.class.getResourceAsStream(resource)) {
            if (input != null) {
                DocumentTemplateSpec template = new DocumentTemplateSpecJsonCodec().read(input);
                try {
                    catalog.getRevision(template.getTemplateId(), template.getVersion());
                } catch (IllegalArgumentException missing) {
                    application.registerTemplate(template);
                }
            }
        } catch (RuntimeException | java.io.IOException e) {
            throw new IllegalStateException("failed to register built-in template: " + resource, e);
        }
    }
}
