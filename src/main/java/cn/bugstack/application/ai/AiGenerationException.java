package cn.bugstack.application.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** AI 调用失败或输出在限定次数内始终无效。 */
public final class AiGenerationException extends IllegalStateException {

    private final AiGenerationMode mode;
    private final int attempts;
    private final List<String> validationErrors;

    public AiGenerationException(String message, AiGenerationMode mode, int attempts,
                                 List<String> validationErrors, Throwable cause) {
        super(message, cause);
        this.mode = mode;
        this.attempts = attempts;
        this.validationErrors = validationErrors == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(validationErrors));
    }

    public AiGenerationMode getMode() {
        return mode;
    }

    public int getAttempts() {
        return attempts;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
