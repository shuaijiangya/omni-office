package cn.bugstack.application.external.http;

import cn.bugstack.application.external.security.CompositeHttpAuthenticator;
import cn.bugstack.application.external.security.HmacJwtAuthenticator;
import cn.bugstack.application.external.security.HttpAuthenticator;
import cn.bugstack.application.external.security.RequestIdentity;
import cn.bugstack.application.external.security.StaticApiKeyAuthenticator;
import cn.bugstack.application.audit.JsonLinesAuditLog;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/** 可通过环境变量配置的独立 HTTP 服务入口。 */
public final class McpHttpServerMain {

    private McpHttpServerMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> env = System.getenv();
        String host = env.getOrDefault("OMNI_OFFICE_HOST", "127.0.0.1");
        int port = integer(env, "OMNI_OFFICE_PORT", 8080);
        Path dataRoot = Path.of(env.getOrDefault("OMNI_OFFICE_DATA_ROOT", "target/omni-office-service"));
        Set<String> origins = csv(env.get("OMNI_OFFICE_ALLOWED_ORIGINS"));
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress(host, port), dataRoot,
                origins, integer(env, "OMNI_OFFICE_MAX_REQUEST_BYTES", 2 * 1024 * 1024),
                integer(env, "OMNI_OFFICE_REQUESTS_PER_MINUTE", 120),
                integer(env, "OMNI_OFFICE_MAX_CONCURRENT_REQUESTS", 8),
                Duration.ofSeconds(integer(env, "OMNI_OFFICE_REQUEST_TIMEOUT_SECONDS", 60)),
                Duration.ofMinutes(integer(env, "OMNI_OFFICE_SESSION_TTL_MINUTES", 480)));
        HttpAuthenticator apiKeys = apiKeyAuthenticator(env.get("OMNI_OFFICE_API_KEYS"));
        String jwtSecret = env.get("OMNI_OFFICE_JWT_SECRET");
        HttpAuthenticator jwt = jwtSecret == null || jwtSecret.isBlank() ? null : new HmacJwtAuthenticator(
                jwtSecret, env.get("OMNI_OFFICE_JWT_ISSUER"), env.get("OMNI_OFFICE_JWT_AUDIENCE"));
        if (apiKeys == null && jwt == null) {
            throw new IllegalStateException("configure OMNI_OFFICE_API_KEYS and/or OMNI_OFFICE_JWT_SECRET");
        }
        McpHttpServer server = new McpHttpServer(config, new CompositeHttpAuthenticator(apiKeys, jwt),
                new JsonLinesAuditLog(dataRoot.resolve("audit/events.jsonl")));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "omni-office-shutdown"));
        server.start();
        System.err.println("Omni Office MCP HTTP listening on http://" + host + ":" + server.getAddress().getPort());
        new CountDownLatch(1).await();
    }

    private static HttpAuthenticator apiKeyAuthenticator(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return null;
        }
        Map<String, RequestIdentity> keys = new LinkedHashMap<>();
        for (String entry : configuration.split(",")) {
            String[] sides = entry.trim().split("=", 2);
            String[] identity = sides.length == 2 ? sides[1].split(":", 2) : new String[0];
            if (identity.length != 2) {
                throw new IllegalArgumentException("OMNI_OFFICE_API_KEYS uses key=tenant:principal entries");
            }
            keys.put(sides[0], new RequestIdentity(identity[0], identity[1], Collections.singleton("*")));
        }
        return new StaticApiKeyAuthenticator(keys);
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).forEach(result::add);
        return result;
    }

    private static int integer(Map<String, String> env, String key, int defaultValue) {
        String value = env.get(key);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }
}
