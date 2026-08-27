package cn.bugstack.application.document;

import cn.bugstack.application.diagram.DiagramSpecValidationResult;
import cn.bugstack.application.diagram.DiagramSpecValidator;
import cn.bugstack.application.diagram.DiagramSpecViolation;

import cn.bugstack.protocol.document.DocumentLayoutSpec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentSpecVersion;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.AbstractListBlockSpec;
import cn.bugstack.protocol.document.block.BlockSpec;
import cn.bugstack.protocol.document.block.ChartBlockSpec;
import cn.bugstack.protocol.document.block.ChartSeriesSpec;
import cn.bugstack.protocol.document.block.DiagramBlockSpec;
import cn.bugstack.protocol.document.block.ImageBlockSpec;
import cn.bugstack.protocol.document.block.PageBreakBlockSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import cn.bugstack.protocol.document.block.TableMergeSpec;
import cn.bugstack.protocol.document.block.TextRangeSpec;
import cn.bugstack.protocol.document.block.TextRangeStyleSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 在 DocumentSpec 编译前执行协议版本、结构、规模和样式白名单校验。
 */
public final class DocumentSpecValidator {

    private static final Set<String> PARAGRAPH_STYLES = new HashSet<>(Arrays.asList(
            "Normal", "Title", "Subtitle", "BodyText", "Caption", "ImageCaption", "CodeBlock",
            "Heading1", "Heading2", "Heading3", "Heading4", "Heading5",
            "Heading6", "Heading7", "Heading8", "Heading9"));
    private static final Set<String> TABLE_STYLES = new HashSet<>(Arrays.asList(
            "TableNormal", "TableHeader", "TableCell"));
    private static final Set<String> DIAGRAM_EMBED_MODES = new HashSet<>(Arrays.asList(
            "PREVIEW_IMAGE", "EDITABLE_VISIO"));
    private static final Set<String> TABLE_ALIGNMENTS = new HashSet<>(Arrays.asList(
            "LEFT", "CENTER", "RIGHT"));
    private static final Set<String> CAPTION_POSITIONS = new HashSet<>(Arrays.asList(
            "ABOVE", "BELOW"));
    private static final Set<String> CHART_TYPES = new HashSet<>(Arrays.asList(
            "COLUMN", "BAR", "PIE", "LINE", "RADAR"));
    private static final Set<String> CHART_LEGEND_POSITIONS = new HashSet<>(Arrays.asList(
            "BOTTOM", "TOP", "LEFT", "RIGHT"));

    private final DocumentSpecLimits limits;
    private final boolean diagramEnabled;
    private final DiagramSpecValidator diagramSpecValidator = new DiagramSpecValidator();

    public DocumentSpecValidator() {
        this(DocumentSpecLimits.defaults(), false);
    }

    public DocumentSpecValidator(DocumentSpecLimits limits) {
        this(limits, false);
    }

    /**
     * 创建校验器。
     *
     * @param limits 文档规模限制
     * @param diagramEnabled 是否已安装 M2 图形制品解析能力
     */
    public DocumentSpecValidator(DocumentSpecLimits limits, boolean diagramEnabled) {
        if (limits == null) {
            throw new IllegalArgumentException("document spec limits must not be null");
        }
        this.limits = limits;
        this.diagramEnabled = diagramEnabled;
    }

    public DocumentSpecValidationResult validate(DocumentSpec spec) {
        ValidationState state = new ValidationState();
        if (spec == null) {
            state.add("/", "REQUIRED", "document spec must not be null");
            return state.result();
        }
        if (!DocumentSpecVersion.V1.equals(spec.getSchemaVersion())) {
            state.add("/schemaVersion", "UNSUPPORTED_VERSION",
                    "supported document spec version is " + DocumentSpecVersion.V1);
        }
        if (spec.getMetadata() == null) {
            state.add("/metadata", "REQUIRED", "document metadata must not be null");
        } else {
            validateText(spec.getMetadata().getTitle(), "/metadata/title", true, state);
            validateText(spec.getMetadata().getAuthor(), "/metadata/author", false, state);
            validateText(spec.getMetadata().getSubject(), "/metadata/subject", false, state);
        }
        validateLayout(spec.getLayout(), state);
        validateFrontMatter(spec, state);
        if (spec.getSections() == null || spec.getSections().isEmpty()) {
            state.add("/sections", "REQUIRED", "document must contain at least one section");
        } else {
            for (int i = 0; i < spec.getSections().size(); i++) {
                validateSection(spec.getSections().get(i), "/sections/" + i, 1, state);
            }
        }
        return state.result();
    }

