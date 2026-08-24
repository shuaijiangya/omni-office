package cn.bugstack.application.document;

/** 提交生成任务前可返回给调用方的确定性文档规模估算。 */
public final class DocumentCostEstimate {
    private final int sections;
    private final int blocks;
    private final long textCharacters;
    private final long tableCells;
    private final int mediaBlocks;
    private final int estimatedPages;

    public DocumentCostEstimate(int sections, int blocks, long textCharacters,
                                long tableCells, int mediaBlocks, int estimatedPages) {
        this.sections = sections;
        this.blocks = blocks;
        this.textCharacters = textCharacters;
        this.tableCells = tableCells;
        this.mediaBlocks = mediaBlocks;
        this.estimatedPages = estimatedPages;
    }

    public int getSections() { return sections; }
    public int getBlocks() { return blocks; }
    public long getTextCharacters() { return textCharacters; }
    public long getTableCells() { return tableCells; }
    public int getMediaBlocks() { return mediaBlocks; }
    public int getEstimatedPages() { return estimatedPages; }
}
