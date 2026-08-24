package cn.bugstack.application.external.http;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.external.ExternalArtifactStoreProvider;
import cn.bugstack.application.external.LocalExternalArtifactStoreProvider;
import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.concurrent.BoundedExecutors;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.time.Instant;
import java.time.Duration;
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
    private final StructuredAiClient aiClient;
    private final Duration reviewTimeout;
    private final ExecutorService generationExecutor;
    private final ScheduledExecutorService generationCoordinator;
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
                GenerationQuotaPolicy.unlimited(), null);
    }

    /**
     * 创建带终态事件发布器的本地租户注册表。
     *
     * @param dataRoot 服务数据根目录
     * @param eventPublisher 终态事件发布器
     */
    public TenantApplicationRegistry(Path dataRoot, GenerationEventPublisher eventPublisher) {
        this(dataRoot, eventPublisher, new FileGenerationJobRepositoryProvider(dataRoot),
                new LocalExternalArtifactStoreProvider(), GenerationQuotaPolicy.unlimited(), null);
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
                GenerationQuotaPolicy.unlimited(), null);
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
                GenerationQuotaPolicy.unlimited(), null);
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
        this(dataRoot, eventPublisher, jobRepositories, artifactStores, quotaPolicy, null);
    }

    /**
     * 创建可选启用内部 AI 的完整租户注册表。
     *
     * @param dataRoot 服务数据根目录
     * @param eventPublisher 终态事件发布器
     * @param jobRepositories 任务仓储提供器
     * @param artifactStores 工件库提供器
     * @param quotaPolicy 租户配额策略
     * @param aiClient 结构化 AI 客户端；为空时不启用 AI 任务模式
     */
    public TenantApplicationRegistry(Path dataRoot, GenerationEventPublisher eventPublisher,
                                     GenerationJobRepositoryProvider jobRepositories,
                                     ExternalArtifactStoreProvider artifactStores,
                                     GenerationQuotaPolicy quotaPolicy, StructuredAiClient aiClient) {
        this(dataRoot, eventPublisher, jobRepositories, artifactStores, quotaPolicy, aiClient,
                Duration.ofDays(30));
    }

    /** 创建具有显式 AI 人工审核期限的完整租户注册表。 */
    public TenantApplicationRegistry(Path dataRoot, GenerationEventPublisher eventPublisher,
                                     GenerationJobRepositoryProvider jobRepositories,
                                     ExternalArtifactStoreProvider artifactStores,
                                     GenerationQuotaPolicy quotaPolicy, StructuredAiClient aiClient,
                                     Duration reviewTimeout) {
        if (dataRoot == null) {
            throw new IllegalArgumentException("tenant data root is required");
        }
        if (eventPublisher == null || jobRepositories == null || artifactStores == null
                || quotaPolicy == null || reviewTimeout == null || reviewTimeout.isZero()
                || reviewTimeout.isNegative()) {
            throw new IllegalArgumentException("generation application dependencies are required");
        }
        this.tenantsRoot = dataRoot.toAbsolutePath().normalize().resolve("tenants");
        this.eventPublisher = eventPublisher;
        this.jobRepositories = jobRepositories;
        this.artifactStores = artifactStores;
        this.quotaPolicy = quotaPolicy;
        this.aiClient = aiClient;
        this.reviewTimeout = reviewTimeout;
        this.generationExecutor = BoundedExecutors.fixed(8, 256, "omni-generation",
                new ThreadPoolExecutor.AbortPolicy());
        this.generationCoordinator = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "omni-generation-coordinator");
            thread.setDaemon(true);
            return thread;
        });
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
                eventPublisher, quotaPolicy.quota(value),
                aiClient == null ? null : application.createInternalAiService(aiClient),
                generationExecutor, generationCoordinator, reviewTimeout));
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

    /** 终结超过审核期限的 AI Generation Job。 */
    public int expirePendingReviews(Instant now) {
        return generationApplications.values().stream()
                .mapToInt(application -> application.expirePendingReviews(now)).sum();
    }

    /**
     * 启动时发现持久化队列并创建对应租户 Worker，不依赖新的 HTTP 请求触发恢复。
     *
     * @param now 启动时刻
     * @return 已恢复的租户 Worker 数量
     */
    public int recoverPersistedGenerationJobs(Instant now) {
        int recovered = 0;
        for (String tenantId : jobRepositories.recoverableTenantIds(now)) {
            requireGeneration(tenantId);
            recovered++;
        }
        return recovered;
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
        if (aiClient != null) checks.put("internalAi", true);
        return checks;
    }

    /** @return 所有已加载租户的任务状态汇总 */
    public Map<GenerationJobStatus, Long> generationCounts() {
        return jobRepositories.countsByStatus();
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

    /** @return 服务级共享生成 Worker 当前排队任务数 */
    public int generationWorkerQueueSize() {
        return generationExecutor instanceof ThreadPoolExecutor
                ? ((ThreadPoolExecutor) generationExecutor).getQueue().size() : 0;
    }

    /** @return 服务级共享生成 Worker 当前执行任务数 */
    public int generationWorkersActive() {
        return generationExecutor instanceof ThreadPoolExecutor
                ? ((ThreadPoolExecutor) generationExecutor).getActiveCount() : 0;
    }

    /** 关闭所有租户应用及共享仓储资源。 */
    @Override
    public void close() {
        generationApplications.values().forEach(GenerationJobApplication::close);
        generationApplications.clear();
        templateManagement.clear();
        generationExecutor.shutdownNow();
        generationCoordinator.shutdownNow();
        jobRepositories.close();
        artifactStores.close();
    }

    private ExternalDocumentToolApplication create(String tenantId) {
        Path root = tenantRoot(tenantId);
        FileDocumentTemplateCatalog catalog = new FileDocumentTemplateCatalog(root.resolve("templates"));
        ExternalDocumentToolApplication application = new ExternalDocumentToolApplication(
                root, catalog, artifactStores.store(tenantId, root), artifactStores.retention());
        registerClasspathTemplate(application, catalog,
                "/document-template/1.0/example-assessment-template.json");
        registerClasspathTemplate(application, catalog,
                "/internal-ai/1.0/omni-office-demo-template.json");
        templateManagement.put(tenantId, new TemplateManagementApplication(
                catalog, application.createTemplatePublicationGate()));
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
