package cn.bugstack.application.diagram;

/** DiagramSpec 校验错误。 */
public final class DiagramSpecViolation {

    private final String path;
    private final String code;
    private final String message;

    public DiagramSpecViolation(String path, String code, String message) {
        this.path = path;
        this.code = code;
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return path + " [" + code + "] " + message;
    }
}
