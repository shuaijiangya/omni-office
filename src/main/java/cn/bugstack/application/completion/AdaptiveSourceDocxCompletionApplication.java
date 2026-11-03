package cn.bugstack.application.completion;

import cn.bugstack.application.ai.StructuredAiClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无需在 Word 文档预埋槽位，依据用户描述与业务数据补充正文、表格及可编辑图表。
 *
 * <p>模型选择已有物理锚点并返回内容，校验器阻止无依据的数字和未经授权的正文覆盖；
 * 所有修改在源文件摘要复核后一次写入新的 DOCX。</p>
 */
public final class AdaptiveSourceDocxCompletionApplication {

    private static final Pattern NUMBER = Pattern.compile("(?<![0-9])(?:[0-9]+(?:\\.[0-9]+)?)(?![0-9])");
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "^(?:(?:待补充|待填写|待完善|待更新|TODO|TBD)[^。；\\n]{0,80}|[\\s_＿—-]+)$",
            Pattern.CASE_INSENSITIVE);
    private final SourceDocxCompletionApplication source;
    private final TargetedSourceDocxCompletionPlanner modulePlanner;
    private final StructuredAiChartCompletionPlanner chartPlanner;
    private final DocumentCompletionPlanValidator validator = new DocumentCompletionPlanValidator();

    /** @param client 正文、表格及图表均使用的真实结构化模型客户端 */
    public AdaptiveSourceDocxCompletionApplication(StructuredAiClient client) {
        if (client == null) throw new IllegalArgumentException("AI client is required");
        this.source = new SourceDocxCompletionApplication(client);
        this.modulePlanner = new TargetedSourceDocxCompletionPlanner(client);
        this.chartPlanner = new StructuredAiChartCompletionPlanner(client);
    }

    /**
     * 从整份文档自行选择可补位置，不要求文档内存在固定提示词或占位符。
     *
     * @param docx 已上传的 .doc 或 .docx 源文件
     * @param instruction 用户本次补充要求，例如“按业务数据补全能补充的内容”
     * @param businessFacts 用户提供或授权业务函数返回的 JSON 对象
     * @return 可审核的完整补全计划
     */
    public DocumentCompletionPlan plan(Path docx, String instruction, JsonNode businessFacts) {
        if (docx == null || instruction == null || instruction.trim().isEmpty()
                || businessFacts == null || !businessFacts.isObject()) {
            throw new IllegalArgumentException("DOCX, instruction and object business facts are required");
        }
        DocumentStructureSnapshot snapshot = source.analyze(docx);
        DocumentCompletionPlan body = modulePlanner.plan(docx, instruction, snapshot, businessFacts);
        DocumentCompletionPlan charts = chartPlanner.plan(instruction, snapshot, businessFacts);
        List<String> questions = new ArrayList<>(body.getQuestions());
        questions.addAll(charts.getQuestions());
        DocumentCompletionPlan combined = new DocumentCompletionPlan(snapshot.getSha256(),
                body.getParagraphs(), body.getTables(), body.getCells(), body.getRuns(), charts.getCharts(),
                Math.min(body.getConfidence(), charts.getConfidence()),
                body.getReason() + "; " + charts.getReason(), questions);
        if (!questions.isEmpty()) return combined;
        List<String> errors = validator.validate(snapshot, combined);
        errors.addAll(validateFactGrounding(snapshot, combined, businessFacts));
        errors.addAll(validateSupplementOnly(snapshot, combined, instruction));
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("adaptive document plan is unsafe: " + String.join("; ", errors));
        }
        return combined;
    }

    /** 复核源文件摘要并把已审核计划写入新 DOCX，不覆盖上传原件。 */
    public DocumentCompletionResult apply(Path sourceDocx, Path output, DocumentCompletionPlan plan) {
        if (sourceDocx == null || output == null
                || !output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx")
                || sourceDocx.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("output must be a new .docx path distinct from the Word source");
        }
        return source.applyDocument(sourceDocx, output, plan);
    }

    /** 仅在计划完整且通过全部安全校验时自动生成新 DOCX。 */
    public DocumentCompletionResult generate(Path sourceDocx, Path output, String instruction,
                                             JsonNode businessFacts) {
        DocumentCompletionPlan plan = plan(sourceDocx, instruction, businessFacts);
        if (!plan.isReady()) {
            throw new IllegalStateException("document requires user input: " + plan.getQuestions());
        }
        return apply(sourceDocx, output, plan);
    }

    /** 新增数值必须可以在源文档或业务事实中找到，图表还需逐点指向业务事实。 */
    private List<String> validateFactGrounding(DocumentStructureSnapshot snapshot,
                                               DocumentCompletionPlan plan, JsonNode facts) {
        Set<String> available = numbers(facts.toString());
        for (SourceParagraphSnapshot paragraph : snapshot.getParagraphs()) {
            available.addAll(numbers(paragraph.getText()));
        }
        for (SourceTableSnapshot table : snapshot.getTables()) {
            for (String header : table.getHeaders()) available.addAll(numbers(header));
            for (List<String> row : table.getRows()) {
                for (String value : row) available.addAll(numbers(value));
            }
        }
        List<String> errors = new ArrayList<>();
        for (ParagraphPatchPlan patch : plan.getParagraphs()) {
            for (String text : patch.getTexts()) checkNumbers(text, available, errors);
        }
        for (RunPatchPlan patch : plan.getRuns()) checkNumbers(patch.getText(), available, errors);
        for (CellPatchPlan patch : plan.getCells()) checkNumbers(patch.getText(), available, errors);
        for (TableCompletionPlanningResult table : plan.getTables()) {
            for (java.util.Map<String, String> row : table.getPatchPlan().getRows()) {
                for (String value : row.values()) checkNumbers(value, available, errors);
            }
        }
        return errors;
    }

    /** “补充”默认保留已有内容，仅允许替换显式待填写文本。 */
    private List<String> validateSupplementOnly(DocumentStructureSnapshot snapshot,
                                                DocumentCompletionPlan plan, String instruction) {
        List<String> errors = new ArrayList<>();
        if (instruction.matches(".*(?:覆盖|重写|替换|改写).*$")) return errors;
        for (ParagraphPatchPlan patch : plan.getParagraphs()) {
            if (patch.getOperation() != ParagraphPatchOperation.REPLACE_TEXT) continue;
            SourceParagraphSnapshot sourceParagraph = paragraph(snapshot, patch.getParagraphId());
            if (sourceParagraph != null && !sourceParagraph.getText().trim().isEmpty()
                    && !PLACEHOLDER.matcher(sourceParagraph.getText().trim()).matches()) {
                errors.add("supplement request cannot replace existing prose: " + patch.getParagraphId());
            }
        }
        for (RunPatchPlan patch : plan.getRuns()) {
            if (!patch.getExpectedText().trim().isEmpty()
                    && !PLACEHOLDER.matcher(patch.getExpectedText().trim()).matches()) {
                errors.add("supplement request cannot replace existing styled run: " + patch.getParagraphId());
            }
        }
        for (CellPatchPlan patch : plan.getCells()) {
            SourceTableSnapshot table = table(snapshot, patch.getTableId());
            if (table == null || patch.getRowIndex() > table.getRows().size()) continue;
            List<String> row = patch.getRowIndex() == 0 ? table.getHeaders()
                    : table.getRows().get(patch.getRowIndex() - 1);
            String old = row.get(patch.getColumnIndex()).trim();
            if (!old.isEmpty() && !PLACEHOLDER.matcher(old).matches()) {
                errors.add("supplement request cannot replace populated table cell: " + patch.getTableId());
            }
        }
        for (TableCompletionPlanningResult tablePlan : plan.getTables()) {
            if (tablePlan.getPatchPlan().getOverwritePolicy() == TableOverwritePolicy.OVERWRITE_ALL) {
                errors.add("supplement request cannot overwrite populated table rows: "
                        + tablePlan.getPatchPlan().getTableId());
            }
        }
        return errors;
    }

    /** 返回段落 ID 对应的可信源快照。 */
    private SourceParagraphSnapshot paragraph(DocumentStructureSnapshot snapshot, String id) {
        for (SourceParagraphSnapshot item : snapshot.getParagraphs()) {
            if (item.getParagraphId().equals(id)) return item;
        }
        return null;
    }

    /** 返回表格 ID 对应的可信源快照。 */
    private SourceTableSnapshot table(DocumentStructureSnapshot snapshot, String id) {
        for (SourceTableSnapshot item : snapshot.getTables()) {
            if (item.getTableId().equals(id)) return item;
        }
        return null;
    }

    /** 收集文本中全部阿拉伯数字。 */
    private Set<String> numbers(String text) {
        Set<String> result = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) result.add(new java.math.BigDecimal(matcher.group()).stripTrailingZeros().toPlainString());
        return result;
    }

    /** 拒绝模型引入业务事实与源文档均未提供的数字。 */
    private void checkNumbers(String text, Set<String> available, List<String> errors) {
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String normalized = new java.math.BigDecimal(matcher.group()).stripTrailingZeros().toPlainString();
            if (!available.contains(normalized)) errors.add("generated number lacks source fact: " + normalized);
        }
    }
}
