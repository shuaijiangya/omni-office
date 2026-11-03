package cn.bugstack.application.template.importer;

import cn.bugstack.application.ai.report.StructuredReportAiApplication;
import cn.bugstack.office.docx.model.ChartNode;
import cn.bugstack.office.docx.model.ChartRenderMode;
import cn.bugstack.office.docx.model.ChartSeriesNode;
import cn.bugstack.office.docx.model.ChartStylePreset;
import cn.bugstack.office.docx.model.ChartType;
import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import cn.bugstack.office.docx.render.EChartsSvgRenderer;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.BlockSpec;
import cn.bugstack.protocol.document.block.ChartBlockSpec;
import cn.bugstack.protocol.document.block.ChartSeriesSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import com.aspose.words.Bookmark;
import com.aspose.words.Cell;
import com.aspose.words.CompositeNode;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Row;
import com.aspose.words.Run;
import com.aspose.words.Shape;
import com.aspose.words.StructuredDocumentTag;
import com.aspose.words.Table;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将逐标题 AI 结果按已审核的物理槽位写回原 DOCX 模板。
 *
 * <p>只修改明确标记的槽位；其它封面、目录、标题、段落和固定表格行保持源文档原样。
 * 表格动态行须在首个数据行放置 {@code {{table:name}}}，该行作为重复样式原型。</p>
 */
public final class DocxTemplateReportApplication {

    private final StructuredReportAiApplication ai;
    private final DocxTemplateBindingReview review = new DocxTemplateBindingReview();

    /** @param ai 已配置的逐标题 AI 生成器 */
    public DocxTemplateReportApplication(StructuredReportAiApplication ai) {
        if (ai == null) throw new IllegalArgumentException("AI application is required");
        this.ai = ai;
    }

