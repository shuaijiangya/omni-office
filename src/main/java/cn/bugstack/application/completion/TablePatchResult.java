package cn.bugstack.application.completion;

import java.nio.file.Path;

/** 表格补丁执行结果。 */
public final class TablePatchResult {

    private final Path outputPath;
    private final int updatedRows;
    private final int appendedRows;

    /** 创建执行结果。 */
    public TablePatchResult(Path outputPath, int updatedRows, int appendedRows) {
        this.outputPath = outputPath;
        this.updatedRows = updatedRows;
        this.appendedRows = appendedRows;
    }

    /** @return 输出 DOCX */
    public Path getOutputPath() {
        return outputPath;
    }

    /** @return 更新的已有行数 */
    public int getUpdatedRows() {
        return updatedRows;
    }

    /** @return 新增行数 */
    public int getAppendedRows() {
        return appendedRows;
    }
}
