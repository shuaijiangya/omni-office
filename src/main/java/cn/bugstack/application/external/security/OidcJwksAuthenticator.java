package cn.bugstack.application.external.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * OIDC Discovery + JWKS 的 RS256 资源服务器认证器。校验 issuer、audience、exp、nbf、sub、tenant 和 scope。
 */
public final class OidcJwksAuthenticator implements HttpAuthenticator {

    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private final URI issuer;
    private final String audience;
    private final String tenantClaim;
    private final HttpClient client;
    private final Duration requestTimeout;
    private final Duration cacheTtl;
    private final Duration clockSkew;
    private final Clock clock;
    private final ObjectMapper mapper;
    private volatile KeySnapshot keys;

    /**
     * 创建使用 OIDC Discovery 和 RS256 JWKS 的认证器。
     *
     * <p>租户默认从 {@code tenant} claim 读取；认证器初始化时加载 Discovery 和 JWKS，随后按缓存
     * 有效期或未知 {@code kid} 刷新密钥。</p>
     *
     * @param issuer OIDC Issuer；生产环境必须使用 HTTPS
     * @param audience 访问令牌必须包含的 Audience
     */
    public OidcJwksAuthenticator(URI issuer, String audience) {
        this(issuer, audience, "tenant", HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
                Duration.ofSeconds(10), Duration.ofMinutes(15), Duration.ofSeconds(30),
                Clock.systemUTC(), new ObjectMapper());
    }

    OidcJwksAuthenticator(URI issuer, String audience, String tenantClaim, HttpClient client,
                          Duration requestTimeout, Duration cacheTtl, Duration clockSkew,
                          Clock clock, ObjectMapper mapper) {
        validateSecureUri(issuer, "OIDC issuer");
        if (audience == null || audience.isBlank() || tenantClaim == null
                || !tenantClaim.matches("[A-Za-z0-9_.-]{1,64}") || client == null
                || requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                || cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()
                || clockSkew == null || clockSkew.isNegative() || clock == null || mapper == null) {
            throw new IllegalArgumentException("OIDC/JWKS configuration is invalid");
        }
        this.issuer = normalizeIssuer(issuer);
        this.audience = audience.trim();
        this.tenantClaim = tenantClaim;
        this.client = client;
        this.requestTimeout = requestTimeout;
        this.cacheTtl = cacheTtl;
        this.clockSkew = clockSkew;
        this.clock = clock;
        this.mapper = mapper.copy();
        this.keys = refreshKeys();
    }