    /**
     * 生成后写入新 DOCX；源模板不会被覆盖。
     *
     * @param source 源 DOCX
     * @param reportTemplate 审核后的逐标题模板
     * @param bindings 审核后的物理样式与槽位绑定
     * @param businessData 业务事实 JSON
     * @param output 新 DOCX 路径
     */
    public void generate(Path source, JsonNode reportTemplate, JsonNode bindings,
                         JsonNode businessData, Path output) {
        if (source == null || output == null || source.toAbsolutePath().normalize()
                .equals(output.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("output must differ from source DOCX");
        }
        review.validate(source, reportTemplate, bindings);
        DocumentSpec generated = ai.generate(reportTemplate, businessData);
        List<SectionOutput> sections = flatten(generated);
        if (sections.size() != reportTemplate.path("sections").size()) {
            throw new IllegalStateException("generated outline differs from source template");
        }
        try {
            AsposeWordsLicenseLoader.applyConfiguredLicense();
            Document document = new Document(source.toString());
            Map<String, Node> anchors = anchorIndex(document);
            for (int index = 0; index < sections.size(); index++) {
                JsonNode definition = reportTemplate.path("sections").get(index);
                SectionOutput section = sections.get(index);
                if (!section.title.equals(definition.path("title").asText())) {
                    throw new IllegalStateException("generated title differs from source: " + section.title);
                }
                JsonNode targets = bindings.path("sections").path(definition.path("id").asText()).path("slots");
                Set<String> filled = new HashSet<>();
                for (BlockSpec block : section.blocks) {
                    String slotId = block.getSlotId();
                    if (slotId == null || !filled.add(slotId) || !targets.has(slotId)) {
                        throw new IllegalStateException("missing or duplicate template slot: " + slotId);
                    }
                    fill(document, anchors, targets.path(slotId), block);
                }
                if (filled.size() != targets.size()) {
                    throw new IllegalStateException("not every template slot was filled in " + section.title);
                }
            }
            Path absoluteOutput = output.toAbsolutePath().normalize();
            Path parent = absoluteOutput.getParent();
            Files.createDirectories(parent);
            if (Files.exists(absoluteOutput)) {
                throw new IllegalStateException("output DOCX already exists: " + absoluteOutput);
            }
            Path temporaryOutput = Files.createTempFile(parent, ".template-report-", ".docx");
            try {
                document.save(temporaryOutput.toString());
                try {
                    Files.move(temporaryOutput, absoluteOutput, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException unavailable) {
                    Files.move(temporaryOutput, absoluteOutput);
                }
            } finally {
                Files.deleteIfExists(temporaryOutput);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to write reviewed DOCX template", e);
        }
    }

    private void fill(Document document, Map<String, Node> anchors, JsonNode target, BlockSpec block)
            throws Exception {
        Node node = anchors.get(target.path("anchorId").asText());
        if (node == null) throw new IllegalStateException("slot anchor is unavailable");
        String kind = target.path("kind").asText();
        String name = target.path("name").asText();
        if (block instanceof ParagraphBlockSpec) {
            replace(node, kind, name, ((ParagraphBlockSpec) block).getText());
        } else if (block instanceof TableBlockSpec) {
            if (!(node.getParentNode() instanceof Cell)) {
                throw new IllegalStateException("table slot must be inside a table cell");
            }
            fillTableRow((Cell) node.getParentNode(), (TableBlockSpec) block, kind, name);
        } else if (block instanceof ChartBlockSpec) {
            if (!(node instanceof Paragraph)) throw new IllegalStateException("chart slot must be a paragraph");
            if (!"TEXT".equals(kind) || !node.getText().trim().equals("{{" + name + "}}")) {
                throw new IllegalStateException("chart marker must occupy its own paragraph");
            }
            replace(node, kind, name, "");
            insertChart(document, (Paragraph) node, (ChartBlockSpec) block);
        } else {
            throw new IllegalStateException("unsupported source-template block type: " + block.getClass());
        }
    }

    private void replace(Node node, String kind, String name, String value) throws Exception {
        if ("TEXT".equals(kind)) {
            int count = node.getRange().replace("{{" + name + "}}", value);
            if (count != 1) throw new IllegalStateException("text marker must occur exactly once: " + name);
        } else if ("CONTENT_CONTROL".equals(kind)) {
            NodeCollection controls = ((CompositeNode<?>) node).getChildNodes(NodeType.STRUCTURED_DOCUMENT_TAG, true);
            int matched = 0;
            for (int index = 0; index < controls.getCount(); index++) {
                StructuredDocumentTag control = (StructuredDocumentTag) controls.get(index);
                if (name.equals(control.getTag()) || name.equals(control.getTitle())) {
                    NodeCollection runs = control.getChildNodes(NodeType.RUN, true);
                    if (runs.getCount() == 0) {
                        throw new IllegalStateException("content control needs a styled text sample: " + name);
                    }
                    ((Run) runs.get(0)).setText(value);
                    for (int run = 1; run < runs.getCount(); run++) ((Run) runs.get(run)).setText("");
                    matched++;
                }
            }
            if (matched != 1) throw new IllegalStateException("content-control marker is ambiguous: " + name);
        } else if ("BOOKMARK".equals(kind)) {
            Bookmark bookmark = node.getDocument().getRange().getBookmarks().get(name);
            if (bookmark == null) throw new IllegalStateException("bookmark no longer exists: " + name);
            bookmark.setText(value);
        } else {
            throw new IllegalStateException("unknown template marker kind: " + kind);
        }
    }

    private void fillTableRow(Cell markerCell, TableBlockSpec generated, String kind, String name)
            throws Exception {
        if (!"TEXT".equals(kind) || !name.startsWith("table:")) {
            throw new IllegalStateException("repeat table row requires {{table:name}} marker");
        }
        Row prototype = (Row) markerCell.getParentNode();
        Table table = (Table) prototype.getParentNode();
        int width = prototype.getCells().getCount();
        if (generated.getHeaders().size() != width || generated.getRows().isEmpty()) {
            throw new IllegalStateException("generated table width or rows do not match source prototype");
        }
        int rowIndex = table.getRows().indexOf(prototype);
        if (rowIndex < 1) throw new IllegalStateException("repeat row requires a fixed header above it");
        Row header = table.getRows().get(rowIndex - 1);
        for (int column = 0; column < width; column++) {
            String expected = header.getCells().get(column).toString(com.aspose.words.SaveFormat.TEXT).trim();
            if (!expected.equals(generated.getHeaders().get(column))) {
                throw new IllegalStateException("AI table header differs from fixed template header");
            }
        }
        List<Row> rows = new ArrayList<>();
        rows.add(prototype);
        for (int index = 1; index < generated.getRows().size(); index++) {
            Row clone = (Row) prototype.deepClone(true);
            table.insertAfter(clone, rows.get(rows.size() - 1));
            rows.add(clone);
        }
        for (int index = 0; index < rows.size(); index++) {
            List<String> values = generated.getRows().get(index);
            if (values.size() != width) throw new IllegalStateException("generated row width mismatch");
            for (int column = 0; column < width; column++) {
                Cell cell = rows.get(index).getCells().get(column);
                if (cell.getParagraphs().getCount() != 1 || cell.getTables().getCount() > 0) {
                    throw new IllegalStateException("repeat row contains complex cell content");
                }
                Paragraph paragraph = cell.getFirstParagraph();
                Run sample = (Run) paragraph.getChild(NodeType.RUN, 0, false);
                for (int run = paragraph.getRuns().getCount() - 1; run >= 0; run--) {
                    paragraph.getRuns().get(run).remove();
                }
                Run replacement = sample == null ? new Run(cell.getDocument(), values.get(column))
                        : (Run) sample.deepClone(true);
                replacement.setText(values.get(column));
                paragraph.appendChild(replacement);
            }
        }
    }

    private void insertChart(Document document, Paragraph target, ChartBlockSpec spec) throws Exception {
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.moveTo(target);
        double width = Math.min(spec.getWidthPoints(), target.getParentSection().getPageSetup().getPageWidth()
                - target.getParentSection().getPageSetup().getLeftMargin()
                - target.getParentSection().getPageSetup().getRightMargin());
        double height = spec.getHeightPoints() * width / spec.getWidthPoints();
        if ("ECHARTS_IMAGE".equals(spec.getRenderMode())) {
            builder.insertImage(new EChartsSvgRenderer().render(toNode(spec)), width, height);
            return;
        }
        int type = asposeType(spec.getChartType());
        Shape shape = builder.insertChart(type, width, height);
        com.aspose.words.Chart chart = shape.getChart();
        chart.getSeries().clear();
        String[] categories = spec.getCategories().toArray(new String[0]);
        int index = 0;
        for (ChartSeriesSpec item : spec.getSeries()) {
            double[] values = item.getValues().stream().mapToDouble(Double::doubleValue).toArray();
            com.aspose.words.ChartSeries series = chart.getSeries().add(item.getName(), categories, values);
            applyColor(series, spec.getStylePreset(), index++);
        }
        chart.getTitle().setShow(spec.getTitle() != null && !spec.getTitle().isEmpty());
        if (spec.getTitle() != null && !spec.getTitle().isEmpty()) chart.getTitle().setText(spec.getTitle());
    }

    private void applyColor(com.aspose.words.ChartSeries series, String preset, int index) {
        if ("DEFAULT".equals(preset)) return;
        java.awt.Color[] colors = "MONOCHROME".equals(preset)
                ? new java.awt.Color[]{new java.awt.Color(45, 45, 45), new java.awt.Color(110, 110, 110)}
                : new java.awt.Color[]{new java.awt.Color(24, 76, 139), new java.awt.Color(47, 121, 184)};
        java.awt.Color color = colors[index % colors.length];
        series.getFormat().getFill().setForeColor(color);
        series.getFormat().getStroke().setForeColor(color);
    }

    private ChartNode toNode(ChartBlockSpec spec) {
        ChartNode node = new ChartNode();
        node.setChartType(ChartType.valueOf(spec.getChartType()));
        node.setStylePreset(ChartStylePreset.valueOf(spec.getStylePreset()));
        node.setRenderMode(ChartRenderMode.ECHARTS_IMAGE);
        node.setTitle(spec.getTitle());
        node.setCategories(spec.getCategories());
        node.setWidthPoints(spec.getWidthPoints());
        node.setHeightPoints(spec.getHeightPoints());
        for (ChartSeriesSpec series : spec.getSeries()) {
            node.addSeries(new ChartSeriesNode(series.getName(), series.getValues()));
        }
        return node;
    }

    private int asposeType(String type) {
        switch (type) {
            case "BAR": return com.aspose.words.ChartType.BAR;
            case "PIE": return com.aspose.words.ChartType.PIE;
            case "LINE": return com.aspose.words.ChartType.LINE;
            case "RADAR": return com.aspose.words.ChartType.RADAR;
            default: return com.aspose.words.ChartType.COLUMN;
        }
    }

    private Map<String, Node> anchorIndex(Document document) {
        Map<String, Node> anchors = new HashMap<>();
        for (int section = 0; section < document.getSections().getCount(); section++) {
            NodeCollection children = document.getSections().get(section).getBody().getChildNodes(NodeType.ANY, false);
            for (int child = 0; child < children.getCount(); child++) {
                Node node = children.get(child);
                String key = "s" + section + "-b" + child;
                anchors.put(key, node);
                if (node instanceof Table) {
                    Table table = (Table) node;
                    for (int row = 0; row < table.getRows().getCount(); row++) {
                        Row tableRow = table.getRows().get(row);
                        for (int column = 0; column < tableRow.getCells().getCount(); column++) {
                            anchors.put(key + "-r" + row + "c" + column,
                                    tableRow.getCells().get(column).getFirstParagraph());
                        }
                    }
                }
            }
        }
        return anchors;
    }

    private List<SectionOutput> flatten(DocumentSpec document) {
        List<SectionOutput> result = new ArrayList<>();
        for (SectionSpec root : document.getSections()) visit(root.getTitle(), root.getBlocks(), result);
        return result;
    }

    private void visit(String title, List<BlockSpec> blocks, List<SectionOutput> result) {
        SectionOutput output = new SectionOutput(title);
        result.add(output);
        for (BlockSpec block : blocks) {
            if (block instanceof SubsectionBlockSpec) {
                SubsectionBlockSpec child = (SubsectionBlockSpec) block;
                visit(child.getTitle(), child.getBlocks(), result);
            } else output.blocks.add(block);
        }
    }

    private static final class SectionOutput {
        private final String title;
        private final List<BlockSpec> blocks = new ArrayList<>();
        private SectionOutput(String title) { this.title = title; }
    }
}
