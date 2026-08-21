package cn.bugstack.application.generation;

/** 未配置 Webhook 时的显式边界；不会静默接受无法投递的 webhookId。 */
public final class NoopGenerationEventPublisher implements GenerationEventPublisher {

    @Override
    public void validateWebhook(String tenantId, String webhookId) {
        if (webhookId != null) {
            throw new IllegalArgumentException("webhookId cannot be used because no webhook registry is configured");
        }
    }

    @Override
    public String enqueueTerminal(GenerationJobRecord job) {
        return null;
    }
}
