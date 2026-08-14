package cn.bugstack.office.diagram.example;

import cn.bugstack.office.diagram.api.DiagramRenderer;
import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramNodeType;
import cn.bugstack.office.diagram.model.DiagramType;
import cn.bugstack.office.diagram.render.SvgDiagramRenderer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * SVG 设计图生成示例。
 *
 * <p>该示例会在 {@code target/svg-diagram-example} 目录中分别生成用例图、流程图和
 * 数据库 ER 图。图生成模块不依赖 Aspose 或 Word，可单独用于设计文档附件、Web 预览或
 * 后续的文档图片插入。</p>
 *
 * @author luojiang
 */
public final class SvgDiagramExample {

    /**
     * SVG 示例文件的输出目录。
     */
    private static final Path OUTPUT_DIRECTORY = Path.of("target", "svg-diagram-example");

    /**
     * 私有构造方法，避免实例化工具示例类。
     */
    private SvgDiagramExample() {
    }

    /**
     * 生成三种 SVG 设计图。
     *
     * @param args 命令行参数，当前未使用
     * @throws IOException 当 SVG 文件无法写入时抛出
     */
    public static void main(String[] args) throws IOException {
        DiagramRenderer renderer = new SvgDiagramRenderer();
        renderer.render(createUseCaseDiagram(), OUTPUT_DIRECTORY.resolve("user-auth-use-case.svg"));
        renderer.render(createFlowDiagram(), OUTPUT_DIRECTORY.resolve("risk-disposal-flow.svg"));
        renderer.render(createErDiagram(), OUTPUT_DIRECTORY.resolve("risk-assessment-er.svg"));
    }

    /**
     * 创建用户认证用例图定义。
     *
     * @return 用户认证用例图定义
     */
    private static DiagramDefinition createUseCaseDiagram() {
        return DiagramDefinition.builder(DiagramType.USE_CASE, "用户认证用例图")
                .node(new DiagramNode("user", "业务用户", DiagramNodeType.ACTOR))
                .node(new DiagramNode("admin", "系统管理员", DiagramNodeType.ACTOR))
                .node(new DiagramNode("login", "登录认证", DiagramNodeType.USE_CASE))
                .node(new DiagramNode("reset", "重置密码", DiagramNodeType.USE_CASE))
                .node(new DiagramNode("audit", "查看认证日志", DiagramNodeType.USE_CASE))
                .edge(new DiagramEdge("user", "login", "发起"))
                .edge(new DiagramEdge("user", "reset", "申请"))
                .edge(new DiagramEdge("admin", "reset", "审核"))
                .edge(new DiagramEdge("admin", "audit", "查询"))
                .build();
    }

    /**
     * 创建风险处置流程图定义。
     *
     * @return 风险处置流程图定义
     */
    private static DiagramDefinition createFlowDiagram() {
        return DiagramDefinition.builder(DiagramType.FLOW, "风险处置流程图")
                .node(new DiagramNode("start", "开始", DiagramNodeType.START))
                .node(new DiagramNode("identify", "识别风险项", DiagramNodeType.PROCESS))
                .node(new DiagramNode("assess", "风险等级是否达标", DiagramNodeType.DECISION))
                .node(new DiagramNode("dispose", "制定处置计划", DiagramNodeType.PROCESS))
                .node(new DiagramNode("verify", "回归验证并归档", DiagramNodeType.PROCESS))
                .node(new DiagramNode("end", "结束", DiagramNodeType.END))
                .edge(new DiagramEdge("start", "identify"))
                .edge(new DiagramEdge("identify", "assess"))
                .edge(new DiagramEdge("assess", "dispose", "否"))
                .edge(new DiagramEdge("assess", "end", "是"))
                .edge(new DiagramEdge("dispose", "verify"))
                .edge(new DiagramEdge("verify", "end"))
                .build();
    }

    /**
     * 创建风险评估数据库 ER 图定义。
     *
     * @return 风险评估数据库 ER 图定义
     */
    private static DiagramDefinition createErDiagram() {
        return DiagramDefinition.builder(DiagramType.ER, "风险评估数据库 ER 图")
                .node(DiagramNode.entity("risk", "风险项",
                        "id bigint PK", "assessment_id bigint FK", "risk_level varchar(16)", "description varchar(500)"))
                .node(DiagramNode.entity("assessment", "评估报告",
                        "id bigint PK", "project_id bigint FK", "status varchar(16)", "created_at datetime"))
                .node(DiagramNode.entity("project", "项目",
                        "id bigint PK", "project_name varchar(128)", "owner varchar(64)"))
                .edge(new DiagramEdge("project", "assessment", "1:N"))
                .edge(new DiagramEdge("assessment", "risk", "1:N"))
                .build();
    }
}
