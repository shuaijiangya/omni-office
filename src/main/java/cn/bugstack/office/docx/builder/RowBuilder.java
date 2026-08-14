package cn.bugstack.office.docx.builder;

import cn.bugstack.office.docx.model.TableCellNode;
import cn.bugstack.office.docx.model.TableRowNode;

import java.util.function.Consumer;

/**
 * Row 级 Builder，用于向表格行中追加单元格。
 */
public class RowBuilder {

    /** 正在构建的表格行节点。 */
    private final TableRowNode row;

    /**
     * 创建行 Builder。
     *
     * @param row 当前行节点
     */
    public RowBuilder(TableRowNode row) {
        this.row = row;
    }

    /**
     * 追加一个自定义单元格。
     *
     * @param customizer 单元格构建回调
     * @return 当前行 Builder
     */
    public RowBuilder cell(Consumer<CellBuilder> customizer) {
        return cell(1, customizer);
    }

    /**
     * 追加一个指定跨列数的自定义单元格。
     *
     * @param columnSpan 跨列数，最小为 1
     * @param customizer 单元格构建回调
     * @return 当前行 Builder
     */
    public RowBuilder cell(int columnSpan, Consumer<CellBuilder> customizer) {
        TableCellNode cell = new TableCellNode();
        cell.setColumnSpan(columnSpan);
        row.addCell(cell);
        customizer.accept(new CellBuilder(this, cell));
        return this;
    }
}
