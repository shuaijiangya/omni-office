package cn.bugstack.application.completion;

/** 上传文档段落中的一个直接 Run 文本片段。 */
public final class SourceRunSnapshot {
    private final int runIndex;
    private final String text;
    private final boolean plainText;

    /** 创建以零为起点的 Run 摘要。 */
    public SourceRunSnapshot(int runIndex, String text, boolean plainText) {
        this.runIndex = runIndex;
        this.text = text;
        this.plainText = plainText;
    }

    /** @return 段落内直接 Run 的序号 */
    public int getRunIndex() { return runIndex; }

    /** @return 该 Run 的原始文本 */
    public String getText() { return text; }

    /** @return 是否为不含分页等特殊控制字符的纯文本 Run */
    public boolean isPlainText() { return plainText; }
}
