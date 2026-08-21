package cn.bugstack.application.generation;

/** 同一身份复用了幂等键但请求内容不一致。 */
public final class GenerationJobConflictException extends RuntimeException {

    /**
     * 创建生成任务冲突异常。
     *
     * @param message 冲突原因
     */
    public GenerationJobConflictException(String message) {
        super(message);
    }
}
