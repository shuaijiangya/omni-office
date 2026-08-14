package cn.bugstack.office.docx.example;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.api.VisioDiagramRenderer;
import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramNodeType;
import cn.bugstack.office.diagram.model.DiagramType;
import cn.bugstack.office.diagram.visio.VsdxDiagramRenderer;
import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 在 Word 中嵌入可编辑 Visio 图的完整示例。
 *
 * <p>运行后会在 {@code target/editable-visio-example} 中生成三份产物：可编辑
 * {@code .vsdx}、Word 显示用 {@code .png} 和嵌入 VSDX OLE 对象的 {@code .docx}。
 * 在支持 Visio OLE 的 Office 客户端中双击文档内图形即可编辑嵌入的图元。</p>
 *
 * @author luojiang
 */
public final class EditableVisioWordExample {

    /** 示例输出目录。 */
    private static final Path OUTPUT_DIRECTORY = Path.of("target", "editable-visio-example");

    /** Word 中流程图的最大显示宽度，单位为 point。 */
    private static final double WORD_FLOW_WIDTH = 300D;
    /** Word 中流程图的最大显示高度，单位为 point。 */
    private static final double WORD_FLOW_HEIGHT = 488D;
    /** Word 中用例图的最大显示宽度，单位为 point。 */
    private static final double WORD_USE_CASE_WIDTH = 420D;
    /** Word 中用例图的最大显示高度，单位为 point。 */
    private static final double WORD_USE_CASE_HEIGHT = 340D;
    /** Word 中 ER 图的最大显示宽度，单位为 point。 */
    private static final double WORD_ER_WIDTH = 390D;
    /** Word 中 ER 图的最大显示高度，单位为 point。 */
    private static final double WORD_ER_HEIGHT = 470D;
    /** Word 中 UML 类图的最大显示宽度，单位为 point。 */
    private static final double WORD_CLASS_WIDTH = 420D;
    /** Word 中 UML 类图的最大显示高度，单位为 point。 */
    private static final double WORD_CLASS_HEIGHT = 330D;
    /** Word 中 CSCI 部件逻辑图的最大显示宽度，单位为 point。 */
    private static final double WORD_CSCI_LOGICAL_WIDTH = 420D;
    /** Word 中 CSCI 部件逻辑图的最大显示高度，单位为 point。 */
    private static final double WORD_CSCI_LOGICAL_HEIGHT = 300D;
    /** Word 中 CSCI 部件设计图的最大显示宽度，单位为 point。 */
    private static final double WORD_CSCI_DESIGN_WIDTH = 420D;
    /** Word 中 CSCI 部件设计图的最大显示高度，单位为 point。 */
    private static final double WORD_CSCI_DESIGN_HEIGHT = 360D;
    /** Word 中概念数据模型 ER 图的最大显示宽度，单位为 point。 */
    private static final double WORD_CONCEPTUAL_ER_WIDTH = 420D;
    /** Word 中概念数据模型 ER 图的最大显示高度，单位为 point。 */
    private static final double WORD_CONCEPTUAL_ER_HEIGHT = 340D;
    /** Word 中系统 E-R 图的最大显示宽度，单位为 point。 */
    private static final double WORD_SYSTEM_ER_WIDTH = 420D;
    /** Word 中系统 E-R 图的最大显示高度，单位为 point。 */
    private static final double WORD_SYSTEM_ER_HEIGHT = 420D;
    /** Word 中总体功能逻辑图的最大显示宽度，单位为 point。 */
    private static final double WORD_FUNCTION_LOGIC_WIDTH = 420D;
    /** Word 中总体功能逻辑图的最大显示高度，单位为 point。 */
    private static final double WORD_FUNCTION_LOGIC_HEIGHT = 300D;

    /**
     * 私有构造方法，避免实例化示例类。
     */
    private EditableVisioWordExample() {
    }

