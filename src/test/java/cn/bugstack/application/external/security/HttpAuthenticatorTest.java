package cn.bugstack.application.external.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpAuthenticatorTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validatesHs256SignatureExpiryTenantSubjectAndScopes() throws Exception {
        HmacJwtAuthenticator authenticator = new HmacJwtAuthenticator(SECRET);
        String token = token(Instant.now().plusSeconds(60).getEpochSecond());
        RequestIdentity identity = authenticator.authenticate(
                new HttpAuthenticationRequest("Bearer " + token, null));
        assertEquals("tenant-a", identity.getTenantId());
        assertEquals("alice", identity.getPrincipalId());
        assertTrue(identity.hasScope("mcp:invoke"));
        assertThrows(AuthenticationException.class, () -> authenticator.authenticate(
                new HttpAuthenticationRequest("Bearer " + token.substring(0, token.length() - 2) + "aa", null)));
        assertThrows(AuthenticationException.class, () -> authenticator.authenticate(
                new HttpAuthenticationRequest("Bearer " + token(Instant.now().minusSeconds(1).getEpochSecond()), null)));
    }

    private String token(long expiry) throws Exception {
        ObjectNode header = mapper.createObjectNode().put("alg", "HS256").put("typ", "JWT");
        ObjectNode claims = mapper.createObjectNode().put("sub", "alice").put("tenant", "tenant-a")
                .put("exp", expiry).put("scope", "mcp:invoke artifacts:read");
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String signingInput = encoder.encodeToString(mapper.writeValueAsBytes(header)) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return signingInput + "." + encoder.encodeToString(mac.doFinal(
                signingInput.getBytes(StandardCharsets.US_ASCII)));
    }
}
