package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一个段落锚点上的受控文本补全计划。 */
public final class ParagraphPatchPlan {

    private final String paragraphId;
    private final ParagraphPatchOperation operation;
    private final TableOverwritePolicy overwritePolicy;
    private final List<String> texts;

    /** 创建段落补丁。 */
    public ParagraphPatchPlan(String paragraphId, ParagraphPatchOperation operation,
                              TableOverwritePolicy overwritePolicy, List<String> texts) {
        this.paragraphId = paragraphId;
        this.operation = operation;
        this.overwritePolicy = overwritePolicy;
        this.texts = Collections.unmodifiableList(new ArrayList<>(texts));
    }

    /** @return 目标段落标识 */
    public String getParagraphId() { return paragraphId; }

    /** @return 补全操作 */
    public ParagraphPatchOperation getOperation() { return operation; }

    /** @return 覆盖策略 */
    public TableOverwritePolicy getOverwritePolicy() { return overwritePolicy; }

    /** @return 待写入文本；插入操作可包含多个段落 */
    public List<String> getTexts() { return texts; }
}
