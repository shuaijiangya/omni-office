package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 自然语言表格补全规划结果。 */
public final class TableCompletionPlanningResult {

    private final TableCompletionStatus status;
    private final TableBindingSpec binding;
    private final TablePatchPlan patchPlan;
    private final double confidence;
    private final String reason;
    private final List<String> questions;
    private final int attempts;

    /** 创建规划结果。 */
    public TableCompletionPlanningResult(TableCompletionStatus status, TableBindingSpec binding,
                                         TablePatchPlan patchPlan, double confidence, String reason,
                                         List<String> questions, int attempts) {
        this.status = status;
        this.binding = binding;
        this.patchPlan = patchPlan;
        this.confidence = confidence;
        this.reason = reason;
        this.questions = Collections.unmodifiableList(new ArrayList<>(questions));
        this.attempts = attempts;
    }

    /** @return 规划状态 */
    public TableCompletionStatus getStatus() {
        return status;
    }

    /** @return 已确认到本地校验阶段的表格绑定 */
    public TableBindingSpec getBinding() {
        return binding;
    }

    /** @return 已确认到本地校验阶段的补丁计划 */
    public TablePatchPlan getPatchPlan() {
        return patchPlan;
    }

    /** @return 模型置信度，范围 0 到 1 */
    public double getConfidence() {
        return confidence;
    }

    /** @return 选择目标和操作方式的原因 */
    public String getReason() {
        return reason;
    }

    /** @return 需要补充的信息 */
    public List<String> getQuestions() {
        return questions;
    }

    /** @return 实际模型尝试次数 */
    public int getAttempts() {
        return attempts;
    }
}
