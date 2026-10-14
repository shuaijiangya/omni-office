package cn.bugstack.application.completion;

/** 在不触碰其他格式片段的情况下替换段落中的一个 Run。 */
public final class RunPatchPlan {
    private final String paragraphId;
    private final int runIndex;
    private final String expectedText;
    private final String text;
    private final TableOverwritePolicy overwritePolicy;

    /** 创建一个带源文本前置条件的 Run 补丁。 */
    public RunPatchPlan(String paragraphId, int runIndex, String expectedText, String text,
                        TableOverwritePolicy overwritePolicy) {
        this.paragraphId = paragraphId;
        this.runIndex = runIndex;
        this.expectedText = expectedText;
        this.text = text;
        this.overwritePolicy = overwritePolicy;
    }

    /** @return 段落 ID */
    public String getParagraphId() { return paragraphId; }

    /** @return 从零开始的直接 Run 序号 */
    public int getRunIndex() { return runIndex; }

    /** @return 规划时该 Run 的原始文本 */
    public String getExpectedText() { return expectedText; }

    /** @return 写入的新文本 */
    public String getText() { return text; }

    /** @return 覆盖策略 */
    public TableOverwritePolicy getOverwritePolicy() { return overwritePolicy; }
}
