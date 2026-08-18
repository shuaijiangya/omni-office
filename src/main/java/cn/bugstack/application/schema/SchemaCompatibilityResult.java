package cn.bugstack.application.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** JSON Schema 向后兼容性检查结果。 */
public final class SchemaCompatibilityResult {

    private final List<String> violations;

    public SchemaCompatibilityResult(List<String> violations) {
        this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
    }

    public boolean isCompatible() { return violations.isEmpty(); }
    public List<String> getViolations() { return violations; }
}
