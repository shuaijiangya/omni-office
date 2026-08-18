package cn.bugstack.application.external.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

/** 验证 HS256 JWT 的轻量认证器，要求 {@code sub}、{@code tenant} 和未来过期时间 {@code exp}。 */
public final class HmacJwtAuthenticator implements HttpAuthenticator {

    private final byte[] secret;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String expectedIssuer;
    private final String expectedAudience;

    public HmacJwtAuthenticator(String secret) {
        this(secret, null, null);
    }

    public HmacJwtAuthenticator(String secret, String expectedIssuer, String expectedAudience) {
        this(secret, expectedIssuer, expectedAudience, new ObjectMapper(), Clock.systemUTC());
    }

    HmacJwtAuthenticator(String secret, String expectedIssuer, String expectedAudience,
                         ObjectMapper mapper, Clock clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT HMAC secret must contain at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper.copy();
        this.clock = clock;
        this.expectedIssuer = blankToNull(expectedIssuer);
        this.expectedAudience = blankToNull(expectedAudience);
    }

    @Override
    public RequestIdentity authenticate(HttpAuthenticationRequest request) {
        String authorization = request == null ? null : request.getAuthorization();
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AuthenticationException("missing bearer token");
        }
        String token = authorization.substring(7).trim();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new AuthenticationException("invalid credentials");
        }
        try {
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText()) || !"JWT".equals(header.path("typ").asText("JWT"))) {
                throw new AuthenticationException("invalid credentials");
            }
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new AuthenticationException("invalid credentials");
            }
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long exp = claims.path("exp").asLong(0);
            if (exp <= clock.instant().getEpochSecond()) {
                throw new AuthenticationException("token expired");
            }
            if (expectedIssuer != null && !expectedIssuer.equals(claims.path("iss").asText())) {
                throw new AuthenticationException("invalid credentials");
            }
            if (expectedAudience != null && !containsAudience(claims.path("aud"), expectedAudience)) {
                throw new AuthenticationException("invalid credentials");
            }
            Set<String> scopes = new LinkedHashSet<>();
            JsonNode scope = claims.path("scope");
            if (scope.isTextual()) {
                for (String value : scope.asText().split("\\s+")) {
                    if (!value.isEmpty()) {
                        scopes.add(value);
                    }
                }
            } else if (scope.isArray()) {
                scope.forEach(value -> scopes.add(value.asText()));
            }
            return new RequestIdentity(claims.path("tenant").asText(), claims.path("sub").asText(), scopes);
        } catch (AuthenticationException e) {
            throw e;
        } catch (RuntimeException | java.io.IOException e) {
            throw new AuthenticationException("invalid credentials", e);
        }
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private boolean containsAudience(JsonNode value, String audience) {
        if (value.isTextual()) return audience.equals(value.asText());
        if (value.isArray()) {
            for (JsonNode item : value) if (audience.equals(item.asText())) return true;
        }
        return false;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
