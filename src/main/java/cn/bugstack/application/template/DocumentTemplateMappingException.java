package cn.bugstack.application.template;

/** 模板占位符或结构指令无法安全映射时抛出。 */
public final class DocumentTemplateMappingException extends IllegalArgumentException {

    private final String templatePath;

    public DocumentTemplateMappingException(String templatePath, String message) {
        super("template mapping failed at " + templatePath + ": " + message);
        this.templatePath = templatePath;
    }

    public String getTemplatePath() {
        return templatePath;
    }
}
