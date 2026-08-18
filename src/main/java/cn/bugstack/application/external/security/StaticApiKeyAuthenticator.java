package cn.bugstack.application.external.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 不保存 API Key 明文并使用常量时间摘要比较的静态认证器。 */
public final class StaticApiKeyAuthenticator implements HttpAuthenticator {

    private final List<Entry> entries;

    public StaticApiKeyAuthenticator(Map<String, RequestIdentity> identitiesByKey) {
        if (identitiesByKey == null || identitiesByKey.isEmpty()) {
            throw new IllegalArgumentException("at least one API key is required");
        }
        List<Entry> values = new ArrayList<>();
        for (Map.Entry<String, RequestIdentity> entry : identitiesByKey.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() < 8 || entry.getValue() == null) {
                throw new IllegalArgumentException("API keys must contain at least 8 characters and an identity");
            }
            values.add(new Entry(sha256(entry.getKey()), entry.getValue()));
        }
        entries = values;
    }

    @Override
    public RequestIdentity authenticate(HttpAuthenticationRequest request) {
        String value = request == null ? null : request.getApiKey();
        if (value == null || value.isEmpty()) {
            throw new AuthenticationException("missing API key");
        }
        byte[] actual = sha256(value);
        for (Entry entry : entries) {
            if (MessageDigest.isEqual(entry.digest, actual)) {
                return entry.identity;
            }
        }
        throw new AuthenticationException("invalid credentials");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static final class Entry {
        private final byte[] digest;
        private final RequestIdentity identity;
        private Entry(byte[] digest, RequestIdentity identity) {
            this.digest = digest;
            this.identity = identity;
        }
    }
}