    /**
     * 生成可编辑 VSDX 及包含该 OLE 对象的 Word 文档。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 当 Office 文件无法生成时抛出
     */
    public static void main(String[] args) throws Exception {
        AsposeWordsLicenseLoader.applyConfiguredLicense();
        Files.createDirectories(OUTPUT_DIRECTORY);

        VisioDiagramRenderer visioRenderer = new VsdxDiagramRenderer();
        VisioDiagramArtifact flowArtifact = visioRenderer.render(createRiskDisposalFlow(),
                OUTPUT_DIRECTORY.resolve("risk-disposal-flow.vsdx"));
        VisioDiagramArtifact useCaseArtifact = visioRenderer.render(createRiskUseCase(),
                OUTPUT_DIRECTORY.resolve("risk-management-use-case.vsdx"));
        VisioDiagramArtifact erArtifact = visioRenderer.render(createRiskErDiagram(),
                OUTPUT_DIRECTORY.resolve("risk-management-er.vsdx"));
        VisioDiagramArtifact classArtifact = visioRenderer.render(createRiskClassDiagram(),
                OUTPUT_DIRECTORY.resolve("risk-management-class.vsdx"));
        VisioDiagramArtifact csciLogicalArtifact = visioRenderer.render(createCsciLogicalDiagram(),
                OUTPUT_DIRECTORY.resolve("csci-component-logical.vsdx"));
        VisioDiagramArtifact csciDesignArtifact = visioRenderer.render(createCsciDesignDiagram(),
                OUTPUT_DIRECTORY.resolve("csci-component-design.vsdx"));
        VisioDiagramArtifact conceptualErArtifact = visioRenderer.render(createConceptualDataModel(),
                OUTPUT_DIRECTORY.resolve("conceptual-data-model-er.vsdx"));
        VisioDiagramArtifact systemErArtifact = visioRenderer.render(createRiskSystemErDiagram(),
                OUTPUT_DIRECTORY.resolve("risk-management-system-er.vsdx"));
        VisioDiagramArtifact functionLogicArtifact = visioRenderer.render(createOverallFunctionLogicDiagram(),
                OUTPUT_DIRECTORY.resolve("risk-management-overall-function-logic.vsdx"));
        Path documentPath = OUTPUT_DIRECTORY.resolve("editable-visio-word-example.docx");

        DocxDocument.create()
                .useDefaultStyles()
                .enableHeadingNumbering()
                .metadata("可编辑 Visio 图示例", "luojiang", "omni-office")
                .section()
                .heading1("风险处置流程图")
                .paragraph()
                .text("下图由可编辑 VSDX 作为 OLE 对象嵌入，PNG 仅用于 Word 页面预览。")
                .end()
                .editableVisio("risk-disposal-flow", flowArtifact.getVsdxPath().toString(),
                        flowArtifact.getPreviewPngPath().toString(), WORD_FLOW_WIDTH, WORD_FLOW_HEIGHT,
                        "风险处置流程图")
                .heading1("风险管理用例图")
                .editableVisio("risk-management-use-case", useCaseArtifact.getVsdxPath().toString(),
                        useCaseArtifact.getPreviewPngPath().toString(), WORD_USE_CASE_WIDTH, WORD_USE_CASE_HEIGHT,
                        "风险管理用例图")
                .heading1("风险管理 ER 图")
                .editableVisio("risk-management-er", erArtifact.getVsdxPath().toString(),
                        erArtifact.getPreviewPngPath().toString(), WORD_ER_WIDTH, WORD_ER_HEIGHT,
                        "风险管理 ER 图")
                .heading1("风险管理类图")
                .editableVisio("risk-management-class", classArtifact.getVsdxPath().toString(),
                        classArtifact.getPreviewPngPath().toString(), WORD_CLASS_WIDTH, WORD_CLASS_HEIGHT,
                        "风险管理类图")
                .heading1("CSCI 部件逻辑图")
                .editableVisio("csci-component-logical", csciLogicalArtifact.getVsdxPath().toString(),
                        csciLogicalArtifact.getPreviewPngPath().toString(), WORD_CSCI_LOGICAL_WIDTH,
                        WORD_CSCI_LOGICAL_HEIGHT, "CSCI 部件逻辑图")
                .heading1("CSCI 部件设计图")
                .editableVisio("csci-component-design", csciDesignArtifact.getVsdxPath().toString(),
                        csciDesignArtifact.getPreviewPngPath().toString(), WORD_CSCI_DESIGN_WIDTH,
                        WORD_CSCI_DESIGN_HEIGHT, "CSCI 部件设计图")
                .heading1("概念数据模型 ER 图")
                .editableVisio("conceptual-data-model-er", conceptualErArtifact.getVsdxPath().toString(),
                        conceptualErArtifact.getPreviewPngPath().toString(), WORD_CONCEPTUAL_ER_WIDTH,
                        WORD_CONCEPTUAL_ER_HEIGHT, "概念数据模型 ER 图")
                .heading1("风险管理系统 E-R 图")
                .editableVisio("risk-management-system-er", systemErArtifact.getVsdxPath().toString(),
                        systemErArtifact.getPreviewPngPath().toString(), WORD_SYSTEM_ER_WIDTH, WORD_SYSTEM_ER_HEIGHT,
                        "风险管理系统 E-R 图")
                .heading1("总体功能逻辑图")
                .editableVisio("risk-management-overall-function-logic",
                        functionLogicArtifact.getVsdxPath().toString(),
                        functionLogicArtifact.getPreviewPngPath().toString(), WORD_FUNCTION_LOGIC_WIDTH,
                        WORD_FUNCTION_LOGIC_HEIGHT, "风险管理系统总体功能逻辑图")
                .end()
                .save(documentPath);

        System.out.println("Generated flow VSDX: " + flowArtifact.getVsdxPath().toAbsolutePath());
        System.out.println("Generated use case VSDX: " + useCaseArtifact.getVsdxPath().toAbsolutePath());
        System.out.println("Generated ER VSDX: " + erArtifact.getVsdxPath().toAbsolutePath());
        System.out.println("Generated class VSDX: " + classArtifact.getVsdxPath().toAbsolutePath());
        System.out.println("Generated CSCI logical VSDX: " + csciLogicalArtifact.getVsdxPath().toAbsolutePath());
        System.out.println("Generated CSCI design VSDX: " + csciDesignArtifact.getVsdxPath().toAbsolutePath());
        System.out.println("Generated conceptual ER VSDX: " + conceptualErArtifact.getVsdxPath().toAbsolutePath());
        System.out.println("Generated system ER VSDX: " + systemErArtifact.getVsdxPath().toAbsolutePath());
        System.out.println("Generated overall function logic VSDX: "
                + functionLogicArtifact.getVsdxPath().toAbsolutePath());
        System.out.println("Generated DOCX: " + documentPath.toAbsolutePath());
    }

