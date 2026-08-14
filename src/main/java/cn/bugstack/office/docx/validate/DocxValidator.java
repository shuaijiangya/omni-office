package cn.bugstack.office.docx.validate;

import cn.bugstack.office.docx.model.CaptionNode;
import cn.bugstack.office.docx.model.DocumentNode;
import cn.bugstack.office.docx.model.DocxBlock;
import cn.bugstack.office.docx.model.ParagraphNode;
import cn.bugstack.office.docx.model.PageBreakNode;
import cn.bugstack.office.docx.model.SectionNode;
import cn.bugstack.office.docx.model.TableCellNode;
import cn.bugstack.office.docx.model.TableNode;
import cn.bugstack.office.docx.model.TableRowNode;

/**
 * docx 内部组件树校验器。
 *
 * <p>保存前调用该校验器，确保文档至少包含一个 Section，并检查表格结构等基础约束。</p>
 */
public class DocxValidator {

    /**
     * 创建 docx 内部组件树校验器。
     */
    public DocxValidator() {
    }

    /**
     * 校验文档根节点。
     *
     * @param document 文档根节点
     * @return 校验结果
     */
    public ValidationResult validate(DocumentNode document) {
        ValidationResult result = new ValidationResult();
        if (document.getSections().isEmpty()) {
            result.addMessage("document must contain at least one section");
            return result;
        }
        for (int i = 0; i < document.getSections().size(); i++) {
            validateSection(document.getSections().get(i), i, result);
        }
        return result;
    }

    /**
     * 校验章节内各块级节点的结构约束。
     *
     * @param section 待校验章节
     * @param sectionIndex 章节序号
     * @param result 校验结果收集器
     */
    private void validateSection(SectionNode section, int sectionIndex, ValidationResult result) {
        for (int i = 0; i < section.getBlocks().size(); i++) {
            DocxBlock block = section.getBlocks().get(i);
            if (block instanceof TableNode) {
                validateTable((TableNode) block, "section[" + sectionIndex + "].block[" + i + "]", result);
            } else if (!(block instanceof ParagraphNode) && !(block instanceof CaptionNode)
                    && !(block instanceof PageBreakNode)) {
                result.addMessage("unsupported block at section[" + sectionIndex + "].block[" + i + "]");
            }
        }
    }

    /**
     * 校验表格的行、列宽和合并单元格定义。
     *
     * @param table 待校验表格
     * @param path 表格节点路径
     * @param result 校验结果收集器
     */
    private void validateTable(TableNode table, String path, ValidationResult result) {
        if (table.getRows().isEmpty()) {
            result.addMessage(path + " table must contain at least one row");
            return;
        }

        int expectedCells = logicalCellCount(table.getRows().get(0));
        if (expectedCells == 0) {
            result.addMessage(path + " first row must contain at least one cell");
        }

        for (int i = 0; i < table.getRows().size(); i++) {
            TableRowNode row = table.getRows().get(i);
            int actualCells = logicalCellCount(row);
            if (actualCells != expectedCells) {
                result.addMessage(path + ".row[" + i + "] cell count must be " + expectedCells
                        + " but was " + actualCells);
            }
            for (int j = 0; j < row.getCells().size(); j++) {
                validateCell(row.getCells().get(j), path + ".row[" + i + "].cell[" + j + "]", result);
            }
        }
    }

    /**
     * 计算表格行经横向合并后的逻辑单元格数量。
     *
     * @param row 表格行
     * @return 逻辑单元格数量
     */
    private int logicalCellCount(TableRowNode row) {
        int count = 0;
        for (TableCellNode cell : row.getCells()) {
            count += cell.getColumnSpan();
        }
        return count;
    }

    /**
     * 校验单元格合并状态及其内部块级内容。
     *
     * @param cell 待校验单元格
     * @param path 单元格节点路径
     * @param result 校验结果收集器
     */
    private void validateCell(TableCellNode cell, String path, ValidationResult result) {
        for (int i = 0; i < cell.getBlocks().size(); i++) {
            DocxBlock block = cell.getBlocks().get(i);
            if (block instanceof TableNode) {
                validateTable((TableNode) block, path + ".block[" + i + "]", result);
            } else if (!(block instanceof ParagraphNode) && !(block instanceof CaptionNode)) {
                result.addMessage("unsupported block at " + path + ".block[" + i + "]");
            }
        }
    }
}
