package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 在修改源 DOCX 前校验多位置补全计划。 */
public final class DocumentCompletionPlanValidator {

    private final TablePatchValidator tableValidator = new TablePatchValidator();

    /** @return 所有校验错误；空列表表示可执行 */
    public List<String> validate(DocumentStructureSnapshot snapshot, DocumentCompletionPlan plan) {
        List<String> errors = new ArrayList<>();
        if (snapshot == null || plan == null) {
            errors.add("document snapshot and completion plan are required");
            return errors;
        }
        if (!snapshot.getSha256().equals(plan.getSourceSha256())) {
            errors.add("source DOCX changed after planning");
        }
        if (!plan.isReady()) {
            errors.add("completion plan has no executable operations or requires user input");
        }
        Set<String> paragraphIds = new HashSet<>();
        for (ParagraphPatchPlan patch : plan.getParagraphs()) {
            if (patch == null || !paragraphIds.add(patch.getParagraphId())) {
                errors.add("paragraph patches must be non-null and target unique paragraphs");
                continue;
            }
            SourceParagraphSnapshot paragraph = findParagraph(snapshot, patch.getParagraphId());
            if (paragraph == null) {
                errors.add("unknown paragraph: " + patch.getParagraphId());
                continue;
            }
            if (paragraph.isContainsObjects() && patch.getOperation() == ParagraphPatchOperation.REPLACE_TEXT) {
                errors.add("paragraph contains fields, shapes or structured content: " + patch.getParagraphId());
            }
            if (patch.getOperation() == null || patch.getOverwritePolicy() == null
                    || patch.getTexts().isEmpty()) {
                errors.add("paragraph operation, overwrite policy and text are required: "
                        + patch.getParagraphId());
                continue;
            }
            if (patch.getOperation() == ParagraphPatchOperation.REPLACE_TEXT && patch.getTexts().size() != 1) {
                errors.add("REPLACE_TEXT requires exactly one paragraph: " + patch.getParagraphId());
            }
            if (patch.getOperation() == ParagraphPatchOperation.REPLACE_TEXT
                    && patch.getTexts().size() == 1
                    && Objects.equals(paragraph.getText(), patch.getTexts().get(0))) {
                errors.add("replacement text is identical to the source; use the supplied fact: "
                        + patch.getParagraphId());
            }
            if (patch.getOperation() == ParagraphPatchOperation.INSERT_AFTER
                    && patch.getTexts().contains(paragraph.getText())) {
                errors.add("INSERT_AFTER must not duplicate the anchor paragraph: "
                        + patch.getParagraphId());
            }
            if (patch.getOperation() == ParagraphPatchOperation.REPLACE_TEXT
                    && paragraph.getRuns().size() > 1) {
                errors.add("REPLACE_TEXT would discard independently styled Runs; use run patches: "
                        + patch.getParagraphId());
            }
            if (patch.getOperation() == ParagraphPatchOperation.REPLACE_TEXT
                    && patch.getOverwritePolicy() == TableOverwritePolicy.BLANKS_ONLY
                    && !paragraph.getText().isEmpty()) {
                errors.add("BLANKS_ONLY cannot replace a nonempty paragraph: " + patch.getParagraphId());
            }
            if (patch.getOperation() == ParagraphPatchOperation.INSERT_AFTER
                    && patch.getOverwritePolicy() == TableOverwritePolicy.OVERWRITE_ALL) {
                errors.add("INSERT_AFTER does not overwrite the anchor paragraph: " + patch.getParagraphId());
            }
            for (String value : patch.getTexts()) {
                if (value == null || value.trim().isEmpty() || value.length() > 20_000) {
                    errors.add("paragraph text must be nonblank and at most 20000 characters: "
                            + patch.getParagraphId());
                }
            }
        }
        Set<String> runAddresses = new HashSet<>();
        for (RunPatchPlan patch : plan.getRuns()) {
            if (patch == null) {
                errors.add("run patch must not be null");
                continue;
            }
            String address = patch.getParagraphId() + ":" + patch.getRunIndex();
            if (!runAddresses.add(address)) errors.add("duplicate run patch: " + address);
            if (paragraphIds.contains(patch.getParagraphId())) {
                errors.add("cannot mix paragraph and run patches on one anchor: " + patch.getParagraphId());
            }
            SourceParagraphSnapshot paragraph = findParagraph(snapshot, patch.getParagraphId());
            if (paragraph == null) {
                errors.add("unknown run patch paragraph: " + patch.getParagraphId());
                continue;
            }
            if (patch.getRunIndex() < 0 || patch.getRunIndex() >= paragraph.getRuns().size()) {
                errors.add("run index is outside the source paragraph: " + address);
                continue;
            }
            SourceRunSnapshot run = paragraph.getRuns().get(patch.getRunIndex());
            if (!run.isPlainText()) errors.add("run contains nontext controls: " + address);
            if (!Objects.equals(run.getText(), patch.getExpectedText())) {
                errors.add("run source text changed: " + address);
            }
            if (Objects.equals(run.getText(), patch.getText())) {
                errors.add("run replacement is identical to the source; use the supplied fact: " + address);
            }
            if (patch.getOverwritePolicy() == null || patch.getText() == null
                    || patch.getText().trim().isEmpty() || patch.getText().length() > 20_000
                    || containsControl(patch.getText())) {
                errors.add("run patch requires a policy and plain nonblank text of at most 20000 characters: "
                        + address);
            } else if (patch.getOverwritePolicy() == TableOverwritePolicy.BLANKS_ONLY
                    && run.getText() != null && !run.getText().trim().isEmpty()) {
                errors.add("BLANKS_ONLY cannot replace a nonempty run: " + address);
            }
        }
        Set<String> tableIds = new HashSet<>();
        for (TableCompletionPlanningResult tablePlan : plan.getTables()) {
            if (tablePlan == null || tablePlan.getStatus() != TableCompletionStatus.READY
                    || tablePlan.getBinding() == null || tablePlan.getPatchPlan() == null) {
                errors.add("table plan must be READY with binding and patch data");
                continue;
            }
            String tableId = tablePlan.getPatchPlan().getTableId();
            if (!tableIds.add(tableId)) {
                errors.add("duplicate table patch: " + tableId);
            }
            SourceTableSnapshot table = findTable(snapshot, tableId);
            if (table == null) {
                errors.add("unknown table: " + tableId);
                continue;
            }
            if (!snapshot.getSha256().equals(tablePlan.getBinding().getSourceSha256())) {
                errors.add("table binding source DOCX changed: " + tableId);
            }
            errors.addAll(tableValidator.validate(table, tablePlan.getBinding(), tablePlan.getPatchPlan()));
        }
        Set<String> cellAddresses = new HashSet<>();
        for (CellPatchPlan cell : plan.getCells()) {
            if (cell == null) {
                errors.add("cell patch must not be null");
                continue;
            }
            SourceTableSnapshot table = findTable(snapshot, cell.getTableId());
            if (table == null) {
                errors.add("unknown cell patch table: " + cell.getTableId());
                continue;
            }
            String address = cell.getTableId() + ":" + cell.getRowIndex() + ":" + cell.getColumnIndex();
            if (!cellAddresses.add(address)) errors.add("duplicate cell patch: " + address);
            if (tableIds.contains(cell.getTableId())) {
                errors.add("cannot mix row UPSERT and physical cell patches in one table: " + cell.getTableId());
            }
            if (cell.getRowIndex() < 0 || cell.getRowIndex() > table.getRows().size()) {
                errors.add("cell patch row is outside source table: " + address);
                continue;
            }
            List<String> row = cell.getRowIndex() == 0 ? table.getHeaders()
                    : table.getRows().get(cell.getRowIndex() - 1);
            if (cell.getColumnIndex() < 0 || cell.getColumnIndex() >= row.size()) {
                errors.add("cell patch column is outside source table: " + address);
                continue;
            }
            if (!table.isCellWritable(cell.getRowIndex(), cell.getColumnIndex())) {
                errors.add("cell patch target contains merged continuation or structured content: " + address);
            }
            if (cell.getOverwritePolicy() == null || cell.getText() == null
                    || cell.getText().trim().isEmpty() || cell.getText().length() > 20_000) {
                errors.add("cell patch requires a policy and nonblank text of at most 20000 characters: "
                        + address);
            } else if (cell.getOverwritePolicy() == TableOverwritePolicy.BLANKS_ONLY
                    && !row.get(cell.getColumnIndex()).isEmpty()) {
                errors.add("BLANKS_ONLY cannot replace a nonempty cell: " + address);
            }
        }
        return errors;
    }

    private boolean containsControl(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < 0x20 && character != '\t' && character != '\n' && character != '\r') return true;
        }
        return false;
    }

    private SourceParagraphSnapshot findParagraph(DocumentStructureSnapshot snapshot, String id) {
        for (SourceParagraphSnapshot paragraph : snapshot.getParagraphs()) {
            if (paragraph.getParagraphId().equals(id)) return paragraph;
        }
        return null;
    }

    private SourceTableSnapshot findTable(DocumentStructureSnapshot snapshot, String id) {
        for (SourceTableSnapshot table : snapshot.getTables()) {
            if (table.getTableId().equals(id)) return table;
        }
        return null;
    }
}