    /**
     * 创建风险处置流程图定义。
     *
     * @return 风险处置流程图定义
     */
    private static DiagramDefinition createRiskDisposalFlow() {
        return DiagramDefinition.builder(DiagramType.FLOW, "风险处置流程图")
                .node(new DiagramNode("start", "开始", DiagramNodeType.START))
                .node(new DiagramNode("collect", "收集并登记风险信息", DiagramNodeType.PROCESS))
                .node(new DiagramNode("complete", "风险信息是否完整", DiagramNodeType.DECISION))
                .node(new DiagramNode("supplement", "补充风险描述与证据", DiagramNodeType.PROCESS))
                .node(new DiagramNode("critical", "是否为重大风险", DiagramNodeType.DECISION))
                .node(new DiagramNode("routine", "纳入常规处置队列", DiagramNodeType.PROCESS))
                .node(new DiagramNode("parallel-split", "并行评审", DiagramNodeType.PARALLEL_SPLIT))
                .node(new DiagramNode("business-review", "业务影响评审", DiagramNodeType.PROCESS))
                .node(new DiagramNode("security-review", "安全合规评审", DiagramNodeType.PROCESS))
                .node(new DiagramNode("technical-review", "技术可行性评审", DiagramNodeType.PROCESS))
                .node(new DiagramNode("parallel-join", "评审结果汇聚", DiagramNodeType.PARALLEL_JOIN))
                .node(new DiagramNode("dispose", "制定处置计划并分配责任人", DiagramNodeType.PROCESS))
                .node(new DiagramNode("verify", "回归验证并归档", DiagramNodeType.PROCESS))
                .node(new DiagramNode("end", "结束", DiagramNodeType.END))
                .edge(new DiagramEdge("start", "collect"))
                .edge(new DiagramEdge("collect", "complete"))
                .edge(new DiagramEdge("complete", "supplement", "否"))
                .edge(new DiagramEdge("complete", "critical", "是"))
                .edge(new DiagramEdge("supplement", "critical"))
                .edge(new DiagramEdge("critical", "routine", "否"))
                .edge(new DiagramEdge("critical", "parallel-split", "是"))
                .edge(new DiagramEdge("routine", "parallel-split"))
                .edge(new DiagramEdge("parallel-split", "business-review"))
                .edge(new DiagramEdge("parallel-split", "security-review"))
                .edge(new DiagramEdge("parallel-split", "technical-review"))
                .edge(new DiagramEdge("business-review", "parallel-join"))
                .edge(new DiagramEdge("security-review", "parallel-join"))
                .edge(new DiagramEdge("technical-review", "parallel-join"))
                .edge(new DiagramEdge("parallel-join", "dispose"))
                .edge(new DiagramEdge("dispose", "verify"))
                .edge(new DiagramEdge("verify", "end"))
                .build();
    }

