package cn.bugstack.application.generation.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileWebhookDeliveryRepositoryLeaseTest {

    @Test
    void claimsDueDeliveryOnceAndRecoversExpiredLease() throws Exception {
        FileWebhookDeliveryRepository repository = new FileWebhookDeliveryRepository(
                Files.createTempDirectory("webhook-lease"));
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        WebhookDeliveryRecord event = repository.enqueue(record(now));

        WebhookDeliveryRecord claimed = repository.claimDue(
                "worker-a", now, now.plusSeconds(30), 10).get(0);
        assertEquals(1L, event.getVersion());
        assertEquals(2L, claimed.getVersion());
        assertFalse(repository.claimDue("worker-b", now.plusSeconds(15),
                now.plusSeconds(45), 10).iterator().hasNext());

        WebhookDeliveryRecord recovered = repository.claimDue(
                "worker-b", now.plusSeconds(31), now.plusSeconds(61), 10).get(0);
        assertEquals("worker-b", recovered.getLeaseOwner());
        assertThrows(IllegalStateException.class,
                () -> repository.saveClaimed(recovered, "worker-a", now.plusSeconds(32)));

        recovered.setStatus(WebhookDeliveryStatus.DELIVERED);
        recovered.setDeliveredAt(now.plusSeconds(32));
        recovered.setNextAttemptAt(null);
        recovered.setUpdatedAt(now.plusSeconds(32));
        WebhookDeliveryRecord delivered = repository.saveClaimed(
                recovered, "worker-b", now.plusSeconds(32));
        assertEquals(WebhookDeliveryStatus.DELIVERED, delivered.getStatus());
        assertFalse(repository.claimDue("worker-c", now.plusSeconds(100),
                now.plusSeconds(130), 10).iterator().hasNext());
    }

    private WebhookDeliveryRecord record(Instant now) {
        WebhookDeliveryRecord value = new WebhookDeliveryRecord();
        value.setEventId(UUID.randomUUID().toString());
        value.setEventType("generation.succeeded");
        value.setTenantId("tenant-a");
        value.setWebhookId("erp");
        value.setJobId(UUID.randomUUID().toString());
        value.setPayload(new ObjectMapper().createObjectNode().put("eventId", value.getEventId()));
        value.setStatus(WebhookDeliveryStatus.PENDING);
        value.setNextAttemptAt(now);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return value;
    }
}
