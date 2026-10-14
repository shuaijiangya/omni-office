package cn.bugstack.application.completion;

import cn.bugstack.application.ai.StructuredAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 上传 DOCX 的段落与表格识别、自然语言规划及确定性写回的应用门面。
 */
public final class SourceDocxCompletionApplication {

    private final SourceDocxStructureExtractor extractor;
    private final StructuredAiTableCompletionPlanner planner;
    private final SourceDocxTablePatchExecutor executor;
    private final StructuredAiClient aiClient;
    private final SourceDocxDocumentPatchExecutor documentExecutor;
    private final DocumentCompletionPlanJsonCodec planCodec;

    /** 使用指定结构化 AI 客户端创建应用门面。 */
    public SourceDocxCompletionApplication(StructuredAiClient aiClient) {
        this(new SourceDocxStructureExtractor(), new StructuredAiTableCompletionPlanner(aiClient),
                new SourceDocxTablePatchExecutor(), aiClient);
    }

    /** 使用指定组件创建应用门面。 */
    public SourceDocxCompletionApplication(SourceDocxStructureExtractor extractor,
                                           StructuredAiTableCompletionPlanner planner,
                                           SourceDocxTablePatchExecutor executor) {
        this(extractor, planner, executor, null);
    }

    private SourceDocxCompletionApplication(SourceDocxStructureExtractor extractor,
                                            StructuredAiTableCompletionPlanner planner,
                                            SourceDocxTablePatchExecutor executor,
                                            StructuredAiClient aiClient) {
        if (extractor == null || planner == null || executor == null) {
            throw new IllegalArgumentException("completion extractor, planner and executor are required");
        }
        this.extractor = extractor;
        this.planner = planner;
        this.executor = executor;
        this.aiClient = aiClient;
        this.documentExecutor = new SourceDocxDocumentPatchExecutor();
        this.planCodec = new DocumentCompletionPlanJsonCodec();
    }

    /** @return 上传 DOCX 的安全结构快照 */
    public DocumentStructureSnapshot analyze(Path source) {
        return extractor.extract(source);
    }

    /** 只返回指定 Section 的可写入结构；ID、物理位置及源 SHA-256 保持全局值。 */
    public DocumentStructureSnapshot analyzeSection(Path source, int sectionIndex) {
        return sectionSnapshot(extractor.extract(source), sectionIndex);
    }

    /**
     * 根据用户描述和已取得的业务事实规划补全操作，但不修改文件。
     *
     * @param source 上传 DOCX
     * @param instruction 用户描述
     * @param businessContext Function Calling 或普通业务代码提供的事实上下文
     * @return 预览计划或待补充问题
     */
    public TableCompletionPlanningResult plan(Path source, String instruction, JsonNode businessContext) {
        return planner.plan(instruction, extractor.extract(source), businessContext);
    }

    /**
     * 允许模型先调用显式注册的业务函数获取事实数据，再生成补丁计划。
     *
     * @param source 上传 DOCX
     * @param instruction 用户描述
     * @param baseContext 已有业务上下文
     * @param functions 允许调用的函数白名单
     * @return 可执行计划或待补充问题
     */
    public TableCompletionPlanningResult planWithFunctions(Path source, String instruction,
                                                           JsonNode baseContext,
                                                           DocumentCompletionFunctionRegistry functions) {
        if (aiClient == null) {
            throw new IllegalStateException("planWithFunctions requires the StructuredAiClient constructor");
        }
        return new StructuredAiCompletionFunctionOrchestrator(aiClient, functions)
                .plan(instruction, extractor.extract(source), baseContext);
    }

    /**
     * 应用已经过本地校验的 READY 计划。
     *
     * @param source 源 DOCX
     * @param output 预览或正式输出 DOCX
     * @param planningResult 规划结果
     * @return 更新统计
     */
    public TablePatchResult apply(Path source, Path output,
                                  TableCompletionPlanningResult planningResult) {
        if (planningResult == null || planningResult.getStatus() != TableCompletionStatus.READY
                || planningResult.getBinding() == null || planningResult.getPatchPlan() == null) {
            throw new IllegalArgumentException("only a READY completion plan can be applied");
        }
        return executor.apply(source, output, planningResult.getBinding(), planningResult.getPatchPlan());
    }

    /** 规划一份文档内的多个正文段落与表格，不修改源文件。 */
    public DocumentCompletionPlan planDocument(Path source, String instruction, JsonNode businessFacts) {
        if (aiClient == null) {
            throw new IllegalStateException("planDocument requires the StructuredAiClient constructor");
        }
        return new StructuredAiDocumentCompletionPlanner(aiClient)
                .plan(instruction, extractor.extract(source), businessFacts);
    }

    /** 在指定 Section 内规划补全，适合大型文档分批审核与写回。 */
    public DocumentCompletionPlan planDocumentSection(Path source, int sectionIndex,
                                                      String instruction, JsonNode businessFacts) {
        if (aiClient == null) {
            throw new IllegalStateException("planDocumentSection requires the StructuredAiClient constructor");
        }
        return new StructuredAiDocumentCompletionPlanner(aiClient)
                .plan(instruction, analyzeSection(source, sectionIndex), businessFacts);
    }

    /** 先执行白名单业务函数，再规划多位置文档补全。 */
    public DocumentCompletionPlan planDocumentWithFunctions(Path source, String instruction,
                                                            JsonNode businessFacts,
                                                            DocumentCompletionFunctionRegistry functions) {
        if (aiClient == null) {
            throw new IllegalStateException("planDocumentWithFunctions requires the StructuredAiClient constructor");
        }
        return new StructuredAiCompletionFunctionOrchestrator(aiClient, functions)
                .planDocument(instruction, extractor.extract(source), businessFacts);
    }

    /** 对经人工审核的文档级计划执行本地校验，并写入新的 DOCX。 */
    public DocumentCompletionResult applyDocument(Path source, Path output, DocumentCompletionPlan plan) {
        return documentExecutor.apply(source, output, plan);
    }

    /** 将计划转换为含源文件摘要的审核 JSON，供跨请求展示和确认。 */
    public ObjectNode reviewablePlan(DocumentCompletionPlan plan) {
        return planCodec.write(plan);
    }

    /** 从审核 JSON 重建计划，复核当前源文档后写入输出 DOCX。 */
    public DocumentCompletionResult applyReviewedPlan(Path source, Path output, JsonNode reviewedPlan) {
        DocumentStructureSnapshot snapshot = extractor.extract(source);
        DocumentCompletionPlan plan = planCodec.readReviewed(reviewedPlan, snapshot);
        return documentExecutor.apply(source, output, plan);
    }

    private DocumentStructureSnapshot sectionSnapshot(DocumentStructureSnapshot full, int sectionIndex) {
        if (sectionIndex < 0) throw new IllegalArgumentException("sectionIndex must be nonnegative");
        List<SourceTableSnapshot> tables = full.getTables().stream()
                .filter(table -> table.getSectionIndex() == sectionIndex).collect(Collectors.toList());
        List<SourceParagraphSnapshot> paragraphs = full.getParagraphs().stream()
                .filter(paragraph -> paragraph.getSectionIndex() == sectionIndex).collect(Collectors.toList());
        if (tables.isEmpty() && paragraphs.isEmpty()) {
            throw new IllegalArgumentException("sectionIndex has no editable body content");
        }
        return new DocumentStructureSnapshot(full.getFileName(), full.getSha256(), tables, paragraphs);
    }
}