    /**
     * 创建风险管理用例图定义。
     *
     * @return 风险管理用例图定义
     */
    private static DiagramDefinition createRiskUseCase() {
        return DiagramDefinition.builder(DiagramType.USE_CASE, "风险管理模块功能用例图")
                .node(new DiagramNode("risk-manager", "风险管理员", DiagramNodeType.ACTOR))
                .node(new DiagramNode("register-risk", "登记风险", DiagramNodeType.USE_CASE))
                .node(new DiagramNode("assess-risk", "评估风险", DiagramNodeType.USE_CASE))
                .node(new DiagramNode("assign-owner", "分配处置责任人", DiagramNodeType.USE_CASE))
                .node(new DiagramNode("create-plan", "制定处置计划", DiagramNodeType.USE_CASE))
                .node(new DiagramNode("track-disposal", "跟踪处置进度", DiagramNodeType.USE_CASE))
                .node(new DiagramNode("verify-result", "验证处置结果", DiagramNodeType.USE_CASE))
                .node(new DiagramNode("export-report", "导出风险报告", DiagramNodeType.USE_CASE))
                .edge(new DiagramEdge("risk-manager", "register-risk"))
                .edge(new DiagramEdge("risk-manager", "assess-risk"))
                .edge(new DiagramEdge("risk-manager", "assign-owner"))
                .edge(new DiagramEdge("risk-manager", "create-plan"))
                .edge(new DiagramEdge("risk-manager", "track-disposal"))
                .edge(new DiagramEdge("risk-manager", "verify-result"))
                .edge(new DiagramEdge("risk-manager", "export-report"))
                .build();
    }

    /**
     * 创建风险管理 ER 图定义。
     *
     * @return 风险管理 ER 图定义
     */
    private static DiagramDefinition createRiskErDiagram() {
        return DiagramDefinition.builder(DiagramType.ER, "风险管理 ER 图")
                .node(DiagramNode.entity("project", "项目", "id: Long", "name: String", "owner: String"))
                .node(DiagramNode.entity("risk", "风险项", "id: Long", "projectId: Long", "level: String"))
                .node(DiagramNode.entity("assessment", "风险评估", "id: Long", "riskId: Long", "score: Integer"))
                .node(DiagramNode.entity("measure", "控制措施", "id: Long", "riskId: Long", "ownerId: Long"))
                .node(DiagramNode.entity("disposal", "处置计划", "id: Long", "riskId: Long", "status: String"))
                .node(DiagramNode.entity("verification", "验证记录", "id: Long", "planId: Long", "result: String"))
                .node(DiagramNode.entity("user", "用户", "id: Long", "name: String", "roleId: Long"))
                .node(DiagramNode.entity("role", "角色", "id: Long", "name: String", "scope: String"))
                .node(DiagramNode.entity("audit-log", "审计日志", "id: Long", "riskId: Long", "action: String"))
                .node(DiagramNode.entity("attachment", "风险附件", "id: Long", "riskId: Long", "fileName: String"))
                .edge(new DiagramEdge("project", "risk", "1:N"))
                .edge(new DiagramEdge("risk", "assessment", "1:N"))
                .edge(new DiagramEdge("risk", "measure", "1:N"))
                .edge(new DiagramEdge("risk", "disposal", "1:N"))
                .edge(new DiagramEdge("disposal", "verification", "1:N"))
                .edge(new DiagramEdge("role", "user", "1:N"))
                .edge(new DiagramEdge("audit-log", "attachment", "1:N"))
                .build();
    }

