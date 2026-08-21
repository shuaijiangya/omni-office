package cn.bugstack.application.external.http;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.external.ExternalArtifactStoreProvider;
import cn.bugstack.application.external.LocalExternalArtifactStoreProvider;
import cn.bugstack.application.generation.FileGenerationJobRepositoryProvider;
import cn.bugstack.application.generation.GenerationJobApplication;
import cn.bugstack.application.generation.GenerationEventPublisher;
import cn.bugstack.application.generation.GenerationJobRepositoryProvider;
import cn.bugstack.application.generation.NoopGenerationEventPublisher;
import cn.bugstack.application.generation.GenerationQuotaPolicy;
import cn.bugstack.application.template.governance.FileDocumentTemplateCatalog;
import cn.bugstack.application.template.governance.TemplateManagementApplication;
import cn.bugstack.protocol.template.DocumentTemplateSpec;
import cn.bugstack.protocol.template.DocumentTemplateSpecJsonCodec;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.Instant;
import java.nio.file.Files;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import cn.bugstack.application.generation.GenerationJobStatus;

/** 每个租户拥有物理隔离的工件根目录和独立模板目录。 */
public final class TenantApplicationRegistry implements AutoCloseable {

    private final Path tenantsRoot;
    private final GenerationEventPublisher eventPublisher;
    private final GenerationJobRepositoryProvider jobRepositories;
    private final ExternalArtifactStoreProvider artifactStores;
    private final GenerationQuotaPolicy quotaPolicy;
    private final ConcurrentMap<String, ExternalDocumentToolApplication> applications = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, GenerationJobApplication> generationApplications = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TemplateManagementApplication> templateManagement = new ConcurrentHashMap<>();

    /**
     * 创建使用本地文件任务仓储和本地工件库的租户注册表。
     *
     * @param dataRoot 服务数据根目录
     */
    public TenantApplicationRegistry(Path dataRoot) {
        this(dataRoot, new NoopGenerationEventPublisher(),
                new FileGenerationJobRepositoryProvider(dataRoot), new LocalExternalArtifactStoreProvider(),
                GenerationQuotaPolicy.unlimited());
    }

    /**
     * 创建带终态事件发布器的本地租户注册表。
     *
     * @param dataRoot 服务数据根目录
     * @param eventPublisher 终态事件发布器
     */
    public TenantApplicationRegistry(Path dataRoot, GenerationEventPublisher eventPublisher) {
        this(dataRoot, eventPublisher, new FileGenerationJobRepositoryProvider(dataRoot),
                new LocalExternalArtifactStoreProvider(), GenerationQuotaPolicy.unlimited());
    }

    /**
     * 创建使用指定任务仓储的租户注册表。
     *
     * @param dataRoot 服务数据根目录
     * @param eventPublisher 终态事件发布器
     * @param jobRepositories 任务仓储提供器
     */
    public TenantApplicationRegistry(Path dataRoot, GenerationEventPublisher eventPublisher,
                                     GenerationJobRepositoryProvider jobRepositories) {
        this(dataRoot, eventPublisher, jobRepositories, new LocalExternalArtifactStoreProvider(),
                GenerationQuotaPolicy.unlimited());
    }

    /**
     * 创建使用指定任务仓储和工件库的租户注册表。
     *
     * @param dataRoot 服务数据根目录
     * @param eventPublisher 终态事件发布器
     * @param jobRepositories 任务仓储提供器
     * @param artifactStores 工件库提供器
     */
    public TenantApplicationRegistry(Path dataRoot, GenerationEventPublisher eventPublisher,
                                     GenerationJobRepositoryProvider jobRepositories,
                                     ExternalArtifactStoreProvider artifactStores) {
        this(dataRoot, eventPublisher, jobRepositories, artifactStores,
                GenerationQuotaPolicy.unlimited());
    }

    /**
     * 创建完整配置的租户注册表。
     *
     * @param dataRoot 服务数据根目录
     * @param eventPublisher 终态事件发布器
     * @param jobRepositories 任务仓储提供器
     * @param artifactStores 工件库提供器
     * @param quotaPolicy 租户配额策略
     */
    public TenantApplicationRegistry(Path dataRoot, GenerationEventPublisher eventPublisher,
                                     GenerationJobRepositoryProvider jobRepositories,
                                     ExternalArtifactStoreProvider artifactStores,
                                     GenerationQuotaPolicy quotaPolicy) {
        if (dataRoot == null) {
            throw new IllegalArgumentException("tenant data root is required");
        }
        if (eventPublisher == null || jobRepositories == null || artifactStores == null
                || quotaPolicy == null) {
            throw new IllegalArgumentException("generation application dependencies are required");
        }
        this.tenantsRoot = dataRoot.toAbsolutePath().normalize().resolve("tenants");
        this.eventPublisher = eventPublisher;
        this.jobRepositories = jobRepositories;
        this.artifactStores = artifactStores;
        this.quotaPolicy = quotaPolicy;
    }

