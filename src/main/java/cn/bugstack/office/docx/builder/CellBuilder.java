package cn.bugstack.office.docx.builder;

import cn.bugstack.office.docx.model.ParagraphNode;
import cn.bugstack.office.docx.model.TableCellVerticalAlignment;
import cn.bugstack.office.docx.model.TableCellNode;
import cn.bugstack.office.docx.model.TableVerticalMerge;

/**
 * Cell 级 Builder，用于向表格单元格中追加块级内容。
 *
 * <p>单元格内部同样使用 block 结构，因此单元格文本也通过段落承载。</p>
 */
public class CellBuilder {

    /** 所属行构建器。 */
    private final RowBuilder rowBuilder;
    /** 正在构建的单元格节点。 */
    private final TableCellNode cell;

    /**
     * 创建单元格 Builder。
     *
     * @param rowBuilder 父级行 Builder
     * @param cell 当前单元格节点
     */
    public CellBuilder(RowBuilder rowBuilder, TableCellNode cell) {
        this.rowBuilder = rowBuilder;
        this.cell = cell;
    }

    /**
     * 在当前单元格中追加一个段落。
     *
     * @return 段落 Builder
     */
    public ParagraphBuilder<CellBuilder> paragraph() {
        ParagraphNode paragraph = new ParagraphNode();
        cell.addBlock(paragraph);
        return new ParagraphBuilder<>(this, paragraph);
    }

    /**
     * 设置当前单元格跨列数。
     *
     * @param columnSpan 跨列数，最小为 1
     * @return 当前单元格 Builder
     */
    public CellBuilder colspan(int columnSpan) {
        cell.setColumnSpan(columnSpan);
        return this;
    }

    /**
     * 设置当前单元格纵向合并方式。
     *
     * @param verticalMerge 纵向合并方式
     * @return 当前单元格 Builder
     */
    public CellBuilder verticalMerge(TableVerticalMerge verticalMerge) {
        cell.setVerticalMerge(verticalMerge);
        return this;
    }

    /**
     * 设置当前单元格垂直对齐方式。
     *
     * @param alignment 垂直对齐方式
     * @return 当前单元格 Builder
     */
    public CellBuilder verticalAlign(TableCellVerticalAlignment alignment) {
        cell.setVerticalAlignment(alignment);
        return this;
    }

    /**
     * 结束当前单元格构建并返回父级行 Builder。
     *
     * @return 父级行 Builder
     */
    public RowBuilder end() {
        return rowBuilder;
    }
}
