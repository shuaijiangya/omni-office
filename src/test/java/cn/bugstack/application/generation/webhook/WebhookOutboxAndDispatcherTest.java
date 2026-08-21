package cn.bugstack.application.generation.webhook;

import cn.bugstack.application.generation.GenerationArtifact;
import cn.bugstack.application.generation.GenerationJobRecord;
import cn.bugstack.application.generation.GenerationJobStatus;
import cn.bugstack.application.generation.GenerationMode;
import cn.bugstack.application.generation.GenerationJobApplication;
import cn.bugstack.application.generation.FileGenerationJobRepository;
import cn.bugstack.application.external.ExternalDocumentToolApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookOutboxAndDispatcherTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void enqueuesIdempotentlySignsAndRetriesTerminalEvents() throws Exception {
        HttpServer receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<byte[]> receivedBody = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        AtomicReference<String> eventIdHeader = new AtomicReference<>();
        receiver.createContext("/events", exchange -> {
            receivedBody.set(exchange.getRequestBody().readAllBytes());
            timestamp.set(exchange.getRequestHeaders().getFirst("X-Omni-Timestamp"));
            signature.set(exchange.getRequestHeaders().getFirst("X-Omni-Signature"));
            eventIdHeader.set(exchange.getRequestHeaders().getFirst("X-Omni-Event-Id"));
            int status = requests.incrementAndGet() == 1 ? 500 : 204;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        receiver.start();
        try {
            URI endpointUrl = URI.create("http://127.0.0.1:" + receiver.getAddress().getPort() + "/events");
            StaticWebhookEndpointRegistry endpoints = new StaticWebhookEndpointRegistry(Collections.singletonList(
                    new WebhookEndpoint("tenant-a", "erp", endpointUrl, SECRET)));
            FileWebhookDeliveryRepository repository = new FileWebhookDeliveryRepository(
                    Files.createTempDirectory("webhook-outbox"));
            WebhookOutboxPublisher publisher = new WebhookOutboxPublisher(endpoints, repository);
            GenerationJobRecord job = succeededJob();

            String eventId = publisher.enqueueTerminal(job);
            assertEquals(eventId, publisher.enqueueTerminal(job));
            assertEquals(1, repository.list("tenant-a", 10).size());
            assertFalse(repository.list("tenant-a", 10).get(0).getPayload().toString()
                    .contains("documentSpec"));

            try (WebhookDispatcher dispatcher = new WebhookDispatcher(endpoints, repository,
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                    Duration.ofSeconds(2), Duration.ZERO, Clock.systemUTC(), mapper)) {
                assertEquals(1, dispatcher.dispatchDue());
                WebhookDeliveryRecord retry = repository.list("tenant-a", 10).get(0);
                assertEquals(WebhookDeliveryStatus.RETRYING, retry.getStatus());
                assertEquals(1, retry.getAttemptCount());

                assertEquals(1, dispatcher.dispatchDue());
                WebhookDeliveryRecord delivered = repository.list("tenant-a", 10).get(0);
                assertEquals(WebhookDeliveryStatus.DELIVERED, delivered.getStatus());
                assertEquals(2, delivered.getAttemptCount());
                assertEquals(204, delivered.getResponseStatus());
            }

            assertEquals(eventId, eventIdHeader.get());
            assertEquals("v1=" + hmac(timestamp.get(), receivedBody.get()), signature.get());
            assertEquals(eventId, mapper.readTree(receivedBody.get()).path("eventId").asText());
        } finally {
            receiver.stop(0);
        }
    }

    @Test
    void loadsOnlyPreRegisteredSecureEndpoints() throws Exception {
        java.nio.file.Path config = Files.createTempFile("webhook-config", ".json").toAbsolutePath();
        Files.writeString(config, "{\"tenants\":{\"tenant-a\":{\"erp\":{\"url\":\"https://example.com/events\","
                + "\"secret\":\"0123456789abcdef0123456789abcdef\"}}}}", StandardCharsets.UTF_8);
        JsonFileWebhookEndpointRegistry registry = new JsonFileWebhookEndpointRegistry(config);
        assertEquals("https://example.com/events", registry.require("tenant-a", "erp").getUrl().toString());
        assertThrows(IllegalArgumentException.class, () -> registry.require("tenant-b", "erp"));
        assertThrows(IllegalArgumentException.class, () -> new WebhookEndpoint("tenant-a", "bad",
                URI.create("http://metadata.internal/events"), SECRET));
    }

    @Test
    void recoversTerminalJobWhenCrashWindowLeftEventMarkerEmpty() throws Exception {
        java.nio.file.Path root = Files.createTempDirectory("webhook-recovery");
        StaticWebhookEndpointRegistry endpoints = new StaticWebhookEndpointRegistry(Collections.singletonList(
                new WebhookEndpoint("tenant-a", "erp", URI.create("https://example.com/events"), SECRET)));
        FileWebhookDeliveryRepository deliveries = new FileWebhookDeliveryRepository(root.resolve("outbox"));
        WebhookOutboxPublisher publisher = new WebhookOutboxPublisher(endpoints, deliveries);
        FileGenerationJobRepository jobs = new FileGenerationJobRepository(root.resolve("jobs"));
        GenerationJobRecord terminal = succeededJob();
        jobs.create(terminal);
        ExternalDocumentToolApplication tools = new ExternalDocumentToolApplication(root.resolve("artifacts"));

        try (GenerationJobApplication ignored = new GenerationJobApplication(
                "tenant-a", tools, jobs, publisher)) {
            assertFalse(jobs.find(terminal.getJobId()).orElseThrow().getTerminalEventId().isBlank());
            assertEquals(1, deliveries.list("tenant-a", 10).size());
        }
        try (GenerationJobApplication ignored = new GenerationJobApplication(
                "tenant-a", tools, jobs, publisher)) {
            assertEquals(1, deliveries.list("tenant-a", 10).size());
        }
    }

    private GenerationJobRecord succeededJob() {
        Instant now = Instant.now();
        GenerationJobRecord job = new GenerationJobRecord();
        job.setJobId("11111111-1111-1111-1111-111111111111");
        job.setTenantId("tenant-a");
        job.setPrincipalId("alice");
        job.setCorrelationId("trace-1");
        job.setMode(GenerationMode.DOCUMENT_SPEC);
        ObjectNode request = mapper.createObjectNode();
        request.put("mode", "DOCUMENT_SPEC");
        request.put("webhookId", "erp");
        request.putObject("documentSpec").put("secretBusinessText", "must-not-leak");
        job.setRequest(request);
        job.setStatus(GenerationJobStatus.SUCCEEDED);
        job.setAttemptCount(1);
        job.setCreatedAt(now.minusSeconds(1));
        job.setUpdatedAt(now);
        job.setCompletedAt(now);
        GenerationArtifact artifact = new GenerationArtifact();
        artifact.setArtifactId("22222222-2222-2222-2222-222222222222");
        artifact.setResourceUri("omni-office://artifacts/22222222-2222-2222-2222-222222222222");
        artifact.setFileName("document.docx");
        artifact.setMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        artifact.setSize(100);
        artifact.setSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        job.setArtifacts(Collections.singletonList(artifact));
        return job;
    }

    private String hmac(String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        byte[] digest = mac.doFinal(body);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
