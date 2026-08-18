package cn.bugstack.application.document;

/**
 * DocumentSpec 中一个可定位的协议或语义错误。
 */
public final class DocumentSpecViolation {

    private final String path;
    private final String code;
    private final String message;

    public DocumentSpecViolation(String path, String code, String message) {
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