    /** 返回当前校验器是否允许图块进入后续工件解析链路。 */
    public boolean isDiagramEnabled() {
        return diagramEnabled;
    }

    private void validateLayout(DocumentLayoutSpec layout, ValidationState state) {
        if (layout == null) {
            state.add("/layout", "REQUIRED", "document layout must not be null");
            return;
        }
        if (layout.getStyleProfile() == null) {
            state.add("/layout/styleProfile", "REQUIRED", "style profile must not be null");
        }
        Integer tocDepth = layout.getTableOfContentsDepth();
        if (tocDepth != null && (tocDepth < 1 || tocDepth > 9)) {
            state.add("/layout/tableOfContentsDepth", "OUT_OF_RANGE",
                    "table of contents depth must be between 1 and 9");
        }
        if (layout.getBodyPageNumberStart() < 1) {
            state.add("/layout/bodyPageNumberStart", "OUT_OF_RANGE",
                    "body page number start must be greater than zero");
        }
        validateText(layout.getHeaderText(), "/layout/headerText", false, state);
        validateText(layout.getFooterText(), "/layout/footerText", false, state);
        if (!Arrays.asList("A4", "A3", "LETTER").contains(layout.getPaperSize())) {
            state.add("/layout/paperSize", "INVALID_ENUM", "paperSize must be A4, A3 or LETTER");
        }
        if (!Arrays.asList("PORTRAIT", "LANDSCAPE").contains(layout.getOrientation())) {
            state.add("/layout/orientation", "INVALID_ENUM", "orientation must be PORTRAIT or LANDSCAPE");
        }
        validateMargin(layout.getTopMarginPoints(), "/layout/topMarginPoints", state);
        validateMargin(layout.getRightMarginPoints(), "/layout/rightMarginPoints", state);
        validateMargin(layout.getBottomMarginPoints(), "/layout/bottomMarginPoints", state);
        validateMargin(layout.getLeftMarginPoints(), "/layout/leftMarginPoints", state);
    }

    private void validateFrontMatter(DocumentSpec spec, ValidationState state) {
        if (spec.getCover() != null) {
            validateText(spec.getCover().getDocumentName(), "/cover/documentName", true, state);
            validateText(spec.getCover().getProjectName(), "/cover/projectName", true, state);
            validateText(spec.getCover().getVersion(), "/cover/version", true, state);
        }
        if (spec.getRevisionHistory() != null) {
            if (spec.getRevisionHistory().size() > 100) {
                state.add("/revisionHistory", "LIMIT_EXCEEDED", "revision history exceeds 100 entries");
            }
            for (int i = 0; i < spec.getRevisionHistory().size(); i++) {
                cn.bugstack.protocol.document.DocumentRevisionSpec item = spec.getRevisionHistory().get(i);
                String path = "/revisionHistory/" + i;
                if (item == null) { state.add(path, "REQUIRED", "revision entry is required"); continue; }
                validateText(item.getVersion(), path + "/version", true, state);
                validateText(item.getDate(), path + "/date", true, state);
                validateText(item.getDescription(), path + "/description", true, state);
                validateText(item.getAuthor(), path + "/author", true, state);
            }
        }
        if (spec.getApprovals() != null) {
            if (spec.getApprovals().size() > 100) {
                state.add("/approvals", "LIMIT_EXCEEDED", "approval list exceeds 100 entries");
            }
            for (int i = 0; i < spec.getApprovals().size(); i++) {
                cn.bugstack.protocol.document.DocumentApprovalSpec item = spec.getApprovals().get(i);
                String path = "/approvals/" + i;
                if (item == null) { state.add(path, "REQUIRED", "approval entry is required"); continue; }
                validateText(item.getRole(), path + "/role", true, state);
                validateText(item.getPerson(), path + "/person", true, state);
                validateText(item.getDate(), path + "/date", true, state);
            }
        }
    }

