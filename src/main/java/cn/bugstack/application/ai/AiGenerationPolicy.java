package cn.bugstack.application.ai;

/** 内部 AI 请求和校验重试的资源边界。 */
public final class AiGenerationPolicy {

    private final int maxAttempts;
    private final int maxInstructionCharacters;
    private final int maxContextCharacters;
    private final int maxOutputCharacters;

    public AiGenerationPolicy(int maxAttempts, int maxInstructionCharacters,
                              int maxContextCharacters, int maxOutputCharacters) {
        this.maxAttempts = positive(maxAttempts, "max attempts");
        if (maxAttempts > 5) {
            throw new IllegalArgumentException("max attempts must not exceed 5");
        }
        this.maxInstructionCharacters = positive(maxInstructionCharacters, "max instruction characters");
        this.maxContextCharacters = positive(maxContextCharacters, "max context characters");
        this.maxOutputCharacters = positive(maxOutputCharacters, "max output characters");
    }

    public static AiGenerationPolicy defaults() {
        return new AiGenerationPolicy(2, 20_000, 100_000, 2_000_000);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getMaxInstructionCharacters() {
        return maxInstructionCharacters;
    }

    public int getMaxContextCharacters() {
        return maxContextCharacters;
    }

    public int getMaxOutputCharacters() {
        return maxOutputCharacters;
    }

    private static int positive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }
}