    @Override
    public RequestIdentity authenticate(HttpAuthenticationRequest request) {
        String authorization = request == null ? null : request.getAuthorization();
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AuthenticationException("missing bearer token");
        }
        String token = authorization.substring(7).trim();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new AuthenticationException("invalid credentials");
        }
        try {
            JsonNode header = decodeJson(parts[0]);
            if (!"RS256".equals(header.path("alg").asText())) {
                throw new AuthenticationException("invalid credentials");
            }
            String typ = header.path("typ").asText();
            if (!typ.isEmpty() && !"JWT".equalsIgnoreCase(typ) && !"at+jwt".equalsIgnoreCase(typ)) {
                throw new AuthenticationException("invalid credentials");
            }
            String kid = header.path("kid").asText();
            if (kid.isBlank()) throw new AuthenticationException("invalid credentials");
            PublicKey key = signingKey(kid);
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(signature)) throw new AuthenticationException("invalid credentials");
            JsonNode claims = decodeJson(parts[1]);
            validateClaims(claims);
            return new RequestIdentity(claims.path(tenantClaim).asText(), claims.path("sub").asText(),
                    scopes(claims));
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthenticationException("invalid credentials", e);
        }
    }

    private void validateClaims(JsonNode claims) {
        long now = clock.instant().getEpochSecond();
        long skew = clockSkew.getSeconds();
        if (!issuer.toString().equals(claims.path("iss").asText())
                || !containsAudience(claims.path("aud"), audience)) {
            throw new AuthenticationException("invalid credentials");
        }
        JsonNode exp = claims.path("exp");
        if (!exp.isIntegralNumber() || exp.asLong() <= now - skew) {
            throw new AuthenticationException("token expired");
        }
        JsonNode nbf = claims.path("nbf");
        if (!nbf.isMissingNode() && (!nbf.isIntegralNumber() || nbf.asLong() > now + skew)) {
            throw new AuthenticationException("token is not active");
        }
        JsonNode iat = claims.path("iat");
        if (!iat.isMissingNode() && (!iat.isIntegralNumber() || iat.asLong() > now + skew)) {
            throw new AuthenticationException("invalid credentials");
        }
        if (!claims.path("sub").isTextual() || claims.path("sub").asText().isBlank()
                || !claims.path(tenantClaim).isTextual() || claims.path(tenantClaim).asText().isBlank()) {
            throw new AuthenticationException("invalid credentials");
        }
    }

    private PublicKey signingKey(String kid) {
        KeySnapshot snapshot = keys;
        if (snapshot == null || !snapshot.expiresAt.isAfter(clock.instant())) {
            snapshot = refreshSynchronized();
        }
        PublicKey key = snapshot.values.get(kid);
        if (key == null) {
            snapshot = refreshSynchronized();
            key = snapshot.values.get(kid);
        }
        if (key == null) throw new AuthenticationException("invalid credentials");
        return key;
    }

    private synchronized KeySnapshot refreshSynchronized() {
        keys = refreshKeys();
        return keys;
    }

    private KeySnapshot refreshKeys() {
        try {
            URI discovery = URI.create(trimTrailingSlash(issuer.toString())
                    + "/.well-known/openid-configuration");
            JsonNode metadata = getJson(discovery);
            if (!issuer.toString().equals(metadata.path("issuer").asText())) {
                throw new IllegalStateException("OIDC discovery issuer does not match configured issuer");
            }
            URI jwksUri = URI.create(metadata.path("jwks_uri").asText());
            validateSecureUri(jwksUri, "OIDC jwks_uri");
            JsonNode jwks = getJson(jwksUri);
            if (!jwks.path("keys").isArray()) throw new IllegalStateException("JWKS requires keys array");
            Map<String, PublicKey> parsed = new LinkedHashMap<>();
            for (JsonNode key : jwks.path("keys")) {
                if (!"RSA".equals(key.path("kty").asText())) continue;
                if (key.has("use") && !"sig".equals(key.path("use").asText())) continue;
                if (key.has("alg") && !"RS256".equals(key.path("alg").asText())) continue;
                String kid = key.path("kid").asText();
                if (kid.isBlank() || !key.path("n").isTextual() || !key.path("e").isTextual()) continue;
                PublicKey publicKey = rsaKey(key.path("n").asText(), key.path("e").asText());
                if (parsed.put(kid, publicKey) != null) {
                    throw new IllegalStateException("JWKS contains duplicate signing kid");
                }
            }
            if (parsed.isEmpty()) throw new IllegalStateException("JWKS has no supported RS256 signing keys");
            return new KeySnapshot(Collections.unmodifiableMap(parsed), clock.instant().plus(cacheTtl));
        } catch (AuthenticationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AuthenticationException("identity provider metadata is unavailable", e);
        }
    }

    private JsonNode getJson(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(requestTimeout)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("identity provider returned HTTP " + response.statusCode());
            }
            try (InputStream input = response.body()) {
                return mapper.readTree(readBounded(input));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("identity provider request was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("identity provider request failed", e);
        }
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > MAX_METADATA_BYTES) throw new IOException("identity metadata is too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private PublicKey rsaKey(String modulus, String exponent) {
        try {
            BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
            BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
            if (n.bitLength() < 2048 || e.signum() <= 0) {
                throw new IllegalStateException("JWKS RSA key strength is invalid");
            }
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
        } catch (Exception error) {
            throw new IllegalStateException("invalid RSA JWK", error);
        }
    }

    private JsonNode decodeJson(String value) throws IOException {
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        if (decoded.length > 64 * 1024) throw new IOException("JWT part is too large");
        JsonNode result = mapper.readTree(decoded);
        if (result == null || !result.isObject()) throw new IOException("JWT part must be an object");
        return result;
    }

    private Set<String> scopes(JsonNode claims) {
        Set<String> result = new LinkedHashSet<>();
        JsonNode scope = claims.has("scope") ? claims.path("scope") : claims.path("scp");
        if (scope.isTextual()) {
            for (String item : scope.asText().split("\\s+")) if (!item.isBlank()) result.add(item);
        } else if (scope.isArray()) {
            scope.forEach(item -> { if (item.isTextual() && !item.asText().isBlank()) result.add(item.asText()); });
        }
        return result;
    }

    private boolean containsAudience(JsonNode value, String expected) {
        if (value.isTextual()) return expected.equals(value.asText());
        if (value.isArray()) for (JsonNode item : value) if (expected.equals(item.asText())) return true;
        return false;
    }

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

    private static URI normalizeIssuer(URI value) {
        return URI.create(trimTrailingSlash(value.normalize().toString()));
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static final class KeySnapshot {
        private final Map<String, PublicKey> values;
        private final Instant expiresAt;

        private KeySnapshot(Map<String, PublicKey> values, Instant expiresAt) {
            this.values = values;
            this.expiresAt = expiresAt;
        }
    }
}
