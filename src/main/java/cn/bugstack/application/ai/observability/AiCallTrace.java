package cn.bugstack.application.ai.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 不保存提示词正文或模型输出正文的 AI 调用追踪记录。 */
public final class AiCallTrace {

    private String traceId;
    private String provider;
    private String model;
    private String operation;
    private int attempt;
    private int feedbackCount;
    private String instructionSha256;
    private String contextSha256;
    private String outputSha256;
    private int outputCharacters;
    private long durationMillis;
    private boolean success;
    private String errorType;
    private Instant startedAt;

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
    public int getFeedbackCount() { return feedbackCount; }
    public void setFeedbackCount(int feedbackCount) { this.feedbackCount = feedbackCount; }
    public String getInstructionSha256() { return instructionSha256; }
    public void setInstructionSha256(String instructionSha256) { this.instructionSha256 = instructionSha256; }
    public String getContextSha256() { return contextSha256; }
    public void setContextSha256(String contextSha256) { this.contextSha256 = contextSha256; }
    public String getOutputSha256() { return outputSha256; }
    public void setOutputSha256(String outputSha256) { this.outputSha256 = outputSha256; }
    public int getOutputCharacters() { return outputCharacters; }
    public void setOutputCharacters(int outputCharacters) { this.outputCharacters = outputCharacters; }
    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
}
