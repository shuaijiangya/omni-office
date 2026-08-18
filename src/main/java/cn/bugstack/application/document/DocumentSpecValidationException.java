package cn.bugstack.application.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 阻止无效 DocumentSpec 进入编译与渲染阶段。
 */
public final class DocumentSpecValidationException extends IllegalArgumentException {

    private final List<DocumentSpecViolation> violations;

    public DocumentSpecValidationException(List<DocumentSpecViolation> violations) {
        super(message(violations));
        this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
    }

    public List<DocumentSpecViolation> getViolations() {
        return violations;
    }

    private static String message(List<DocumentSpecViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return "document spec validation failed";
        }
        return "document spec validation failed: " + violations.stream()
                .map(DocumentSpecViolation::toString)
                .collect(Collectors.joining("; "));
    }
}
