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
import cn.bugstack.protocol.document.block.DiagramBlockSpec;
import cn.bugstack.protocol.document.block.ImageBlockSpec;
import cn.bugstack.protocol.document.block.PageBreakBlockSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
                validateText(paragraph.getText(), blockPath + "/text", true, state);
                validateParagraphStyle(paragraph.getStyleName(), blockPath + "/styleName", state);
            } else if (block instanceof AbstractListBlockSpec) {
                validateList((AbstractListBlockSpec) block, blockPath, state);
            } else if (block instanceof TableBlockSpec) {
                validateTable((TableBlockSpec) block, blockPath, state);
            } else if (block instanceof ImageBlockSpec) {
                validateImage((ImageBlockSpec) block, blockPath, state);
            } else if (block instanceof DiagramBlockSpec) {
                validateDiagram((DiagramBlockSpec) block, blockPath, state);
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

    private void validateList(AbstractListBlockSpec list, String path, ValidationState state) {
        validateParagraphStyle(list.getStyleName(), path + "/styleName", state);
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
        List<String> headers = table.getHeaders();
        if (headers == null || headers.isEmpty()) {
            state.add(path + "/headers", "REQUIRED", "table headers must not be empty");
            return;
        }
        if (headers.size() > limits.getMaxTableColumns()) {
            state.add(path + "/headers", "LIMIT_EXCEEDED",
                    "table column count exceeds " + limits.getMaxTableColumns());
        }
        for (int i = 0; i < headers.size(); i++) {
            validateText(headers.get(i), path + "/headers/" + i, true, state);
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
                    if (cell == null) {
                        state.add(cellPath, "REQUIRED", "table cell must not be null");
                    } else {
                        validateText(cell, cellPath, false, state);
                    }
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

    private void validateImage(ImageBlockSpec image, String path, ValidationState state) {
        validateText(image.getSource(), path + "/source", true, state);
        validateText(image.getAlternativeText(), path + "/alternativeText", false, state);
        validateText(image.getCaption(), path + "/caption", false, state);
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

    private void validateParagraphStyle(String styleName, String path, ValidationState state) {
        if (hasText(styleName) && !PARAGRAPH_STYLES.contains(styleName)) {
            state.add(path, "STYLE_NOT_ALLOWED", "paragraph style is not allowed");
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
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class ValidationState {

        private final List<DocumentSpecViolation> violations = new ArrayList<>();
        private int sectionCount;
        private int blockCount;

        private void add(String path, String code, String message) {
            violations.add(new DocumentSpecViolation(path, code, message));
        }

        private DocumentSpecValidationResult result() {
            return new DocumentSpecValidationResult(violations);
        }
    }
}
