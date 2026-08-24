package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramNodeType;
import cn.bugstack.office.diagram.model.DiagramType;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.HorzAlignValue;
import com.aspose.diagram.Shape;
import com.aspose.diagram.VerticalAlignValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VsdxDiagramRenderer} 的单元测试。
 *
 * @author luojiang
 */
class VsdxDiagramRendererTest {

    /** 布局整数坐标转换为英寸时允许的最大半点舍入误差。 */
    private static final double MIDPOINT_TOLERANCE_INCH = 0.5D / 72D + 0.00001D;

    /** 可写入 VSDX 测试文件的临时目录。 */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证流程定义可以生成带有多个可编辑 Shape、小四居中文本和箭头 Shape 的 VSDX 与 PNG 预览图。
     *
     * @throws Exception 当读取生成的 VSDX 文件失败时抛出
     */
    @Test
    void shouldRenderEditableVsdxAndPreview() throws Exception {
        DiagramDefinition definition = DiagramDefinition.builder(DiagramType.FLOW, "可编辑流程图")
                .node(new DiagramNode("start", "开始", DiagramNodeType.START))
                .node(new DiagramNode("assess", "评估风险", DiagramNodeType.PROCESS))
                .node(new DiagramNode("decision", "是否达标", DiagramNodeType.DECISION))
                .node(new DiagramNode("end", "结束", DiagramNodeType.END))
                .edge(new DiagramEdge("start", "assess"))
                .edge(new DiagramEdge("assess", "decision"))
                .edge(new DiagramEdge("decision", "end", "是"))
                .build();

        VisioDiagramArtifact artifact = new VsdxDiagramRenderer()
                .render(definition, temporaryDirectory.resolve("risk-flow.vsdx"));

        assertTrue(Files.exists(artifact.getVsdxPath()));
        assertTrue(Files.size(artifact.getVsdxPath()) > 0L);
        assertTrue(Files.exists(artifact.getPreviewPngPath()));
        assertTrue(Files.size(artifact.getPreviewPngPath()) > 0L);
        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            assertTrue(loaded.getPages().getPage(0).getShapes().getCount() >= 8);
            assertTrue(hasSmallFourCenteredText(loaded));
        } finally {
            loaded.dispose();
        }
    }

    /**
     * 验证并行分叉和汇聚均使用无文字的标准同步条，并保留业务标签元数据。
     *
     * @throws Exception 当读取生成的 VSDX 文件失败时抛出
     */
    @Test
    void shouldRenderStandardParallelControlBars() throws Exception {
        DiagramDefinition definition = DiagramDefinition.builder(DiagramType.FLOW, "并行流程")
                .node(new DiagramNode("start", "开始", DiagramNodeType.START))
                .node(new DiagramNode("split", "并行处理", DiagramNodeType.PARALLEL_SPLIT))
                .node(new DiagramNode("first", "任务一", DiagramNodeType.PROCESS))
                .node(new DiagramNode("second", "任务二", DiagramNodeType.PROCESS))
                .node(new DiagramNode("join", "结果汇聚", DiagramNodeType.PARALLEL_JOIN))
                .node(new DiagramNode("end", "结束", DiagramNodeType.END))
                .edge(new DiagramEdge("start", "split"))
                .edge(new DiagramEdge("split", "first"))
                .edge(new DiagramEdge("split", "second"))
                .edge(new DiagramEdge("first", "join"))
                .edge(new DiagramEdge("second", "join"))
                .edge(new DiagramEdge("join", "end"))
                .build();

        VisioDiagramArtifact artifact = new VsdxDiagramRenderer()
                .render(definition, temporaryDirectory.resolve("parallel-control-bars.vsdx"));

        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            assertTrue(hasStandardParallelControlBars(loaded, "并行处理", "结果汇聚"));
        } finally {
            loaded.dispose();
        }
    }

    /**
     * 验证系统 E-R 图可写入实体矩形、关系菱形、属性椭圆和无箭头关联线。
     *
     * @throws Exception 当读取生成的 VSDX 文件失败时抛出
     */
    @Test
    void shouldRenderEditableSystemErDiagram() throws Exception {
        DiagramDefinition definition = DiagramDefinition.builder(DiagramType.SYSTEM_ER, "系统 E-R 图")
                .node(DiagramNode.systemEntity("user", "用户"))
                .node(DiagramNode.systemEntity("role", "角色"))
                .node(DiagramNode.relationship("assign", "分配"))
                .node(DiagramNode.attribute("user-name", "用户名"))
                .edge(new DiagramEdge("user", "assign", "N"))
                .edge(new DiagramEdge("assign", "role", "1"))
                .edge(new DiagramEdge("user", "user-name"))
                .build();

        VsdxDiagramRenderer renderer = new VsdxDiagramRenderer();
        VisioDiagramArtifact artifact = renderer.render(definition, temporaryDirectory.resolve("system-er.vsdx"));

        Diagram loaded = renderer.compose(definition);
        try {
            assertTrue(Files.exists(artifact.getPreviewPngPath()));
            assertTrue(containsPureText(loaded, "用户"));
            assertTrue(containsPureText(loaded, "分配"));
            assertTrue(containsPureText(loaded, "用户名"));
            assertTrue(hasNamedShape(loaded, "Association_"));
        } finally {
            loaded.dispose();
        }
    }

    /**
     * 验证 UML 类图可以生成包含类名、属性、方法和关系线的可编辑 VSDX。
     *
     * @throws Exception 当读取生成的 VSDX 文件失败时抛出
     */
    @Test
    void shouldRenderEditableClassDiagram() throws Exception {
        DiagramDefinition definition = DiagramDefinition.builder(DiagramType.CLASS, "风险类图")
                .node(DiagramNode.classNode("service", "RiskService", List.of("- repository: RiskRepository"),
                        List.of("+ assess(): RiskLevel")))
                .node(DiagramNode.classNode("repository", "RiskRepository", List.of(),
                        List.of("+ save(): Long")))
                .edge(new DiagramEdge("service", "repository", "依赖"))
                .build();

        VsdxDiagramRenderer renderer = new VsdxDiagramRenderer();
        VisioDiagramArtifact artifact = renderer.render(definition, temporaryDirectory.resolve("risk-class.vsdx"));

        assertTrue(Files.exists(artifact.getVsdxPath()));
        assertTrue(Files.exists(artifact.getPreviewPngPath()));
        Diagram loaded = renderer.compose(definition);
        try {
            assertTrue(loaded.getPages().getPage(0).getShapes().getCount() >= 12);
            assertTrue(hasSmallFourCenteredText(loaded));
            assertTrue(hasZeroMarginMiddleAlignedText(loaded));
        } finally {
            loaded.dispose();
        }
    }

    /**
     * 验证总体功能逻辑图可按系统、模块和功能项三层结构生成可编辑 VSDX。
     *
     * @throws Exception 当生成或回读 VSDX 文件失败时抛出
     */
    @Test
    void shouldRenderEditableOverallFunctionLogicDiagram() throws Exception {
        DiagramDefinition definition = DiagramDefinition.builder(DiagramType.OVERALL_FUNCTION_LOGIC,
                        "总体功能逻辑图")
                .node(DiagramNode.functionSystem("system", "风险管理系统"))
                .node(DiagramNode.functionModule("register", "风险登记"))
                .node(DiagramNode.functionModule("assess", "风险评估"))
                .node(DiagramNode.functionItem("source", "风险来源登记"))
                .node(DiagramNode.functionItem("maintain", "风险信息维护"))
                .node(DiagramNode.functionItem("level", "风险等级评估"))
                .edge(new DiagramEdge("system", "register"))
                .edge(new DiagramEdge("system", "assess"))
                .edge(new DiagramEdge("register", "source"))
                .edge(new DiagramEdge("register", "maintain"))
                .edge(new DiagramEdge("assess", "level"))
                .build();

        VsdxDiagramRenderer renderer = new VsdxDiagramRenderer();
        VisioDiagramArtifact artifact = renderer.render(definition,
                temporaryDirectory.resolve("overall-function-logic.vsdx"));

        assertTrue(Files.exists(artifact.getVsdxPath()));
        assertTrue(Files.exists(artifact.getPreviewPngPath()));
        Diagram loaded = renderer.compose(definition);
        try {
            assertTrue(containsPureText(loaded, "风险管理系统"));
            assertTrue(containsPureText(loaded, "风险登记"));
            assertTrue(hasNamedShape(loaded, "FunctionNode_"));
            assertTrue(hasNamedShape(loaded, "FunctionBus_"));
            assertTrue(hasNamedShape(loaded, "Connector_"));
            assertFunctionNodesCenteredOnPage(loaded);
            assertFunctionNodeSize(loaded, "FunctionNode_FUNCTION_SYSTEM_", 1, 180D, 40D);
            assertFunctionNodeSize(loaded, "FunctionNode_FUNCTION_MODULE_", 2, 140D, 40D);
            assertFunctionNodeSize(loaded, "FunctionNode_FUNCTION_ITEM_", 3, 42D, 220D);
            assertAllFunctionTextUsesTwelvePoints(loaded, 6);
        } finally {
            loaded.dispose();
        }
    }

    /**
     * 验证指定层级功能节点的数量与尺寸。
     *
     * @param diagram 已回读的总体功能逻辑 VSDX
     * @param namePrefix 功能节点 Shape 名称前缀
     * @param expectedCount 期望节点数量
     * @param expectedWidthPoints 期望宽度，单位为 point
     * @param expectedHeightPoints 期望高度，单位为 point
     */
    private void assertFunctionNodeSize(Diagram diagram, String namePrefix, int expectedCount,
                                        double expectedWidthPoints, double expectedHeightPoints) {
        int actualCount = 0;
        for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
            Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
            if (shape.getNameU() == null || !shape.getNameU().startsWith(namePrefix)) {
                continue;
            }
            assertEquals(expectedWidthPoints / 72D, shape.getXForm().getWidth().getValue(), 0.0001D);
            assertEquals(expectedHeightPoints / 72D, shape.getXForm().getHeight().getValue(), 0.0001D);
            actualCount++;
        }
        assertEquals(expectedCount, actualCount);
    }

    /**
     * 验证总体功能逻辑图的全部节点文字均为 12pt。
     *
     * @param diagram 已回读的总体功能逻辑 VSDX
     * @param expectedCount 期望功能文字 Shape 数量
     */
    private void assertAllFunctionTextUsesTwelvePoints(Diagram diagram, int expectedCount) {
        int actualCount = 0;
        for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
            Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
            if (shape.getNameU() == null || !shape.getNameU().startsWith("FunctionText_")) {
                continue;
            }
            assertTrue(shape.getChars().getCount() > 0);
            for (int characterIndex = 0; characterIndex < shape.getChars().getCount(); characterIndex++) {
                assertEquals(12D / 72D, shape.getChars().get(characterIndex).getSize().getValue(), 0.0001D);
            }
            actualCount++;
        }
        assertEquals(expectedCount, actualCount);
    }

    /**
     * 验证总体功能逻辑图全部节点的可见边界中心与页面水平中心一致。
     *
     * @param diagram 已回读的总体功能逻辑 VSDX
     */
    private void assertFunctionNodesCenteredOnPage(Diagram diagram) {
        double minimumLeft = Double.POSITIVE_INFINITY;
        double maximumRight = Double.NEGATIVE_INFINITY;
        int functionNodeCount = 0;
        for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
            Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
            if (shape.getNameU() == null || !shape.getNameU().startsWith("FunctionNode_")) {
                continue;
            }
            double centerX = shape.getXForm().getPinX().getValue();
            double width = shape.getXForm().getWidth().getValue();
            minimumLeft = Math.min(minimumLeft, centerX - width / 2D);
            maximumRight = Math.max(maximumRight, centerX + width / 2D);
            functionNodeCount++;
        }
        double pageCenter = diagram.getPages().getPage(0).getPageSheet().getPageProps()
                .getPageWidth().getValue() / 2D;
        assertTrue(functionNodeCount > 0);
        assertEquals(pageCenter, (minimumLeft + maximumRight) / 2D, MIDPOINT_TOLERANCE_INCH);
    }

    /**
     * 验证多条类关系的标签会分散到不同位置，避免共享中点导致文字重叠。
     *
     * @throws Exception 当读取生成的 VSDX 文件失败时抛出
     */
    @Test
    void shouldSeparateMultipleClassRelationLabels() throws Exception {
        DiagramDefinition definition = DiagramDefinition.builder(DiagramType.CLASS, "关系标签布局")
                .node(DiagramNode.classNode("controller", "Controller", List.of(), List.of("+ execute(): void")))
                .node(DiagramNode.classNode("service", "Service", List.of("- repository: Repository"),
                        List.of("+ execute(): void")))
                .node(DiagramNode.classNode("repository", "Repository", List.of(), List.of("+ save(): void")))
                .node(DiagramNode.classNode("entity", "Entity", List.of("- id: Long"),
                        List.of("+ validate(): boolean")))
                .edge(new DiagramEdge("controller", "service", "调用"))
                .edge(new DiagramEdge("service", "repository", "依赖"))
                .edge(new DiagramEdge("service", "entity", "管理"))
                .edge(new DiagramEdge("entity", "repository", "关联"))
                .build();

        VsdxDiagramRenderer renderer = new VsdxDiagramRenderer();
        renderer.render(definition, temporaryDirectory.resolve("class-label-layout.vsdx"));

        Diagram loaded = renderer.compose(definition);
        try {
            assertTrue(hasDistinctClassRelationLabelPositions(loaded, 4));
        } finally {
            loaded.dispose();
        }
    }

    /**
     * 验证所有受支持图型的关系文字均位于对应连线的几何中点，并保持透明背景。
     *
     * <p>每种定义只构造一条带标签的直线关系，使标签 Shape 可以与唯一的连接线 Shape
     * 一一对应。该测试覆盖流程图、用例图、数据库 E-R 图、类图、CSCI 部件图和系统 E-R 图。</p>
     *
     * @throws Exception 当生成或回读 VSDX 文件失败时抛出
     */
    @Test
    void shouldPlaceEveryRelationLabelAtConnectorMidpoint() throws Exception {
        List<RelationLabelCase> cases = List.of(
                new RelationLabelCase("flow", DiagramDefinition.builder(DiagramType.FLOW, "流程关系")
                        .node(new DiagramNode("start", "开始", DiagramNodeType.START))
                        .node(new DiagramNode("process", "处理", DiagramNodeType.PROCESS))
                        .edge(new DiagramEdge("start", "process", "流转"))
                        .build(), "FlowRelationLabel_", "Connector_"),
                new RelationLabelCase("use-case", DiagramDefinition.builder(DiagramType.USE_CASE, "用例关系")
                        .node(new DiagramNode("actor", "用户", DiagramNodeType.ACTOR))
                        .node(new DiagramNode("case", "提交申请", DiagramNodeType.USE_CASE))
                        .edge(new DiagramEdge("actor", "case", "调用"))
                        .build(), "UseCaseRelationLabel_", "Connector_"),
                new RelationLabelCase("er", DiagramDefinition.builder(DiagramType.ER, "数据库关系")
                        .node(DiagramNode.entity("project", "项目", "id: Long"))
                        .node(DiagramNode.entity("risk", "风险", "id: Long"))
                        .edge(new DiagramEdge("project", "risk", "1:N"))
                        .build(), "ErRelationLabel_", "Connector_"),
                new RelationLabelCase("class", DiagramDefinition.builder(DiagramType.CLASS, "类关系")
                        .node(DiagramNode.classNode("service", "Service", List.of(), List.of("+ run(): void")))
                        .node(DiagramNode.classNode("repository", "Repository", List.of(), List.of("+ save(): void")))
                        .edge(new DiagramEdge("service", "repository", "依赖"))
                        .build(), "ClassRelationLabel_", "Connector_"),
                new RelationLabelCase("component", DiagramDefinition.builder(DiagramType.COMPONENT, "部件关系")
                        .node(DiagramNode.component("api", "接口部件", "接收请求"))
                        .node(DiagramNode.component("core", "核心部件", "处理业务"))
                        .edge(new DiagramEdge("api", "core", "调用"))
                        .build(), "ComponentRelationLabel_", "Connector_"),
                new RelationLabelCase("system-er", DiagramDefinition.builder(DiagramType.SYSTEM_ER, "系统关系")
                        .node(DiagramNode.systemEntity("risk", "风险"))
                        .node(DiagramNode.relationship("own", "归属"))
                        .edge(new DiagramEdge("risk", "own", "N"))
                        .build(), "Cardinality_", "Association_")
        );

        for (RelationLabelCase relationCase : cases) {
            VsdxDiagramRenderer renderer = new VsdxDiagramRenderer();
            renderer.render(relationCase.getDefinition(),
                    temporaryDirectory.resolve(relationCase.getFileStem() + ".vsdx"));
            Diagram loaded = renderer.compose(relationCase.getDefinition());
            try {
                assertLabelAtConnectorMidpoint(loaded, relationCase.getLabelPrefix(),
                        relationCase.getConnectorPrefix());
            } finally {
                loaded.dispose();
            }
        }
    }

    /**
     * 判断生成的文本 Shape 是否使用小四字号和水平居中对齐。
     *
     * @param diagram 已回读的 VSDX 文档
     * @return 存在符合要求的文本 Shape 时返回 {@code true}
     */
    private boolean hasSmallFourCenteredText(Diagram diagram) {
        for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
            Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
            if (shape.getNameU() != null && shape.getNameU().startsWith("Text_")
                    && shape.getParas().getCount() > 0 && shape.getChars().getCount() > 0) {
                return shape.getParas().get(0).getHorzAlign().getValue() == HorzAlignValue.CENTER
                        && Math.abs(shape.getChars().get(0).getSize().getValue() - 12D / 72D) < 0.0001D;
            }
        }
        return false;
    }

    /**
     * 判断生成的文本 Shape 是否移除了默认内边距并采用垂直居中。
     *
     * <p>该规则保证小四文本在 UML 类图的固定行高内渲染，不会压住属性区和方法区的分隔线。</p>
     *
     * @param diagram 已回读的 VSDX 文档
     * @return 存在符合无内边距、垂直居中要求的文本 Shape 时返回 {@code true}
     */
    private boolean hasZeroMarginMiddleAlignedText(Diagram diagram) {
        for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
            Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
            if (shape.getNameU() != null && shape.getNameU().startsWith("Text_")
                    && shape.getTextBlock().getVerticalAlign().getValue() == VerticalAlignValue.MIDDLE) {
                return Math.abs(shape.getTextBlock().getLeftMargin().getValue()) < 0.0001D
                        && Math.abs(shape.getTextBlock().getRightMargin().getValue()) < 0.0001D
                        && Math.abs(shape.getTextBlock().getTopMargin().getValue()) < 0.0001D
                        && Math.abs(shape.getTextBlock().getBottomMargin().getValue()) < 0.0001D;
            }
        }
        return false;
    }

    /**
     * 判断 VSDX 是否包含指定的可编辑文本内容。
     *
     * @param diagram 已回读的 VSDX 文档
     * @param expectedText 期望文本
     * @return 存在对应文本 Shape 时返回 {@code true}
     */
    private boolean containsPureText(Diagram diagram, String expectedText) {
        for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
            if (expectedText.equals(diagram.getPages().getPage(0).getShapes().get(index).getPureText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 VSDX 是否存在使用指定名称前缀的 Shape。
     *
     * @param diagram 已回读的 VSDX 文档
     * @param namePrefix Shape 名称前缀
     * @return 存在匹配 Shape 时返回 {@code true}
     */
    private boolean hasNamedShape(Diagram diagram, String namePrefix) {
        for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
            String name = diagram.getPages().getPage(0).getShapes().get(index).getNameU();
            if (name != null && name.startsWith(namePrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断类关系标签是否拥有互不重复的中心坐标。
     *
     * @param diagram 已回读的 VSDX 文档
     * @param expectedCount 期望的关系标签数量
     * @return 标签数量与位置均符合预期时返回 {@code true}
     */
    private boolean hasDistinctClassRelationLabelPositions(Diagram diagram, int expectedCount) {
        Set<String> positions = new HashSet<>();
        int labelCount = 0;
        for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
            Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
            if (shape.getNameU() != null && shape.getNameU().startsWith("ClassRelationLabel_")) {
                labelCount++;
                positions.add(String.format("%.4f:%.4f", shape.getXForm().getPinX().getValue(),
                        shape.getXForm().getPinY().getValue()));
            }
        }
        return labelCount == expectedCount && positions.size() == expectedCount;
    }

    /**
     * 判断指定业务语义是否保存于无文字并行同步条的元数据中。
     *
     * @param diagram 已回读的 VSDX 文档
     * @param expectedLabels 期望存在的同步条标签
     * @return 所有期望文本均存在于同步条时返回 {@code true}
     */
    private boolean hasStandardParallelControlBars(Diagram diagram, String... expectedLabels) {
        Set<String> actualLabels = new HashSet<>();
        int forkCount = 0;
        int joinCount = 0;
        for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
            Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
            if (shape.getNameU() != null && shape.getNameU().startsWith("ParallelFork_")) {
                forkCount++;
                actualLabels.add(shape.getData1());
                if (!shape.getPureText().isEmpty() || !hasWhiteFillAndBlackBorder(shape)) {
                    return false;
                }
            }
            if (shape.getNameU() != null && shape.getNameU().startsWith("ParallelJoin_")) {
                joinCount++;
                actualLabels.add(shape.getData1());
                if (!shape.getPureText().isEmpty() || !hasWhiteFillAndBlackBorder(shape)) {
                    return false;
                }
            }
        }
        for (String expectedLabel : expectedLabels) {
            if (!actualLabels.contains(expectedLabel)) {
                return false;
            }
        }
        return forkCount == 1 && joinCount == 1;
    }

    /**
     * 判断同步条是否采用白色实心填充和黑色轮廓，避免在文档预览中显示为实心黑条。
     *
     * @param shape 待检查的同步条 Shape
     * @return 填充和轮廓均满足样式规范时返回 {@code true}
     */
    private boolean hasWhiteFillAndBlackBorder(Shape shape) {
        return "#FFFFFF".equalsIgnoreCase(shape.getFill().getFillForegnd().getValue())
                && "#FFFFFF".equalsIgnoreCase(shape.getFill().getFillBkgnd().getValue())
                && "#000000".equalsIgnoreCase(shape.getLine().getLineColor().getValue());
    }

    /**
     * 断言指定标签 Shape 与连接线 Shape 的中心坐标完全一致，并且标签背景透明。
     *
     * @param diagram 已回读的 VSDX 文档
     * @param labelPrefix 关系标签 Shape 名称前缀
     * @param connectorPrefix 连接线 Shape 名称前缀
     */
    private void assertLabelAtConnectorMidpoint(Diagram diagram, String labelPrefix, String connectorPrefix) {
        Shape label = findSingleNamedShape(diagram, labelPrefix);
        Shape connector = findSingleNamedShape(diagram, connectorPrefix);
        assertEquals(connector.getXForm().getPinX().getValue(), label.getXForm().getPinX().getValue(),
                MIDPOINT_TOLERANCE_INCH);
        assertEquals(connector.getXForm().getPinY().getValue(), label.getXForm().getPinY().getValue(),
                MIDPOINT_TOLERANCE_INCH);
        assertEquals(1D, label.getFill().getFillForegndTrans().getValue(), 0.0001D);
        assertEquals(1D, label.getFill().getFillBkgndTrans().getValue(), 0.0001D);
    }

    /**
     * 按名称前缀查找唯一的页面 Shape。
     *
     * @param diagram 已回读的 VSDX 文档
     * @param namePrefix Shape 名称前缀
     * @return 唯一匹配的 Shape
     * @throws AssertionError 当匹配数量不是一个时抛出
     */
    private Shape findSingleNamedShape(Diagram diagram, String namePrefix) {
        List<Shape> matches = new ArrayList<>();
        for (int index = 0; index < diagram.getPages().getPage(0).getShapes().getCount(); index++) {
            Shape shape = diagram.getPages().getPage(0).getShapes().get(index);
            if (shape.getNameU() != null && shape.getNameU().startsWith(namePrefix)) {
                matches.add(shape);
            }
        }
        assertEquals(1, matches.size(), "Expected one shape named " + namePrefix);
        return matches.get(0);
    }

    /**
     * 单一关系标签位置测试所需的图定义及 Shape 名称规则。
     */
    private static final class RelationLabelCase {

        /** 测试输出文件基础名称。 */
        private final String fileStem;
        /** 待渲染的图定义。 */
        private final DiagramDefinition definition;
        /** 关系标签 Shape 名称前缀。 */
        private final String labelPrefix;
        /** 连接线 Shape 名称前缀。 */
        private final String connectorPrefix;

        /**
         * 创建单一关系标签测试用例。
         *
         * @param fileStem 测试输出文件基础名称
         * @param definition 待渲染的图定义
         * @param labelPrefix 关系标签 Shape 名称前缀
         * @param connectorPrefix 连接线 Shape 名称前缀
         */
        private RelationLabelCase(String fileStem, DiagramDefinition definition, String labelPrefix,
                                  String connectorPrefix) {
            this.fileStem = fileStem;
            this.definition = definition;
            this.labelPrefix = labelPrefix;
            this.connectorPrefix = connectorPrefix;
        }

        /** @return 测试输出文件基础名称 */
        private String getFileStem() {
            return fileStem;
        }

        /** @return 待渲染的图定义 */
        private DiagramDefinition getDefinition() {
            return definition;
        }

        /** @return 关系标签 Shape 名称前缀 */
        private String getLabelPrefix() {
            return labelPrefix;
        }

        /** @return 连接线 Shape 名称前缀 */
        private String getConnectorPrefix() {
            return connectorPrefix;
        }
    }
}