    /**
     * 获取或创建租户的外部文档工具门面。
     *
     * @param tenantId 租户 ID
     * @return 租户隔离的工具门面
     */
    public ExternalDocumentToolApplication require(String tenantId) {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("tenant id is invalid");
        }
        return applications.computeIfAbsent(tenantId, this::create);
    }

    /**
     * 获取或创建租户的异步生成应用服务。
     *
     * @param tenantId 租户 ID
     * @return 异步生成应用服务
     */
    public GenerationJobApplication requireGeneration(String tenantId) {
        ExternalDocumentToolApplication application = require(tenantId);
        return generationApplications.computeIfAbsent(tenantId, value -> new GenerationJobApplication(
                value, application, jobRepositories.repository(value),
                eventPublisher, quotaPolicy.quota(value)));
    }

    /**
     * 获取租户的模板管理应用服务。
     *
     * @param tenantId 租户 ID
     * @return 模板管理应用服务
     */
    public TemplateManagementApplication requireTemplateManagement(String tenantId) {
        require(tenantId);
        TemplateManagementApplication value = templateManagement.get(tenantId);
        if (value == null) throw new IllegalStateException("template management application is unavailable");
        return value;
    }

    /**
     * 计算受控租户数据根目录。
     *
     * @param tenantId 租户 ID
     * @return 规范化后的租户目录
     */
    public Path tenantRoot(String tenantId) {
        Path value = tenantsRoot.resolve(tenantId).normalize();
        if (!tenantsRoot.equals(value.getParent())) {
            throw new IllegalArgumentException("tenant root escapes configured data directory");
        }
        return value;
    }

    /**
     * 清理所有已加载租户的过期工件。
     *
     * @param now 当前时刻
     * @return 删除的工件数
     */
    public int purgeExpiredArtifacts(Instant now) {
        return applications.values().stream().mapToInt(application -> application.purgeExpiredArtifacts(now)).sum();
    }

    /** @return 所有关键依赖均就绪时返回 {@code true} */
    public boolean isReady() {
        try {
            Files.createDirectories(tenantsRoot);
            return Files.isDirectory(tenantsRoot) && Files.isWritable(tenantsRoot)
                    && jobRepositories.isReady() && artifactStores.isReady();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 获取不包含凭证和路径的依赖就绪明细。
     *
     * @return 检查项名称与状态
     */
    public Map<String, Boolean> readinessChecks() {
        Map<String, Boolean> checks = new java.util.LinkedHashMap<>();
        boolean dataDirectory;
        try {
            Files.createDirectories(tenantsRoot);
            dataDirectory = Files.isDirectory(tenantsRoot) && Files.isWritable(tenantsRoot);
        } catch (IOException e) {
            dataDirectory = false;
        }
        checks.put("dataDirectory", dataDirectory);
        checks.put("generationRepository", jobRepositories.isReady());
        checks.put("artifactStorage", artifactStores.isReady());
        return checks;
    }

    /** @return 所有已加载租户的任务状态汇总 */
    public Map<GenerationJobStatus, Long> generationCounts() {
        Map<GenerationJobStatus, Long> result = new EnumMap<>(GenerationJobStatus.class);
        for (GenerationJobStatus status : GenerationJobStatus.values()) result.put(status, 0L);
        generationApplications.values().forEach(application -> application.countsByStatus()
                .forEach((status, count) -> result.put(status, result.get(status) + count)));
        return result;
    }

    /**
     * 获取指定租户的任务状态汇总。
     *
     * @param tenantId 租户 ID
     * @return 任务状态数量映射
     */
    public Map<GenerationJobStatus, Long> generationCounts(String tenantId) {
        return requireGeneration(tenantId).countsByStatus();
    }

    /** 关闭所有租户应用及共享仓储资源。 */
    @Override
    public void close() {
        generationApplications.values().forEach(GenerationJobApplication::close);
        generationApplications.clear();
        templateManagement.clear();
        jobRepositories.close();
        artifactStores.close();
    }

    private ExternalDocumentToolApplication create(String tenantId) {
        Path root = tenantRoot(tenantId);
        FileDocumentTemplateCatalog catalog = new FileDocumentTemplateCatalog(root.resolve("templates"));
        ExternalDocumentToolApplication application = new ExternalDocumentToolApplication(
                root, catalog, artifactStores.store(tenantId, root));
        registerClasspathTemplate(application, catalog,
                "/document-template/1.0/example-assessment-template.json");
        registerClasspathTemplate(application, catalog,
                "/internal-ai/1.0/omni-office-demo-template.json");
        templateManagement.put(tenantId, new TemplateManagementApplication(catalog));
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
