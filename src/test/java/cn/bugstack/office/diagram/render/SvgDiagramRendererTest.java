package cn.bugstack.office.diagram.render;

import cn.bugstack.office.diagram.api.DiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramNodeType;
import cn.bugstack.office.diagram.model.DiagramType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SvgDiagramRenderer} 的单元测试。
 *
 * @author luojiang
 */
class SvgDiagramRendererTest {

    /**
     * 临时 SVG 输出目录。
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证三种内置图类型均可生成包含预期元素的 SVG 文件。
     *
     * @throws IOException 当读取测试输出文件失败时抛出
     */
    @Test
    void shouldRenderBuiltInDiagramTypes() throws IOException {
        SvgDiagramRenderer renderer = new SvgDiagramRenderer();

        DiagramArtifact useCaseArtifact = renderer.render(DiagramDefinition.builder(DiagramType.USE_CASE, "认证用例")
                .node(new DiagramNode("user", "用户", DiagramNodeType.ACTOR))
                .node(new DiagramNode("login", "登录", DiagramNodeType.USE_CASE))
                .edge(new DiagramEdge("user", "login"))
                .build(), temporaryDirectory.resolve("use-case.svg"));
        DiagramArtifact flowArtifact = renderer.render(DiagramDefinition.builder(DiagramType.FLOW, "审批流程")
                .node(new DiagramNode("start", "开始", DiagramNodeType.START))
                .node(new DiagramNode("check", "是否通过", DiagramNodeType.DECISION))
                .node(new DiagramNode("end", "结束", DiagramNodeType.END))
                .edge(new DiagramEdge("start", "check"))
                .edge(new DiagramEdge("check", "end", "是"))
                .build(), temporaryDirectory.resolve("flow.svg"));
        DiagramArtifact erArtifact = renderer.render(DiagramDefinition.builder(DiagramType.ER, "用户数据")
                .node(DiagramNode.entity("user", "用户", "id bigint PK", "name varchar(64)"))
                .node(DiagramNode.entity("role", "角色", "id bigint PK"))
                .edge(new DiagramEdge("user", "role", "N:1"))
                .build(), temporaryDirectory.resolve("er.svg"));
        DiagramArtifact systemErArtifact = renderer.render(DiagramDefinition.builder(DiagramType.SYSTEM_ER, "用户系统 E-R")
                .node(DiagramNode.systemEntity("user", "用户"))
                .node(DiagramNode.systemEntity("role", "角色"))
                .node(DiagramNode.relationship("assign", "分配"))
                .node(DiagramNode.attribute("name", "姓名"))
                .edge(new DiagramEdge("user", "assign", "N"))
                .edge(new DiagramEdge("assign", "role", "1"))
                .edge(new DiagramEdge("user", "name"))
                .build(), temporaryDirectory.resolve("system-er.svg"));

        assertTrue(Files.readString(useCaseArtifact.getSvgPath()).contains("<ellipse"));
        assertTrue(Files.readString(flowArtifact.getSvgPath()).contains("<polygon"));
        assertTrue(Files.readString(erArtifact.getSvgPath()).contains("id bigint PK"));
        assertTrue(Files.readString(systemErArtifact.getSvgPath()).contains("<ellipse"));
        assertTrue(Files.readString(systemErArtifact.getSvgPath()).contains("分配"));
    }

    /**
     * 验证图定义不能引用未定义的边端点，避免生成不完整关系图。
     */
    @Test
    void shouldRejectEdgeThatReferencesMissingNode() {
        assertThrows(IllegalStateException.class, () -> DiagramDefinition.builder(DiagramType.FLOW, "错误流程")
                .node(new DiagramNode("start", "开始", DiagramNodeType.START))
                .edge(new DiagramEdge("start", "missing"))
                .build());
    }
}
