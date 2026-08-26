package cn.bugstack.export.core;

import cn.bugstack.export.document.ReportClassDesignTable;
import cn.bugstack.export.document.ReportChart;
import cn.bugstack.export.document.ReportChartSeries;
import cn.bugstack.export.document.ReportChartType;
import cn.bugstack.export.document.ReportDocument;
import cn.bugstack.export.document.ReportDiagram;
import cn.bugstack.export.document.ReportDiagramEmbedMode;
import cn.bugstack.export.document.ReportElement;
import cn.bugstack.export.document.ReportImage;
import cn.bugstack.export.document.ReportListItem;
import cn.bugstack.export.document.ReportParagraph;
import cn.bugstack.export.document.ReportSection;
import cn.bugstack.export.document.ReportTable;
import cn.bugstack.export.document.ReportTableMerge;
import cn.bugstack.export.document.ReportTextRange;
import cn.bugstack.export.document.ReportTextRangeStyle;

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
                ReportParagraph paragraph = (ReportParagraph) element;
                validateParagraph(paragraph, elementPath, errors);
                validateFontColor(paragraph.getFontColor(), elementPath, errors);
            } else if (element instanceof ReportListItem) {
                ReportListItem item = (ReportListItem) element;
                if (!hasText(item.getText())) {
                    errors.add("report list item text must not be blank at " + elementPath);
                }
                validateFontColor(item.getFontColor(), elementPath, errors);
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
            } else if (element instanceof ReportDiagram) {
                validateDiagram((ReportDiagram) element, elementPath, errors);
            } else if (element instanceof ReportChart) {
                validateChart((ReportChart) element, elementPath, errors);
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

    /** 校验 Report 段落的单文本或多文本范围内容。 */
    private void validateParagraph(ReportParagraph paragraph, String path, List<String> errors) {
        boolean hasSingleText = hasText(paragraph.getText());
        boolean hasRanges = paragraph.getTextRanges() != null && !paragraph.getTextRanges().isEmpty();
        if (hasSingleText == hasRanges) {
            errors.add("report paragraph must configure exactly one of text and textRanges at " + path);
            return;
        }
        if (hasSingleText) return;
        for (int index = 0; index < paragraph.getTextRanges().size(); index++) {
            ReportTextRange range = paragraph.getTextRanges().get(index);
            String rangePath = path + "/textRange[" + index + "]";
            if (range == null || !hasText(range.getText())) {
                errors.add("report text range must not be null or blank at " + rangePath);
                continue;
            }
            validateTextRangeStyle(range.getStyle(), rangePath, errors);
        }
    }

    /** 校验 Report 文本范围样式。 */
    private void validateTextRangeStyle(ReportTextRangeStyle style, String path, List<String> errors) {
        if (style == null) return;
        if (style.getFontFamily() != null && !hasText(style.getFontFamily())) {
            errors.add("report text range font family must not be blank at " + path);
        }
        if (style.getAsciiFontFamily() != null && !hasText(style.getAsciiFontFamily())) {
            errors.add("report text range ASCII font family must not be blank at " + path);
        }
        if (style.getFarEastFontFamily() != null && !hasText(style.getFarEastFontFamily())) {
            errors.add("report text range Far East font family must not be blank at " + path);
        }
        if (style.getFontSize() != null && (!Double.isFinite(style.getFontSize())
                || style.getFontSize() < 1 || style.getFontSize() > 200)) {
            errors.add("report text range font size must be between 1 and 200 at " + path);
        }
        validateFontColor(style.getFontColor(), path, errors);
    }

    private void validateDiagram(ReportDiagram diagram, String path, List<String> errors) {
        if (!hasText(diagram.getPreviewSource())) {
            errors.add("report diagram preview source must not be blank at " + path);
        }
        if (diagram.getEmbedMode() == null) {
            errors.add("report diagram embed mode must not be null at " + path);
        } else if (diagram.getEmbedMode() == ReportDiagramEmbedMode.EDITABLE_VISIO
                && !hasText(diagram.getVsdxSource())) {
            errors.add("editable report diagram VSDX source must not be blank at " + path);
        }
        if (!Double.isFinite(diagram.getMaxWidthPoints()) || !Double.isFinite(diagram.getMaxHeightPoints())
                || diagram.getMaxWidthPoints() <= 0 || diagram.getMaxHeightPoints() <= 0) {
            errors.add("report diagram dimensions must be finite positive numbers at " + path);
        }
    }

    /** 校验图表类型、数据矩阵和尺寸。 */
    private void validateChart(ReportChart chart, String path, List<String> errors) {
        if (chart.getChartType() == null) errors.add("report chart type must not be null at " + path);
        if (chart.getCategories().isEmpty()) errors.add("report chart categories must not be empty at " + path);
        if (chart.getSeries().isEmpty()) errors.add("report chart series must not be empty at " + path);
        for (int index = 0; index < chart.getCategories().size(); index++) {
            if (!hasText(chart.getCategories().get(index))) {
                errors.add("report chart category must not be blank at " + path + "/categories[" + index + "]");
            }
        }
        if (!Double.isFinite(chart.getWidthPoints()) || !Double.isFinite(chart.getHeightPoints())
                || chart.getWidthPoints() <= 0 || chart.getHeightPoints() <= 0) {
            errors.add("report chart dimensions must be finite positive numbers at " + path);
        }
        for (int index = 0; index < chart.getSeries().size(); index++) {
            ReportChartSeries series = chart.getSeries().get(index);
            if (series == null) {
                errors.add("report chart series must not be null at " + path + "/series[" + index + "]");
                continue;
            }
            if (series.getValues().size() != chart.getCategories().size()) {
                errors.add("report chart series values must match category count at " + path + "/series[" + index + "]");
            }
            for (Double value : series.getValues()) {
                if (value == null || !Double.isFinite(value)) {
                    errors.add("report chart values must be finite numbers at " + path + "/series[" + index + "]");
                    break;
                }
            }
        }
        if (chart.getChartType() == ReportChartType.PIE && chart.getSeries().size() != 1) {
            errors.add("report pie chart must contain exactly one series at " + path);
        }
        if (chart.getChartType() == ReportChartType.PIE && chart.getSeries().size() == 1) {
            double total = 0D;
            boolean nonNegative = true;
            for (Double value : chart.getSeries().get(0).getValues()) {
                if (value != null && Double.isFinite(value)) {
                    total += value;
                    nonNegative &= value >= 0D;
                }
            }
            if (!nonNegative || total <= 0D) {
                errors.add("report pie chart values must be non-negative with a positive total at " + path);
            }
        }
        if (chart.getChartType() == ReportChartType.RADAR && chart.getCategories().size() < 3) {
            errors.add("report radar chart must contain at least three categories at " + path);
        }
        if (chart.isShowPercentages() && chart.getChartType() != ReportChartType.PIE) {
            errors.add("report chart percentages are supported only for PIE at " + path);
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
        validateFontColor(table.getFontColor(), path, errors);
        validateTextRangeStyle(table.getHeaderTextStyle(), path + "/headerTextStyle", errors);
        validateTextRangeStyle(table.getBodyTextStyle(), path + "/bodyTextStyle", errors);
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
        boolean[][] covered = new boolean[table.getRows().size() + 1][columnCount];
        boolean[][] anchors = new boolean[table.getRows().size() + 1][columnCount];
        for (int mergeIndex = 0; mergeIndex < table.getMerges().size(); mergeIndex++) {
            ReportTableMerge merge = table.getMerges().get(mergeIndex);
            if (merge == null
                    || merge.getStartRow() + merge.getRowSpan() > covered.length
                    || merge.getStartColumn() + merge.getColumnSpan() > columnCount) {
                errors.add("report table merge exceeds table boundary at " + path + "/merge[" + mergeIndex + "]");
                continue;
            }
            boolean overlaps = false;
            for (int row = merge.getStartRow(); row < merge.getStartRow() + merge.getRowSpan(); row++) {
                for (int column = merge.getStartColumn();
                     column < merge.getStartColumn() + merge.getColumnSpan(); column++) {
                    overlaps |= covered[row][column];
                }
            }
            if (overlaps) {
                errors.add("report table merges must not overlap at " + path + "/merge[" + mergeIndex + "]");
                continue;
            }
            for (int row = merge.getStartRow(); row < merge.getStartRow() + merge.getRowSpan(); row++) {
                for (int column = merge.getStartColumn();
                     column < merge.getStartColumn() + merge.getColumnSpan(); column++) {
                    covered[row][column] = true;
                }
            }
            anchors[merge.getStartRow()][merge.getStartColumn()] = true;
        }
        for (int column = 0; column < columnCount; column++) {
            validateReportTableCell(table.getHeaders().get(column), 0, column,
                    path + "/header[" + column + "]", true, covered, anchors, errors);
        }
        for (int row = 0; row < table.getRows().size(); row++) {
            List<String> values = table.getRows().get(row);
            if (values == null || values.size() != columnCount) {
                continue;
            }
            for (int column = 0; column < columnCount; column++) {
                validateReportTableCell(values.get(column), row + 1, column,
                        path + "/row[" + row + "]/cell[" + column + "]", false,
                        covered, anchors, errors);
            }
        }
    }

    /** 校验 Report 层表格单元格及合并后续内容。 */
    private void validateReportTableCell(String value, int row, int column, String path, boolean header,
                                         boolean[][] covered, boolean[][] anchors, List<String> errors) {
        boolean follower = covered[row][column] && !anchors[row][column];
        if (follower) {
            if (hasText(value)) {
                errors.add("merged report table cell must be blank at " + path);
            }
            return;
        }
        if (value == null || (header && !hasText(value))) {
            errors.add("report table cell must not be null or blank at " + path);
        }
    }

    /** 校验可选字体颜色。 */
    private void validateFontColor(String color, String path, List<String> errors) {
        if (hasText(color) && !color.matches("#[0-9A-Fa-f]{6}")) {
            errors.add("report font color must use #RRGGBB format at " + path);
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
