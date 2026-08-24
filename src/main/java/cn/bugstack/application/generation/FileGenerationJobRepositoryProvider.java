package cn.bugstack.application.generation;

import cn.bugstack.application.generation.webhook.FileWebhookDeliveryRepository;
import cn.bugstack.application.generation.webhook.WebhookDeliveryRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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

    @Override
    public Set<String> recoverableTenantIds(Instant now) {
        if (now == null) throw new IllegalArgumentException("generation recovery time is required");
        Set<String> result = new LinkedHashSet<>();
        forEachTenant((tenantId, repository) -> {
            boolean recoverable = repository.list(Integer.MAX_VALUE).stream().anyMatch(record ->
                    !record.getStatus().isTerminal() || record.getTerminalEventId() == null
                            && record.getRequest() != null && record.getRequest().has("webhookId"));
            if (recoverable) result.add(tenantId);
        });
        return result;
    }

    @Override
    public Map<GenerationJobStatus, Long> countsByStatus() {
        Map<GenerationJobStatus, Long> result = new EnumMap<>(GenerationJobStatus.class);
        for (GenerationJobStatus status : GenerationJobStatus.values()) result.put(status, 0L);
        forEachTenant((tenantId, repository) -> repository.countsByStatus().forEach((status, count) ->
                result.put(status, result.get(status) + count)));
        return result;
    }

    @Override
    public int purgeTerminalBefore(Instant cutoff, int limit) {
        if (cutoff == null || limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("generation purge boundary is invalid");
        }
        int[] remaining = {limit};
        int[] deleted = {0};
        forEachTenant((tenantId, repository) -> {
            if (remaining[0] > 0) {
                int count = repository.purgeTerminalBefore(cutoff, remaining[0]);
                deleted[0] += count;
                remaining[0] -= count;
            }
        });
        return deleted[0];
    }

    private void forEachTenant(TenantRepositoryConsumer consumer) {
        if (!Files.isDirectory(tenantsRoot)) return;
        try (Stream<Path> paths = Files.list(tenantsRoot)) {
            paths.filter(Files::isDirectory).forEach(path -> {
                String tenantId = path.getFileName().toString();
                if (tenantId.matches("[A-Za-z0-9._-]{1,64}")) {
                    consumer.accept(tenantId, new FileGenerationJobRepository(path.resolve("generation-jobs")));
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("failed to inspect generation tenants", e);
        }
    }

    @FunctionalInterface
    private interface TenantRepositoryConsumer {
        void accept(String tenantId, FileGenerationJobRepository repository);
    }
}