    /**
     * 创建风险管理 UML 类图定义。
     *
     * @return 风险管理 UML 类图定义
     */
    private static DiagramDefinition createRiskClassDiagram() {
        return DiagramDefinition.builder(DiagramType.CLASS, "风险管理类图")
                .node(DiagramNode.classNode("controller", "RiskController",
                        List.of(), List.of("+ register(): Long", "+ assess(): RiskView")))
                .node(DiagramNode.classNode("service", "RiskApplicationService",
                        List.of("- repository: RiskRepository", "- policy: RiskPolicy"),
                        List.of("+ register(): Long", "+ evaluate(): RiskLevel")))
                .node(DiagramNode.classNode("repository", "RiskRepository",
                        List.of(), List.of("+ save(risk: Risk): Long", "+ findById(id: Long): Risk")))
                .node(DiagramNode.classNode("risk", "Risk",
                        List.of("- id: Long", "- level: RiskLevel"),
                        List.of("+ assess(): RiskLevel", "+ createPlan(): Plan")))
                .node(DiagramNode.classNode("plan", "RiskDisposalPlan",
                        List.of("- ownerId: Long", "- status: PlanStatus"),
                        List.of("+ assign(ownerId: Long): void", "+ verify(): void")))
                .edge(new DiagramEdge("controller", "service", "调用"))
                .edge(new DiagramEdge("service", "repository", "依赖"))
                .edge(new DiagramEdge("service", "risk", "管理"))
                .edge(new DiagramEdge("risk", "plan", "组合"))
                .build();
    }

    /**
     * 创建 CSCI 部件逻辑图定义。
     *
     * <p>该图描述 CSCI 内控制、业务、数据与外部接口部件之间的逻辑协作关系，
     * 不绑定具体类、数据库表或部署节点。</p>
     *
     * @return CSCI 部件逻辑图定义
     */
    private static DiagramDefinition createCsciLogicalDiagram() {
        return DiagramDefinition.builder(DiagramType.COMPONENT, "风险管理 CSCI 部件逻辑图")
                .node(DiagramNode.component("portal", "风险管理门户部件", "登记与查询", "处置跟踪"))
                .node(DiagramNode.component("application", "风险管理应用部件", "风险识别", "评估与处置编排"))
                .node(DiagramNode.component("rule", "风险规则部件", "等级判定", "阈值与策略"))
                .node(DiagramNode.component("data", "风险数据部件", "风险数据管理", "处置数据管理"))
                .node(DiagramNode.component("integration", "外部集成部件", "消息通知", "审计与报告"))
                .edge(new DiagramEdge("portal", "application", "业务请求"))
                .edge(new DiagramEdge("application", "rule", "规则调用"))
                .edge(new DiagramEdge("application", "data", "数据读写"))
                .edge(new DiagramEdge("application", "integration", "事件通知"))
                .build();
    }

