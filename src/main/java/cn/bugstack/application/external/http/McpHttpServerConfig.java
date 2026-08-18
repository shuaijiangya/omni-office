package cn.bugstack.application.external.http;

import java.net.InetSocketAddress;
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

    public McpHttpServerConfig(InetSocketAddress address, Path dataRoot, Set<String> allowedOrigins,
                               int maxRequestBytes, int requestsPerMinute, int maxConcurrentRequests,
                               Duration requestTimeout, Duration sessionTtl) {
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
    }

    public static McpHttpServerConfig local(Path dataRoot, int port) {
        return new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", port), dataRoot,
                Collections.emptySet(), 2 * 1024 * 1024, 120, 8,
                Duration.ofSeconds(60), Duration.ofHours(8));
    }

    public InetSocketAddress getAddress() { return address; }
    public Path getDataRoot() { return dataRoot; }
    public Set<String> getAllowedOrigins() { return allowedOrigins; }
    public int getMaxRequestBytes() { return maxRequestBytes; }
    public int getRequestsPerMinute() { return requestsPerMinute; }
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public Duration getSessionTtl() { return sessionTtl; }
}
