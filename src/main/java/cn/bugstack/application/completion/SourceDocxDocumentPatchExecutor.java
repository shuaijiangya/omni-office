package cn.bugstack.application.completion;

import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Run;
import com.aspose.words.StyleIdentifier;
import com.aspose.words.ChartType;
import com.aspose.words.OutlineLevel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 对源 DOCX 的多个正文段落和表格执行一次校验、一次保存。 */
public final class SourceDocxDocumentPatchExecutor {

    private final SourceDocxStructureExtractor extractor = new SourceDocxStructureExtractor();
    private final DocumentCompletionPlanValidator validator = new DocumentCompletionPlanValidator();
    private final SourceDocxTablePatchExecutor tableExecutor = new SourceDocxTablePatchExecutor();

    /**
     * 应用多位置补全计划并生成新 DOCX。
     *
     * @param source 上传的源 DOCX
     * @param output 预览或正式输出 DOCX
     * @param plan 已审核的补全计划
     * @return 保存结果和修改统计
     */
    public DocumentCompletionResult apply(Path source, Path output, DocumentCompletionPlan plan) {
        if (output == null) throw new IllegalArgumentException("output DOCX path is required");
        DocumentStructureSnapshot snapshot = extractor.extract(source);
        List<String> errors = validator.validate(snapshot, plan);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("document completion plan is invalid: "
                    + String.join("; ", errors));
        }
        try {
            AsposeWordsLicenseLoader.applyConfiguredLicense();
            Document document = new Document(source.toString());
            Map<String, Paragraph> sourceParagraphs = sourceParagraphs(document, snapshot);
            int updated = 0;
            int appended = 0;
            for (TableCompletionPlanningResult tablePlan : plan.getTables()) {
                SourceTableSnapshot table = findTable(snapshot, tablePlan.getPatchPlan().getTableId());
                int[] counts = tableExecutor.applyToDocument(document, table,
                        tablePlan.getBinding(), tablePlan.getPatchPlan());
                updated += counts[0];
                appended += counts[1];
            }
            int cellsChanged = 0;
            for (CellPatchPlan cellPlan : plan.getCells()) {
                SourceTableSnapshot table = findTable(snapshot, cellPlan.getTableId());
                if (tableExecutor.applyCellToDocument(document, table, cellPlan)) cellsChanged++;
            }
            int runsChanged = 0;
            for (RunPatchPlan patch : plan.getRuns()) {
                Paragraph paragraph = sourceParagraphs.get(patch.getParagraphId());
                Run run = (Run) paragraph.getChildNodes(NodeType.RUN, false).get(patch.getRunIndex());
                if (!run.getText().equals(patch.getText())) {
                    run.setText(patch.getText());
                    runsChanged++;
                }
            }
            Map<String, Node> lastAfterAnchor = new HashMap<>();
            int paragraphChanges = 0;
            for (ParagraphPatchPlan patch : plan.getParagraphs()) {
                Paragraph target = sourceParagraphs.get(patch.getParagraphId());
                if (patch.getOperation() == ParagraphPatchOperation.REPLACE_TEXT) {
                    replaceText(document, target, patch.getTexts().get(0));
                    paragraphChanges++;
                } else {
                    Node previous = target;
                    for (String text : patch.getTexts()) {
                        Paragraph inserted = bodyParagraphAfter(document, target, text);
                        previous.getParentNode().insertAfter(inserted, previous);
                        previous = inserted;
                        paragraphChanges++;
                    }
                    lastAfterAnchor.put(patch.getParagraphId(), previous);
                }
            }
            int chartsInserted = 0;
            for (ChartPatchPlan chartPatch : plan.getCharts()) {
                Paragraph anchor = sourceParagraphs.get(chartPatch.getAnchorParagraphId());
                Node after = lastAfterAnchor.getOrDefault(chartPatch.getAnchorParagraphId(), anchor);
                Paragraph chartParagraph = bodyParagraphAfter(document, anchor, "");
                after.getParentNode().insertAfter(chartParagraph, after);
                DocumentBuilder builder = new DocumentBuilder(document);
                builder.moveTo(chartParagraph);
                double available = chartParagraph.getParentSection().getPageSetup().getPageWidth()
                        - chartParagraph.getParentSection().getPageSetup().getLeftMargin()
                        - chartParagraph.getParentSection().getPageSetup().getRightMargin();
                double width = Math.min(450D, available);
                com.aspose.words.Chart chart = builder.insertChart(chartType(chartPatch.getChartType()),
                        width, width * 0.58D).getChart();
                chart.getSeries().clear();
                double[] values = chartPatch.getValues().stream().mapToDouble(Double::doubleValue).toArray();
                chart.getSeries().add(chartPatch.getSeriesName(),
                        chartPatch.getCategories().toArray(new String[0]), values);
                chart.getTitle().setShow(true);
                chart.getTitle().setText(chartPatch.getTitle());
                String chartFont = document.getStyles().getByStyleIdentifier(StyleIdentifier.NORMAL)
                        .getFont().getNameFarEast();
                if (chartFont == null || chartFont.trim().isEmpty()) {
                    chartFont = document.getStyles().getByStyleIdentifier(StyleIdentifier.NORMAL)
                            .getFont().getName();
                }
                if (chartFont != null && !chartFont.trim().isEmpty()) {
                    chart.getTitle().getFont().setName(chartFont);
                    chart.getLegend().getFont().setName(chartFont);
                    chart.getAxisX().getTickLabels().getFont().setName(chartFont);
                    chart.getAxisY().getTickLabels().getFont().setName(chartFont);
                }
                chartsInserted++;
            }
            SourceDocxTablePatchExecutor.atomicSave(document, output);
            return new DocumentCompletionResult(output.toAbsolutePath().normalize(),
                    paragraphChanges, updated, appended, cellsChanged, runsChanged, chartsInserted);
        } catch (Exception e) {
            throw new IllegalStateException("failed to apply document completion plan", e);
        }
    }

    private void replaceText(Document document, Paragraph paragraph, String value) {
        Run prototype = paragraph.getRuns().getCount() > 0
                ? (Run) paragraph.getRuns().get(0).deepClone(true) : new Run(document);
        paragraph.removeAllChildren();
        prototype.setText(value);
        paragraph.appendChild(prototype);
    }

    /** 在修改前固定原段落对象，避免前方插入内容使物理序号漂移。 */
    private Map<String, Paragraph> sourceParagraphs(Document document, DocumentStructureSnapshot snapshot) {
        Map<String, Paragraph> anchors = new HashMap<>();
        for (SourceParagraphSnapshot source : snapshot.getParagraphs()) {
            NodeCollection children = document.getSections().get(source.getSectionIndex())
                    .getBody().getChildNodes(NodeType.ANY, false);
            anchors.put(source.getParagraphId(), (Paragraph) children.get(source.getBodyChildIndex()));
        }
        return anchors;
    }

    /** 新正文沿用同一文档节里最近的非标题段落及其字符样式。 */
    private Paragraph bodyParagraphAfter(Document document, Paragraph anchor, String text) {
        Paragraph sample = null;
        Node next = anchor.getNextSibling();
        while (next != null) {
            if (next instanceof Paragraph) {
                Paragraph candidate = (Paragraph) next;
                if (isHeading(candidate)) break;
                sample = candidate;
                break;
            }
            next = next.getNextSibling();
        }
        if (sample == null) {
            Node previous = anchor.getPreviousSibling();
            while (previous != null) {
                if (previous instanceof Paragraph && !isHeading((Paragraph) previous)) {
                    sample = (Paragraph) previous;
                    break;
                }
                previous = previous.getPreviousSibling();
            }
        }
        Paragraph result = sample == null ? new Paragraph(document) : (Paragraph) sample.deepClone(true);
        if (sample == null) result.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        Run fontSample = sample != null && sample.getRuns().getCount() > 0
                ? (Run) sample.getRuns().get(0).deepClone(true) : new Run(document);
        result.removeAllChildren();
        if (!text.isEmpty()) {
            fontSample.setText(text);
            result.appendChild(fontSample);
        }
        return result;
    }

    /** 识别 Word 语义标题，避免把标题字体复制到新正文。 */
    private boolean isHeading(Paragraph paragraph) {
        int outline = paragraph.getParagraphFormat().getOutlineLevel();
        return outline >= OutlineLevel.LEVEL_1 && outline <= OutlineLevel.LEVEL_9;
    }

    /** 将受控图表类型映射为 Aspose 原生图表。 */
    private int chartType(String type) {
        switch (type) {
            case "BAR": return ChartType.BAR;
            case "PIE": return ChartType.PIE;
            case "LINE": return ChartType.LINE;
            case "RADAR": return ChartType.RADAR;
            default: return ChartType.COLUMN;
        }
    }

    private SourceTableSnapshot findTable(DocumentStructureSnapshot snapshot, String tableId) {
        for (SourceTableSnapshot table : snapshot.getTables()) {
            if (table.getTableId().equals(tableId)) return table;
        }
        throw new IllegalArgumentException("unknown table: " + tableId);
    }
}
