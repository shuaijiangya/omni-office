package cn.bugstack.application.generation.webhook;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 不可变端点注册表，供配置文件加载器和测试复用。 */
public final class StaticWebhookEndpointRegistry implements WebhookEndpointRegistry {

    private final Map<String, WebhookEndpoint> endpoints;

    /**
     * 创建不可变端点注册表。
     *
     * @param values 预注册端点集合
     */
    public StaticWebhookEndpointRegistry(Collection<WebhookEndpoint> values) {
        Map<String, WebhookEndpoint> result = new LinkedHashMap<>();
        if (values != null) {
            for (WebhookEndpoint endpoint : values) {
                String key = key(endpoint.getTenantId(), endpoint.getWebhookId());
                if (result.put(key, endpoint) != null) {
                    throw new IllegalArgumentException("duplicate webhook endpoint: " + key);
                }
            }
        }
        this.endpoints = Collections.unmodifiableMap(result);
    }

    @Override
    public WebhookEndpoint require(String tenantId, String webhookId) {
        WebhookEndpoint value = endpoints.get(key(tenantId, webhookId));
        if (value == null) throw new IllegalArgumentException("webhookId is not registered for this tenant");
        return value;
    }

    private String key(String tenantId, String webhookId) {
        return tenantId + ":" + webhookId;
    }
}
