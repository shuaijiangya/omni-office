package cn.bugstack.application.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DocumentSpec 校验结果。
 */
public final class DocumentSpecValidationResult {

    private final List<DocumentSpecViolation> violations;

    public DocumentSpecValidationResult(List<DocumentSpecViolation> violations) {
        this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
    }

    public boolean isValid() {
        return violations.isEmpty();
    }

    public List<DocumentSpecViolation> getViolations() {
        return violations;
    }

    public void throwIfInvalid() {
        if (!isValid()) {
            throw new DocumentSpecValidationException(violations);
        }
    }
}
