package cn.bugstack.application.ai.evaluation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一次评测运行的逐用例结果和汇总通过率。 */
public final class AiEvaluationReport {

    private final Instant createdAt;
    private final List<CaseResult> cases;

    public AiEvaluationReport(Instant createdAt, List<CaseResult> cases) {
        this.createdAt = createdAt;
        this.cases = Collections.unmodifiableList(new ArrayList<>(cases));
    }

    public Instant getCreatedAt() { return createdAt; }
    public List<CaseResult> getCases() { return cases; }
    public long getPassed() { return cases.stream().filter(CaseResult::isPassed).count(); }
    public double getPassRate() { return cases.isEmpty() ? 0 : (double) getPassed() / cases.size(); }

    public static final class CaseResult {
        private final String id;
        private final boolean passed;
        private final int attempts;
        private final long durationMillis;
        private final List<String> violations;

        public CaseResult(String id, boolean passed, int attempts, long durationMillis, List<String> violations) {
            this.id = id;
            this.passed = passed;
            this.attempts = attempts;
            this.durationMillis = durationMillis;
            this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
        }
        public String getId() { return id; }
        public boolean isPassed() { return passed; }
        public int getAttempts() { return attempts; }
        public long getDurationMillis() { return durationMillis; }
        public List<String> getViolations() { return violations; }
    }
}
