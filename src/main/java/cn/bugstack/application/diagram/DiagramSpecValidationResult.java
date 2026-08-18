package cn.bugstack.application.diagram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** DiagramSpec 校验结果。 */
public final class DiagramSpecValidationResult {

    private final List<DiagramSpecViolation> violations;

    DiagramSpecValidationResult(List<DiagramSpecViolation> violations) {
        this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
    }

    public boolean isValid() {
        return violations.isEmpty();
    }

    public List<DiagramSpecViolation> getViolations() {
        return violations;
    }

    public void throwIfInvalid() {
        if (!isValid()) {
            throw new IllegalArgumentException("invalid diagram spec: " + violations);
        }
    }
}