    private void validateMargin(double value, String path, ValidationState state) {
        if (!Double.isFinite(value) || value < 0D || value > 288D) {
            state.add(path, "OUT_OF_RANGE", "page margin must be between 0 and 288 points");
        }
    }

    private void validateSection(SectionSpec section, String path, int depth, ValidationState state) {
        state.sectionCount++;
        if (state.sectionCount > limits.getMaxSections()) {
            state.add(path, "LIMIT_EXCEEDED", "section count exceeds " + limits.getMaxSections());
            return;
        }
        if (section == null) {
            state.add(path, "REQUIRED", "section must not be null");
            return;
        }
        validateText(section.getTitle(), path + "/title", true, state);
        if (depth > limits.getMaxSectionDepth()) {
            state.add(path, "LIMIT_EXCEEDED", "section depth exceeds " + limits.getMaxSectionDepth());
            return;
        }
        if (section.getBlocks() == null) {
            state.add(path + "/blocks", "REQUIRED", "section blocks must not be null");
            return;
        }
        validateBlocks(section.getBlocks(), path + "/blocks", depth, state);
    }

    private void validateBlocks(List<BlockSpec> blocks, String path, int depth, ValidationState state) {
        for (int i = 0; i < blocks.size(); i++) {
            String blockPath = path + "/" + i;
            BlockSpec block = blocks.get(i);
            state.blockCount++;
            if (state.blockCount > limits.getMaxBlocks()) {
                state.add(blockPath, "LIMIT_EXCEEDED", "block count exceeds " + limits.getMaxBlocks());
                return;
            }
            if (block == null) {
                state.add(blockPath, "REQUIRED", "block must not be null");
            } else if (block instanceof ParagraphBlockSpec) {
                ParagraphBlockSpec paragraph = (ParagraphBlockSpec) block;
                validateParagraphContent(paragraph, blockPath, state);
                validateParagraphStyle(paragraph.getStyleName(), blockPath + "/styleName", state);
                validateFontColor(paragraph.getFontColor(), blockPath + "/fontColor", state);
            } else if (block instanceof AbstractListBlockSpec) {
                validateList((AbstractListBlockSpec) block, blockPath, state);
            } else if (block instanceof TableBlockSpec) {
                validateTable((TableBlockSpec) block, blockPath, state);
            } else if (block instanceof ImageBlockSpec) {
                countMedia(blockPath, state);
                validateImage((ImageBlockSpec) block, blockPath, state);
            } else if (block instanceof DiagramBlockSpec) {
                countMedia(blockPath, state);
                validateDiagram((DiagramBlockSpec) block, blockPath, state);
            } else if (block instanceof ChartBlockSpec) {
                countMedia(blockPath, state);
                validateChart((ChartBlockSpec) block, blockPath, state);
            } else if (block instanceof SubsectionBlockSpec) {
                SubsectionBlockSpec subsection = (SubsectionBlockSpec) block;
                SectionSpec child = new SectionSpec(subsection.getTitle());
                child.setBlocks(subsection.getBlocks());
                validateSection(child, blockPath, depth + 1, state);
            } else if (!(block instanceof PageBreakBlockSpec)) {
                state.add(blockPath, "UNSUPPORTED_BLOCK", "unsupported document block type");
            }
        }
    }

    /** 校验单文本与多文本范围互斥，并校验每个范围的内容和样式。 */
    private void validateParagraphContent(ParagraphBlockSpec paragraph, String path, ValidationState state) {
        boolean hasSingleText = hasText(paragraph.getText());
        boolean hasRanges = paragraph.getTextRanges() != null && !paragraph.getTextRanges().isEmpty();
        if (hasSingleText == hasRanges) {
            state.add(path, "ONE_OF_REQUIRED", "exactly one of text and textRanges must be configured");
            return;
        }
        if (hasSingleText) {
            validateText(paragraph.getText(), path + "/text", true, state);
            return;
        }
        if (paragraph.getTextRanges().size() > limits.getMaxListItems()) {
            state.add(path + "/textRanges", "LIMIT_EXCEEDED",
                    "text range count exceeds " + limits.getMaxListItems());
        }
        for (int index = 0; index < paragraph.getTextRanges().size(); index++) {
            TextRangeSpec range = paragraph.getTextRanges().get(index);
            String rangePath = path + "/textRanges/" + index;
            if (range == null) {
                state.add(rangePath, "REQUIRED", "text range must not be null");
                continue;
            }
            validateText(range.getText(), rangePath + "/text", true, state);
            validateTextRangeStyle(range.getStyle(), rangePath + "/style", state);
        }
    }

