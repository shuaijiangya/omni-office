package cn.bugstack.application.generation.webhook;

/** Webhook Outbox 投递状态。 */
public enum WebhookDeliveryStatus {
    PENDING,
    RETRYING,
    DELIVERED,
    DEAD;

    /**
     * 判断是否不再自动投递。
     *
     * @return 已送达或死信状态返回 {@code true}
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == DEAD;
    }
}
