package cn.bugstack.application.generation.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/** 单线程持久化 Webhook 投递器，采用至少一次语义；接收方应按 eventId 去重。 */
public final class WebhookDispatcher implements AutoCloseable {

    private final WebhookEndpointRegistry endpoints;
    private final WebhookDeliveryRepository repository;
    private final HttpClient client;
    private final Duration requestTimeout;
    private final Duration retryBase;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final String workerId = "webhook:" + UUID.randomUUID();
    private final Duration leaseDuration = Duration.ofSeconds(30);

    /**
     * 使用默认 HTTP 客户端、超时和退避策略创建投递器。
     *
     * @param endpoints 预注册端点目录
     * @param repository Webhook Outbox 仓储
     */
    public WebhookDispatcher(WebhookEndpointRegistry endpoints, WebhookDeliveryRepository repository) {
        this(endpoints, repository, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                Duration.ofSeconds(10), Duration.ofSeconds(5), Clock.systemUTC(), new ObjectMapper());
    }

    WebhookDispatcher(WebhookEndpointRegistry endpoints, WebhookDeliveryRepository repository,
                      HttpClient client, Duration requestTimeout, Duration retryBase,
                      Clock clock, ObjectMapper mapper) {
        if (endpoints == null || repository == null || client == null || requestTimeout == null
                || requestTimeout.isNegative() || requestTimeout.isZero() || retryBase == null
                || retryBase.isNegative() || clock == null || mapper == null) {
            throw new IllegalArgumentException("webhook dispatcher dependencies are invalid");
        }
        this.endpoints = endpoints;
        this.repository = repository;
        this.client = client;
        this.requestTimeout = requestTimeout;
        this.retryBase = retryBase;
        this.clock = clock;
        this.mapper = mapper.copy();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "omni-webhook-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 启动每秒一次的后台投递轮询。 */
    public void start() {
        scheduler.scheduleWithFixedDelay(() -> {
            try { dispatchDue(); } catch (RuntimeException ignored) { }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * 同步领取并投递一个到期事件。
     *
     * @return 本轮处理的事件数
     */
    public int dispatchDue() {
        int processed = 0;
        Instant now = clock.instant();
        for (WebhookDeliveryRecord record : repository.claimDue(
                workerId, now, now.plus(leaseDuration), 1)) {
            dispatch(record);
            processed++;
        }
        return processed;
    }

    private void dispatch(WebhookDeliveryRecord record) {
        WebhookEndpoint endpoint;
        try {
            endpoint = endpoints.require(record.getTenantId(), record.getWebhookId());
        } catch (RuntimeException e) {
            terminal(record, null, "webhook endpoint is no longer registered");
            return;
        }
        try {
            byte[] body = mapper.writeValueAsBytes(record.getPayload());
            String timestamp = Long.toString(clock.instant().getEpochSecond());
            String signature = sign(endpoint.getSecret(), timestamp, body);
            HttpRequest request = HttpRequest.newBuilder(endpoint.getUrl()).timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "omni-office-webhook/1.0")
                    .header("X-Omni-Event-Id", record.getEventId())
                    .header("X-Omni-Event-Type", record.getEventType())
                    .header("X-Omni-Timestamp", timestamp)
                    .header("X-Omni-Signature", "v1=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivered(record, response.statusCode());
            } else if (response.statusCode() == 408 || response.statusCode() == 429
                    || response.statusCode() >= 500) {
                retry(record, response.statusCode(), "webhook returned retryable HTTP status");
            } else {
                terminal(record, response.statusCode(), "webhook returned permanent HTTP status");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            retry(record, null, "webhook delivery was interrupted");
        } catch (IOException | RuntimeException e) {
            retry(record, null, safe(e.getMessage()));
        }
    }

    private void delivered(WebhookDeliveryRecord record, int status) {
        Instant now = clock.instant();
        record.setAttemptCount(record.getAttemptCount() + 1);
        record.setStatus(WebhookDeliveryStatus.DELIVERED);
        record.setResponseStatus(status);
        record.setLastError(null);
        record.setDeliveredAt(now);
        record.setNextAttemptAt(null);
        record.setUpdatedAt(now);
        repository.saveClaimed(record, workerId, now);
    }

    private void retry(WebhookDeliveryRecord record, Integer status, String error) {
        int attempts = record.getAttemptCount() + 1;
        if (attempts >= record.getMaxAttempts()) {
            terminal(record, status, error);
            return;
        }
        Instant now = clock.instant();
        long multiplier = 1L << Math.min(attempts - 1, 10);
        Duration delay = retryBase.multipliedBy(multiplier);
        if (delay.compareTo(Duration.ofHours(1)) > 0) delay = Duration.ofHours(1);
        record.setAttemptCount(attempts);
        record.setStatus(WebhookDeliveryStatus.RETRYING);
        record.setResponseStatus(status);
        record.setLastError(safe(error));
        record.setNextAttemptAt(now.plus(delay));
        record.setUpdatedAt(now);
        repository.saveClaimed(record, workerId, now);
    }

    private void terminal(WebhookDeliveryRecord record, Integer status, String error) {
        record.setAttemptCount(record.getAttemptCount() + 1);
        record.setStatus(WebhookDeliveryStatus.DEAD);
        record.setResponseStatus(status);
        record.setLastError(safe(error));
        record.setNextAttemptAt(null);
        Instant now = clock.instant();
        record.setUpdatedAt(now);
        repository.saveClaimed(record, workerId, now);
    }

    private String sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            byte[] digest = mac.doFinal(body);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "unknown webhook delivery error";
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    /** 停止后台投递线程。 */
    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