    /**
     * 创建 CSCI 部件设计图定义。
     *
     * <p>该图在逻辑部件基础上补充接口适配、事务服务、仓储和审计等实现部件，
     * 用于描述 CSCI 内部可追踪的设计分解。</p>
     *
     * @return CSCI 部件设计图定义
     */
    private static DiagramDefinition createCsciDesignDiagram() {
        return DiagramDefinition.builder(DiagramType.COMPONENT, "风险管理 CSCI 部件设计图")
                .node(DiagramNode.component("api", "风险服务接口部件", "IRiskCommandService", "IRiskQueryService"))
                .node(DiagramNode.component("command", "风险命令应用部件", "登记命令处理", "处置命令处理"))
                .node(DiagramNode.component("query", "风险查询应用部件", "风险查询编排", "报表查询编排"))
                .node(DiagramNode.component("domain", "风险领域部件", "Risk 聚合", "RiskDisposalPlan 聚合"))
                .node(DiagramNode.component("repository", "风险仓储适配部件", "IRiskRepository", "IPlanRepository"))
                .node(DiagramNode.component("audit", "审计事件部件", "领域事件发布", "审计记录订阅"))
                .edge(new DiagramEdge("api", "command", "命令接口"))
                .edge(new DiagramEdge("api", "query", "查询接口"))
                .edge(new DiagramEdge("command", "domain", "领域调用"))
                .edge(new DiagramEdge("query", "repository", "查询访问"))
                .edge(new DiagramEdge("domain", "repository", "仓储接口"))
                .edge(new DiagramEdge("domain", "audit", "领域事件"))
                .build();
    }

    /**
     * 创建概念数据模型 ER 图定义。
     *
     * <p>概念模型仅展示业务实体、概念属性和基数关系，不包含主键、外键或具体数据库类型。</p>
     *
     * @return 概念数据模型 ER 图定义
     */
    private static DiagramDefinition createConceptualDataModel() {
        return DiagramDefinition.builder(DiagramType.ER, "风险管理概念数据模型 ER 图")
                .node(DiagramNode.entity("risk", "风险", "风险名称", "风险等级", "当前状态"))
                .node(DiagramNode.entity("source", "风险来源", "来源类别", "来源描述"))
                .node(DiagramNode.entity("assessment", "风险评估", "评估结论", "评估时间"))
                .node(DiagramNode.entity("plan", "处置计划", "处置策略", "计划状态"))
                .node(DiagramNode.entity("measure", "控制措施", "措施内容", "执行状态"))
                .node(DiagramNode.entity("owner", "责任主体", "主体名称", "责任角色"))
                .node(DiagramNode.entity("verification", "验证记录", "验证结论", "验证时间"))
                .edge(new DiagramEdge("source", "risk", "1:N"))
                .edge(new DiagramEdge("risk", "assessment", "1:N"))
                .edge(new DiagramEdge("risk", "plan", "1:N"))
                .edge(new DiagramEdge("plan", "measure", "1:N"))
                .edge(new DiagramEdge("owner", "plan", "1:N"))
                .edge(new DiagramEdge("plan", "verification", "1:N"))
                .build();
    }

    /**
     * 创建 Chen 表示法的风险管理系统 E-R 图定义。
     *
     * <p>该图用于系统分析阶段，分别以实体、关系和属性描述业务概念，展示风险、项目、
     * 责任主体与处置计划之间的业务语义；不引入数据库字段和物理表结构。</p>
     *
     * @return 风险管理系统 E-R 图定义
     */
    private static DiagramDefinition createRiskSystemErDiagram() {
        return DiagramDefinition.builder(DiagramType.SYSTEM_ER, "风险管理系统 E-R 图")
                .node(DiagramNode.systemEntity("risk", "风险"))
                .node(DiagramNode.systemEntity("project", "项目"))
                .node(DiagramNode.systemEntity("owner", "责任主体"))
                .node(DiagramNode.systemEntity("plan", "处置计划"))
                .node(DiagramNode.relationship("belong-to", "归属"))
                .node(DiagramNode.relationship("owned-by", "负责"))
                .node(DiagramNode.relationship("handled-by", "处置"))
                .node(DiagramNode.attribute("risk-name", "风险名称"))
                .node(DiagramNode.attribute("risk-level", "风险等级"))
                .node(DiagramNode.attribute("risk-status", "当前状态"))
                .node(DiagramNode.attribute("project-name", "项目名称"))
                .node(DiagramNode.attribute("owner-name", "主体名称"))
                .node(DiagramNode.attribute("plan-status", "计划状态"))
                .edge(new DiagramEdge("risk", "belong-to", "N"))
                .edge(new DiagramEdge("belong-to", "project", "1"))
                .edge(new DiagramEdge("risk", "owned-by", "N"))
                .edge(new DiagramEdge("owned-by", "owner", "1"))
                .edge(new DiagramEdge("risk", "handled-by", "1"))
                .edge(new DiagramEdge("handled-by", "plan", "1"))
                .edge(new DiagramEdge("risk", "risk-name"))
                .edge(new DiagramEdge("risk", "risk-level"))
                .edge(new DiagramEdge("risk", "risk-status"))
                .edge(new DiagramEdge("project", "project-name"))
                .edge(new DiagramEdge("owner", "owner-name"))
                .edge(new DiagramEdge("plan", "plan-status"))
                .build();
    }

