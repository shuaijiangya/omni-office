package cn.bugstack.application.template.importer;

import cn.bugstack.application.completion.DocumentStructureSnapshot;
import cn.bugstack.application.completion.SourceDocxStructureExtractor;
import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.BookmarkStart;
import com.aspose.words.Cell;
import com.aspose.words.CompositeNode;
import com.aspose.words.Document;
import com.aspose.words.Font;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.OutlineLevel;
import com.aspose.words.Paragraph;
import com.aspose.words.ParagraphFormat;
import com.aspose.words.Row;
import com.aspose.words.Run;
import com.aspose.words.Section;
import com.aspose.words.Shape;
import com.aspose.words.StructuredDocumentTag;
import com.aspose.words.Style;
import com.aspose.words.StyleIdentifier;
import com.aspose.words.Table;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分析上传的 DOCX 模板，并生成章节模板及物理样式绑定草案。
 *
 * <p>只有 Word 大纲级别或标题样式被视为确定的章节。占位符优先识别内容控件、
 * 书签和跨 Run 的 {@code {{name}}} 文本；缺少明确填充标记时保留原文并提出审核问题。</p>
 */
public final class DocxTemplateAnalyzer {

    private static final Pattern HEADING_STYLE = Pattern.compile("(?i)^(?:heading|标题)\\s*([1-9])$");
    private static final Pattern TEXT_MARKER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_.:-]{0,99})}}" );
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 解析一个源 DOCX，不修改原文件或自动执行任何 AI 生成。
     *
     * @param source 用户上传的 DOCX
     * @param templateId 版本化业务模板标识
     * @return 可审核的结构、内容模板和样式绑定
     */
    public DocxTemplateAnalysis analyze(Path source, String templateId) {
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new IllegalArgumentException("templateId is required");
        }
        DocumentStructureSnapshot sourceSnapshot = new SourceDocxStructureExtractor().extract(source);
        try {
            AsposeWordsLicenseLoader.applyConfiguredLicense();
            Document document = new Document(source.toString());
            ObjectNode structure = mapper.createObjectNode();
            structure.put("schemaVersion", "1.0");
            structure.put("sourceFileName", sourceSnapshot.getFileName());
            structure.put("sourceSha256", sourceSnapshot.getSha256());
            ArrayNode sections = structure.putArray("sections");
            ObjectNode styles = structure.putObject("styles");
            ObjectNode paragraphStyles = styles.putObject("paragraphs");
            ObjectNode tableStyles = styles.putObject("tables");
            ArrayNode pageLayouts = structure.putArray("pageLayouts");
            ArrayNode issues = structure.putArray("reviewIssues");

            ObjectNode reportTemplate = mapper.createObjectNode();
            reportTemplate.put("schemaVersion", "1.0");
            reportTemplate.put("templateId", templateId.trim());
            reportTemplate.put("version", "1.0");
            reportTemplate.put("title", withoutDocxExtension(sourceSnapshot.getFileName()));
            ArrayNode reportSections = reportTemplate.putArray("sections");

            ObjectNode bindings = mapper.createObjectNode();
            bindings.put("schemaVersion", "1.0");
            bindings.put("templateId", templateId.trim());
            bindings.put("sourceSha256", sourceSnapshot.getSha256());
            ObjectNode sectionBindings = bindings.putObject("sections");

            ObjectNode currentSection = null;
            ObjectNode currentReportSection = null;
            ObjectNode currentBinding = null;
            int headingCount = 0;
            int maxLevel = 0;
            for (int sectionIndex = 0; sectionIndex < document.getSections().getCount(); sectionIndex++) {
                Section wordSection = document.getSections().get(sectionIndex);
                pageLayouts.add(pageLayout(wordSection, sectionIndex));
                NodeCollection children = wordSection.getBody().getChildNodes(NodeType.ANY, false);
                for (int childIndex = 0; childIndex < children.getCount(); childIndex++) {
                    Node child = children.get(childIndex);
                    String anchor = "s" + sectionIndex + "-b" + childIndex;
                    if (child instanceof Paragraph) {
                        Paragraph paragraph = (Paragraph) child;
                        String text = cleanText(paragraph.getText());
                        String styleName = paragraph.getParagraphFormat().getStyleName();
                        recordParagraphStyle(document, paragraphStyles, styleName, paragraph);
                        int level = headingLevel(paragraph);
                        if (level > 0 && !text.isEmpty()) {
                            headingCount++;
                            maxLevel = Math.max(maxLevel, level);
                            String id = String.format(Locale.ROOT, "section-%03d", headingCount);
                            currentSection = sections.addObject();
                            currentSection.put("id", id).put("level", level).put("title", text)
                                    .put("headingAnchor", anchor).put("headingStyle", styleName);
                            currentSection.putArray("blocks");
                            currentSection.putArray("placeholders");
                            currentReportSection = reportSections.addObject();
                            currentReportSection.put("id", id).put("level", level).put("title", text)
                                    .put("mode", "HEADING_ONLY");
                            currentReportSection.putArray("slots");
                            currentBinding = sectionBindings.putObject(id);
                            currentBinding.put("headingAnchor", anchor).put("headingStyle", styleName);
                            currentBinding.putObject("blockSamples");
                            currentBinding.putArray("placeholderAnchors");
                            currentBinding.putObject("slots");
                            if (headingCount == 1 && level != 1) {
                                issue(issues, "HEADING_LEVEL_GAP", anchor,
                                        "first heading is not level 1; review the outline before generation");
                            }
                        } else if (!text.isEmpty() || containsChart(paragraph)) {
                            if (currentSection == null) {
                                issue(issues, "UNMAPPED_CONTENT", anchor,
                                        "content before the first recognized heading is preserved but not AI-filled");
                            } else {
                                ObjectNode block = currentSection.withArray("blocks").addObject();
                                block.put("anchorId", anchor).put("type", containsChart(paragraph) ? "chart" : "paragraph")
                                        .put("styleName", styleName).put("text", text);
                                recordParagraphLayout(block, paragraph);
                                recordSample(currentBinding, block.path("type").asText(), anchor, issues);
                                addPlaceholders(paragraph, text, anchor, currentSection, currentReportSection, currentBinding);
                            }
                        }
                    } else if (child instanceof Table) {
                        Table table = (Table) child;
                        recordTableStyle(tableStyles, table);
                        if (currentSection == null) {
                            issue(issues, "UNMAPPED_CONTENT", anchor,
                                    "table before the first recognized heading is preserved but not AI-filled");
                        } else {
                            ObjectNode block = currentSection.withArray("blocks").addObject();
                            block.put("anchorId", anchor).put("type", "table")
                                    .put("styleName", table.getStyleName()).put("rows", table.getRows().getCount());
                            ArrayNode widths = block.putArray("firstRowCellWidths");
                            int columns = 0;
                            if (table.getRows().getCount() > 0) {
                                Row first = table.getRows().get(0);
                                columns = first.getCells().getCount();
                                for (Cell cell : first.getCells()) {
                                    widths.add(cell.getCellFormat().getWidth());
                                }
                            }
                            block.put("columns", columns).put("allowAutoFit", table.getAllowAutoFit());
                            block.put("alignment", table.getAlignment());
                            ArrayNode rowLayouts = block.putArray("rowLayouts");
                            for (int row = 0; row < table.getRows().getCount(); row++) {
                                Row tableRow = table.getRows().get(row);
                                ObjectNode rowLayout = rowLayouts.addObject();
                                rowLayout.put("rowIndex", row).put("height", tableRow.getRowFormat().getHeight())
                                        .put("allowBreakAcrossPages", tableRow.getRowFormat().getAllowBreakAcrossPages());
                                ArrayNode cells = rowLayout.putArray("cells");
                                for (int column = 0; column < tableRow.getCells().getCount(); column++) {
                                    Cell cell = tableRow.getCells().get(column);
                                    cells.addObject().put("columnIndex", column)
                                            .put("text", cleanText(cell.getText()))
                                            .put("width", cell.getCellFormat().getWidth())
                                            .put("horizontalMerge", cell.getCellFormat().getHorizontalMerge())
                                            .put("verticalMerge", cell.getCellFormat().getVerticalMerge())
                                            .put("verticalAlignment", cell.getCellFormat().getVerticalAlignment());
                                }
                            }
                            recordSample(currentBinding, "table", anchor, issues);
                            for (int row = 0; row < table.getRows().getCount(); row++) {
                                Row tableRow = table.getRows().get(row);
                                for (int column = 0; column < tableRow.getCells().getCount(); column++) {
                                    Cell cell = tableRow.getCells().get(column);
                                    String cellAnchor = anchor + "-r" + row + "c" + column;
                                    addPlaceholders(cell, cleanText(cell.getText()), cellAnchor,
                                            currentSection, currentReportSection, currentBinding);
                                }
                            }
                        }
                    }
                }
            }
            if (headingCount == 0) {
                issue(issues, "NO_HEADINGS", "document",
                        "no Word outline heading was recognized; a human must define section boundaries");
            }
            reportTemplate.put("tableOfContentsDepth", Math.max(1, maxLevel));
            for (int index = 0; index < sections.size(); index++) {
                ObjectNode section = (ObjectNode) sections.get(index);
                ObjectNode reportSection = (ObjectNode) reportSections.get(index);
                if (section.withArray("placeholders").size() > 0) {
                    reportSection.put("mode", "AI_WRITE");
                    reportSection.put("prompt", "从 facts 中 /content 提取本节需要的事实，按占位符含义填充；不得编造数据。");
                    reportSection.putArray("dataRefs").add("/content");
                    ArrayNode allowed = reportSection.putArray("allowedBlocks");
                    allowed.add("paragraph");
                    for (com.fasterxml.jackson.databind.JsonNode slot : reportSection.withArray("slots")) {
                        String type = slot.path("type").asText();
                        if (!contains(allowed, type)) {
                            allowed.add(type);
                        }
                    }
                    reportSection.putArray("requiredBlocks");
                } else {
                    issue(issues, "NO_FILL_MARKER", section.path("headingAnchor").asText(),
                            "section has no explicit fill marker; source content remains unchanged");
                }
            }
            return new DocxTemplateAnalysis(structure, reportTemplate, bindings);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to analyze DOCX template", e);
        }
    }

    private ObjectNode pageLayout(Section section, int index) {
        ObjectNode layout = mapper.createObjectNode();
        com.aspose.words.PageSetup page = section.getPageSetup();
        layout.put("sectionIndex", index).put("pageWidth", page.getPageWidth())
                .put("pageHeight", page.getPageHeight()).put("topMargin", page.getTopMargin())
                .put("bottomMargin", page.getBottomMargin()).put("leftMargin", page.getLeftMargin())
                .put("rightMargin", page.getRightMargin());
        return layout;
    }

    private void recordParagraphStyle(Document document, ObjectNode styles, String name, Paragraph sample) {
        if (name == null || name.trim().isEmpty() || styles.has(name)) return;
        ObjectNode item = styles.putObject(name);
        Style style = document.getStyles().get(name);
        item.put("name", name);
        if (style != null) {
            item.put("basedOn", style.getBaseStyleName());
            Font font = style.getFont();
            if (font != null) {
                item.put("asciiFont", font.getNameAscii()).put("farEastFont", font.getNameFarEast())
                        .put("fontSize", font.getSize()).put("bold", font.getBold());
            }
        }
        ParagraphFormat format = sample.getParagraphFormat();
        item.put("alignment", format.getAlignment()).put("spaceBefore", format.getSpaceBefore())
                .put("spaceAfter", format.getSpaceAfter()).put("firstLineIndent", format.getFirstLineIndent())
                .put("lineSpacing", format.getLineSpacing())
                .put("lineSpacingRule", format.getLineSpacingRule());
        Run firstRun = (Run) sample.getChild(NodeType.RUN, 0, false);
        if (firstRun != null) {
            ObjectNode direct = item.putObject("sampleRunFormat");
            direct.put("asciiFont", firstRun.getFont().getNameAscii())
                    .put("farEastFont", firstRun.getFont().getNameFarEast())
                    .put("fontSize", firstRun.getFont().getSize()).put("bold", firstRun.getFont().getBold());
        }
    }

    private void recordParagraphLayout(ObjectNode block, Paragraph paragraph) {
        ParagraphFormat format = paragraph.getParagraphFormat();
        ObjectNode layout = block.putObject("paragraphLayout");
        layout.put("alignment", format.getAlignment()).put("firstLineIndent", format.getFirstLineIndent())
                .put("leftIndent", format.getLeftIndent()).put("rightIndent", format.getRightIndent())
                .put("spaceBefore", format.getSpaceBefore()).put("spaceAfter", format.getSpaceAfter())
                .put("lineSpacing", format.getLineSpacing()).put("lineSpacingRule", format.getLineSpacingRule())
                .put("keepTogether", format.getKeepTogether())
                .put("keepWithNext", format.getKeepWithNext());
        ArrayNode runs = block.putArray("runs");
        for (Run run : paragraph.getRuns()) {
            Font font = run.getFont();
            runs.addObject().put("text", run.getText())
                    .put("asciiFont", font.getNameAscii()).put("farEastFont", font.getNameFarEast())
                    .put("fontSize", font.getSize()).put("bold", font.getBold())
                    .put("italic", font.getItalic());
        }
    }

    private void recordTableStyle(ObjectNode styles, Table table) {
        String name = table.getStyleName();
        if (name == null || name.trim().isEmpty() || styles.has(name)) return;
        styles.putObject(name).put("name", name).put("allowAutoFit", table.getAllowAutoFit());
    }

    private void addPlaceholders(CompositeNode<?> node, String text, String anchor,
                                 ObjectNode section, ObjectNode reportSection, ObjectNode binding) {
        Matcher matcher = TEXT_MARKER.matcher(text);
        while (matcher.find()) {
            placeholder(section, reportSection, binding, "TEXT", matcher.group(1), anchor);
        }
        NodeCollection controls = node.getChildNodes(NodeType.STRUCTURED_DOCUMENT_TAG, true);
        for (int index = 0; index < controls.getCount(); index++) {
            StructuredDocumentTag control = (StructuredDocumentTag) controls.get(index);
            String name = control.getTag();
            if (name == null || name.trim().isEmpty()) name = control.getTitle();
            if (name != null && !name.trim().isEmpty()) {
                placeholder(section, reportSection, binding, "CONTENT_CONTROL", name.trim(), anchor);
            }
        }
        NodeCollection bookmarks = node.getChildNodes(NodeType.BOOKMARK_START, true);
        for (int index = 0; index < bookmarks.getCount(); index++) {
            String name = ((BookmarkStart) bookmarks.get(index)).getName();
            if (name != null && name.startsWith("fill_")) {
                placeholder(section, reportSection, binding, "BOOKMARK", name, anchor);
            }
        }
    }

    private void placeholder(ObjectNode section, ObjectNode reportSection, ObjectNode binding,
                             String kind, String name, String anchor) {
        String slotId = String.format(Locale.ROOT, "slot-%03d", reportSection.withArray("slots").size() + 1);
        String type = name.startsWith("chart:") ? "chart" : name.startsWith("table:") ? "table" : "paragraph";
        ObjectNode marker = section.withArray("placeholders").addObject();
        marker.put("kind", kind).put("name", name).put("anchorId", anchor).put("slotId", slotId);
        reportSection.withArray("slots").addObject().put("id", slotId).put("name", name).put("type", type);
        binding.with("slots").putObject(slotId).put("kind", kind).put("name", name)
                .put("anchorId", anchor).put("type", type);
        binding.withArray("placeholderAnchors").add(anchor);
    }

    private void recordSample(ObjectNode binding, String type, String anchor, ArrayNode issues) {
        ObjectNode samples = binding.with("blockSamples");
        if (samples.has(type)) {
            issue(issues, "AMBIGUOUS_BLOCK_SAMPLE", anchor,
                    "multiple " + type + " samples exist in one section; confirm which one is repeatable");
        } else {
            samples.put(type, anchor);
        }
    }

    private boolean containsChart(Paragraph paragraph) {
        NodeCollection shapes = paragraph.getChildNodes(NodeType.SHAPE, true);
        for (int index = 0; index < shapes.getCount(); index++) {
            if (((Shape) shapes.get(index)).hasChart()) return true;
        }
        return false;
    }

    private int headingLevel(Paragraph paragraph) {
        // Word 的 Title 样式用于报告封面标题，不应成为待 AI 填充的一级章节。
        if (paragraph.getParagraphFormat().getStyleIdentifier() == StyleIdentifier.TITLE) return 0;
        int outline = paragraph.getParagraphFormat().getOutlineLevel();
        if (outline >= OutlineLevel.LEVEL_1 && outline <= OutlineLevel.LEVEL_9) return outline + 1;
        String style = paragraph.getParagraphFormat().getStyleName();
        if (style == null) return 0;
        Matcher matcher = HEADING_STYLE.matcher(style.trim());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private void issue(ArrayNode issues, String code, String anchor, String message) {
        issues.addObject().put("code", code).put("anchorId", anchor).put("message", message);
    }

    private boolean contains(ArrayNode values, String text) {
        for (com.fasterxml.jackson.databind.JsonNode value : values) {
            if (text.equals(value.asText())) return true;
        }
        return false;
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replace('\u0007', ' ').replace('\r', ' ').trim();
    }

    private String withoutDocxExtension(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".docx") ? name.substring(0, name.length() - 5) : name;
    }
}
