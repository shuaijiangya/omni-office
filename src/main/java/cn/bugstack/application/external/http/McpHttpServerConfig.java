package cn.bugstack.application.external.http;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Streamable HTTP 服务的不可变运行配置。 */
public final class McpHttpServerConfig {

    private final InetSocketAddress address;
    private final Path dataRoot;
    private final Set<String> allowedOrigins;
    private final int maxRequestBytes;
    private final int requestsPerMinute;
    private final int maxConcurrentRequests;
    private final Duration requestTimeout;
    private final Duration sessionTtl;
    private final Path webhookConfigPath;
    private final URI authorizationServer;
    private final URI resourceIdentifier;

    /**
     * 创建不启用 Webhook 和 OIDC 元数据的服务配置。
     *
     * @param address 监听地址
     * @param dataRoot 服务数据根目录
     * @param allowedOrigins 允许的浏览器 Origin
     * @param maxRequestBytes 最大请求体字节数
     * @param requestsPerMinute 每个身份每分钟请求数
     * @param maxConcurrentRequests 最大并发请求数
     * @param requestTimeout 单次操作超时
     * @param sessionTtl MCP 会话有效期
     */
    public McpHttpServerConfig(InetSocketAddress address, Path dataRoot, Set<String> allowedOrigins,
                               int maxRequestBytes, int requestsPerMinute, int maxConcurrentRequests,
                               Duration requestTimeout, Duration sessionTtl) {
        this(address, dataRoot, allowedOrigins, maxRequestBytes, requestsPerMinute,
                maxConcurrentRequests, requestTimeout, sessionTtl, null, null, null);
    }

    /**
     * 创建启用可选 Webhook 配置的服务配置。
     *
     * @param address 监听地址
     * @param dataRoot 服务数据根目录
     * @param allowedOrigins 允许的浏览器 Origin
     * @param maxRequestBytes 最大请求体字节数
     * @param requestsPerMinute 每个身份每分钟请求数
     * @param maxConcurrentRequests 最大并发请求数
     * @param requestTimeout 单次操作超时
     * @param sessionTtl MCP 会话有效期
     * @param webhookConfigPath 预注册 Webhook 配置绝对路径
     */
    public McpHttpServerConfig(InetSocketAddress address, Path dataRoot, Set<String> allowedOrigins,
                               int maxRequestBytes, int requestsPerMinute, int maxConcurrentRequests,
                               Duration requestTimeout, Duration sessionTtl, Path webhookConfigPath) {
        this(address, dataRoot, allowedOrigins, maxRequestBytes, requestsPerMinute,
                maxConcurrentRequests, requestTimeout, sessionTtl, webhookConfigPath, null, null);
    }

    /**
     * 创建完整服务配置。
     *
     * @param address 监听地址
     * @param dataRoot 服务数据根目录
     * @param allowedOrigins 允许的浏览器 Origin
     * @param maxRequestBytes 最大请求体字节数
     * @param requestsPerMinute 每个身份每分钟请求数
     * @param maxConcurrentRequests 最大并发请求数
     * @param requestTimeout 单次操作超时
     * @param sessionTtl MCP 会话有效期
     * @param webhookConfigPath 预注册 Webhook 配置绝对路径
     * @param authorizationServer OIDC 授权服务器地址
     * @param resourceIdentifier OAuth 受保护资源标识
     */
    public McpHttpServerConfig(InetSocketAddress address, Path dataRoot, Set<String> allowedOrigins,
                               int maxRequestBytes, int requestsPerMinute, int maxConcurrentRequests,
                               Duration requestTimeout, Duration sessionTtl, Path webhookConfigPath,
                               URI authorizationServer, URI resourceIdentifier) {
        if (address == null || dataRoot == null || maxRequestBytes < 1024 || requestsPerMinute < 1
                || maxConcurrentRequests < 1 || requestTimeout == null || requestTimeout.isNegative()
                || sessionTtl == null || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("invalid MCP HTTP server configuration");
        }
        this.address = address;
        this.dataRoot = dataRoot.toAbsolutePath().normalize();
        this.allowedOrigins = allowedOrigins == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(allowedOrigins));
        this.maxRequestBytes = maxRequestBytes;
        this.requestsPerMinute = requestsPerMinute;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.requestTimeout = requestTimeout;
        this.sessionTtl = sessionTtl;
        if (webhookConfigPath != null && !webhookConfigPath.isAbsolute()) {
            throw new IllegalArgumentException("webhook configuration path must be absolute");
        }
        this.webhookConfigPath = webhookConfigPath == null ? null : webhookConfigPath.normalize();
        if ((authorizationServer == null) != (resourceIdentifier == null)) {
            throw new IllegalArgumentException("authorization server and resource identifier are paired");
        }
        if (authorizationServer != null) {
            validateSecureUri(authorizationServer, "authorization server");
            validateSecureUri(resourceIdentifier, "resource identifier");
            String resourcePath = resourceIdentifier.getPath();
            if (resourcePath != null && !resourcePath.isEmpty() && !"/".equals(resourcePath)) {
                throw new IllegalArgumentException("resource identifier must not contain a path");
            }
        }
        this.authorizationServer = authorizationServer == null ? null : authorizationServer.normalize();
        this.resourceIdentifier = resourceIdentifier == null ? null : resourceIdentifier.normalize();
    }

    /**
     * 创建仅绑定回环地址的本地开发配置。
     *
     * @param dataRoot 服务数据根目录
     * @param port 监听端口；传入 0 时由系统分配
     * @return 本地服务配置
     */
    public static McpHttpServerConfig local(Path dataRoot, int port) {
        return new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", port), dataRoot,
                Collections.emptySet(), 2 * 1024 * 1024, 120, 8,
                Duration.ofSeconds(60), Duration.ofHours(8));
    }

    /** @return 监听地址 */
    public InetSocketAddress getAddress() { return address; }
    /** @return 服务数据根目录 */
    public Path getDataRoot() { return dataRoot; }
    /** @return 不可变的允许 Origin 集合 */
    public Set<String> getAllowedOrigins() { return allowedOrigins; }
    /** @return 最大请求体字节数 */
    public int getMaxRequestBytes() { return maxRequestBytes; }
    /** @return 每个身份每分钟请求上限 */
    public int getRequestsPerMinute() { return requestsPerMinute; }
    /** @return 最大并发请求数 */
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    /** @return 单次操作超时 */
    public Duration getRequestTimeout() { return requestTimeout; }
    /** @return MCP 会话有效期 */
    public Duration getSessionTtl() { return sessionTtl; }
    /** @return Webhook 配置绝对路径，未配置时为 {@code null} */
    public Path getWebhookConfigPath() { return webhookConfigPath; }
    /** @return OIDC 授权服务器，未配置时为 {@code null} */
    public URI getAuthorizationServer() { return authorizationServer; }
    /** @return OAuth 受保护资源标识，未配置时为 {@code null} */
    public URI getResourceIdentifier() { return resourceIdentifier; }

    private static void validateSecureUri(URI value, String field) {
        if (value == null || value.getHost() == null || value.getUserInfo() != null
                || value.getQuery() != null || value.getFragment() != null) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        if ("https".equalsIgnoreCase(value.getScheme())) return;
        String host = value.getHost();
        boolean loopback = "http".equalsIgnoreCase(value.getScheme())
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
        if (!loopback) throw new IllegalArgumentException(field + " must use HTTPS (HTTP is loopback-only)");
    }
}