    /** 校验文本范围的字体、字号、颜色和文字效果。 */
    private void validateTextRangeStyle(TextRangeStyleSpec style, String path, ValidationState state) {
        if (style == null) return;
        validateFontFamily(style.getFontFamily(), path + "/fontFamily", "fontFamily", state);
        validateFontFamily(style.getAsciiFontFamily(), path + "/asciiFontFamily", "asciiFontFamily", state);
        validateFontFamily(style.getFarEastFontFamily(), path + "/farEastFontFamily", "farEastFontFamily", state);
        if (style.getFontSize() != null && (!Double.isFinite(style.getFontSize())
                || style.getFontSize() < 1 || style.getFontSize() > 200)) {
            state.add(path + "/fontSize", "OUT_OF_RANGE", "fontSize must be between 1 and 200 points");
        }
        validateFontColor(style.getFontColor(), path + "/fontColor", state);
    }

    private void validateList(AbstractListBlockSpec list, String path, ValidationState state) {
        validateParagraphStyle(list.getStyleName(), path + "/styleName", state);
        validateFontColor(list.getFontColor(), path + "/fontColor", state);
        if (list.getItems() == null || list.getItems().isEmpty()) {
            state.add(path + "/items", "REQUIRED", "list must contain at least one item");
            return;
        }
        if (list.getItems().size() > limits.getMaxListItems()) {
            state.add(path + "/items", "LIMIT_EXCEEDED",
                    "list item count exceeds " + limits.getMaxListItems());
        }
        for (int i = 0; i < list.getItems().size(); i++) {
            validateText(list.getItems().get(i), path + "/items/" + i, true, state);
        }
    }

