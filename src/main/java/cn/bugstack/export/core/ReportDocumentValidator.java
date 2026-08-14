package cn.bugstack.export.core;

import cn.bugstack.export.document.ReportClassDesignTable;
import cn.bugstack.export.document.ReportDocument;
import cn.bugstack.export.document.ReportElement;
import cn.bugstack.export.document.ReportImage;
import cn.bugstack.export.document.ReportListItem;
import cn.bugstack.export.document.ReportParagraph;
import cn.bugstack.export.document.ReportSection;
import cn.bugstack.export.document.ReportTable;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 报告语义树校验器。
 */
public final class ReportDocumentValidator {

    /**
     * 创建报告语义文档校验器。
     */
    public ReportDocumentValidator() {
    }

    /**
     * 校验报告语义文档。
     *
     * @param document 报告文档
     * @return 校验错误列表；空列表表示有效
     */
    public List<String> validate(ReportDocument document) {
        List<String> errors = new ArrayList<>();
        if (document == null) {
            errors.add("report document must not be null");
            return errors;
        }
        if (!hasText(document.getTitle())) {
            errors.add("report document title must not be blank");
        }
        for (ReportSection section : document.getSections()) {
            validateSection(section, 1, "root", errors);
        }
        return errors;
    }

    /**
     * 递归校验章节及其包含的语义元素。
     *
     * @param section 待校验章节
     * @param depth 当前章节深度
     * @param path 当前元素路径
     * @param errors 校验错误收集器
     */
    private void validateSection(ReportSection section, int depth, String path, List<String> errors) {
        if (section == null) {
            errors.add("report section must not be null at " + path);
            return;
        }
        if (!hasText(section.getTitle())) {
            errors.add("report section title must not be blank at " + path);
        }
        if (depth > 9) {
            errors.add("report section depth must not exceed 9 at " + path);
        }
        int index = 0;
        for (ReportElement element : section.getElements()) {
            String elementPath = path + "/" + section.getTitle() + "[" + index++ + "]";
            if (element == null) {
                errors.add("report element must not be null at " + elementPath);
            } else if (element instanceof ReportSection) {
                validateSection((ReportSection) element, depth + 1, elementPath, errors);
            } else if (element instanceof ReportParagraph) {
                if (!hasText(((ReportParagraph) element).getText())) {
                    errors.add("report paragraph text must not be blank at " + elementPath);
                }
            } else if (element instanceof ReportListItem) {
                if (!hasText(((ReportListItem) element).getText())) {
                    errors.add("report list item text must not be blank at " + elementPath);
                }
            } else if (element instanceof ReportTable) {
                validateTable((ReportTable) element, elementPath, errors);
            } else if (element instanceof ReportImage) {
                ReportImage image = (ReportImage) element;
                if (!hasText(image.getSource())) {
                    errors.add("report image source must not be blank at " + elementPath);
                }
                if ((image.getWidth() == null) != (image.getHeight() == null)) {
                    errors.add("report image width and height must be configured together at " + elementPath);
                }
                if (image.getWidth() != null && image.getHeight() != null
                        && (image.getWidth() <= 0 || image.getHeight() <= 0)) {
                    errors.add("report image width and height must be greater than zero at " + elementPath);
                }
            } else if (element instanceof ReportClassDesignTable) {
                ReportClassDesignTable table = (ReportClassDesignTable) element;
                if (table.getSourceRoot() == null || !hasText(table.getClassName())) {
                    errors.add("class design table source root and class name are required at " + elementPath);
                } else if (!Files.isDirectory(table.getSourceRoot())) {
                    errors.add("class design table source root must be an existing directory at " + elementPath);
                } else if (!hasText(table.getTitle())) {
                    errors.add("class design table title must not be blank at " + elementPath);
                }
            }
        }
    }

    /**
     * 校验表头、数据行及列宽定义是否一致。
     *
     * @param table 待校验表格
     * @param path 当前元素路径
     * @param errors 校验错误收集器
     */
    private void validateTable(ReportTable table, String path, List<String> errors) {
        int columnCount = table.getHeaders().size();
        if (columnCount == 0) {
            errors.add("report table headers must not be empty at " + path);
            return;
        }
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            if (!hasText(table.getHeaders().get(columnIndex))) {
                errors.add("report table header must not be blank at " + path + "/header[" + columnIndex + "]");
            }
        }
        if (table.getColumnWidths().length > 0 && table.getColumnWidths().length != columnCount) {
            errors.add("report table width count must match header count at " + path);
        }
        for (double width : table.getColumnWidths()) {
            if (Double.isNaN(width) || Double.isInfinite(width) || width <= 0) {
                errors.add("report table width must be a finite positive number at " + path);
                break;
            }
        }
        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            if (table.getRows().get(rowIndex) == null || table.getRows().get(rowIndex).size() != columnCount) {
                errors.add("report table row width must match header count at " + path + "/row[" + rowIndex + "]");
            }
        }
    }

    /**
     * 判断字符串是否包含非空白内容。
     *
     * @param value 待判断文本
     * @return 包含有效文本时返回 {@code true}
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
