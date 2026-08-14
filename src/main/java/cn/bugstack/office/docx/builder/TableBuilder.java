package cn.bugstack.office.docx.builder;

import cn.bugstack.office.docx.model.ParagraphNode;
import cn.bugstack.office.docx.model.TableCellNode;
import cn.bugstack.office.docx.model.TableNode;
import cn.bugstack.office.docx.model.TableRowNode;
import cn.bugstack.office.docx.model.TextRunInline;

import java.util.function.Consumer;

/**
 * Table 级 Builder，用于向表格中追加表头和数据行。
 *
 * @param <P> 父级 Builder 类型
 */
public class TableBuilder<P> {

    /** 表格所属的父构建器。 */
    private final P parent;
    /** 正在构建的表格节点。 */
    private final TableNode table;

    /**
     * 创建表格 Builder。
     *
     * @param parent 父级 Builder
     * @param table 当前表格节点
     */
    public TableBuilder(P parent, TableNode table) {
        this.parent = parent;
        this.table = table;
    }

    /**
     * 设置表格样式名称。
     *
     * @param styleName 表格样式名称，例如 {@code TableNormal}、{@code TableHeader}
     * @return 当前表格 Builder
     */
    public TableBuilder<P> style(String styleName) {
        table.setStyleName(styleName);
        return this;
    }

    /**
     * 设置表格列宽。
     *
     * @param widths 列宽数组，单位为 point
     * @return 当前表格 Builder
     */
    public TableBuilder<P> widths(double... widths) {
        table.setColumnWidths(widths);
        return this;
    }

    /**
     * 追加表头行。
     *
     * @param values 表头单元格文本
     * @return 当前表格 Builder
     */
    public TableBuilder<P> headers(String... values) {
        return row(values);
    }

    /**
     * 追加一行纯文本单元格。
     *
     * @param values 单元格文本
     * @return 当前表格 Builder
     */
    public TableBuilder<P> row(String... values) {
        TableRowNode row = new TableRowNode();
        for (String value : values) {
            TableCellNode cell = new TableCellNode();
            ParagraphNode paragraph = new ParagraphNode();
            paragraph.addInline(new TextRunInline(value));
            cell.addBlock(paragraph);
            row.addCell(cell);
        }
        table.addRow(row);
        return this;
    }

    /**
     * 追加一行自定义单元格。
     *
     * <p>该方式允许在单元格中创建段落、图片和后续扩展的块级内容。</p>
     *
     * @param customizer 行构建回调
     * @return 当前表格 Builder
     */
    public TableBuilder<P> row(Consumer<RowBuilder> customizer) {
        TableRowNode row = new TableRowNode();
        table.addRow(row);
        customizer.accept(new RowBuilder(row));
        return this;
    }

    /**
     * 结束当前表格构建并返回父级 Builder。
     *
     * @return 父级 Builder
     */
    public P end() {
        return parent;
    }
}
