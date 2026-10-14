package cn.bugstack.application.completion;

import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.Document;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Run;
import com.aspose.words.StyleIdentifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
            Map<String, SourceParagraphSnapshot> paragraphs = new HashMap<>();
            for (SourceParagraphSnapshot paragraph : snapshot.getParagraphs()) {
                paragraphs.put(paragraph.getParagraphId(), paragraph);
            }
            int runsChanged = 0;
            for (RunPatchPlan patch : plan.getRuns()) {
                SourceParagraphSnapshot anchor = paragraphs.get(patch.getParagraphId());
                NodeCollection children = document.getSections().get(anchor.getSectionIndex())
                        .getBody().getChildNodes(NodeType.ANY, false);
                Paragraph paragraph = (Paragraph) children.get(anchor.getBodyChildIndex());
                Run run = (Run) paragraph.getChildNodes(NodeType.RUN, false).get(patch.getRunIndex());
                if (!run.getText().equals(patch.getText())) {
                    run.setText(patch.getText());
                    runsChanged++;
                }
            }
            List<ParagraphPatchPlan> patches = new ArrayList<>(plan.getParagraphs());
            patches.sort(Comparator.comparingInt((ParagraphPatchPlan patch) ->
                    paragraphs.get(patch.getParagraphId()).getSectionIndex()).reversed()
                    .thenComparing(Comparator.comparingInt((ParagraphPatchPlan patch) ->
                            paragraphs.get(patch.getParagraphId()).getBodyChildIndex()).reversed()));
            int paragraphChanges = 0;
            for (ParagraphPatchPlan patch : patches) {
                SourceParagraphSnapshot anchor = paragraphs.get(patch.getParagraphId());
                NodeCollection children = document.getSections().get(anchor.getSectionIndex())
                        .getBody().getChildNodes(NodeType.ANY, false);
                Paragraph target = (Paragraph) children.get(anchor.getBodyChildIndex());
                if (patch.getOperation() == ParagraphPatchOperation.REPLACE_TEXT) {
                    replaceText(document, target, patch.getTexts().get(0));
                    paragraphChanges++;
                } else {
                    Node previous = target;
                    for (String text : patch.getTexts()) {
                        Paragraph inserted = new Paragraph(document);
                        inserted.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
                        inserted.appendChild(new Run(document, text));
                        previous.getParentNode().insertAfter(inserted, previous);
                        previous = inserted;
                        paragraphChanges++;
                    }
                }
            }
            SourceDocxTablePatchExecutor.atomicSave(document, output);
            return new DocumentCompletionResult(output.toAbsolutePath().normalize(),
                    paragraphChanges, updated, appended, cellsChanged, runsChanged);
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

    private SourceTableSnapshot findTable(DocumentStructureSnapshot snapshot, String tableId) {
        for (SourceTableSnapshot table : snapshot.getTables()) {
            if (table.getTableId().equals(tableId)) return table;
        }
        throw new IllegalArgumentException("unknown table: " + tableId);
    }
}
