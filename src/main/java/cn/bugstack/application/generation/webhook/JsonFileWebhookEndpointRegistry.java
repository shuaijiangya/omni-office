package cn.bugstack.application.generation.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** 从仓库外 JSON 文件一次性加载预注册端点；运行期间不读取或回显密钥。 */
public final class JsonFileWebhookEndpointRegistry implements WebhookEndpointRegistry {

    private final StaticWebhookEndpointRegistry delegate;

    /**
     * 从仓库外配置文件加载端点。
     *
     * @param configuration 已存在的绝对 JSON 文件路径
     */
    public JsonFileWebhookEndpointRegistry(Path configuration) {
        if (configuration == null || !configuration.isAbsolute() || !Files.isRegularFile(configuration)) {
            throw new IllegalArgumentException("webhook configuration must be an existing absolute file");
        }
        this.delegate = new StaticWebhookEndpointRegistry(read(configuration));
    }

    @Override
    public WebhookEndpoint require(String tenantId, String webhookId) {
        return delegate.require(tenantId, webhookId);
    }

    private List<WebhookEndpoint> read(Path path) {
        try {
            JsonNode root = new ObjectMapper().readTree(path.toFile());
            JsonNode tenants = root == null ? null : root.path("tenants");
            if (tenants == null || !tenants.isObject()) {
                throw new IllegalArgumentException("webhook configuration requires object field: tenants");
            }
            List<WebhookEndpoint> result = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> tenantFields = tenants.fields();
            while (tenantFields.hasNext()) {
                Map.Entry<String, JsonNode> tenant = tenantFields.next();
                if (!tenant.getValue().isObject()) {
                    throw new IllegalArgumentException("webhook tenant entry must be an object");
                }
                Iterator<Map.Entry<String, JsonNode>> endpointFields = tenant.getValue().fields();
                while (endpointFields.hasNext()) {
                    Map.Entry<String, JsonNode> endpoint = endpointFields.next();
                    JsonNode definition = endpoint.getValue();
                    if (!definition.isObject() || !definition.path("url").isTextual()
                            || !definition.path("secret").isTextual()) {
                        throw new IllegalArgumentException("webhook endpoint requires url and secret");
                    }
                    result.add(new WebhookEndpoint(tenant.getKey(), endpoint.getKey(),
                            URI.create(definition.path("url").asText()), definition.path("secret").asText()));
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("failed to read webhook configuration", e);
        }
    }
}
