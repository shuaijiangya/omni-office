package cn.bugstack.application.external;

/** 调用了目录中不存在的外部工具。 */
public final class UnknownExternalToolException extends IllegalArgumentException {

    public UnknownExternalToolException(String toolName) {
        super("unknown external tool: " + toolName);
    }
}
