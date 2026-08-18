package cn.bugstack.application.document;

/**
 * 防止 DocumentSpec 产生过深或过大的文档结构。
 */
public final class DocumentSpecLimits {

    private final int maxSections;
    private final int maxSectionDepth;
    private final int maxBlocks;
    private final int maxTextLength;
    private final int maxListItems;
    private final int maxTableRows;
    private final int maxTableColumns;

    public DocumentSpecLimits(int maxSections, int maxSectionDepth, int maxBlocks, int maxTextLength,
                              int maxListItems, int maxTableRows, int maxTableColumns) {
        this.maxSections = positive(maxSections, "max sections");
        this.maxSectionDepth = positive(maxSectionDepth, "max section depth");
        this.maxBlocks = positive(maxBlocks, "max blocks");
        this.maxTextLength = positive(maxTextLength, "max text length");
        this.maxListItems = positive(maxListItems, "max list items");
        this.maxTableRows = positive(maxTableRows, "max table rows");
        this.maxTableColumns = positive(maxTableColumns, "max table columns");
    }

    public static DocumentSpecLimits defaults() {
        return new DocumentSpecLimits(100, 9, 2000, 20000, 500, 1000, 50);
    }

    public int getMaxSections() {
        return maxSections;
    }

    public int getMaxSectionDepth() {
        return maxSectionDepth;
    }

    public int getMaxBlocks() {
        return maxBlocks;
    }

    public int getMaxTextLength() {
        return maxTextLength;
    }

    public int getMaxListItems() {
        return maxListItems;
    }

    public int getMaxTableRows() {
        return maxTableRows;
    }

    public int getMaxTableColumns() {
        return maxTableColumns;
    }

    private static int positive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }
}
