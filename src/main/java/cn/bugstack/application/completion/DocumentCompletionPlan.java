package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 对一份源 DOCX 的多位置补全计划。 */
public final class DocumentCompletionPlan {

    private final String sourceSha256;
    private final List<ParagraphPatchPlan> paragraphs;
    private final List<TableCompletionPlanningResult> tables;
    private final List<CellPatchPlan> cells;
    private final List<RunPatchPlan> runs;
    private final double confidence;
    private final String reason;
    private final List<String> questions;

    /** 创建文档补全计划。 */
    public DocumentCompletionPlan(String sourceSha256, List<ParagraphPatchPlan> paragraphs,
                                  List<TableCompletionPlanningResult> tables,
                                  double confidence, String reason, List<String> questions) {
        this(sourceSha256, paragraphs, tables, Collections.emptyList(), confidence, reason, questions);
    }

    /** 创建包含定点单元格补全的文档计划。 */
    public DocumentCompletionPlan(String sourceSha256, List<ParagraphPatchPlan> paragraphs,
                                  List<TableCompletionPlanningResult> tables, List<CellPatchPlan> cells,
                                  double confidence, String reason, List<String> questions) {
        this(sourceSha256, paragraphs, tables, cells, Collections.emptyList(),
                confidence, reason, questions);
    }

    /** 创建包含独立 Run 文本片段补全的文档计划。 */
    public DocumentCompletionPlan(String sourceSha256, List<ParagraphPatchPlan> paragraphs,
                                  List<TableCompletionPlanningResult> tables, List<CellPatchPlan> cells,
                                  List<RunPatchPlan> runs,
                                  double confidence, String reason, List<String> questions) {
        this.sourceSha256 = sourceSha256;
        this.paragraphs = Collections.unmodifiableList(new ArrayList<>(paragraphs));
        this.tables = Collections.unmodifiableList(new ArrayList<>(tables));
        this.cells = Collections.unmodifiableList(new ArrayList<>(cells));
        this.runs = Collections.unmodifiableList(new ArrayList<>(runs));
        this.confidence = confidence;
        this.reason = reason;
        this.questions = Collections.unmodifiableList(new ArrayList<>(questions));
    }

    /** @return 规划时源文件的 SHA-256 */
    public String getSourceSha256() { return sourceSha256; }

    /** @return 段落操作 */
    public List<ParagraphPatchPlan> getParagraphs() { return paragraphs; }

    /** @return 表格操作 */
    public List<TableCompletionPlanningResult> getTables() { return tables; }

    /** @return 固定格式表格的定点单元格操作 */
    public List<CellPatchPlan> getCells() { return cells; }

    /** @return 按独立 Run 定点替换的操作 */
    public List<RunPatchPlan> getRuns() { return runs; }

    /** @return 模型置信度 */
    public double getConfidence() { return confidence; }

    /** @return 规划原因 */
    public String getReason() { return reason; }

    /** @return 待补充问题 */
    public List<String> getQuestions() { return questions; }

    /** @return 当前计划是否包含可执行操作 */
    public boolean isReady() {
        return questions.isEmpty() && (!paragraphs.isEmpty() || !tables.isEmpty()
                || !cells.isEmpty() || !runs.isEmpty());
    }
}