    /**
     * 创建风险管理系统总体功能逻辑图定义。
     *
     * <p>该图从系统总体能力出发，将风险管理分解为风险登记、风险评估、处置管理和
     * 统计分析四个一级功能模块，并继续细化为可实现、可追踪的末级功能项。</p>
     *
     * @return 风险管理系统总体功能逻辑图定义
     */
    private static DiagramDefinition createOverallFunctionLogicDiagram() {
        return DiagramDefinition.builder(DiagramType.OVERALL_FUNCTION_LOGIC, "风险管理系统总体功能逻辑图")
                .node(DiagramNode.functionSystem("system", "风险管理系统"))
                .node(DiagramNode.functionModule("registration", "风险登记"))
                .node(DiagramNode.functionModule("assessment", "风险评估"))
                .node(DiagramNode.functionModule("disposal", "处置管理"))
                .node(DiagramNode.functionModule("statistics", "统计分析"))
                .node(DiagramNode.functionItem("source-register", "风险来源登记"))
                .node(DiagramNode.functionItem("risk-maintain", "风险信息维护"))
                .node(DiagramNode.functionItem("attachment-manage", "附件资料管理"))
                .node(DiagramNode.functionItem("level-assess", "风险等级评估"))
                .node(DiagramNode.functionItem("impact-analyse", "影响范围分析"))
                .node(DiagramNode.functionItem("result-review", "评估结果复核"))
                .node(DiagramNode.functionItem("plan-create", "处置计划制定"))
                .node(DiagramNode.functionItem("owner-assign", "责任人员分配"))
                .node(DiagramNode.functionItem("disposal-track", "处置过程跟踪"))
                .node(DiagramNode.functionItem("trend-statistics", "风险趋势统计"))
                .node(DiagramNode.functionItem("level-distribution", "等级分布分析"))
                .node(DiagramNode.functionItem("report-export", "统计报表导出"))
                .node(DiagramNode.functionItem("report-export2", "统计报表导出2"))
                .node(DiagramNode.functionItem("report-export3", "统计报表导出3"))
                .edge(new DiagramEdge("system", "registration"))
                .edge(new DiagramEdge("system", "assessment"))
                .edge(new DiagramEdge("system", "disposal"))
                .edge(new DiagramEdge("system", "statistics"))
                .edge(new DiagramEdge("registration", "source-register"))
                .edge(new DiagramEdge("registration", "risk-maintain"))
                .edge(new DiagramEdge("registration", "attachment-manage"))
                .edge(new DiagramEdge("assessment", "level-assess"))
                .edge(new DiagramEdge("assessment", "impact-analyse"))
                .edge(new DiagramEdge("assessment", "result-review"))
                .edge(new DiagramEdge("disposal", "plan-create"))
                .edge(new DiagramEdge("disposal", "owner-assign"))
                .edge(new DiagramEdge("disposal", "disposal-track"))
                .edge(new DiagramEdge("statistics", "trend-statistics"))
                .edge(new DiagramEdge("statistics", "level-distribution"))
                .edge(new DiagramEdge("statistics", "report-export"))
                .edge(new DiagramEdge("statistics", "report-export2"))
                .edge(new DiagramEdge("statistics", "report-export3"))
                .build();
    }
}
