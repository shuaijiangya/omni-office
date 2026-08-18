package cn.bugstack.application.template;

/** 模板定义或模板数据中的定位错误。 */
public final class DocumentTemplateViolation {

    private final String path;
    private final String code;
    private final String message;

    public DocumentTemplateViolation(String path, String code, String message) {
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
