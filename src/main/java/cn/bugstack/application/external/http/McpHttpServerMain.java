package cn.bugstack.application.external.http;

import cn.bugstack.application.external.security.CompositeHttpAuthenticator;
import cn.bugstack.application.external.security.HmacJwtAuthenticator;
import cn.bugstack.application.external.security.OidcJwksAuthenticator;
import cn.bugstack.application.external.security.CompositeBearerAuthenticator;
import cn.bugstack.application.external.security.HttpAuthenticator;
import cn.bugstack.application.external.security.RequestIdentity;
import cn.bugstack.application.external.security.StaticApiKeyAuthenticator;
import cn.bugstack.application.audit.JsonLinesAuditLog;
import cn.bugstack.application.external.ExternalArtifactStoreProvider;
import cn.bugstack.application.external.LocalExternalArtifactStoreProvider;
import cn.bugstack.application.external.storage.S3ExternalArtifactStoreProvider;
import cn.bugstack.application.generation.FileGenerationJobRepositoryProvider;
import cn.bugstack.application.generation.GenerationJobRepositoryProvider;
import cn.bugstack.application.generation.PostgresGenerationJobRepositoryProvider;
import cn.bugstack.application.generation.GenerationQuotaPolicy;
import cn.bugstack.application.generation.JsonGenerationQuotaPolicy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
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
        URI oidcIssuer = uri(env.get("OMNI_OFFICE_OIDC_ISSUER"));
        URI resourceIdentifier = uri(env.get("OMNI_OFFICE_RESOURCE_IDENTIFIER"));
        if ((oidcIssuer == null) != (resourceIdentifier == null)) {
            throw new IllegalArgumentException("OIDC issuer and resource identifier must be configured together");
        }
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress(host, port), dataRoot,
                origins, integer(env, "OMNI_OFFICE_MAX_REQUEST_BYTES", 2 * 1024 * 1024),
                integer(env, "OMNI_OFFICE_REQUESTS_PER_MINUTE", 120),
                integer(env, "OMNI_OFFICE_MAX_CONCURRENT_REQUESTS", 8),
                Duration.ofSeconds(integer(env, "OMNI_OFFICE_REQUEST_TIMEOUT_SECONDS", 60)),
                Duration.ofMinutes(integer(env, "OMNI_OFFICE_SESSION_TTL_MINUTES", 480)),
                absolutePath(env.get("OMNI_OFFICE_WEBHOOK_CONFIG_PATH")), oidcIssuer, resourceIdentifier);
        HttpAuthenticator apiKeys = apiKeyAuthenticator(env.get("OMNI_OFFICE_API_KEYS"));
        List<HttpAuthenticator> bearerAuthenticators = new ArrayList<>();
        String jwtSecret = env.get("OMNI_OFFICE_JWT_SECRET");
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            bearerAuthenticators.add(new HmacJwtAuthenticator(jwtSecret,
                    env.get("OMNI_OFFICE_JWT_ISSUER"), env.get("OMNI_OFFICE_JWT_AUDIENCE")));
        }
        if (oidcIssuer != null) {
            String audience = env.get("OMNI_OFFICE_OIDC_AUDIENCE");
            if (audience == null || audience.isBlank()) {
                throw new IllegalArgumentException("OMNI_OFFICE_OIDC_AUDIENCE is required with OIDC");
            }
            bearerAuthenticators.add(new OidcJwksAuthenticator(oidcIssuer, audience));
        }
        HttpAuthenticator bearer = bearerAuthenticators.isEmpty() ? null
                : bearerAuthenticators.size() == 1 ? bearerAuthenticators.get(0)
                : new CompositeBearerAuthenticator(bearerAuthenticators);
        if (apiKeys == null && bearer == null) {
            throw new IllegalStateException("configure API Key, HS256 JWT and/or OIDC/JWKS authentication");
        }
        GenerationJobRepositoryProvider jobRepositories = generationRepositories(env, dataRoot);
        ExternalArtifactStoreProvider artifactStores;
        try {
            artifactStores = artifactStores(env);
        } catch (RuntimeException e) {
            jobRepositories.close();
            throw e;
        }
        McpHttpServer server;
        GenerationQuotaPolicy quotaPolicy = quotaPolicy(env);
        try {
            server = new McpHttpServer(config, new CompositeHttpAuthenticator(apiKeys, bearer),
                    new JsonLinesAuditLog(dataRoot.resolve("audit/events.jsonl")),
                    jobRepositories, artifactStores, quotaPolicy);
        } catch (RuntimeException e) {
            jobRepositories.close();
            artifactStores.close();
            throw e;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "omni-office-shutdown"));
        server.start();
        System.err.println("Omni Office HTTP service listening on http://" + host + ":" + server.getAddress().getPort());
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

    private static Path absolutePath(String value) {
        if (value == null || value.isBlank()) return null;
        Path path = Path.of(value.trim());
        if (!path.isAbsolute()) throw new IllegalArgumentException("webhook config path must be absolute");
        return path.normalize();
    }

    private static URI uri(String value) {
        return value == null || value.isBlank() ? null : URI.create(value.trim());
    }

    private static GenerationJobRepositoryProvider generationRepositories(Map<String, String> env,
                                                                           Path dataRoot) {
        String jdbcUrl = env.get("OMNI_OFFICE_DATABASE_URL");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return new FileGenerationJobRepositoryProvider(dataRoot);
        }
        String username = env.get("OMNI_OFFICE_DATABASE_USERNAME");
        String password = env.get("OMNI_OFFICE_DATABASE_PASSWORD");
        return new PostgresGenerationJobRepositoryProvider(jdbcUrl.trim(), username, password,
                integer(env, "OMNI_OFFICE_DATABASE_POOL_SIZE", 10));
    }

    private static ExternalArtifactStoreProvider artifactStores(Map<String, String> env) {
        String bucket = env.get("OMNI_OFFICE_S3_BUCKET");
        if (bucket == null || bucket.isBlank()) return new LocalExternalArtifactStoreProvider();
        String region = env.getOrDefault("OMNI_OFFICE_S3_REGION", "us-east-1");
        String prefix = env.getOrDefault("OMNI_OFFICE_S3_PREFIX", "omni-office");
        URI endpoint = uri(env.get("OMNI_OFFICE_S3_ENDPOINT"));
        String accessKey = blankToNull(env.get("OMNI_OFFICE_S3_ACCESS_KEY"));
        String secretKey = blankToNull(env.get("OMNI_OFFICE_S3_SECRET_KEY"));
        boolean pathStyle = Boolean.parseBoolean(env.getOrDefault("OMNI_OFFICE_S3_PATH_STYLE", "false"));
        return new S3ExternalArtifactStoreProvider(bucket.trim(), region.trim(), prefix, endpoint,
                accessKey, secretKey, pathStyle,
                Duration.ofHours(integer(env, "OMNI_OFFICE_ARTIFACT_RETENTION_HOURS", 24)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static GenerationQuotaPolicy quotaPolicy(Map<String, String> env) {
        String value = env.get("OMNI_OFFICE_QUOTA_CONFIG_PATH");
        if (value == null || value.isBlank()) return GenerationQuotaPolicy.unlimited();
        Path path = Path.of(value.trim());
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("generation quota config path must be absolute");
        }
        return new JsonGenerationQuotaPolicy(path.normalize());
    }
}
