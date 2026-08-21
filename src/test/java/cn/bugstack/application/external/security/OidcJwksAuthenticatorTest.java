package cn.bugstack.application.external.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OidcJwksAuthenticatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void discoversJwksValidatesClaimsAndRefreshesUnknownKid() throws Exception {
        KeyPair first = keyPair();
        AtomicReference<KeyPair> activeKey = new AtomicReference<>(first);
        AtomicReference<String> activeKid = new AtomicReference<>("key-1");
        HttpServer identityProvider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String issuer = "http://127.0.0.1:" + identityProvider.getAddress().getPort();
        identityProvider.createContext("/.well-known/openid-configuration", exchange -> {
            ObjectNode metadata = mapper.createObjectNode();
            metadata.put("issuer", issuer);
            metadata.put("jwks_uri", issuer + "/jwks");
            byte[] body = mapper.writeValueAsBytes(metadata);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        identityProvider.createContext("/jwks", exchange -> {
            byte[] body = mapper.writeValueAsBytes(jwks(activeKid.get(), activeKey.get()));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        identityProvider.start();
        try {
            OidcJwksAuthenticator authenticator = new OidcJwksAuthenticator(URI.create(issuer), "omni-office");
            String firstToken = token("key-1", first, issuer, "omni-office",
                    Instant.now().plusSeconds(60).getEpochSecond());
            RequestIdentity identity = authenticator.authenticate(
                    new HttpAuthenticationRequest("Bearer " + firstToken, null));
            assertEquals("tenant-a", identity.getTenantId());
            assertEquals("alice", identity.getPrincipalId());
            assertTrue(identity.hasScope("generation:create"));

            assertThrows(AuthenticationException.class, () -> authenticator.authenticate(
                    new HttpAuthenticationRequest("Bearer " + token("key-1", first, issuer, "wrong",
                            Instant.now().plusSeconds(60).getEpochSecond()), null)));
            assertThrows(AuthenticationException.class, () -> authenticator.authenticate(
                    new HttpAuthenticationRequest("Bearer " + token("key-1", first, issuer, "omni-office",
                            Instant.now().minusSeconds(60).getEpochSecond()), null)));

            KeyPair rotated = keyPair();
            activeKey.set(rotated);
            activeKid.set("key-2");
            String rotatedToken = token("key-2", rotated, issuer, "omni-office",
                    Instant.now().plusSeconds(60).getEpochSecond());
            assertEquals("alice", authenticator.authenticate(
                    new HttpAuthenticationRequest("Bearer " + rotatedToken, null)).getPrincipalId());
        } finally {
            identityProvider.stop(0);
        }
    }

    @Test
    void rejectsNonTlsNonLoopbackIssuer() {
        assertThrows(IllegalArgumentException.class,
                () -> new OidcJwksAuthenticator(URI.create("http://identity.internal"), "omni-office"));
    }

    private ObjectNode jwks(String kid, KeyPair keyPair) {
        RSAPublicKey key = (RSAPublicKey) keyPair.getPublic();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode keys = root.putArray("keys");
        ObjectNode value = keys.addObject();
        value.put("kty", "RSA");
        value.put("use", "sig");
        value.put("alg", "RS256");
        value.put("kid", kid);
        value.put("n", base64(unsigned(key.getModulus().toByteArray())));
        value.put("e", base64(unsigned(key.getPublicExponent().toByteArray())));
        return root;
    }

    private String token(String kid, KeyPair key, String issuer, String audience, long expiry) throws Exception {
        ObjectNode header = mapper.createObjectNode().put("alg", "RS256").put("typ", "at+jwt").put("kid", kid);
        ObjectNode claims = mapper.createObjectNode().put("iss", issuer).put("aud", audience)
                .put("sub", "alice").put("tenant", "tenant-a").put("exp", expiry)
                .put("iat", Instant.now().getEpochSecond()).put("scope", "generation:create generation:read");
        String signingInput = base64(mapper.writeValueAsBytes(header)) + "."
                + base64(mapper.writeValueAsBytes(claims));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key.getPrivate());
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + base64(signer.sign());
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private byte[] unsigned(byte[] value) {
        if (value.length > 1 && value[0] == 0) return java.util.Arrays.copyOfRange(value, 1, value.length);
        return value;
    }

    private String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
