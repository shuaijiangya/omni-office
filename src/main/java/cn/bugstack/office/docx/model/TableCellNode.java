package cn.bugstack.office.docx.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 表格单元格节点，内部继续承载块级节点。
 *
 * <p>这使单元格中可以放段落、图片所在段落、甚至嵌套表格。</p>
 */
public class TableCellNode implements DocxNode {

    private final List<DocxBlock> blocks = new ArrayList<>();
    /** 单元格横向合并的列数。 */
    private int columnSpan = 1;
    /** 单元格纵向合并状态。 */
    private TableVerticalMerge verticalMerge = TableVerticalMerge.NONE;
    /** 单元格内容的垂直对齐方式。 */
    private TableCellVerticalAlignment verticalAlignment = TableCellVerticalAlignment.TOP;

    /**
     * 创建空的表格单元格节点。
     */
    public TableCellNode() {
    }

    /**
     * 追加单元格内的块级节点。
     *
     * @param block 块级节点
     */
    public void addBlock(DocxBlock block) {
        blocks.add(block);
    }

    /**
     * 获取单元格内的块级节点。
     *
     * @return 不可修改的块级节点列表
     */
    public List<DocxBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    /**
     * 获取单元格跨列数。
     *
     * @return 跨列数，最小为 1
     */
    public int getColumnSpan() {
        return columnSpan;
    }

    /**
     * 设置单元格跨列数。
     *
     * @param columnSpan 跨列数，最小为 1
     */
    public void setColumnSpan(int columnSpan) {
        if (columnSpan < 1) {
            throw new IllegalArgumentException("columnSpan must be at least 1: " + columnSpan);
        }
        this.columnSpan = columnSpan;
    }

    /**
     * 获取纵向合并方式。
     *
     * @return 纵向合并方式
     */
    public TableVerticalMerge getVerticalMerge() {
        return verticalMerge;
    }

    /**
     * 设置纵向合并方式。
     *
     * @param verticalMerge 纵向合并方式
     */
    public void setVerticalMerge(TableVerticalMerge verticalMerge) {
        this.verticalMerge = verticalMerge;
    }

    /**
     * 获取单元格垂直对齐方式。
     *
     * @return 单元格垂直对齐方式
     */
    public TableCellVerticalAlignment getVerticalAlignment() {
        return verticalAlignment;
    }

    /**
     * 设置单元格垂直对齐方式。
     *
     * @param verticalAlignment 单元格垂直对齐方式
     */
    public void setVerticalAlignment(TableCellVerticalAlignment verticalAlignment) {
        this.verticalAlignment = verticalAlignment;
    }
}
