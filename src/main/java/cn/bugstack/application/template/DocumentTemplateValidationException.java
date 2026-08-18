package cn.bugstack.application.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 模板定义、输入数据或映射结果不合法。 */
public final class DocumentTemplateValidationException extends IllegalArgumentException {

    private final List<DocumentTemplateViolation> violations;

    public DocumentTemplateValidationException(String message, List<DocumentTemplateViolation> violations) {
        super(message + ": " + violations);
        this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
    }

    public List<DocumentTemplateViolation> getViolations() {
        return violations;
    }
}
