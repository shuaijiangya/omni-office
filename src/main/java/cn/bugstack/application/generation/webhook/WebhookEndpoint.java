package cn.bugstack.application.generation.webhook;

import java.net.URI;

/** 由服务端预注册的租户 Webhook 端点。密钥不会进入任务或响应。 */
public final class WebhookEndpoint {

    private final String tenantId;
    private final String webhookId;
    private final URI url;
    private final String secret;

    /**
     * 创建管理员预注册的 Webhook 端点。
     *
     * @param tenantId 租户 ID
     * @param webhookId Webhook 配置 ID
     * @param url HTTPS 回调地址；HTTP 仅允许回环地址
     * @param secret 长度为 32～512 的 HMAC 密钥
     */
    public WebhookEndpoint(String tenantId, String webhookId, URI url, String secret) {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("webhook tenant id is invalid");
        }
        if (webhookId == null || !webhookId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("webhook id is invalid");
        }
        validateUrl(url);
        if (secret == null || secret.length() < 32 || secret.length() > 512) {
            throw new IllegalArgumentException("webhook secret must contain 32 to 512 characters");
        }
        this.tenantId = tenantId;
        this.webhookId = webhookId;
        this.url = url;
        this.secret = secret;
    }

    /** @return 租户 ID */
    public String getTenantId() { return tenantId; }

    /** @return Webhook 配置 ID */
    public String getWebhookId() { return webhookId; }

    /** @return 回调地址 */
    public URI getUrl() { return url; }

    /** @return HMAC 密钥；不得写入日志或响应 */
    public String getSecret() { return secret; }

    private void validateUrl(URI value) {
        if (value == null || value.getHost() == null || value.getUserInfo() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException("webhook URL is invalid");
        }
        if ("https".equalsIgnoreCase(value.getScheme())) return;
        String host = value.getHost();
        boolean loopback = "http".equalsIgnoreCase(value.getScheme())
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
        if (!loopback) throw new IllegalArgumentException("webhook URL must use HTTPS (HTTP is loopback-only)");
    }
}
