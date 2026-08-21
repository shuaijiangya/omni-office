package cn.bugstack.application.generation.webhook;

/** 只解析服务端预注册 ID，不接受调用方提交 URL。 */
public interface WebhookEndpointRegistry {

    /**
     * 获取预注册 Webhook。
     *
     * @param tenantId 租户 ID
     * @param webhookId Webhook 配置 ID
     * @return Webhook 端点
     * @throws IllegalArgumentException 配置不存在时抛出
     */
    WebhookEndpoint require(String tenantId, String webhookId);
}
