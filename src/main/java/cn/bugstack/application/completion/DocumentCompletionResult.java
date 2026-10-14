package cn.bugstack.application.completion;

import java.nio.file.Path;

/** 源 DOCX 多位置补全的保存结果。 */
public final class DocumentCompletionResult {

    private final Path outputPath;
    private final int paragraphsChanged;
    private final int rowsUpdated;
    private final int rowsAppended;
    private final int cellsChanged;
    private final int runsChanged;

    /** 创建保存结果。 */
    public DocumentCompletionResult(Path outputPath, int paragraphsChanged,
                                    int rowsUpdated, int rowsAppended) {
        this(outputPath, paragraphsChanged, rowsUpdated, rowsAppended, 0);
    }

    /** 创建包含定点单元格统计的保存结果。 */
    public DocumentCompletionResult(Path outputPath, int paragraphsChanged,
                                    int rowsUpdated, int rowsAppended, int cellsChanged) {
        this(outputPath, paragraphsChanged, rowsUpdated, rowsAppended, cellsChanged, 0);
    }

    /** 创建包含 Run 定点补全统计的保存结果。 */
    public DocumentCompletionResult(Path outputPath, int paragraphsChanged,
                                    int rowsUpdated, int rowsAppended, int cellsChanged, int runsChanged) {
        this.outputPath = outputPath;
        this.paragraphsChanged = paragraphsChanged;
        this.rowsUpdated = rowsUpdated;
        this.rowsAppended = rowsAppended;
        this.cellsChanged = cellsChanged;
        this.runsChanged = runsChanged;
    }

    /** @return 输出 DOCX 路径 */
    public Path getOutputPath() { return outputPath; }

    /** @return 修改或插入的段落数量 */
    public int getParagraphsChanged() { return paragraphsChanged; }

    /** @return 更新的表格行数 */
    public int getRowsUpdated() { return rowsUpdated; }

    /** @return 追加的表格行数 */
    public int getRowsAppended() { return rowsAppended; }

    /** @return 定点写入的单元格数量 */
    public int getCellsChanged() { return cellsChanged; }

    /** @return 定点写入的 Run 数量 */
    public int getRunsChanged() { return runsChanged; }
}
