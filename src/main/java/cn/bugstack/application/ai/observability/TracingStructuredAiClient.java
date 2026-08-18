package cn.bugstack.application.ai.observability;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.StructuredAiRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** 为任意 StructuredAiClient 增加隐私友好的调用轨迹。 */
public final class TracingStructuredAiClient implements StructuredAiClient {

    private final StructuredAiClient delegate;
    private final AiTraceStore store;
    private final String provider;
    private final String model;
    private final Clock clock;

    public TracingStructuredAiClient(StructuredAiClient delegate, AiTraceStore store,
                                     String provider, String model) {
        this(delegate, store, provider, model, Clock.systemUTC());
    }

    TracingStructuredAiClient(StructuredAiClient delegate, AiTraceStore store,
                              String provider, String model, Clock clock) {
        if (delegate == null || store == null || provider == null || model == null || clock == null) {
            throw new IllegalArgumentException("AI tracing dependencies are required");
        }
        this.delegate = delegate;
        this.store = store;
        this.provider = provider;
        this.model = model;
        this.clock = clock;
    }

    @Override
    public String generateJson(StructuredAiRequest request) {
        Instant started = clock.instant();
        AiCallTrace trace = new AiCallTrace();
        trace.setTraceId(UUID.randomUUID().toString());
        trace.setProvider(provider);
        trace.setModel(model);
        trace.setOperation(request.getOperation());
        trace.setAttempt(request.getAttempt());
        trace.setFeedbackCount(request.getValidationFeedback().size());
        trace.setInstructionSha256(sha256(request.getUserInstruction()));
        trace.setContextSha256(sha256(request.getContext() == null ? null : request.getContext().toString()));
        trace.setStartedAt(started);
        try {
            String output = delegate.generateJson(request);
            trace.setSuccess(true);
            trace.setOutputCharacters(output == null ? 0 : output.length());
            trace.setOutputSha256(sha256(output));
            return output;
        } catch (RuntimeException e) {
            trace.setSuccess(false);
            trace.setErrorType(e.getClass().getSimpleName());
            throw e;
        } finally {
            trace.setDurationMillis(Math.max(0, clock.millis() - started.toEpochMilli()));
            store.append(trace);
        }
    }

    private String sha256(String value) {
        if (value == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