    private void validateTable(TableBlockSpec table, String path, ValidationState state) {
        validateFontColor(table.getFontColor(), path + "/fontColor", state);
        validateTextRangeStyle(table.getHeaderTextStyle(), path + "/headerTextStyle", state);
        validateTextRangeStyle(table.getBodyTextStyle(), path + "/bodyTextStyle", state);
        validateEnum(table.getAlignment(), TABLE_ALIGNMENTS, path + "/alignment",
                "table alignment must be LEFT, CENTER or RIGHT", state);
        validateEnum(table.getCaptionPosition(), CAPTION_POSITIONS, path + "/captionPosition",
                "captionPosition must be ABOVE or BELOW", state);
        List<String> headers = table.getHeaders();
        if (headers == null || headers.isEmpty()) {
            state.add(path + "/headers", "REQUIRED", "table headers must not be empty");
            return;
        }
        if (headers.size() > limits.getMaxTableColumns()) {
            state.add(path + "/headers", "LIMIT_EXCEEDED",
                    "table column count exceeds " + limits.getMaxTableColumns());
        }
        int rows = table.getRows() == null ? 0 : table.getRows().size();
        MergeCoverage mergeCoverage = validateTableMerges(table.getMerges(), rows + 1,
                headers.size(), path + "/merges", state);
        state.tableCellCount += headers.size() * (long) (rows + 1);
        if (state.tableCellCount > limits.getMaxTableCells() && !state.tableCellLimitReported) {
            state.tableCellLimitReported = true;
            state.add(path, "LIMIT_EXCEEDED", "total table cell count exceeds " + limits.getMaxTableCells());
        }
        for (int i = 0; i < headers.size(); i++) {
            validateTableCell(headers.get(i), 0, i, path + "/headers/" + i,
                    table, mergeCoverage, state);
        }
        if (table.getRows() != null && table.getRows().size() > limits.getMaxTableRows()) {
            state.add(path + "/rows", "LIMIT_EXCEEDED",
                    "table row count exceeds " + limits.getMaxTableRows());
        }
        if (table.getRows() != null) {
            for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
                List<String> row = table.getRows().get(rowIndex);
                if (row == null || row.size() != headers.size()) {
                    state.add(path + "/rows/" + rowIndex, "COLUMN_MISMATCH",
                            "table row width must match header count");
                    continue;
                }
                for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                    String cell = row.get(columnIndex);
                    String cellPath = path + "/rows/" + rowIndex + "/" + columnIndex;
                    validateTableCell(cell, rowIndex + 1, columnIndex, cellPath,
                            table, mergeCoverage, state);
                }
            }
        }
        List<Double> widths = table.getColumnWidths();
        if (widths != null && !widths.isEmpty() && widths.size() != headers.size()) {
            state.add(path + "/columnWidths", "COLUMN_MISMATCH",
                    "column width count must match header count");
        } else if (widths != null) {
            for (int i = 0; i < widths.size(); i++) {
                Double width = widths.get(i);
                if (width == null || !Double.isFinite(width) || width <= 0) {
                    state.add(path + "/columnWidths/" + i, "OUT_OF_RANGE",
                            "column width must be a finite positive number");
                }
            }
        }
        if (hasText(table.getStyleName()) && !TABLE_STYLES.contains(table.getStyleName())) {
            state.add(path + "/styleName", "STYLE_NOT_ALLOWED", "table style is not allowed");
        }
        validateText(table.getCaption(), path + "/caption", false, state);
    }

    /** 校验矩形合并区域，并建立逻辑单元格覆盖关系。 */
    private MergeCoverage validateTableMerges(List<TableMergeSpec> merges, int rowCount, int columnCount,
                                              String path, ValidationState state) {
        MergeCoverage coverage = new MergeCoverage(rowCount, columnCount);
        if (merges == null) {
            state.add(path, "REQUIRED", "table merges must not be null");
            return coverage;
        }
        for (int index = 0; index < merges.size(); index++) {
            TableMergeSpec merge = merges.get(index);
            String mergePath = path + "/" + index;
            if (merge == null) {
                state.add(mergePath, "REQUIRED", "table merge must not be null");
                continue;
            }
            if (merge.getStartRow() < 0 || merge.getStartColumn() < 0
                    || merge.getRowSpan() < 1 || merge.getColumnSpan() < 1) {
                state.add(mergePath, "OUT_OF_RANGE", "table merge coordinates and spans are out of range");
                continue;
            }
            if (merge.getRowSpan() == 1 && merge.getColumnSpan() == 1) {
                state.add(mergePath, "INVALID_MERGE", "table merge must span multiple rows or columns");
                continue;
            }
            if (merge.getStartRow() + merge.getRowSpan() > rowCount
                    || merge.getStartColumn() + merge.getColumnSpan() > columnCount) {
                state.add(mergePath, "OUT_OF_RANGE", "table merge exceeds the table boundary");
                continue;
            }
            boolean overlaps = false;
            for (int row = merge.getStartRow(); row < merge.getStartRow() + merge.getRowSpan(); row++) {
                for (int column = merge.getStartColumn();
                     column < merge.getStartColumn() + merge.getColumnSpan(); column++) {
                    overlaps |= coverage.covered[row][column];
                }
            }
            if (overlaps) {
                state.add(mergePath, "OVERLAPPING_MERGE", "table merge overlaps another merge region");
                continue;
            }
            for (int row = merge.getStartRow(); row < merge.getStartRow() + merge.getRowSpan(); row++) {
                for (int column = merge.getStartColumn();
                     column < merge.getStartColumn() + merge.getColumnSpan(); column++) {
                    coverage.covered[row][column] = true;
                    coverage.anchorRows[row][column] = merge.getStartRow();
                    coverage.anchorColumns[row][column] = merge.getStartColumn();
                }
            }
            coverage.anchors[merge.getStartRow()][merge.getStartColumn()] = true;
        }
        return coverage;
    }

    /** 校验普通、合并起点及合并后续单元格的内容约束。 */
    private void validateTableCell(String value, int row, int column, String path,
                                   TableBlockSpec table, MergeCoverage coverage, ValidationState state) {
        if (value == null) {
            state.add(path, "REQUIRED", "table cell must not be null");
            return;
        }
        if (coverage.isFollower(row, column)) {
            String anchorValue = tableCellValue(table, coverage.anchorRow(row, column),
                    coverage.anchorColumn(row, column));
            if (hasText(value) && !Objects.equals(value, anchorValue)) {
                state.add(path, "MERGED_CELL_CONTENT_MISMATCH",
                        "a merged follower cell must be blank or equal to its top-left cell");
            }
            return;
        }
        validateText(value, path, row == 0, state);
    }

    /** 按包含表头的逻辑坐标读取表格单元格。 */
    private String tableCellValue(TableBlockSpec table, int row, int column) {
        if (row == 0) {
            return table.getHeaders().get(column);
        }
        List<String> values = table.getRows().get(row - 1);
        return values == null || column >= values.size() ? null : values.get(column);
    }

    private void validateImage(ImageBlockSpec image, String path, ValidationState state) {
        boolean hasSource = hasText(image.getSource());
        boolean hasAssetId = hasText(image.getAssetId());
        if (hasSource == hasAssetId) {
            state.add(path, "ONE_OF_REQUIRED", "exactly one of source and assetId must be configured");
        }
        if (hasSource) validateText(image.getSource(), path + "/source", true, state);
        if (hasAssetId) {
            validateText(image.getAssetId(), path + "/assetId", true, state);
            try {
                java.util.UUID.fromString(image.getAssetId());
            } catch (IllegalArgumentException e) {
                state.add(path + "/assetId", "INVALID_FORMAT", "image assetId must be a UUID");
            }
        }
        validateText(image.getAlternativeText(), path + "/alternativeText", false, state);
        validateText(image.getCaption(), path + "/caption", false, state);
        validateEnum(image.getCaptionPosition(), CAPTION_POSITIONS, path + "/captionPosition",
                "captionPosition must be ABOVE or BELOW", state);
        if ((image.getWidth() == null) != (image.getHeight() == null)) {
            state.add(path, "DIMENSION_MISMATCH", "image width and height must be configured together");
        } else if (image.getWidth() != null && (image.getWidth() <= 0 || image.getHeight() <= 0)) {
            state.add(path, "OUT_OF_RANGE", "image width and height must be greater than zero");
        }
    }

    private void validateDiagram(DiagramBlockSpec diagram, String path, ValidationState state) {
        boolean hasArtifactId = hasText(diagram.getDiagramArtifactId());
        boolean hasDefinition = diagram.getDefinition() != null;
        if (hasArtifactId == hasDefinition) {
            state.add(path, "ONE_OF_REQUIRED",
                    "exactly one of diagramArtifactId and definition must be configured");
        }
        if (hasArtifactId) {
            validateText(diagram.getDiagramArtifactId(), path + "/diagramArtifactId", true, state);
        }
        if (hasDefinition) {
            DiagramSpecValidationResult result = diagramSpecValidator.validate(diagram.getDefinition());
            for (DiagramSpecViolation violation : result.getViolations()) {
                state.add(path + "/definition" + violation.getPath(),
                        violation.getCode(), violation.getMessage());
            }
        }
        if (!DIAGRAM_EMBED_MODES.contains(diagram.getEmbedMode())) {
            state.add(path + "/embedMode", "INVALID_ENUM", "unsupported diagram embed mode");
        }
        validateText(diagram.getCaption(), path + "/caption", false, state);
        validateEnum(diagram.getCaptionPosition(), CAPTION_POSITIONS, path + "/captionPosition",
                "captionPosition must be ABOVE or BELOW", state);
        if ((diagram.getMaxWidthPoints() == null) != (diagram.getMaxHeightPoints() == null)) {
            state.add(path, "DIMENSION_MISMATCH", "diagram width and height must be configured together");
        } else if (diagram.getMaxWidthPoints() != null
                && (diagram.getMaxWidthPoints() <= 0 || diagram.getMaxHeightPoints() <= 0)) {
            state.add(path, "OUT_OF_RANGE", "diagram width and height must be greater than zero");
        }
        if (!diagramEnabled) {
            state.add(path, "CAPABILITY_NOT_AVAILABLE",
                    "diagram blocks require an explicitly configured diagram artifact capability");
        }
    }

    /** 校验 Word 原生图表数据矩阵及各图表类型的明确边界。 */
    private void validateChart(ChartBlockSpec chart, String path, ValidationState state) {
        validateEnum(chart.getChartType(), CHART_TYPES, path + "/chartType",
                "chartType must be COLUMN, BAR, PIE, LINE or RADAR", state);
        validateEnum(chart.getLegendPosition(), CHART_LEGEND_POSITIONS, path + "/legendPosition",
                "legendPosition must be BOTTOM, TOP, LEFT or RIGHT", state);
        validateEnum(chart.getCaptionPosition(), CAPTION_POSITIONS, path + "/captionPosition",
                "captionPosition must be ABOVE or BELOW", state);
        validateText(chart.getTitle(), path + "/title", false, state);
        validateText(chart.getCategoryAxisTitle(), path + "/categoryAxisTitle", false, state);
        validateText(chart.getValueAxisTitle(), path + "/valueAxisTitle", false, state);
        validateText(chart.getCaption(), path + "/caption", false, state);
        if (!Double.isFinite(chart.getWidthPoints()) || !Double.isFinite(chart.getHeightPoints())
                || chart.getWidthPoints() < 100D || chart.getWidthPoints() > 1000D
                || chart.getHeightPoints() < 100D || chart.getHeightPoints() > 1000D) {
            state.add(path, "OUT_OF_RANGE", "chart width and height must be between 100 and 1000 points");
        }
        List<String> categories = chart.getCategories();
        if (categories == null || categories.isEmpty()) {
            state.add(path + "/categories", "REQUIRED", "chart categories must not be empty");
            return;
        }
        if (categories.size() > 100) {
            state.add(path + "/categories", "LIMIT_EXCEEDED", "chart category count exceeds 100");
        }
        for (int index = 0; index < categories.size(); index++) {
            validateText(categories.get(index), path + "/categories/" + index, true, state);
        }
        List<ChartSeriesSpec> series = chart.getSeries();
        if (series == null || series.isEmpty()) {
            state.add(path + "/series", "REQUIRED", "chart series must not be empty");
            return;
        }
        if (series.size() > 20) {
            state.add(path + "/series", "LIMIT_EXCEEDED", "chart series count exceeds 20");
        }
        boolean pieValuesValid = true;
        double pieTotal = 0D;
        for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
            ChartSeriesSpec item = series.get(seriesIndex);
            String seriesPath = path + "/series/" + seriesIndex;
            if (item == null) {
                state.add(seriesPath, "REQUIRED", "chart series must not be null");
                continue;
            }
            validateText(item.getName(), seriesPath + "/name", false, state);
            if (item.getValues() == null || item.getValues().size() != categories.size()) {
                state.add(seriesPath + "/values", "COLUMN_MISMATCH",
                        "chart series value count must match category count");
                continue;
            }
            for (int valueIndex = 0; valueIndex < item.getValues().size(); valueIndex++) {
                Double value = item.getValues().get(valueIndex);
                if (value == null || !Double.isFinite(value)) {
                    state.add(seriesPath + "/values/" + valueIndex, "OUT_OF_RANGE",
                            "chart value must be a finite number");
                    pieValuesValid = false;
                } else if ("PIE".equals(chart.getChartType())) {
                    pieTotal += value;
                    pieValuesValid &= value >= 0D;
                }
            }
        }
        if ("PIE".equals(chart.getChartType()) && series.size() != 1) {
            state.add(path + "/series", "INVALID_CHART_DATA", "pie chart must contain exactly one series");
        }
        if ("PIE".equals(chart.getChartType()) && (!pieValuesValid || pieTotal <= 0D)) {
            state.add(path + "/series", "INVALID_CHART_DATA",
                    "pie chart values must be non-negative and have a positive total");
        }
        if (chart.isShowPercentages() && !"PIE".equals(chart.getChartType())) {
            state.add(path + "/showPercentages", "INVALID_CHART_OPTION",
                    "showPercentages is supported only for PIE charts");
        }
        if ("RADAR".equals(chart.getChartType()) && categories.size() < 3) {
            state.add(path + "/categories", "INVALID_CHART_DATA",
                    "radar chart must contain at least three categories");
        }
    }

    private void validateParagraphStyle(String styleName, String path, ValidationState state) {
        if (hasText(styleName) && !PARAGRAPH_STYLES.contains(styleName)) {
            state.add(path, "STYLE_NOT_ALLOWED", "paragraph style is not allowed");
        }
    }

    /** 校验可选的六位十六进制字体颜色。 */
    private void validateFontColor(String color, String path, ValidationState state) {
        if (hasText(color) && !color.matches("#[0-9A-Fa-f]{6}")) {
            state.add(path, "INVALID_FORMAT", "fontColor must use #RRGGBB format");
        }
    }

    /** 校验可选字体名称。 */
    private void validateFontFamily(String fontFamily, String path, String fieldName, ValidationState state) {
        if (fontFamily != null && (!hasText(fontFamily) || fontFamily.length() > 200)) {
            state.add(path, "INVALID_FORMAT", fieldName + " must be non-blank and at most 200 characters");
        }
    }

    /** 校验字符串枚举字段。 */
    private void validateEnum(String value, Set<String> allowed, String path, String message,
                              ValidationState state) {
        if (!allowed.contains(value)) {
            state.add(path, "INVALID_ENUM", message);
        }
    }

    private void validateText(String value, String path, boolean required, ValidationState state) {
        if (!hasText(value)) {
            if (required) {
                state.add(path, "REQUIRED", "text must not be blank");
            }
            return;
        }
        if (value.length() > limits.getMaxTextLength()) {
            state.add(path, "LIMIT_EXCEEDED", "text length exceeds " + limits.getMaxTextLength());
        }
        state.totalTextLength += value.length();
        if (state.totalTextLength > limits.getMaxTotalTextLength() && !state.textLimitReported) {
            state.textLimitReported = true;
            state.add(path, "LIMIT_EXCEEDED",
                    "total document text length exceeds " + limits.getMaxTotalTextLength());
        }
    }

    private void countMedia(String path, ValidationState state) {
        state.mediaCount++;
        if (state.mediaCount > limits.getMaxMediaBlocks() && !state.mediaLimitReported) {
            state.mediaLimitReported = true;
            state.add(path, "LIMIT_EXCEEDED", "media block count exceeds " + limits.getMaxMediaBlocks());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class ValidationState {

        private final List<DocumentSpecViolation> violations = new ArrayList<>();
        private int sectionCount;
        private int blockCount;
        private long totalTextLength;
        private long tableCellCount;
        private int mediaCount;
        private boolean textLimitReported;
        private boolean tableCellLimitReported;
        private boolean mediaLimitReported;

        private void add(String path, String code, String message) {
            violations.add(new DocumentSpecViolation(path, code, message));
        }

        private DocumentSpecValidationResult result() {
            return new DocumentSpecValidationResult(violations);
        }
    }

    /** 表格合并覆盖矩阵。 */
    private static final class MergeCoverage {

        private final boolean[][] covered;
        private final boolean[][] anchors;
        private final int[][] anchorRows;
        private final int[][] anchorColumns;

        private MergeCoverage(int rows, int columns) {
            this.covered = new boolean[rows][columns];
            this.anchors = new boolean[rows][columns];
            this.anchorRows = new int[rows][columns];
            this.anchorColumns = new int[rows][columns];
            for (int row = 0; row < rows; row++) {
                Arrays.fill(anchorRows[row], -1);
                Arrays.fill(anchorColumns[row], -1);
            }
        }

        private boolean isFollower(int row, int column) {
            return row < covered.length && column < covered[row].length
                    && covered[row][column] && !anchors[row][column];
        }

        private int anchorRow(int row, int column) {
            return anchorRows[row][column];
        }

        private int anchorColumn(int row, int column) {
            return anchorColumns[row][column];
        }
    }
}
