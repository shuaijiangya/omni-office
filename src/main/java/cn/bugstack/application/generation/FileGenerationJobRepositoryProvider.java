package cn.bugstack.application.generation;

import cn.bugstack.application.generation.webhook.FileWebhookDeliveryRepository;
import cn.bugstack.application.generation.webhook.WebhookDeliveryRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 本地开发仓储提供器；不承诺跨进程锁语义。 */
public final class FileGenerationJobRepositoryProvider implements GenerationJobRepositoryProvider {

    private final Path tenantsRoot;

    /**
     * 创建文件仓储提供器。
     *
     * @param dataRoot 服务数据根目录
     */
    public FileGenerationJobRepositoryProvider(Path dataRoot) {
        if (dataRoot == null) throw new IllegalArgumentException("generation data root is required");
        this.tenantsRoot = dataRoot.toAbsolutePath().normalize().resolve("tenants");
    }

    @Override
    public GenerationJobRepository repository(String tenantId) {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("tenant id is invalid");
        }
        return new FileGenerationJobRepository(tenantsRoot.resolve(tenantId).resolve("generation-jobs"));
    }

    @Override
    public boolean isReady() {
        try {
            Files.createDirectories(tenantsRoot);
            return Files.isDirectory(tenantsRoot) && Files.isWritable(tenantsRoot);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public WebhookDeliveryRepository webhookRepository(Path fileFallbackRoot) {
        return new FileWebhookDeliveryRepository(fileFallbackRoot);
    }
}
