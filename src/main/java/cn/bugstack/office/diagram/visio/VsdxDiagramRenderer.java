package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.api.VisioDiagramRenderer;
import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramNodeType;
import cn.bugstack.office.diagram.model.DiagramType;
import com.aspose.diagram.Char;
import com.aspose.diagram.ColorValue;
import com.aspose.diagram.Cp;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.DoubleValue;
import com.aspose.diagram.HorzAlign;
import com.aspose.diagram.HorzAlignValue;
import com.aspose.diagram.IntValue;
import com.aspose.diagram.Para;
import com.aspose.diagram.Page;
import com.aspose.diagram.Pp;
import com.aspose.diagram.SaveFileFormat;
import com.aspose.diagram.Shape;
import com.aspose.diagram.Txt;
import com.aspose.diagram.VerticalAlign;
import com.aspose.diagram.VerticalAlignValue;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Aspose.Diagram 的可编辑 VSDX 渲染器。
 *
 * <p>该实现直接创建 Visio 的矩形、椭圆、折线和文本 Shape，并从同一份 VSDX 生成
 * Word OLE 使用的 PNG 预览图。这样在 Visio 中可以选中、移动和编辑节点、关系线及文字。</p>
 *
 * @author luojiang
 */
public final class VsdxDiagramRenderer implements VisioDiagramRenderer {

    /** 图布局坐标转换为 Visio 英寸坐标时使用的每英寸点数。 */
    private static final double POINTS_PER_INCH = 72D;
    /** 页边距，单位为图布局 point。 */
    private static final int PADDING = 48;
    /** 用例图参与者名称区域宽度，单位为图布局 point。 */
    private static final int USE_CASE_ACTOR_LABEL_WIDTH = 96;
    /** 用例图参与者与模块边界之间的水平间距，单位为图布局 point。 */
    private static final int USE_CASE_ACTOR_MODULE_GAP = 68;
    /** 模块边界顶部到首个功能用例中心的纵向距离，单位为图布局 point。 */
    private static final int USE_CASE_FIRST_CENTER_OFFSET = 54;
    /** 图标题开始区域的纵坐标，单位为图布局 point。 */
    private static final int TITLE_TOP = 18;
    /** 图标题区域高度，单位为图布局 point。 */
    private static final int TITLE_HEIGHT = 24;
    /** 图标题与第一个图元之间预留的最小纵向空间，单位为图布局 point。 */
    private static final int CONTENT_TOP = 112;
    /** 流程图相邻逻辑行的中心间距，单位为图布局 point。 */
    private static final int FLOW_ROW_GAP = 112;
    /** ER 图实体标题区域高度，单位为图布局 point。 */
    private static final int ENTITY_HEADER_HEIGHT = 34;
    /** ER 图每个字段占用的高度，单位为图布局 point。 */
    private static final int ENTITY_FIELD_HEIGHT = 22;
    /** 分层关系图中为线中部标签预留的水平间距，单位为图布局 point。 */
    private static final int RELATION_LABEL_LAYER_GAP = 80;
    /** ER 图中相邻实体行之间的垂直间距，单位为图布局 point。 */
    private static final int ER_ROW_GAP = 28;
    /** ER 图中不相连实体分组之间的垂直间距，单位为图布局 point。 */
    private static final int ER_COMPONENT_GAP = 56;
    /** ER 图实体卡片允许使用的最小宽度，单位为图布局 point。 */
    private static final int ER_MIN_ENTITY_WIDTH = 180;
    /** UML 类图类名区域高度，单位为图布局 point。 */
    private static final int CLASS_HEADER_HEIGHT = 36;
    /** UML 类图属性或方法每行占用的高度，单位为图布局 point。 */
    private static final int CLASS_MEMBER_HEIGHT = 24;
    /** UML 类图类卡片允许使用的最小宽度，单位为图布局 point。 */
    private static final int CLASS_MIN_WIDTH = 180;
    /** CSCI 部件标题区域高度，单位为图布局 point。 */
    private static final int COMPONENT_HEADER_HEIGHT = 34;
    /** CSCI 部件详情每行占用高度，单位为图布局 point。 */
    private static final int COMPONENT_DETAIL_HEIGHT = 22;
    /** CSCI 部件卡片允许使用的最小宽度，单位为图布局 point。 */
    private static final int COMPONENT_MIN_WIDTH = 200;
    /** 总体功能逻辑图系统根节点宽度，单位为图布局 point。 */
    private static final int FUNCTION_SYSTEM_WIDTH = 180;
    /** 总体功能逻辑图系统根节点高度，单位为图布局 point。 */
    private static final int FUNCTION_SYSTEM_HEIGHT = 40;
    /** 总体功能逻辑图一级功能模块宽度，单位为图布局 point。 */
    private static final int FUNCTION_MODULE_WIDTH = 140;
    /** 总体功能逻辑图一级功能模块高度，单位为图布局 point。 */
    private static final int FUNCTION_MODULE_HEIGHT = 40;
    /** 总体功能逻辑图末级功能项宽度，单位为图布局 point。 */
    private static final int FUNCTION_ITEM_WIDTH = 42;
    /** 总体功能逻辑图末级功能项高度，单位为图布局 point。 */
    private static final int FUNCTION_ITEM_HEIGHT = 220;
    /** 同一功能模块下末级功能项之间的水平间距，单位为图布局 point。 */
    private static final int FUNCTION_ITEM_GAP = 12;
    /** 相邻一级功能模块分组之间的水平间距，单位为图布局 point。 */
    private static final int FUNCTION_GROUP_GAP = 28;
    /** 总体功能逻辑图系统根节点上边界，单位为图布局 point。 */
    private static final int FUNCTION_SYSTEM_TOP = 76;
    /** 总体功能逻辑图一级功能模块上边界，单位为图布局 point。 */
    private static final int FUNCTION_MODULE_TOP = 192;
    /** 总体功能逻辑图末级功能项上边界，单位为图布局 point。 */
    private static final int FUNCTION_ITEM_TOP = 308;
    /** 系统 E-R 图概念实体的默认宽度，单位为图布局 point。 */
    private static final int SYSTEM_ER_ENTITY_WIDTH = 112;
    /** 系统 E-R 图概念实体的默认高度，单位为图布局 point。 */
    private static final int SYSTEM_ER_ENTITY_HEIGHT = 48;
    /** 系统 E-R 图关系菱形的默认宽度，单位为图布局 point。 */
    private static final int SYSTEM_ER_RELATIONSHIP_WIDTH = 84;
    /** 系统 E-R 图关系菱形的默认高度，单位为图布局 point。 */
    private static final int SYSTEM_ER_RELATIONSHIP_HEIGHT = 56;
    /** 系统 E-R 图属性椭圆的默认宽度，单位为图布局 point。 */
    private static final int SYSTEM_ER_ATTRIBUTE_WIDTH = 88;
    /** 系统 E-R 图属性椭圆的默认高度，单位为图布局 point。 */
    private static final int SYSTEM_ER_ATTRIBUTE_HEIGHT = 42;
    /** UML 类图关系标签的最小宽度，单位为图布局 point。 */
    private static final int CLASS_RELATION_LABEL_MIN_WIDTH = 64;
    /** UML 类图关系标签的最小高度，单位为图布局 point。 */
    private static final int CLASS_RELATION_LABEL_HEIGHT = 20;
    /** UML 活动图并行同步条的标准高度，单位为图布局 point。 */
    private static final int PARALLEL_CONTROL_BAR_HEIGHT = 10;
    /** Visio 实心填充图案值。 */
    private static final int SOLID_FILL_PATTERN = 1;
    /** Visio 文本采用的小四字号，等价于 12pt，单位为英寸。 */
    private static final double VISIO_SMALL_FOUR_FONT_SIZE = 12D / POINTS_PER_INCH;
    /** 关系线箭头两侧线段的长度，单位为图布局 point。 */
    private static final int ARROW_HEAD_LENGTH = 10;
    /** 关系线箭头两侧线段相对反向延长线的夹角，单位为弧度。 */
    private static final double ARROW_HEAD_ANGLE = Math.toRadians(25D);

    /**
     * 生成 VSDX 文件及其默认 PNG 预览图。
     *
     * @param definition 图语义定义
     * @param vsdxPath VSDX 输出路径
     * @return 可编辑 Visio 图产物
     * @throws IOException 当 VSDX 或 PNG 无法写入时抛出
     */
    @Override
    public VisioDiagramArtifact render(DiagramDefinition definition, Path vsdxPath) throws IOException {
        String fileName = vsdxPath.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        return render(definition, vsdxPath, vsdxPath.resolveSibling(baseName + ".preview.png"));
    }

    /**
     * 生成 VSDX 文件及指定位置的 PNG 预览图。
     *
     * @param definition 图语义定义
     * @param vsdxPath VSDX 输出路径
     * @param previewPngPath PNG 预览图输出路径
     * @return 可编辑 Visio 图产物
     * @throws IOException 当 VSDX 或 PNG 无法写入时抛出
     */
    @Override
    public VisioDiagramArtifact render(DiagramDefinition definition, Path vsdxPath, Path previewPngPath)
            throws IOException {
        ensureParentDirectory(vsdxPath);
        ensureParentDirectory(previewPngPath);
        Canvas canvas = canvasFor(definition);
        Diagram diagram = compose(definition, canvas);
        try {
            diagram.save(vsdxPath.toString(), SaveFileFormat.VSDX);
            savePreview(definition, previewPngPath, canvas, diagram);
            return new VisioDiagramArtifact(definition.getType(), vsdxPath, previewPngPath);
        } catch (Exception exception) {
            throw new IOException("render editable Visio diagram failed: " + vsdxPath, exception);
        } finally {
            diagram.dispose();
        }
    }

    /**
     * 在内存中组装完整的可编辑 Diagram，供同包结构测试在保存前验证全部 Shape。
     * 调用方负责在使用完成后调用 {@link Diagram#dispose()}。
     *
     * @param definition 图语义定义
     * @return 尚未经过文件保存的完整 Diagram
     * @throws IOException 当图类型不受支持或组装失败时抛出
     */
    Diagram compose(DiagramDefinition definition) throws IOException {
        return compose(definition, canvasFor(definition));
    }

    /** 使用已经计算的画布组装内存 Diagram。 */
    private Diagram compose(DiagramDefinition definition, Canvas canvas) throws IOException {
        Diagram diagram = new Diagram();
        try {
            Page page = diagram.getPages().getPage(0);
            configurePage(page, canvas);
            renderTitle(page, definition.getTitle(), canvas);
            switch (definition.getType()) {
                case USE_CASE:
                    renderUseCase(page, definition, canvas);
                    break;
                case FLOW:
                    renderFlow(page, definition, canvas);
                    break;
                case ER:
                    renderEr(page, definition, canvas);
                    break;
                case SYSTEM_ER:
                    renderSystemEr(page, definition, canvas);
                    break;
                case CLASS:
                    renderClass(page, definition, canvas);
                    break;
                case COMPONENT:
                    renderComponent(page, definition, canvas);
                    break;
                case OVERALL_FUNCTION_LOGIC:
                    renderOverallFunctionLogic(page, definition, canvas);
                    break;
                default:
                    throw new IOException("unsupported editable Visio diagram type: " + definition.getType());
            }
            return diagram;
        } catch (Exception exception) {
            diagram.dispose();
            if (exception instanceof IOException) throw (IOException) exception;
            throw new IOException("compose editable Visio diagram failed", exception);
        }
    }

    /**
     * 根据图类型和节点数量确定 Visio 画布大小。
     *
     * @param definition 图语义定义
     * @return 画布参数
     */
    private Canvas canvasFor(DiagramDefinition definition) {
        int width = definition.getOptions().getWidth();
        if (definition.getType() == DiagramType.ER) {
            width = Math.max(width, minimumLayeredCanvasWidth(definition, ER_MIN_ENTITY_WIDTH));
        } else if (definition.getType() == DiagramType.CLASS) {
            width = Math.max(width, minimumLayeredCanvasWidth(definition, CLASS_MIN_WIDTH));
        } else if (definition.getType() == DiagramType.COMPONENT) {
            width = Math.max(width, minimumLayeredCanvasWidth(definition, COMPONENT_MIN_WIDTH));
        } else if (definition.getType() == DiagramType.OVERALL_FUNCTION_LOGIC) {
            width = Math.max(width, functionLogicRequiredWidth(definition));
        }
        int height;
        if (definition.getType() == DiagramType.FLOW) {
            height = Math.max(definition.getOptions().getHeight(), CONTENT_TOP + definition.getNodes().size() * 112);
        } else if (definition.getType() == DiagramType.ER) {
            height = Math.max(definition.getOptions().getHeight(), erLayoutPlan(definition, width).getRequiredHeight());
        } else if (definition.getType() == DiagramType.CLASS) {
            height = Math.max(definition.getOptions().getHeight(), classLayoutPlan(definition, width).getRequiredHeight());
        } else if (definition.getType() == DiagramType.COMPONENT) {
            height = Math.max(definition.getOptions().getHeight(), componentLayoutPlan(definition, width)
                    .getRequiredHeight());
        } else if (definition.getType() == DiagramType.SYSTEM_ER) {
            height = Math.max(definition.getOptions().getHeight(), 680);
        } else if (definition.getType() == DiagramType.OVERALL_FUNCTION_LOGIC) {
            height = Math.max(definition.getOptions().getHeight(), FUNCTION_ITEM_TOP + FUNCTION_ITEM_HEIGHT + PADDING);
        } else {
            int rowCount = Math.max(1, Math.max(nodesOf(definition, DiagramNodeType.ACTOR).size(),
                    nodesOf(definition, DiagramNodeType.USE_CASE).size()));
            height = Math.max(definition.getOptions().getHeight(), CONTENT_TOP + rowCount * 96);
        }
        return new Canvas(width, height);
    }

    /**
     * 计算分层关系图容纳节点和线中部标签所需的最小画布宽度。
     *
     * @param definition 图语义定义
     * @param minimumNodeWidth 单个节点允许使用的最小宽度
     * @return 不压缩节点与关系标签通道的最小画布宽度
     */
    private int minimumLayeredCanvasWidth(DiagramDefinition definition, int minimumNodeWidth) {
        int layerCount = maximumLayerCount(definition);
        return PADDING * 2 + layerCount * minimumNodeWidth
                + Math.max(0, layerCount - 1) * RELATION_LABEL_LAYER_GAP;
    }

    /**
     * 计算总体功能逻辑图完整展示所有功能分组所需的最小宽度。
     *
     * @param definition 总体功能逻辑图定义
     * @return 最小画布宽度，单位为图布局 point
     */
    private int functionLogicRequiredWidth(DiagramDefinition definition) {
        FunctionStructure structure = functionStructure(definition);
        int contentWidth = 0;
        for (DiagramNode module : structure.getModules()) {
            int itemCount = structure.getItems(module.getId()).size();
            int itemWidth = itemCount == 0 ? 0
                    : itemCount * FUNCTION_ITEM_WIDTH + (itemCount - 1) * FUNCTION_ITEM_GAP;
            contentWidth += Math.max(FUNCTION_MODULE_WIDTH, itemWidth);
        }
        contentWidth += Math.max(0, structure.getModules().size() - 1) * FUNCTION_GROUP_GAP;
        return Math.max(FUNCTION_SYSTEM_WIDTH + PADDING * 2, contentWidth + PADDING * 2);
    }

    /**
     * 计算总体功能逻辑图的节点边界与功能分组。
     *
     * @param definition 总体功能逻辑图定义
     * @param canvasWidth 画布宽度，单位为图布局 point
     * @return 可直接用于绘制的功能逻辑布局
     */
    private FunctionLogicPlan functionLogicPlan(DiagramDefinition definition, int canvasWidth) {
        FunctionStructure structure = functionStructure(definition);
        int requiredWidth = functionLogicRequiredWidthFromStructure(structure);
        int cursorX = Math.max(PADDING, (canvasWidth - requiredWidth) / 2 + PADDING);
        Map<String, NodeBox> boxes = new LinkedHashMap<>();
        Map<String, List<NodeBox>> itemBoxes = new LinkedHashMap<>();
        List<NodeBox> moduleBoxes = new ArrayList<>();
        NodeBox systemBox = centeredBox(canvasWidth / 2, FUNCTION_SYSTEM_TOP + FUNCTION_SYSTEM_HEIGHT / 2,
                FUNCTION_SYSTEM_WIDTH, FUNCTION_SYSTEM_HEIGHT);
        boxes.put(structure.getSystem().getId(), systemBox);
        for (DiagramNode module : structure.getModules()) {
            List<DiagramNode> items = structure.getItems(module.getId());
            int itemRowWidth = items.isEmpty() ? 0
                    : items.size() * FUNCTION_ITEM_WIDTH + (items.size() - 1) * FUNCTION_ITEM_GAP;
            int groupWidth = Math.max(FUNCTION_MODULE_WIDTH, itemRowWidth);
            int groupCenterX = cursorX + groupWidth / 2;
            NodeBox moduleBox = centeredBox(groupCenterX, FUNCTION_MODULE_TOP + FUNCTION_MODULE_HEIGHT / 2,
                    FUNCTION_MODULE_WIDTH, FUNCTION_MODULE_HEIGHT);
            boxes.put(module.getId(), moduleBox);
            moduleBoxes.add(moduleBox);
            List<NodeBox> currentItems = new ArrayList<>();
            int itemLeft = groupCenterX - itemRowWidth / 2;
            for (int index = 0; index < items.size(); index++) {
                DiagramNode item = items.get(index);
                NodeBox itemBox = new NodeBox(itemLeft + index * (FUNCTION_ITEM_WIDTH + FUNCTION_ITEM_GAP),
                        FUNCTION_ITEM_TOP, FUNCTION_ITEM_WIDTH, FUNCTION_ITEM_HEIGHT);
                boxes.put(item.getId(), itemBox);
                currentItems.add(itemBox);
            }
            itemBoxes.put(module.getId(), currentItems);
            cursorX += groupWidth + FUNCTION_GROUP_GAP;
        }
        return new FunctionLogicPlan(boxes, structure.getModules(), systemBox, moduleBoxes, itemBoxes);
    }

    /**
     * 根据已校验的功能树结构计算包含左右边距的最小画布宽度。
     *
     * @param structure 功能树结构
     * @return 最小画布宽度，单位为图布局 point
     */
    private int functionLogicRequiredWidthFromStructure(FunctionStructure structure) {
        int contentWidth = 0;
        for (DiagramNode module : structure.getModules()) {
            int itemCount = structure.getItems(module.getId()).size();
            int itemWidth = itemCount == 0 ? 0
                    : itemCount * FUNCTION_ITEM_WIDTH + (itemCount - 1) * FUNCTION_ITEM_GAP;
            contentWidth += Math.max(FUNCTION_MODULE_WIDTH, itemWidth);
        }
        contentWidth += Math.max(0, structure.getModules().size() - 1) * FUNCTION_GROUP_GAP;
        return Math.max(FUNCTION_SYSTEM_WIDTH + PADDING * 2, contentWidth + PADDING * 2);
    }

    /**
     * 校验并提取总体功能逻辑图的三层树结构。
     *
     * <p>定义必须且只能包含一个系统根节点、至少一个一级功能模块以及任意数量的末级
     * 功能项；边只允许从系统指向模块、从模块指向功能项，每个非根节点必须恰好有一个父节点。</p>
     *
     * @param definition 总体功能逻辑图定义
     * @return 按节点声明顺序组织的功能树结构
     */
    private FunctionStructure functionStructure(DiagramDefinition definition) {
        List<DiagramNode> systems = nodesOf(definition, DiagramNodeType.FUNCTION_SYSTEM);
        List<DiagramNode> modules = nodesOf(definition, DiagramNodeType.FUNCTION_MODULE);
        List<DiagramNode> items = nodesOf(definition, DiagramNodeType.FUNCTION_ITEM);
        if (systems.size() != 1) {
            throw new IllegalArgumentException("overall function logic diagram requires exactly one system node");
        }
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("overall function logic diagram requires at least one module node");
        }
        if (systems.size() + modules.size() + items.size() != definition.getNodes().size()) {
            throw new IllegalArgumentException("overall function logic diagram contains an unsupported node type");
        }
        DiagramNode system = systems.get(0);
        Map<String, DiagramNode> nodes = diagramNodesById(definition);
        Map<String, String> parents = new HashMap<>();
        Map<String, List<DiagramNode>> itemsByModule = new LinkedHashMap<>();
        for (DiagramNode module : modules) {
            itemsByModule.put(module.getId(), new ArrayList<>());
        }
        for (DiagramEdge edge : definition.getEdges()) {
            DiagramNode parent = nodes.get(edge.getFrom());
            DiagramNode child = nodes.get(edge.getTo());
            boolean systemToModule = parent.getType() == DiagramNodeType.FUNCTION_SYSTEM
                    && child.getType() == DiagramNodeType.FUNCTION_MODULE;
            boolean moduleToItem = parent.getType() == DiagramNodeType.FUNCTION_MODULE
                    && child.getType() == DiagramNodeType.FUNCTION_ITEM;
            if (!systemToModule && !moduleToItem) {
                throw new IllegalArgumentException("invalid overall function logic edge: "
                        + edge.getFrom() + " -> " + edge.getTo());
            }
            if (parents.putIfAbsent(child.getId(), parent.getId()) != null) {
                throw new IllegalArgumentException("function node has multiple parents: " + child.getId());
            }
            if (moduleToItem) {
                itemsByModule.get(parent.getId()).add(child);
            }
        }
        for (DiagramNode module : modules) {
            if (!system.getId().equals(parents.get(module.getId()))) {
                throw new IllegalArgumentException("function module is not connected to the system: " + module.getId());
            }
        }
        for (DiagramNode item : items) {
            if (!parents.containsKey(item.getId())) {
                throw new IllegalArgumentException("function item is not connected to a module: " + item.getId());
            }
        }
        return new FunctionStructure(system, modules, itemsByModule);
    }

    /**
     * 计算图中所有连通分量使用的最大关系层数。
     *
     * @param definition 图语义定义
     * @return 最大关系层数，至少为一层
     */
    private int maximumLayerCount(DiagramDefinition definition) {
        int layerCount = 1;
        for (ErComponent component : erComponents(definition)) {
            layerCount = Math.max(layerCount, erLayers(component, definition).size());
        }
        return layerCount;
    }

    /**
     * 配置 Visio 页的尺寸，坐标单位为英寸。
     *
     * @param page Visio 页面
     * @param canvas 画布参数
     */
    private void configurePage(Page page, Canvas canvas) {
        page.getPageSheet().getPageProps().getPageWidth().setValue(canvas.width / POINTS_PER_INCH);
        page.getPageSheet().getPageProps().getPageHeight().setValue(canvas.height / POINTS_PER_INCH);
    }

    /**
     * 生成 Word OLE 的 PNG 预览图。
     *
     * <p>流程图通过 Java2D 绘制，以规避 Aspose.Diagram 试用版对新建中文文本 Shape 的 PNG
     * 渲染缺失问题；VSDX 仍由 Aspose.Diagram 直接生成并作为 Word 中唯一的可编辑对象。</p>
     *
     * @param definition 图语义定义
     * @param previewPngPath PNG 预览图输出路径
     * @param canvas 图布局画布
     * @param diagram 当前 Visio 文档，用于非流程图预览回退
     * @throws Exception 当 PNG 无法生成时抛出
     */
    private void savePreview(DiagramDefinition definition, Path previewPngPath, Canvas canvas, Diagram diagram)
            throws Exception {
        if (definition.getType() == DiagramType.FLOW) {
            saveFlowPreview(definition, previewPngPath, canvas);
            return;
        }
        diagram.save(previewPngPath.toString(), SaveFileFormat.PNG);
    }

    /**
     * 绘制包含小四居中文本和向下箭头的流程图 PNG 预览。
     *
     * @param definition 流程图定义
     * @param previewPngPath PNG 预览图输出路径
     * @param canvas 图布局画布
     * @throws IOException 当 PNG 无法写入时抛出
     */
    private void saveFlowPreview(DiagramDefinition definition, Path previewPngPath, Canvas canvas) throws IOException {
        int scale = 2;
        BufferedImage image = new BufferedImage(canvas.width * scale, canvas.height * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.scale(scale, scale);
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(1F));
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(new Font("Hiragino Sans GB", Font.PLAIN, 12));
            drawCenteredPreviewText(graphics, definition.getTitle(), canvas.width / 2, TITLE_TOP + TITLE_HEIGHT,
                    new Font("Hiragino Sans GB", Font.BOLD, 16));
            Map<String, NodeBox> boxes = flowNodeBoxes(definition, canvas);
            drawPreviewEdges(graphics, definition.getEdges(), boxes, canvas);
            for (DiagramNode node : definition.getNodes()) {
                NodeBox box = boxes.get(node.getId());
                drawPreviewNode(graphics, node, box);
                if (!isParallelNode(node)) {
                    drawCenteredPreviewText(graphics, node.getLabel(), box.centerX(), box.centerY() + 4,
                            new Font("Hiragino Sans GB", Font.PLAIN, 12));
                }
            }
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", previewPngPath.toFile());
    }

    /**
     * 绘制流程图节点外形。
     *
     * @param graphics PNG 绘图上下文
     * @param node 流程节点
     * @param box 节点边界
     */
    private void drawPreviewNode(Graphics2D graphics, DiagramNode node, NodeBox box) {
        if (isParallelNode(node)) {
            Color originalColor = graphics.getColor();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(box.left(), box.top(), box.width, box.height);
            graphics.setColor(Color.BLACK);
            graphics.drawRect(box.left(), box.top(), box.width, box.height);
            graphics.setColor(originalColor);
        } else if (node.getType() == DiagramNodeType.DECISION) {
            Path2D diamond = new Path2D.Double();
            diamond.moveTo(box.centerX(), box.top());
            diamond.lineTo(box.right(), box.centerY());
            diamond.lineTo(box.centerX(), box.bottom());
            diamond.lineTo(box.left(), box.centerY());
            diamond.closePath();
            graphics.draw(diamond);
        } else if (node.getType() == DiagramNodeType.START || node.getType() == DiagramNodeType.END) {
            graphics.draw(new Ellipse2D.Double(box.left(), box.top(), box.width, box.height));
        } else {
            graphics.drawRect(box.left(), box.top(), box.width, box.height);
        }
    }

    /**
     * 绘制流程节点之间的连接线、箭头和边标签。
     *
     * @param graphics PNG 绘图上下文
     * @param edges 关系边集合
     * @param boxes 节点边界映射
     */
    private void drawPreviewEdges(Graphics2D graphics, List<DiagramEdge> edges, Map<String, NodeBox> boxes,
                                  Canvas canvas) {
        for (DiagramEdge edge : edges) {
            NodeBox fromBox = boxes.get(edge.getFrom());
            NodeBox toBox = boxes.get(edge.getTo());
            if (isSideBranch(edge, fromBox, toBox)) {
                drawPreviewSideBranch(graphics, edge, fromBox, toBox, canvas);
                continue;
            }
            Point from = edgePoint(fromBox, toBox);
            Point to = edgePoint(toBox, fromBox);
            graphics.drawLine(from.x, from.y, to.x, to.y);
            drawPreviewArrowHead(graphics, from, to);
            if (edge.getLabel() != null) {
                drawPreviewLineLabel(graphics, edge.getLabel(), List.of(from, to));
            }
        }
    }

    /**
     * 绘制从判定节点右侧绕行至结束节点的 PNG 分支线。
     *
     * @param graphics PNG 绘图上下文
     * @param edge 图关系
     * @param from 起始节点边界
     * @param to 目标节点边界
     * @param canvas 图布局画布
     */
    private void drawPreviewSideBranch(Graphics2D graphics, DiagramEdge edge, NodeBox from, NodeBox to,
                                       Canvas canvas) {
        int routeX = PADDING;
        Point start = new Point(from.left(), from.centerY());
        Point upperCorner = new Point(routeX, from.centerY());
        Point lowerCorner = new Point(routeX, to.centerY());
        Point end = new Point(to.left(), to.centerY());
        graphics.drawLine(start.x, start.y, upperCorner.x, upperCorner.y);
        graphics.drawLine(upperCorner.x, upperCorner.y, lowerCorner.x, lowerCorner.y);
        graphics.drawLine(lowerCorner.x, lowerCorner.y, end.x, end.y);
        drawPreviewArrowHead(graphics, lowerCorner, end);
        drawPreviewLineLabel(graphics, edge.getLabel(), List.of(start, upperCorner, lowerCorner, end));
    }

    /**
     * 在完整连接路径的长度中点绘制流程图预览标签。
     *
     * @param graphics PNG 绘图上下文
     * @param label 关系标签
     * @param points 连接路径坐标
     */
    private void drawPreviewLineLabel(Graphics2D graphics, String label, List<Point> points) {
        Point midpoint = pathMidpoint(points);
        Font font = new Font("Hiragino Sans GB", Font.PLAIN, 12);
        FontMetrics metrics = graphics.getFontMetrics(font);
        int baselineY = midpoint.y + (metrics.getAscent() - metrics.getDescent()) / 2;
        drawCenteredPreviewText(graphics, label, midpoint.x, baselineY, font);
    }

    /**
     * 绘制关系线终点的方向箭头。
     *
     * @param graphics PNG 绘图上下文
     * @param from 关系线起点
     * @param to 关系线终点
     */
    private void drawPreviewArrowHead(Graphics2D graphics, Point from, Point to) {
        double vectorX = to.x - from.x;
        double vectorY = to.y - from.y;
        double length = Math.hypot(vectorX, vectorY);
        if (length == 0D) {
            return;
        }
        Point left = arrowSidePoint(to, -vectorX / length, -vectorY / length, -vectorY / length,
                vectorX / length, 1D);
        Point right = arrowSidePoint(to, -vectorX / length, -vectorY / length, -vectorY / length,
                vectorX / length, -1D);
        graphics.drawLine(to.x, to.y, left.x, left.y);
        graphics.drawLine(to.x, to.y, right.x, right.y);
    }

    /**
     * 使用指定字体在水平中心位置绘制单行文本。
     *
     * @param graphics PNG 绘图上下文
     * @param text 文本内容
     * @param centerX 文本区域中心横坐标
     * @param baselineY 文本基线纵坐标
     * @param font 文本字体
     */
    private void drawCenteredPreviewText(Graphics2D graphics, String text, int centerX, int baselineY, Font font) {
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        graphics.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }

    /**
     * 渲染图标题。
     *
     * @param page Visio 页面
     * @param title 图标题
     * @param canvas 画布参数
     */
    private void renderTitle(Page page, String title, Canvas canvas) throws Exception {
        addText(page, canvas.width / 2, TITLE_TOP, canvas.width - PADDING * 2, TITLE_HEIGHT, title, canvas);
    }

    /**
     * 渲染用例图中的参与者、用例椭圆及关系线。
     *
     * @param page Visio 页面
     * @param definition 用例图定义
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderUseCase(Page page, DiagramDefinition definition, Canvas canvas) throws Exception {
        List<DiagramNode> actors = nodesOf(definition, DiagramNodeType.ACTOR);
        List<DiagramNode> useCases = nodesOf(definition, DiagramNodeType.USE_CASE);
        List<DiagramNode> otherNodes = otherNodes(definition);
        Map<String, NodeBox> boxes = new HashMap<>();
        UseCaseLayout layout = useCaseLayout(canvas);
        drawUseCaseModuleBoundary(page, definition, useCases, layout, canvas);
        renderActors(page, actors, useCases.size(), layout.getActorCenterX(), boxes, canvas);
        renderUseCases(page, useCases, layout.getModuleCenterX(), boxes, canvas);
        renderUseCases(page, otherNodes, canvas.width - 130, boxes, canvas);
        renderUseCaseEdges(page, definition, boxes, canvas);
    }

    /**
     * 绘制单一业务模块的用例边界。
     *
     * <p>边界是原生 Visio 矩形，目的在于清晰表达所有椭圆用例都属于同一个模块，
     * 而不是多个松散的独立业务域。</p>
     *
     * @param page Visio 页面
     * @param definition 用例图定义
     * @param useCases 标准用例节点
     * @param layout 用例图整体布局
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawUseCaseModuleBoundary(Page page, DiagramDefinition definition, List<DiagramNode> useCases,
                                           UseCaseLayout layout, Canvas canvas) throws Exception {
        int top = CONTENT_TOP - 22;
        int height = Math.max(110, useCases.size() * 96 + 24);
        NodeBox boundary = new NodeBox(layout.getModuleCenterX() - layout.getModuleWidth() / 2, top,
                layout.getModuleWidth(), height);
        drawRectangle(page, boundary, canvas);
        addText(page, boundary.centerX(), boundary.top() + 8, boundary.width - 24, 18,
                moduleName(definition.getTitle()), canvas);
    }

    /**
     * 渲染流程图中的节点和关系线。
     *
     * @param page Visio 页面
     * @param definition 流程图定义
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderFlow(Page page, DiagramDefinition definition, Canvas canvas) throws Exception {
        Map<String, NodeBox> boxes = flowNodeBoxes(definition, canvas);
        for (DiagramNode node : definition.getNodes()) {
            NodeBox box = boxes.get(node.getId());
            drawFlowNode(page, node, box, canvas);
        }
        renderEdges(page, definition.getEdges(), boxes, canvas);
    }

    /**
     * 渲染 ER 图的实体卡片和关系线。
     *
     * @param page Visio 页面
     * @param definition ER 图定义
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderEr(Page page, DiagramDefinition definition, Canvas canvas) throws Exception {
        ErLayoutPlan layoutPlan = erLayoutPlan(definition, canvas.width);
        Map<String, NodeBox> boxes = layoutPlan.getBoxes();
        for (DiagramNode node : definition.getNodes()) {
            NodeBox box = boxes.get(node.getId());
            drawEntity(page, node, box, canvas);
        }
        renderOrthogonalEdges(page, definition.getEdges(), boxes, canvas, "ErRelationLabel_");
    }

    /**
     * 渲染 UML 类图的类卡片和关系线。
     *
     * @param page Visio 页面
     * @param definition 类图定义
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderClass(Page page, DiagramDefinition definition, Canvas canvas) throws Exception {
        ErLayoutPlan layoutPlan = classLayoutPlan(definition, canvas.width);
        Map<String, NodeBox> boxes = layoutPlan.getBoxes();
        for (DiagramNode node : definition.getNodes()) {
            drawClass(page, node, boxes.get(node.getId()), canvas);
        }
        renderClassEdges(page, definition.getEdges(), boxes, canvas);
    }

    /**
     * 渲染 CSCI 软件部件关系图。
     *
     * @param page Visio 页面
     * @param definition 部件图语义定义
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderComponent(Page page, DiagramDefinition definition, Canvas canvas) throws Exception {
        ErLayoutPlan layoutPlan = componentLayoutPlan(definition, canvas.width);
        Map<String, NodeBox> boxes = layoutPlan.getBoxes();
        for (DiagramNode node : definition.getNodes()) {
            drawComponent(page, node, boxes.get(node.getId()), canvas);
        }
        renderOrthogonalEdges(page, definition.getEdges(), boxes, canvas, "ComponentRelationLabel_");
    }

    /**
     * 渲染系统总体功能逻辑分解图。
     *
     * <p>系统根节点、一级功能模块和末级功能项按三层树形结构排布。每个父节点先连接到
     * 一条共享水平总线，再由总线垂直分支到各子节点顶部中点，避免逐条斜线交叉并保持
     * 功能分组边界清晰。</p>
     *
     * @param page Visio 页面
     * @param definition 总体功能逻辑图定义
     * @param canvas 画布参数
     * @throws Exception 当定义不符合三层树约束或创建 Shape 失败时抛出
     */
    private void renderOverallFunctionLogic(Page page, DiagramDefinition definition, Canvas canvas) throws Exception {
        FunctionLogicPlan plan = functionLogicPlan(definition, canvas.width);
        for (DiagramNode node : definition.getNodes()) {
            NodeBox box = plan.getBoxes().get(node.getId());
            Shape rectangle = drawRectangle(page, box, canvas);
            rectangle.setNameU("FunctionNode_" + node.getType().name() + "_" + rectangle.getID());
            String text = node.getType() == DiagramNodeType.FUNCTION_ITEM
                    ? verticalFunctionText(node.getLabel()) : node.getLabel();
            Shape textShape = addText(page, box.centerX(), box.top() + 4, box.width - 8, box.height - 8,
                    text, canvas);
            textShape.setNameU("FunctionText_" + node.getType().name() + "_" + textShape.getID());
        }
        drawFunctionBus(page, plan.getSystemBox(), plan.getModuleBoxes(), canvas);
        for (DiagramNode module : plan.getModules()) {
            drawFunctionBus(page, plan.getBoxes().get(module.getId()), plan.getItemBoxes(module.getId()), canvas);
        }
    }

    /**
     * 将末级功能项文本转换为适合纵向窄框展示的逐字换行文本。
     *
     * @param text 原始功能项名称
     * @return 每个非空白字符独占一行的文本
     */
    private String verticalFunctionText(String text) {
        StringBuilder vertical = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isWhitespace(character)) {
                continue;
            }
            if (vertical.length() > 0) {
                vertical.append('\n');
            }
            vertical.append(character);
        }
        return vertical.toString();
    }

    /**
     * 绘制父节点到多个子节点的共享总线连接。
     *
     * @param page Visio 页面
     * @param parent 父节点边界
     * @param children 按横向位置排列的子节点边界
     * @param canvas 画布参数
     * @throws Exception 当创建连接线失败时抛出
     */
    private void drawFunctionBus(Page page, NodeBox parent, List<NodeBox> children, Canvas canvas) throws Exception {
        if (children.isEmpty()) {
            return;
        }
        int busY = (parent.bottom() + children.get(0).top()) / 2;
        Point parentBottom = new Point(parent.centerX(), parent.bottom());
        Point parentBus = new Point(parent.centerX(), busY);
        drawFunctionConnector(page, parentBottom, parentBus, canvas);
        int left = children.get(0).centerX();
        int right = children.get(children.size() - 1).centerX();
        if (left != right) {
            drawFunctionConnector(page, new Point(left, busY), new Point(right, busY), canvas);
        }
        for (NodeBox child : children) {
            Point branchStart = new Point(child.centerX(), busY);
            Point branchEnd = new Point(child.centerX(), child.top());
            drawDirectionalLine(page, branchStart, branchEnd, canvas);
        }
    }

    /**
     * 绘制总体功能逻辑图的无箭头总线段并设置稳定名称。
     *
     * @param page Visio 页面
     * @param fromPoint 起点
     * @param toPoint 终点
     * @param canvas 画布参数
     * @throws Exception 当创建线段失败时抛出
     */
    private void drawFunctionConnector(Page page, Point fromPoint, Point toPoint, Canvas canvas) throws Exception {
        long lineId = page.drawLine(toInch(fromPoint.x), toY(fromPoint.y, canvas), toInch(toPoint.x),
                toY(toPoint.y, canvas));
        Shape line = shapeById(page, lineId);
        line.setNameU("FunctionBus_" + line.getID());
    }

    /**
     * 渲染 Chen 表示法的系统概念 E-R 图。
     *
     * <p>实体绘制为矩形，关系绘制为菱形，属性绘制为椭圆；关联线不带箭头，边标签用于
     * 表达 {@code 1}、{@code N} 等基数，从而与数据库表结构 ER 图明确区分。</p>
     *
     * @param page Visio 页面
     * @param definition 系统 E-R 图语义定义
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderSystemEr(Page page, DiagramDefinition definition, Canvas canvas) throws Exception {
        Map<String, NodeBox> boxes = systemErNodeBoxes(definition, canvas);
        for (DiagramNode node : definition.getNodes()) {
            drawSystemErNode(page, node, boxes.get(node.getId()), canvas);
        }
        renderSystemErEdges(page, definition, boxes, canvas);
    }

    /**
     * 渲染参与者图形及其名称。
     *
     * @param page Visio 页面
     * @param actors 参与者节点
     * @param useCaseCount 模块内功能用例数量
     * @param actorCenterX 参与者中心横坐标
     * @param boxes 节点边界映射
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderActors(Page page, List<DiagramNode> actors, int useCaseCount, int actorCenterX,
                              Map<String, NodeBox> boxes, Canvas canvas) throws Exception {
        int groupCenterY = useCaseGroupCenterY(useCaseCount);
        int actorStartY = groupCenterY - (actors.size() - 1) * 48;
        for (int index = 0; index < actors.size(); index++) {
            DiagramNode actor = actors.get(index);
            int centerY = actorStartY + index * 96;
            page.drawEllipse(toInch(actorCenterX), toY(centerY - 24, canvas), toInch(24), toInch(24));
            page.drawLine(toInch(actorCenterX), toY(centerY - 12, canvas), toInch(actorCenterX),
                    toY(centerY + 24, canvas));
            page.drawLine(toInch(actorCenterX - 18), toY(centerY, canvas), toInch(actorCenterX + 18),
                    toY(centerY, canvas));
            page.drawLine(toInch(actorCenterX), toY(centerY + 24, canvas), toInch(actorCenterX - 16),
                    toY(centerY + 44, canvas));
            page.drawLine(toInch(actorCenterX), toY(centerY + 24, canvas), toInch(actorCenterX + 16),
                    toY(centerY + 44, canvas));
            addText(page, actorCenterX, centerY + 64, USE_CASE_ACTOR_LABEL_WIDTH, 20, actor.getLabel(), canvas);
            boxes.put(actor.getId(), new NodeBox(actorCenterX - 20, centerY - 24, 40, 72));
        }
    }

    /**
     * 渲染用例椭圆及其名称。
     *
     * @param page Visio 页面
     * @param nodes 用例节点
     * @param centerX 用例列中心横坐标
     * @param boxes 节点边界映射
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderUseCases(Page page, List<DiagramNode> nodes, int centerX, Map<String, NodeBox> boxes,
                                Canvas canvas) throws Exception {
        for (int index = 0; index < nodes.size(); index++) {
            DiagramNode node = nodes.get(index);
            int centerY = CONTENT_TOP + USE_CASE_FIRST_CENTER_OFFSET + index * 96;
            NodeBox box = new NodeBox(centerX - 118, centerY - 34, 236, 68);
            page.drawEllipse(toInch(box.centerX()), toY(box.centerY(), canvas), toInch(box.width),
                    toInch(box.height));
            addText(page, centerX, centerY - 10, 204, 20, node.getLabel(), canvas);
            boxes.put(node.getId(), box);
        }
    }

    /**
     * 计算模块内功能用例集合的纵向中心位置。
     *
     * @param useCaseCount 功能用例数量
     * @return 功能用例集合的纵向中心坐标
     */
    private int useCaseGroupCenterY(int useCaseCount) {
        int firstCenterY = CONTENT_TOP + USE_CASE_FIRST_CENTER_OFFSET;
        return firstCenterY + Math.max(0, useCaseCount - 1) * 48;
    }

    /**
     * 渲染单个流程节点。
     *
     * @param page Visio 页面
     * @param node 流程节点
     * @param box 节点边界
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawFlowNode(Page page, DiagramNode node, NodeBox box, Canvas canvas) throws Exception {
        if (isParallelNode(node)) {
            Shape parallelBar = drawRectangle(page, box, canvas);
            configureParallelControlBar(parallelBar, node);
            return;
        }
        if (node.getType() == DiagramNodeType.START || node.getType() == DiagramNodeType.END) {
            page.drawEllipse(toInch(box.centerX()), toY(box.centerY(), canvas), toInch(box.width),
                    toInch(box.height));
        } else if (node.getType() == DiagramNodeType.DECISION) {
            drawDiamond(page, box, canvas);
        } else {
            drawRectangle(page, box, canvas);
        }
        addText(page, box.centerX(), box.centerY() - 10, box.width - 16, 20, node.getLabel(), canvas);
    }

    /**
     * 渲染 ER 实体卡片、字段分割线及字段名称。
     *
     * @param page Visio 页面
     * @param node 实体节点
     * @param box 实体边界
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawEntity(Page page, DiagramNode node, NodeBox box, Canvas canvas) throws Exception {
        drawRectangle(page, box, canvas);
        int headerBottom = box.top() + ENTITY_HEADER_HEIGHT;
        page.drawLine(toInch(box.left()), toY(headerBottom, canvas), toInch(box.right()), toY(headerBottom, canvas));
        addText(page, box.centerX(), box.top() + 8, box.width - 24, 18, node.getLabel(), canvas);
        if (node.getFields().isEmpty()) {
            addText(page, box.centerX(), headerBottom + 4, box.width - 24, 16, "-", canvas);
            return;
        }
        for (int index = 0; index < node.getFields().size(); index++) {
            addText(page, box.centerX(), headerBottom + 4 + index * ENTITY_FIELD_HEIGHT,
                    box.width - 24, 16, node.getFields().get(index), canvas);
        }
    }

    /**
     * 渲染 UML 类卡片的类名、属性和方法三个区域。
     *
     * @param page Visio 页面
     * @param node 类节点
     * @param box 类卡片边界
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawClass(Page page, DiagramNode node, NodeBox box, Canvas canvas) throws Exception {
        drawRectangle(page, box, canvas);
        int headerBottom = box.top() + CLASS_HEADER_HEIGHT;
        int attributeRows = Math.max(1, node.getClassAttributes().size());
        int attributeBottom = headerBottom + attributeRows * CLASS_MEMBER_HEIGHT;
        page.drawLine(toInch(box.left()), toY(headerBottom, canvas), toInch(box.right()), toY(headerBottom, canvas));
        page.drawLine(toInch(box.left()), toY(attributeBottom, canvas), toInch(box.right()),
                toY(attributeBottom, canvas));
        addText(page, box.centerX(), box.top() + 8, box.width - 20, 20, node.getLabel(), canvas);
        drawClassMembers(page, node.getClassAttributes(), box, headerBottom, canvas);
        drawClassMembers(page, node.getClassMethods(), box, attributeBottom, canvas);
    }

    /**
     * 写入 UML 类卡片一个区域内的属性或方法文本。
     *
     * @param page Visio 页面
     * @param members 属性或方法列表
     * @param box 类卡片边界
     * @param sectionTop 区域起始纵坐标
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawClassMembers(Page page, List<String> members, NodeBox box, int sectionTop, Canvas canvas)
            throws Exception {
        if (members.isEmpty()) {
            addText(page, box.centerX(), sectionTop + 2, box.width - 20, CLASS_MEMBER_HEIGHT - 4, "-", canvas);
            return;
        }
        for (int index = 0; index < members.size(); index++) {
            addText(page, box.centerX(), sectionTop + 2 + index * CLASS_MEMBER_HEIGHT, box.width - 20,
                    CLASS_MEMBER_HEIGHT - 4, members.get(index), canvas);
        }
    }

    /**
     * 绘制 CSCI 软件部件卡片及其职责、接口说明。
     *
     * @param page Visio 页面
     * @param node 部件节点
     * @param box 部件卡片边界
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawComponent(Page page, DiagramNode node, NodeBox box, Canvas canvas) throws Exception {
        drawRectangle(page, box, canvas);
        int headerBottom = box.top() + COMPONENT_HEADER_HEIGHT;
        page.drawLine(toInch(box.left()), toY(headerBottom, canvas), toInch(box.right()), toY(headerBottom, canvas));
        addText(page, box.centerX(), box.top() + 8, box.width - 20, 20, node.getLabel(), canvas);
        if (node.getFields().isEmpty()) {
            addText(page, box.centerX(), headerBottom + 2, box.width - 20, COMPONENT_DETAIL_HEIGHT - 4, "-", canvas);
            return;
        }
        for (int index = 0; index < node.getFields().size(); index++) {
            addText(page, box.centerX(), headerBottom + 2 + index * COMPONENT_DETAIL_HEIGHT, box.width - 20,
                    COMPONENT_DETAIL_HEIGHT - 4, node.getFields().get(index), canvas);
        }
    }

    /**
     * 绘制系统 E-R 图中的实体、关系或属性图元。
     *
     * @param page Visio 页面
     * @param node 系统 E-R 节点
     * @param box 节点边界
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawSystemErNode(Page page, DiagramNode node, NodeBox box, Canvas canvas) throws Exception {
        if (node.getType() == DiagramNodeType.RELATIONSHIP) {
            drawDiamond(page, box, canvas);
        } else if (node.getType() == DiagramNodeType.ATTRIBUTE) {
            page.drawEllipse(toInch(box.centerX()), toY(box.centerY(), canvas), toInch(box.width),
                    toInch(box.height));
        } else {
            drawRectangle(page, box, canvas);
        }
        addText(page, box.centerX(), box.top() + (box.height - 18) / 2, box.width - 12, 18, node.getLabel(), canvas);
    }

    /**
     * 依据用例椭圆的真实边界渲染关系线，避免斜线连接到外接矩形角。
     *
     * @param page Visio 页面
     * @param definition 用例图定义
     * @param boxes 节点边界映射
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderUseCaseEdges(Page page, DiagramDefinition definition, Map<String, NodeBox> boxes,
                                    Canvas canvas) throws Exception {
        Map<String, DiagramNodeType> nodeTypes = new HashMap<>();
        for (DiagramNode node : definition.getNodes()) {
            nodeTypes.put(node.getId(), node.getType());
        }
        for (DiagramEdge edge : definition.getEdges()) {
            NodeBox from = boxes.get(edge.getFrom());
            NodeBox to = boxes.get(edge.getTo());
            Point fromPoint = useCaseOutgoingPoint(from, to, nodeTypes.get(edge.getFrom()));
            Point toPoint = useCaseIncomingPoint(to, from, nodeTypes.get(edge.getTo()));
            drawDirectionalLine(page, fromPoint, toPoint, canvas);
            if (edge.getLabel() != null && !edge.getLabel().trim().isEmpty()) {
                addLineLabel(page, List.of(fromPoint, toPoint), edge.getLabel(), 64,
                        "UseCaseRelationLabel_", canvas);
            }
        }
    }

    /**
     * 计算用例图关系线的起始锚点。
     *
     * @param source 起始节点边界
     * @param target 目标节点边界
     * @param sourceType 起始节点类型
     * @return 起始节点上的关系线锚点
     */
    private Point useCaseOutgoingPoint(NodeBox source, NodeBox target, DiagramNodeType sourceType) {
        if (sourceType == DiagramNodeType.ACTOR) {
            return source.centerX() <= target.centerX() ? new Point(source.right(), source.centerY())
                    : new Point(source.left(), source.centerY());
        }
        return source.centerX() <= target.centerX() ? new Point(source.right(), source.centerY())
                : new Point(source.left(), source.centerY());
    }

    /**
     * 计算用例图关系线的终止锚点。
     *
     * <p>外部参与者调用功能用例时，连接线必须落在功能用例椭圆的左侧中点，而不是外接
     * 矩形角或椭圆上的斜向点。这会让同一参与者发出的多条线具有稳定、可读的落点。</p>
     *
     * @param target 目标节点边界
     * @param source 起始节点边界
     * @param targetType 目标节点类型
     * @return 目标节点上的关系线锚点
     */
    private Point useCaseIncomingPoint(NodeBox target, NodeBox source, DiagramNodeType targetType) {
        if (targetType == DiagramNodeType.USE_CASE) {
            return new Point(target.left(), target.centerY());
        }
        return source.centerX() <= target.centerX() ? new Point(target.left(), target.centerY())
                : new Point(target.right(), target.centerY());
    }

    /**
     * 使用中心点、宽度和高度绘制矩形，避免四坐标重载在不同版本中的语义差异。
     *
     * @param page Visio 页面
     * @param box 矩形边界
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private Shape drawRectangle(Page page, NodeBox box, Canvas canvas) throws Exception {
        long shapeId = page.drawRectangle((float) toInch(box.centerX()), (float) toY(box.centerY(), canvas),
                toInch(box.width), toInch(box.height));
        return shapeById(page, shapeId);
    }

    /**
     * 使用四条独立可编辑线段绘制流程图判定菱形。
     *
     * @param page Visio 页面
     * @param box 菱形外接边界
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawDiamond(Page page, NodeBox box, Canvas canvas) throws Exception {
        page.drawLine(toInch(box.centerX()), toY(box.top(), canvas), toInch(box.right()), toY(box.centerY(), canvas));
        page.drawLine(toInch(box.right()), toY(box.centerY(), canvas), toInch(box.centerX()), toY(box.bottom(), canvas));
        page.drawLine(toInch(box.centerX()), toY(box.bottom(), canvas), toInch(box.left()), toY(box.centerY(), canvas));
        page.drawLine(toInch(box.left()), toY(box.centerY(), canvas), toInch(box.centerX()), toY(box.top(), canvas));
    }

    /**
     * 渲染节点之间的可编辑关系线和关系标签。
     *
     * @param page Visio 页面
     * @param edges 关系边集合
     * @param boxes 节点边界映射
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderEdges(Page page, List<DiagramEdge> edges, Map<String, NodeBox> boxes, Canvas canvas)
            throws Exception {
        for (DiagramEdge edge : edges) {
            NodeBox from = boxes.get(edge.getFrom());
            NodeBox to = boxes.get(edge.getTo());
            if (isSideBranch(edge, from, to)) {
                drawSideBranch(page, edge, from, to, canvas);
                continue;
            }
            Point fromPoint = edgePoint(from, to);
            Point toPoint = edgePoint(to, from);
            drawDirectionalLine(page, fromPoint, toPoint, canvas);
            if (edge.getLabel() != null && !edge.getLabel().trim().isEmpty()) {
                addLineLabel(page, List.of(fromPoint, toPoint), edge.getLabel(), 64,
                        "FlowRelationLabel_", canvas);
            }
        }
    }

    /**
     * 绘制数据库 E-R 图或 CSCI 部件图的正交方向关系线和避让标签。
     *
     * <p>原实现将跨行依赖直接绘制为斜线，容易穿越卡片内容区域。此处在两个图元之间的
     * 空白带内进行水平、垂直分段，最后一段才进入目标图元，因此线条清晰且便于二次编辑。</p>
     *
     * @param page Visio 页面
     * @param edges 关系边集合
     * @param boxes 实体或部件边界映射
     * @param canvas 画布参数
     * @param labelNamePrefix 关系标签 Shape 的稳定名称前缀
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderOrthogonalEdges(Page page, List<DiagramEdge> edges, Map<String, NodeBox> boxes,
                                       Canvas canvas, String labelNamePrefix) throws Exception {
        for (DiagramEdge edge : edges) {
            NodeBox from = boxes.get(edge.getFrom());
            NodeBox to = boxes.get(edge.getTo());
            List<Point> points = orthogonalConnectorPoints(from, to);
            drawOrthogonalDirectionalLine(page, points, canvas);
            if (edge.getLabel() != null && !edge.getLabel().trim().isEmpty()) {
                addLineLabel(page, points, edge.getLabel(), CLASS_RELATION_LABEL_MIN_WIDTH,
                        labelNamePrefix, canvas);
            }
        }
    }

    /**
     * 计算两个矩形图元之间不进入图元内部的正交连接点。
     *
     * @param from 起始图元边界
     * @param to 目标图元边界
     * @return 按绘制顺序排列的连接点
     */
    private List<Point> orthogonalConnectorPoints(NodeBox from, NodeBox to) {
        List<Point> points = new ArrayList<>();
        int horizontalDistance = Math.abs(to.centerX() - from.centerX());
        int verticalDistance = Math.abs(to.centerY() - from.centerY());
        if (horizontalDistance >= verticalDistance) {
            boolean leftToRight = from.centerX() <= to.centerX();
            Point start = leftToRight ? new Point(from.right(), from.centerY()) : new Point(from.left(), from.centerY());
            Point end = leftToRight ? new Point(to.left(), to.centerY()) : new Point(to.right(), to.centerY());
            points.add(start);
            if (start.y != end.y) {
                int middleX = (start.x + end.x) / 2;
                points.add(new Point(middleX, start.y));
                points.add(new Point(middleX, end.y));
            }
            points.add(end);
        } else {
            boolean topToBottom = from.centerY() <= to.centerY();
            Point start = topToBottom ? new Point(from.centerX(), from.bottom()) : new Point(from.centerX(), from.top());
            Point end = topToBottom ? new Point(to.centerX(), to.top()) : new Point(to.centerX(), to.bottom());
            points.add(start);
            if (start.x != end.x) {
                int middleY = (start.y + end.y) / 2;
                points.add(new Point(start.x, middleY));
                points.add(new Point(end.x, middleY));
            }
            points.add(end);
        }
        return points;
    }

    /**
     * 使用多条独立且可编辑的线段绘制正交连接线，并在最终线段添加箭头。
     *
     * @param page Visio 页面
     * @param points 连接点集合
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawOrthogonalDirectionalLine(Page page, List<Point> points, Canvas canvas) throws Exception {
        for (int index = 1; index < points.size(); index++) {
            Point fromPoint = points.get(index - 1);
            Point toPoint = points.get(index);
            long lineId = page.drawLine(toInch(fromPoint.x), toY(fromPoint.y, canvas), toInch(toPoint.x),
                    toY(toPoint.y, canvas));
            Shape lineShape = shapeById(page, lineId);
            lineShape.setNameU("Connector_" + lineShape.getID());
        }
        if (points.size() > 1) {
            drawArrowHead(page, points.get(points.size() - 2), points.get(points.size() - 1), canvas);
        }
    }

    /**
     * 为系统 E-R 图计算实体、关系及属性的稳定布局。
     *
     * <p>首个实体作为业务中心，其余实体沿四周排布；关系菱形位于所连接实体之间，
     * 属性椭圆围绕所属实体展开。该布局适合系统分析阶段的概念模型，避免将属性混入
     * 数据库表字段区域。</p>
     *
     * @param definition 系统 E-R 图语义定义
     * @param canvas 画布参数
     * @return 节点标识到边界的映射
     */
    private Map<String, NodeBox> systemErNodeBoxes(DiagramDefinition definition, Canvas canvas) {
        List<DiagramNode> entities = nodesOf(definition, DiagramNodeType.ENTITY);
        Map<String, NodeBox> boxes = new LinkedHashMap<>();
        int[][] entityOffsets = {
                {0, 0}, {270, -156}, {270, 156}, {-270, -156}, {-270, 156}, {0, -246}, {0, 246}
        };
        int centerX = canvas.width / 2;
        int centerY = Math.max(CONTENT_TOP + 180, canvas.height / 2);
        for (int index = 0; index < entities.size(); index++) {
            int[] offset = entityOffsets[Math.min(index, entityOffsets.length - 1)];
            boxes.put(entities.get(index).getId(), centeredBox(centerX + offset[0], centerY + offset[1],
                    SYSTEM_ER_ENTITY_WIDTH, SYSTEM_ER_ENTITY_HEIGHT));
        }
        for (DiagramNode relationship : nodesOf(definition, DiagramNodeType.RELATIONSHIP)) {
            List<NodeBox> relatedEntities = relatedSystemErEntityBoxes(relationship.getId(), definition, boxes);
            NodeBox relationshipBox;
            if (relatedEntities.size() >= 2) {
                NodeBox first = relatedEntities.get(0);
                NodeBox second = relatedEntities.get(1);
                relationshipBox = centeredBox((first.centerX() + second.centerX()) / 2,
                        (first.centerY() + second.centerY()) / 2, SYSTEM_ER_RELATIONSHIP_WIDTH,
                        SYSTEM_ER_RELATIONSHIP_HEIGHT);
            } else if (relatedEntities.size() == 1) {
                NodeBox entity = relatedEntities.get(0);
                relationshipBox = centeredBox(entity.centerX() + 132, entity.centerY() - 76,
                        SYSTEM_ER_RELATIONSHIP_WIDTH, SYSTEM_ER_RELATIONSHIP_HEIGHT);
            } else {
                relationshipBox = centeredBox(centerX, centerY, SYSTEM_ER_RELATIONSHIP_WIDTH,
                        SYSTEM_ER_RELATIONSHIP_HEIGHT);
            }
            boxes.put(relationship.getId(), relationshipBox);
        }
        Map<String, Integer> attributeIndexes = new HashMap<>();
        List<NodeBox> occupiedSystemErBoxes = new ArrayList<>(boxes.values());
        for (DiagramNode attribute : nodesOf(definition, DiagramNodeType.ATTRIBUTE)) {
            String entityId = relatedSystemErEntityId(attribute.getId(), definition);
            NodeBox entity = boxes.get(entityId);
            int index = attributeIndexes.getOrDefault(entityId, 0);
            attributeIndexes.put(entityId, index + 1);
            NodeBox attributeBox = systemErAttributeBox(entity, centerX, centerY, index, occupiedSystemErBoxes);
            boxes.put(attribute.getId(), attributeBox);
            occupiedSystemErBoxes.add(attributeBox);
        }
        return boxes;
    }

    /**
     * 获取与关系节点相连的实体边界。
     *
     * @param relationshipId 关系节点标识
     * @param definition 系统 E-R 图定义
     * @param boxes 已计算的节点边界
     * @return 与关系节点直接相连的实体边界
     */
    private List<NodeBox> relatedSystemErEntityBoxes(String relationshipId, DiagramDefinition definition,
                                                     Map<String, NodeBox> boxes) {
        List<NodeBox> entityBoxes = new ArrayList<>();
        Map<String, DiagramNode> nodes = diagramNodesById(definition);
        for (DiagramEdge edge : definition.getEdges()) {
            String entityId = relationshipId.equals(edge.getFrom()) ? edge.getTo()
                    : relationshipId.equals(edge.getTo()) ? edge.getFrom() : null;
            if (entityId != null && nodes.get(entityId).getType() == DiagramNodeType.ENTITY) {
                entityBoxes.add(boxes.get(entityId));
            }
        }
        return entityBoxes;
    }

    /**
     * 为系统 E-R 属性选择不与实体、关系菱形或既有属性重叠的椭圆边界。
     *
     * @param entity 属性所属实体；未关联实体时为 {@code null}
     * @param canvasCenterX 画布中心横坐标
     * @param canvasCenterY 画布中心纵坐标
     * @param attributeIndex 当前实体的属性序号
     * @param occupiedBoxes 已占用图元边界
     * @return 属性椭圆边界
     */
    private NodeBox systemErAttributeBox(NodeBox entity, int canvasCenterX, int canvasCenterY, int attributeIndex,
                                         List<NodeBox> occupiedBoxes) {
        if (entity == null) {
            return centeredBox(canvasCenterX, canvasCenterY, SYSTEM_ER_ATTRIBUTE_WIDTH, SYSTEM_ER_ATTRIBUTE_HEIGHT);
        }
        int[] angleDegrees = {-90, 180, 135, 90, -135, 0, -45, 45};
        for (int offset = 0; offset < angleDegrees.length; offset++) {
            int angleIndex = (attributeIndex + offset) % angleDegrees.length;
            double angle = Math.toRadians(angleDegrees[angleIndex]);
            NodeBox candidate = centeredBox(entity.centerX() + (int) Math.round(Math.cos(angle) * 138D),
                    entity.centerY() + (int) Math.round(Math.sin(angle) * 100D), SYSTEM_ER_ATTRIBUTE_WIDTH,
                    SYSTEM_ER_ATTRIBUTE_HEIGHT);
            if (!intersectsAny(candidate, occupiedBoxes)) {
                return candidate;
            }
        }
        return centeredBox(entity.centerX() - 138, entity.centerY() + 100 + attributeIndex * 48,
                SYSTEM_ER_ATTRIBUTE_WIDTH, SYSTEM_ER_ATTRIBUTE_HEIGHT);
    }

    /**
     * 判断候选图元是否与任一已布局图元相交。
     *
     * @param candidate 候选图元边界
     * @param occupiedBoxes 已占用图元边界
     * @return 存在相交图元时返回 {@code true}
     */
    private boolean intersectsAny(NodeBox candidate, Iterable<NodeBox> occupiedBoxes) {
        for (NodeBox occupiedBox : occupiedBoxes) {
            if (candidate.intersects(occupiedBox)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查找与属性节点直接相连的系统 E-R 实体标识。
     *
     * @param attributeId 属性节点标识
     * @param definition 系统 E-R 图定义
     * @return 所属实体标识；未关联实体时返回 {@code null}
     */
    private String relatedSystemErEntityId(String attributeId, DiagramDefinition definition) {
        Map<String, DiagramNode> nodes = diagramNodesById(definition);
        for (DiagramEdge edge : definition.getEdges()) {
            String entityId = attributeId.equals(edge.getFrom()) ? edge.getTo()
                    : attributeId.equals(edge.getTo()) ? edge.getFrom() : null;
            if (entityId != null && nodes.get(entityId).getType() == DiagramNodeType.ENTITY) {
                return entityId;
            }
        }
        return null;
    }

    /**
     * 创建节点标识到节点定义的索引。
     *
     * @param definition 图语义定义
     * @return 节点索引
     */
    private Map<String, DiagramNode> diagramNodesById(DiagramDefinition definition) {
        Map<String, DiagramNode> nodes = new HashMap<>();
        for (DiagramNode node : definition.getNodes()) {
            nodes.put(node.getId(), node);
        }
        return nodes;
    }

    /**
     * 绘制系统 E-R 图的无箭头关联线及基数标识。
     *
     * @param page Visio 页面
     * @param edges 系统 E-R 关联边
     * @param boxes 节点边界映射
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderSystemErEdges(Page page, DiagramDefinition definition, Map<String, NodeBox> boxes,
                                     Canvas canvas) throws Exception {
        Map<String, DiagramNode> nodes = diagramNodesById(definition);
        for (DiagramEdge edge : definition.getEdges()) {
            NodeBox from = boxes.get(edge.getFrom());
            NodeBox to = boxes.get(edge.getTo());
            Point fromPoint = systemErEdgePoint(from, nodes.get(edge.getFrom()).getType(), to);
            Point toPoint = systemErEdgePoint(to, nodes.get(edge.getTo()).getType(), from);
            drawPlainLine(page, fromPoint, toPoint, canvas);
            if (edge.getLabel() != null && !edge.getLabel().trim().isEmpty()) {
                addLineLabel(page, List.of(fromPoint, toPoint), edge.getLabel(), 24, "Cardinality_", canvas);
            }
        }
    }

    /**
     * 计算系统 E-R 图元真实轮廓上的关联线锚点。
     *
     * @param source 起始图元边界
     * @param sourceType 起始图元类型
     * @param target 目标图元边界
     * @return 真实轮廓上的连接点
     */
    private Point systemErEdgePoint(NodeBox source, DiagramNodeType sourceType, NodeBox target) {
        if (sourceType == DiagramNodeType.RELATIONSHIP) {
            return diamondEdgePoint(source, target);
        }
        if (sourceType == DiagramNodeType.ATTRIBUTE) {
            return ellipseEdgePoint(source, target);
        }
        return edgePoint(source, target);
    }

    /**
     * 计算菱形轮廓上朝向目标图元的连接点。
     *
     * @param diamond 菱形外接边界
     * @param target 目标图元边界
     * @return 菱形边界连接点
     */
    private Point diamondEdgePoint(NodeBox diamond, NodeBox target) {
        double horizontal = target.centerX() - diamond.centerX();
        double vertical = target.centerY() - diamond.centerY();
        double denominator = Math.abs(horizontal) / (diamond.width / 2D)
                + Math.abs(vertical) / (diamond.height / 2D);
        if (denominator == 0D) {
            return new Point(diamond.centerX(), diamond.centerY());
        }
        return new Point((int) Math.round(diamond.centerX() + horizontal / denominator),
                (int) Math.round(diamond.centerY() + vertical / denominator));
    }

    /**
     * 计算椭圆轮廓上朝向目标图元的连接点。
     *
     * @param ellipse 椭圆外接边界
     * @param target 目标图元边界
     * @return 椭圆边界连接点
     */
    private Point ellipseEdgePoint(NodeBox ellipse, NodeBox target) {
        double horizontal = target.centerX() - ellipse.centerX();
        double vertical = target.centerY() - ellipse.centerY();
        double radiusX = ellipse.width / 2D;
        double radiusY = ellipse.height / 2D;
        double scale = Math.sqrt(horizontal * horizontal / (radiusX * radiusX)
                + vertical * vertical / (radiusY * radiusY));
        if (scale == 0D) {
            return new Point(ellipse.centerX(), ellipse.centerY());
        }
        return new Point((int) Math.round(ellipse.centerX() + horizontal / scale),
                (int) Math.round(ellipse.centerY() + vertical / scale));
    }

    /**
     * 绘制无箭头的可编辑关联线。
     *
     * @param page Visio 页面
     * @param fromPoint 线段起点
     * @param toPoint 线段终点
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawPlainLine(Page page, Point fromPoint, Point toPoint, Canvas canvas) throws Exception {
        long lineId = page.drawLine(toInch(fromPoint.x), toY(fromPoint.y, canvas), toInch(toPoint.x),
                toY(toPoint.y, canvas));
        Shape lineShape = shapeById(page, lineId);
        lineShape.setNameU("Association_" + lineShape.getID());
    }

    /**
     * 以中心点和尺寸创建矩形边界。
     *
     * @param centerX 中心横坐标
     * @param centerY 中心纵坐标
     * @param width 宽度
     * @param height 高度
     * @return 对应节点边界
     */
    private NodeBox centeredBox(int centerX, int centerY, int width, int height) {
        return new NodeBox(centerX - width / 2, centerY - height / 2, width, height);
    }

    /**
     * 渲染 UML 类图的关系线，并将关系标签放在线段中点。
     *
     * @param page Visio 页面
     * @param edges 图关系集合
     * @param boxes 类卡片或部件卡片边界映射
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void renderClassEdges(Page page, List<DiagramEdge> edges, Map<String, NodeBox> boxes, Canvas canvas)
            throws Exception {
        for (DiagramEdge edge : edges) {
            NodeBox from = boxes.get(edge.getFrom());
            NodeBox to = boxes.get(edge.getTo());
            boolean leftToRight = from.centerX() <= to.centerX();
            Point fromPoint = leftToRight ? new Point(from.right(), from.centerY())
                    : new Point(from.left(), from.centerY());
            Point toPoint = leftToRight ? new Point(to.left(), to.centerY())
                    : new Point(to.right(), to.centerY());
            drawDirectionalLine(page, fromPoint, toPoint, canvas);
            if (edge.getLabel() != null && !edge.getLabel().trim().isEmpty()) {
                addLineLabel(page, List.of(fromPoint, toPoint), edge.getLabel(), CLASS_RELATION_LABEL_MIN_WIDTH,
                        "ClassRelationLabel_", canvas);
            }
        }
    }

    /**
     * 判断跨越多个节点的“是”分支是否应从左侧绕行，避免关系线穿过中间节点。
     *
     * @param edge 图关系
     * @param from 起始节点边界
     * @param to 目标节点边界
     * @return 应使用右侧折线路由时返回 {@code true}
     */
    private boolean isSideBranch(DiagramEdge edge, NodeBox from, NodeBox to) {
        return "是".equals(edge.getLabel()) && from.centerX() == to.centerX() && to.top() > from.bottom() + 112;
    }

    /**
     * 使用左侧折线绘制跨层判定分支，并在目标元素左侧中点保留方向箭头。
     *
     * @param page Visio 页面
     * @param edge 图关系
     * @param from 起始节点边界
     * @param to 目标节点边界
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawSideBranch(Page page, DiagramEdge edge, NodeBox from, NodeBox to, Canvas canvas) throws Exception {
        int routeX = PADDING;
        Point start = new Point(from.left(), from.centerY());
        Point upperCorner = new Point(routeX, from.centerY());
        Point lowerCorner = new Point(routeX, to.centerY());
        Point end = new Point(to.left(), to.centerY());
        page.drawLine(toInch(start.x), toY(start.y, canvas), toInch(upperCorner.x), toY(upperCorner.y, canvas));
        page.drawLine(toInch(upperCorner.x), toY(upperCorner.y, canvas), toInch(lowerCorner.x), toY(lowerCorner.y, canvas));
        drawDirectionalLine(page, lowerCorner, end, canvas);
        addLineLabel(page, List.of(start, upperCorner, lowerCorner, end), edge.getLabel(), 36,
                "FlowRelationLabel_", canvas);
    }

    /**
     * 在 Visio 页面中添加可独立编辑的文本 Shape。
     *
     * @param page Visio 页面
     * @param centerX 文本中心横坐标，单位为图布局 point
     * @param topY 文本上边界纵坐标，单位为图布局 point
     * @param width 文本区域宽度，单位为图布局 point
     * @param height 文本区域高度，单位为图布局 point
     * @param value 文本内容
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private Shape addText(Page page, int centerX, int topY, int width, int height, String value, Canvas canvas)
            throws Exception {
        Shape textShape = page.addText(toInch(centerX), toY(topY + height / 2, canvas), toInch(width),
                toInch(height), "");
        textShape.setNameU("Text_" + textShape.getID());
        configureShapeText(textShape, value);
        return textShape;
    }

    /**
     * 在 Visio 页面中添加不遮挡底层连线的透明背景文本 Shape。
     *
     * <p>系统 E-R 图的基数标识需要作为独立 Shape 保存以支持编辑，但其文本框不得填充白色，
     * 否则会在关联线中间形成视觉断点。</p>
     *
     * @param page Visio 页面
     * @param centerX 文本中心横坐标，单位为图布局 point
     * @param topY 文本上边界纵坐标，单位为图布局 point
     * @param width 文本区域宽度，单位为图布局 point
     * @param height 文本区域高度，单位为图布局 point
     * @param value 文本内容
     * @param canvas 画布参数
     * @return 已创建的透明文本 Shape
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private Shape addTransparentText(Page page, int centerX, int topY, int width, int height, String value,
                                     Canvas canvas) throws Exception {
        Shape textShape = addText(page, centerX, topY, width, height, value, canvas);
        textShape.getFill().setFillForegndTrans(new DoubleValue(1D, 0));
        textShape.getFill().setFillBkgndTrans(new DoubleValue(1D, 0));
        return textShape;
    }

    /**
     * 在完整连接路径的几何长度中点添加透明关系标签。
     *
     * <p>直线、正交折线和绕行分支都使用同一算法：先累计全部线段长度，再定位总长度的
     * 二分点。标签中心与该点严格重合，因此文字不会偏向起点或终点，也不会遮断底层连线。</p>
     *
     * @param page Visio 页面
     * @param points 按绘制顺序排列的完整连接路径
     * @param label 关系标签文本
     * @param minimumWidth 标签区域最小宽度，单位为图布局 point
     * @param namePrefix 标签 Shape 的稳定名称前缀
     * @param canvas 画布参数
     * @return 已创建的关系标签 Shape
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private Shape addLineLabel(Page page, List<Point> points, String label, int minimumWidth,
                               String namePrefix, Canvas canvas) throws Exception {
        Point midpoint = pathMidpoint(points);
        int width = Math.max(minimumWidth, label.length() * 12 + 16);
        Shape labelShape = addTransparentText(page, midpoint.x,
                midpoint.y - CLASS_RELATION_LABEL_HEIGHT / 2, width, CLASS_RELATION_LABEL_HEIGHT, label, canvas);
        labelShape.setNameU(namePrefix + labelShape.getID());
        return labelShape;
    }

    /**
     * 计算由多条线段组成的完整路径在总长度上的中点。
     *
     * @param points 按路径顺序排列的坐标，至少包含两个点
     * @return 位于整条路径长度二分位置的坐标
     * @throws IllegalArgumentException 当路径点不足两个时抛出
     */
    private Point pathMidpoint(List<Point> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("A connector path requires at least two points.");
        }
        double totalLength = 0D;
        for (int index = 1; index < points.size(); index++) {
            Point previous = points.get(index - 1);
            Point current = points.get(index);
            totalLength += Math.hypot(current.x - previous.x, current.y - previous.y);
        }
        if (totalLength == 0D) {
            return points.get(0);
        }
        double targetLength = totalLength / 2D;
        double traversedLength = 0D;
        for (int index = 1; index < points.size(); index++) {
            Point previous = points.get(index - 1);
            Point current = points.get(index);
            double segmentLength = Math.hypot(current.x - previous.x, current.y - previous.y);
            if (traversedLength + segmentLength >= targetLength) {
                double ratio = segmentLength == 0D ? 0D : (targetLength - traversedLength) / segmentLength;
                return new Point((int) Math.round(previous.x + (current.x - previous.x) * ratio),
                        (int) Math.round(previous.y + (current.y - previous.y) * ratio));
            }
            traversedLength += segmentLength;
        }
        return points.get(points.size() - 1);
    }

    /**
     * 将居中的小四文本直接写入现有的 Visio Shape。
     *
     * @param shape 承载文本的 Visio Shape
     * @param value 文本内容
     */
    private void configureShapeText(Shape shape, String value) {
        configureTextBlock(shape);
        shape.getText().getValue().clear();
        shape.getParas().clear();
        shape.getChars().clear();
        Para paragraph = new Para();
        paragraph.setIX(0);
        paragraph.setHorzAlign(new HorzAlign(HorzAlignValue.CENTER));
        shape.getParas().add(paragraph);
        Char character = new Char();
        character.setIX(0);
        character.setColor(new ColorValue("#000000", 0));
        character.setSize(new DoubleValue(VISIO_SMALL_FOUR_FONT_SIZE, 0));
        shape.getChars().add(character);
        shape.getText().getValue().add(new Pp(0));
        shape.getText().getValue().add(new Cp(0));
        shape.getText().getValue().add(new Txt(value));
    }

    /**
     * 配置 UML 活动图的 Fork 或 Join 同步条。
     *
     * <p>同步条是并行控制节点，不是业务活动，因此绘制为无文字、白底黑框的细横条。
     * 节点标签写入 {@link Shape#setData1(String)} 保存业务语义，供 VSDX 二次编辑、解析或
     * 追踪使用，但不会显示为同步条上的普通矩形文本。</p>
     *
     * @param parallelBar 同步条 Shape
     * @param node 并行节点定义
     */
    private void configureParallelControlBar(Shape parallelBar, DiagramNode node) {
        parallelBar.getFill().setFillForegnd(new ColorValue("#FFFFFF", 0));
        parallelBar.getFill().setFillBkgnd(new ColorValue("#FFFFFF", 0));
        parallelBar.getFill().setFillPattern(new IntValue(SOLID_FILL_PATTERN, 0));
        parallelBar.getLine().setLineColor(new ColorValue("#000000", 0));
        parallelBar.setData1(node.getLabel());
        parallelBar.setNameU(node.getType() == DiagramNodeType.PARALLEL_SPLIT
                ? "ParallelFork_" + parallelBar.getID() : "ParallelJoin_" + parallelBar.getID());
    }

    /**
     * 配置文本 Shape 的无内边距垂直居中版式。
     *
     * <p>Visio 的默认 TextBlock 上下内边距会吞掉小尺寸文本 Shape 的可用行高，导致文字跨越
     * UML 类图的分隔线。统一移除内边距并采用垂直居中后，文本严格限制在调用方指定的行区域内。</p>
     *
     * @param textShape 待配置的文本 Shape
     */
    private void configureTextBlock(Shape textShape) {
        textShape.getTextBlock().setLeftMargin(new DoubleValue(0D, 0));
        textShape.getTextBlock().setRightMargin(new DoubleValue(0D, 0));
        textShape.getTextBlock().setTopMargin(new DoubleValue(0D, 0));
        textShape.getTextBlock().setBottomMargin(new DoubleValue(0D, 0));
        textShape.getTextBlock().setVerticalAlign(new VerticalAlign(VerticalAlignValue.MIDDLE));
    }

    /**
     * 绘制指向目标节点的可编辑连接线及其箭头头部。
     *
     * <p>Aspose.Diagram 对自由线 {@code EndArrow} 的 VSDX 回写存在兼容性差异，因此使用
     * 主线和两条箭头边组成原生 Visio Shape，确保 Word 预览与 Visio 编辑器均能稳定显示。</p>
     *
     * @param page Visio 页面
     * @param fromPoint 关系线起点
     * @param toPoint 关系线终点，同时是箭头尖端
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawDirectionalLine(Page page, Point fromPoint, Point toPoint, Canvas canvas) throws Exception {
        long lineId = page.drawLine(toInch(fromPoint.x), toY(fromPoint.y, canvas), toInch(toPoint.x),
                toY(toPoint.y, canvas));
        Shape lineShape = shapeById(page, lineId);
        lineShape.setNameU("Connector_" + lineShape.getID());
        drawArrowHead(page, fromPoint, toPoint, canvas);
    }

    /**
     * 使用两条可编辑线段绘制关系线末端的箭头头部。
     *
     * @param page Visio 页面
     * @param fromPoint 关系线起点
     * @param toPoint 箭头尖端
     * @param canvas 画布参数
     * @throws Exception 当创建 Visio Shape 失败时抛出
     */
    private void drawArrowHead(Page page, Point fromPoint, Point toPoint, Canvas canvas) throws Exception {
        double vectorX = toPoint.x - fromPoint.x;
        double vectorY = toPoint.y - fromPoint.y;
        double length = Math.hypot(vectorX, vectorY);
        if (length == 0D) {
            return;
        }
        double backwardX = -vectorX / length;
        double backwardY = -vectorY / length;
        double perpendicularX = -vectorY / length;
        double perpendicularY = vectorX / length;
        Point left = arrowSidePoint(toPoint, backwardX, backwardY, perpendicularX, perpendicularY, 1D);
        Point right = arrowSidePoint(toPoint, backwardX, backwardY, perpendicularX, perpendicularY, -1D);
        nameArrowHead(page, page.drawLine(toInch(toPoint.x), toY(toPoint.y, canvas), toInch(left.x),
                toY(left.y, canvas)));
        nameArrowHead(page, page.drawLine(toInch(toPoint.x), toY(toPoint.y, canvas), toInch(right.x),
                toY(right.y, canvas)));
    }

    /**
     * 计算箭头一侧线段的端点。
     *
     * @param tip 箭头尖端
     * @param backwardX 连接线反向单位向量的横坐标
     * @param backwardY 连接线反向单位向量的纵坐标
     * @param perpendicularX 连接线垂直单位向量的横坐标
     * @param perpendicularY 连接线垂直单位向量的纵坐标
     * @param direction 箭头左侧为 {@code 1}，右侧为 {@code -1}
     * @return 箭头一侧线段的端点
     */
    private Point arrowSidePoint(Point tip, double backwardX, double backwardY, double perpendicularX,
                                 double perpendicularY, double direction) {
        double cosine = Math.cos(ARROW_HEAD_ANGLE);
        double sine = Math.sin(ARROW_HEAD_ANGLE) * direction;
        return new Point((int) Math.round(tip.x + ARROW_HEAD_LENGTH * (backwardX * cosine + perpendicularX * sine)),
                (int) Math.round(tip.y + ARROW_HEAD_LENGTH * (backwardY * cosine + perpendicularY * sine)));
    }

    /**
     * 为箭头头部线段设置稳定名称，便于后续回读与二次编辑。
     *
     * @param page Visio 页面
     * @param shapeId 箭头头部 Shape ID
     */
    private void nameArrowHead(Page page, long shapeId) {
        Shape arrowHead = shapeById(page, shapeId);
        arrowHead.setNameU("ArrowHead_" + arrowHead.getID());
    }

    /**
     * 从页面 Shape 集合中按 Visio 的稳定 Shape ID 查找对象。
     *
     * @param page Visio 页面
     * @param shapeId Visio Shape ID
     * @return 对应的 Shape
     * @throws IllegalArgumentException 当页面中不存在指定 Shape 时抛出
     */
    private Shape shapeById(Page page, long shapeId) {
        for (int index = 0; index < page.getShapes().getCount(); index++) {
            Shape shape = page.getShapes().get(index);
            if (shape.getID() == shapeId) {
                return shape;
            }
        }
        throw new IllegalArgumentException("Visio shape does not exist: " + shapeId);
    }

    /**
     * 计算两个矩形节点之间朝向目标边界的关系线锚点。
     *
     * @param source 起始节点边界
     * @param target 目标节点边界
     * @return 起始节点边界上的连接点
     */
    private Point edgePoint(NodeBox source, NodeBox target) {
        double horizontal = target.centerX() - source.centerX();
        double vertical = target.centerY() - source.centerY();
        double scale = Math.max(Math.abs(horizontal) / (source.width / 2D), Math.abs(vertical) / (source.height / 2D));
        if (scale == 0D) {
            return new Point(source.centerX(), source.centerY());
        }
        return new Point((int) Math.round(source.centerX() + horizontal / scale),
                (int) Math.round(source.centerY() + vertical / scale));
    }

    /**
     * 获取指定类型的节点列表。
     *
     * @param definition 图语义定义
     * @param type 节点类型
     * @return 匹配的节点列表
     */
    private List<DiagramNode> nodesOf(DiagramDefinition definition, DiagramNodeType type) {
        List<DiagramNode> nodes = new ArrayList<>();
        for (DiagramNode node : definition.getNodes()) {
            if (node.getType() == type) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * 获取既非参与者也非标准用例的节点列表。
     *
     * @param definition 图语义定义
     * @return 其他节点列表
     */
    private List<DiagramNode> otherNodes(DiagramDefinition definition) {
        List<DiagramNode> nodes = new ArrayList<>();
        for (DiagramNode node : definition.getNodes()) {
            if (node.getType() != DiagramNodeType.ACTOR && node.getType() != DiagramNodeType.USE_CASE) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * 为 ER 图创建以关系层级为基础的布局方案。
     *
     * <p>每个无向连通分量独立占据一个纵向区域；同一分量内按照有向关系的最长可达路径
     * 分配横向层级。这样主从关系始终从左向右展示，彼此无关联的数据域也不会被强行混排。</p>
     *
     * @param definition ER 图定义
     * @param canvasWidth 画布宽度，单位为图布局 point
     * @return 可复用的 ER 图布局方案
     */
    private ErLayoutPlan erLayoutPlan(DiagramDefinition definition, int canvasWidth) {
        List<ErComponent> components = erComponents(definition);
        int layerCount = 1;
        for (ErComponent component : components) {
            component.setLayers(erLayers(component, definition));
            layerCount = Math.max(layerCount, component.getLayers().size());
        }
        int availableWidth = canvasWidth - PADDING * 2 - (layerCount - 1) * RELATION_LABEL_LAYER_GAP;
        int entityWidth = Math.max(ER_MIN_ENTITY_WIDTH, availableWidth / layerCount);
        Map<String, NodeBox> boxes = new LinkedHashMap<>();
        int componentTop = CONTENT_TOP;
        for (ErComponent component : components) {
            componentTop = layoutErComponent(component, entityWidth, canvasWidth, componentTop, boxes);
        }
        return new ErLayoutPlan(boxes, componentTop - ER_COMPONENT_GAP + PADDING);
    }

    /**
     * 为 UML 类图创建基于关系层级的布局方案。
     *
     * @param definition 类图定义
     * @param canvasWidth 画布宽度，单位为图布局 point
     * @return 可复用的类图布局方案
     */
    private ErLayoutPlan classLayoutPlan(DiagramDefinition definition, int canvasWidth) {
        List<ErComponent> components = erComponents(definition);
        int layerCount = 1;
        for (ErComponent component : components) {
            component.setLayers(erLayers(component, definition));
            layerCount = Math.max(layerCount, component.getLayers().size());
        }
        int availableWidth = canvasWidth - PADDING * 2 - (layerCount - 1) * RELATION_LABEL_LAYER_GAP;
        int classWidth = Math.max(CLASS_MIN_WIDTH, availableWidth / layerCount);
        Map<String, NodeBox> boxes = new LinkedHashMap<>();
        int componentTop = CONTENT_TOP;
        for (ErComponent component : components) {
            componentTop = layoutClassComponent(component, classWidth, canvasWidth, componentTop, boxes);
        }
        return new ErLayoutPlan(boxes, componentTop - ER_COMPONENT_GAP + PADDING);
    }

    /**
     * 为 CSCI 部件图创建基于依赖层级的布局方案。
     *
     * @param definition 部件图语义定义
     * @param canvasWidth 画布宽度，单位为图布局 point
     * @return 可复用的部件图布局方案
     */
    private ErLayoutPlan componentLayoutPlan(DiagramDefinition definition, int canvasWidth) {
        List<ErComponent> components = erComponents(definition);
        int layerCount = 1;
        for (ErComponent component : components) {
            component.setLayers(erLayers(component, definition));
            layerCount = Math.max(layerCount, component.getLayers().size());
        }
        int availableWidth = canvasWidth - PADDING * 2 - (layerCount - 1) * RELATION_LABEL_LAYER_GAP;
        int componentWidth = Math.max(COMPONENT_MIN_WIDTH, availableWidth / layerCount);
        Map<String, NodeBox> boxes = new LinkedHashMap<>();
        int componentTop = CONTENT_TOP;
        for (ErComponent component : components) {
            componentTop = layoutComponentDiagram(component, componentWidth, canvasWidth, componentTop, boxes);
        }
        return new ErLayoutPlan(boxes, componentTop - ER_COMPONENT_GAP + PADDING);
    }

    /**
     * 对一个 ER 连通分量进行逐层、逐行布局。
     *
     * @param component 当前 ER 连通分量
     * @param entityWidth 实体卡片宽度
     * @param canvasWidth 画布宽度
     * @param componentTop 当前分量起始纵坐标
     * @param boxes 待填充的实体边界映射
     * @return 下一个分量的起始纵坐标
     */
    private int layoutErComponent(ErComponent component, int entityWidth, int canvasWidth, int componentTop,
                                  Map<String, NodeBox> boxes) {
        List<List<DiagramNode>> layers = component.getLayers();
        int rowCount = 1;
        for (List<DiagramNode> layer : layers) {
            rowCount = Math.max(rowCount, layer.size());
        }
        int[] rowHeights = new int[rowCount];
        for (List<DiagramNode> layer : layers) {
            for (int index = 0; index < layer.size(); index++) {
                int row = distributeRow(index, layer.size(), rowCount);
                rowHeights[row] = Math.max(rowHeights[row], entityHeight(layer.get(index)));
            }
        }
        int[] rowTops = new int[rowCount];
        int componentHeight = 0;
        for (int row = 0; row < rowCount; row++) {
            rowTops[row] = componentTop + componentHeight;
            componentHeight += rowHeights[row];
            if (row < rowCount - 1) {
                componentHeight += ER_ROW_GAP;
            }
        }
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<DiagramNode> layer = layers.get(layerIndex);
            int componentWidth = layers.size() * entityWidth
                    + (layers.size() - 1) * RELATION_LABEL_LAYER_GAP;
            int x = (canvasWidth - componentWidth) / 2
                    + layerIndex * (entityWidth + RELATION_LABEL_LAYER_GAP);
            for (int index = 0; index < layer.size(); index++) {
                DiagramNode node = layer.get(index);
                int row = distributeRow(index, layer.size(), rowCount);
                int height = entityHeight(node);
                int y = rowTops[row] + (rowHeights[row] - height) / 2;
                boxes.put(node.getId(), new NodeBox(x, y, entityWidth, height));
            }
        }
        return componentTop + componentHeight + ER_COMPONENT_GAP;
    }

    /**
     * 对一个 UML 类图连通分量进行逐层、逐行布局。
     *
     * @param component 当前类图连通分量
     * @param classWidth 类卡片宽度
     * @param canvasWidth 画布宽度
     * @param componentTop 当前分量起始纵坐标
     * @param boxes 待填充的类卡片边界映射
     * @return 下一个分量的起始纵坐标
     */
    private int layoutClassComponent(ErComponent component, int classWidth, int canvasWidth, int componentTop,
                                     Map<String, NodeBox> boxes) {
        List<List<DiagramNode>> layers = component.getLayers();
        int rowCount = 1;
        for (List<DiagramNode> layer : layers) {
            rowCount = Math.max(rowCount, layer.size());
        }
        int[] rowHeights = new int[rowCount];
        for (List<DiagramNode> layer : layers) {
            for (int index = 0; index < layer.size(); index++) {
                int row = distributeRow(index, layer.size(), rowCount);
                rowHeights[row] = Math.max(rowHeights[row], classHeight(layer.get(index)));
            }
        }
        int[] rowTops = new int[rowCount];
        int componentHeight = 0;
        for (int row = 0; row < rowCount; row++) {
            rowTops[row] = componentTop + componentHeight;
            componentHeight += rowHeights[row];
            if (row < rowCount - 1) {
                componentHeight += ER_ROW_GAP;
            }
        }
        int componentWidth = layers.size() * classWidth + (layers.size() - 1) * RELATION_LABEL_LAYER_GAP;
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<DiagramNode> layer = layers.get(layerIndex);
            int x = (canvasWidth - componentWidth) / 2
                    + layerIndex * (classWidth + RELATION_LABEL_LAYER_GAP);
            for (int index = 0; index < layer.size(); index++) {
                DiagramNode node = layer.get(index);
                int row = distributeRow(index, layer.size(), rowCount);
                int height = classHeight(node);
                int y = rowTops[row] + (rowHeights[row] - height) / 2;
                boxes.put(node.getId(), new NodeBox(x, y, classWidth, height));
            }
        }
        return componentTop + componentHeight + ER_COMPONENT_GAP;
    }

    /**
     * 对一个 CSCI 部件连通分量进行逐层、逐行布局。
     *
     * @param component 当前部件连通分量
     * @param componentWidth 部件卡片宽度
     * @param canvasWidth 画布宽度
     * @param componentTop 当前分量起始纵坐标
     * @param boxes 待填充的部件边界映射
     * @return 下一个分量的起始纵坐标
     */
    private int layoutComponentDiagram(ErComponent component, int componentWidth, int canvasWidth, int componentTop,
                                       Map<String, NodeBox> boxes) {
        List<List<DiagramNode>> layers = component.getLayers();
        int rowCount = 1;
        for (List<DiagramNode> layer : layers) {
            rowCount = Math.max(rowCount, layer.size());
        }
        int[] rowHeights = new int[rowCount];
        for (List<DiagramNode> layer : layers) {
            for (int index = 0; index < layer.size(); index++) {
                int row = distributeRow(index, layer.size(), rowCount);
                rowHeights[row] = Math.max(rowHeights[row], componentNodeHeight(layer.get(index)));
            }
        }
        int[] rowTops = new int[rowCount];
        int componentHeight = 0;
        for (int row = 0; row < rowCount; row++) {
            rowTops[row] = componentTop + componentHeight;
            componentHeight += rowHeights[row];
            if (row < rowCount - 1) {
                componentHeight += ER_ROW_GAP;
            }
        }
        int componentLayoutWidth = layers.size() * componentWidth
                + (layers.size() - 1) * RELATION_LABEL_LAYER_GAP;
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<DiagramNode> layer = layers.get(layerIndex);
            int x = (canvasWidth - componentLayoutWidth) / 2
                    + layerIndex * (componentWidth + RELATION_LABEL_LAYER_GAP);
            for (int index = 0; index < layer.size(); index++) {
                DiagramNode node = layer.get(index);
                int row = distributeRow(index, layer.size(), rowCount);
                int height = componentNodeHeight(node);
                int y = rowTops[row] + (rowHeights[row] - height) / 2;
                boxes.put(node.getId(), new NodeBox(x, y, componentWidth, height));
            }
        }
        return componentTop + componentHeight + ER_COMPONENT_GAP;
    }

    /**
     * 将层内节点均匀分布至分量的逻辑行。
     *
     * @param index 节点在层内的索引
     * @param layerSize 当前层节点数量
     * @param rowCount 分量总行数
     * @return 节点所在的逻辑行索引
     */
    private int distributeRow(int index, int layerSize, int rowCount) {
        if (layerSize <= 1) {
            return (rowCount - 1) / 2;
        }
        return index * (rowCount - 1) / (layerSize - 1);
    }

    /**
     * 计算实体卡片高度。
     *
     * @param node 实体节点
     * @return 实体卡片高度，单位为图布局 point
     */
    private int entityHeight(DiagramNode node) {
        return ENTITY_HEADER_HEIGHT + Math.max(1, node.getFields().size()) * ENTITY_FIELD_HEIGHT;
    }

    /**
     * 计算 UML 类卡片高度。
     *
     * @param node 类节点
     * @return 类卡片高度，单位为图布局 point
     */
    private int classHeight(DiagramNode node) {
        return CLASS_HEADER_HEIGHT + Math.max(1, node.getClassAttributes().size()) * CLASS_MEMBER_HEIGHT
                + Math.max(1, node.getClassMethods().size()) * CLASS_MEMBER_HEIGHT;
    }

    /**
     * 计算 CSCI 部件卡片高度。
     *
     * @param node 部件节点
     * @return 部件卡片高度，单位为图布局 point
     */
    private int componentNodeHeight(DiagramNode node) {
        return COMPONENT_HEADER_HEIGHT + Math.max(1, node.getFields().size()) * COMPONENT_DETAIL_HEIGHT;
    }

    /**
     * 按无向关系将 ER 节点拆分为互不相连的业务分量。
     *
     * @param definition ER 图定义
     * @return 保持节点声明顺序的连通分量集合
     */
    private List<ErComponent> erComponents(DiagramDefinition definition) {
        Map<String, Set<String>> neighbors = new LinkedHashMap<>();
        for (DiagramNode node : definition.getNodes()) {
            neighbors.put(node.getId(), new LinkedHashSet<String>());
        }
        for (DiagramEdge edge : definition.getEdges()) {
            neighbors.get(edge.getFrom()).add(edge.getTo());
            neighbors.get(edge.getTo()).add(edge.getFrom());
        }
        List<ErComponent> components = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (DiagramNode node : definition.getNodes()) {
            if (!visited.add(node.getId())) {
                continue;
            }
            Set<String> componentIds = new LinkedHashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(node.getId());
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                componentIds.add(current);
                for (String neighbor : neighbors.get(current)) {
                    if (visited.add(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            List<DiagramNode> componentNodes = new ArrayList<>();
            for (DiagramNode candidate : definition.getNodes()) {
                if (componentIds.contains(candidate.getId())) {
                    componentNodes.add(candidate);
                }
            }
            components.add(new ErComponent(componentNodes, componentIds));
        }
        return components;
    }

    /**
     * 基于有向关系为一个连通分量划分从左到右的实体层级。
     *
     * <p>无入边实体作为起点。若数据包含环或缺失关系根，则使用尚未分层的首个实体作为
     * 新起点，以保证任意合法定义均能生成稳定布局。</p>
     *
     * @param component ER 连通分量
     * @param definition ER 图定义
     * @return 从左到右排列的实体层集合
     */
    private List<List<DiagramNode>> erLayers(ErComponent component, DiagramDefinition definition) {
        Map<String, List<String>> children = new HashMap<>();
        Map<String, Integer> incomingCounts = new HashMap<>();
        for (DiagramNode node : component.getNodes()) {
            children.put(node.getId(), new ArrayList<String>());
            incomingCounts.put(node.getId(), 0);
        }
        for (DiagramEdge edge : definition.getEdges()) {
            if (component.contains(edge.getFrom()) && component.contains(edge.getTo())) {
                children.get(edge.getFrom()).add(edge.getTo());
                incomingCounts.put(edge.getTo(), incomingCounts.get(edge.getTo()) + 1);
            }
        }
        Map<String, Integer> levels = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        for (DiagramNode node : component.getNodes()) {
            if (incomingCounts.get(node.getId()) == 0) {
                levels.put(node.getId(), 0);
                queue.addLast(node.getId());
            }
        }
        assignErLevels(component, children, levels, queue);
        for (DiagramNode node : component.getNodes()) {
            if (!levels.containsKey(node.getId())) {
                levels.put(node.getId(), 0);
                queue.addLast(node.getId());
                assignErLevels(component, children, levels, queue);
            }
        }
        int maxLevel = 0;
        for (Integer level : levels.values()) {
            maxLevel = Math.max(maxLevel, level);
        }
        List<List<DiagramNode>> layers = new ArrayList<>();
        for (int index = 0; index <= maxLevel; index++) {
            layers.add(new ArrayList<DiagramNode>());
        }
        for (DiagramNode node : component.getNodes()) {
            layers.get(levels.get(node.getId())).add(node);
        }
        return layers;
    }

    /**
     * 使用广度优先遍历为可达 ER 实体分配层级。
     *
     * @param component 当前 ER 连通分量
     * @param children 节点到子节点的映射
     * @param levels 已分配层级映射
     * @param queue 待处理节点队列
     */
    private void assignErLevels(ErComponent component, Map<String, List<String>> children,
                                Map<String, Integer> levels, Deque<String> queue) {
        while (!queue.isEmpty()) {
            String parent = queue.removeFirst();
            int childLevel = levels.get(parent) + 1;
            for (String child : children.get(parent)) {
                if (component.contains(child) && !levels.containsKey(child)) {
                    levels.put(child, childLevel);
                    queue.addLast(child);
                }
            }
        }
    }

    /**
     * 创建流程节点的边界。
     *
     * @param node 流程节点
     * @param centerX 节点中心横坐标
     * @param centerY 节点中心纵坐标
     * @return 节点边界
     */
    private NodeBox flowNodeBox(DiagramNode node, int centerX, int centerY) {
        int width = node.getType() == DiagramNodeType.DECISION ? 172
                : node.getType() == DiagramNodeType.START || node.getType() == DiagramNodeType.END ? 156 : 236;
        int height = node.getType() == DiagramNodeType.DECISION ? 80
                : isParallelNode(node) ? PARALLEL_CONTROL_BAR_HEIGHT : 56;
        return new NodeBox(centerX - width / 2, centerY - height / 2, width, height);
    }

    /**
     * 计算流程节点边界，并把同一并行分支后的任务节点布局在相同行。
     *
     * <p>默认节点采用纵向主线布局。当遇到并行分支节点时，其直接后继会横向展开，
     * 随后的并行汇合节点固定回到主线中心，从而使 VSDX 与 PNG 预览共享同一套布局规则。</p>
     *
     * @param definition 流程图定义
     * @param canvas 画布参数
     * @return 按节点标识索引的布局边界
     */
    private Map<String, NodeBox> flowNodeBoxes(DiagramDefinition definition, Canvas canvas) {
        Map<String, NodeBox> boxes = new HashMap<>();
        int centerX = canvas.width / 2;
        int centerY = CONTENT_TOP + 36;
        Set<String> positionedNodes = new HashSet<>();
        for (int index = 0; index < definition.getNodes().size(); index++) {
            DiagramNode node = definition.getNodes().get(index);
            if (positionedNodes.contains(node.getId())) {
                continue;
            }
            boxes.put(node.getId(), flowNodeBox(node, centerX, centerY));
            positionedNodes.add(node.getId());
            centerY += FLOW_ROW_GAP;
            if (node.getType() != DiagramNodeType.PARALLEL_SPLIT) {
                continue;
            }

            DiagramNode split = node;
            List<DiagramEdge> branchEdges = outgoingEdges(definition, split.getId());
            if (branchEdges.isEmpty()) {
                continue;
            }
            int branchCenterY = centerY;
            for (int branchIndex = 0; branchIndex < branchEdges.size(); branchIndex++) {
                DiagramEdge branch = branchEdges.get(branchIndex);
                DiagramNode branchNode = nodeById(definition, branch.getTo());
                int branchCenterX = centerX + (branchIndex * 260) - ((branchEdges.size() - 1) * 130);
                boxes.put(branch.getTo(), flowNodeBox(branchNode, branchCenterX, branchCenterY));
                positionedNodes.add(branchNode.getId());
            }
            centerY += FLOW_ROW_GAP;
            DiagramNode join = nextParallelJoin(definition, index + 1);
            if (join != null) {
                boxes.put(join.getId(), flowNodeBox(join, centerX, centerY));
                positionedNodes.add(join.getId());
                centerY += FLOW_ROW_GAP;
            }
        }
        return boxes;
    }

    /**
     * 获取从指定节点发出的关系边。
     *
     * @param definition 图语义定义
     * @param nodeId 节点标识
     * @return 按定义顺序排列的出边
     */
    private List<DiagramEdge> outgoingEdges(DiagramDefinition definition, String nodeId) {
        List<DiagramEdge> edges = new ArrayList<>();
        for (DiagramEdge edge : definition.getEdges()) {
            if (nodeId.equals(edge.getFrom())) {
                edges.add(edge);
            }
        }
        return edges;
    }

    /**
     * 在指定节点索引之后查找最近的并行汇合节点。
     *
     * @param definition 图语义定义
     * @param startIndex 起始搜索索引
     * @return 最近的并行汇合节点；不存在时返回 {@code null}
     */
    private DiagramNode nextParallelJoin(DiagramDefinition definition, int startIndex) {
        for (int index = startIndex; index < definition.getNodes().size(); index++) {
            DiagramNode node = definition.getNodes().get(index);
            if (node.getType() == DiagramNodeType.PARALLEL_JOIN) {
                return node;
            }
        }
        return null;
    }

    /**
     * 根据节点标识查找图节点。
     *
     * @param definition 图语义定义
     * @param nodeId 节点标识
     * @return 匹配的图节点
     * @throws IllegalArgumentException 当节点不存在时抛出
     */
    private DiagramNode nodeById(DiagramDefinition definition, String nodeId) {
        for (DiagramNode node : definition.getNodes()) {
            if (node.getId().equals(nodeId)) {
                return node;
            }
        }
        throw new IllegalArgumentException("unknown diagram node: " + nodeId);
    }

    /**
     * 判断节点是否表示并行分支或并行汇合的横杠。
     *
     * @param node 图节点
     * @return 并行控制节点时返回 {@code true}
     */
    private boolean isParallelNode(DiagramNode node) {
        return node.getType() == DiagramNodeType.PARALLEL_SPLIT
                || node.getType() == DiagramNodeType.PARALLEL_JOIN;
    }

    /**
     * 从用例图标题中提取模块边界名称。
     *
     * @param title 用例图标题
     * @return 模块边界显示名称
     */
    private String moduleName(String title) {
        if (title.endsWith("功能用例图")) {
            return title.substring(0, title.length() - "功能用例图".length());
        }
        if (title.endsWith("用例图")) {
            return title.substring(0, title.length() - "用例图".length());
        }
        return title;
    }

    /**
     * 计算居中的用例图整体布局。
     *
     * <p>参与者、参与者名称区域、留白和模块边界作为一个组合整体居中，避免仅把模块矩形
     * 居中后导致参与者全部偏向页面左侧。</p>
     *
     * @param canvas 画布参数
     * @return 用例图整体布局
     */
    private UseCaseLayout useCaseLayout(Canvas canvas) {
        int moduleWidth = Math.min(360, canvas.width - PADDING * 2 - USE_CASE_ACTOR_LABEL_WIDTH
                - USE_CASE_ACTOR_MODULE_GAP);
        int groupWidth = USE_CASE_ACTOR_LABEL_WIDTH + USE_CASE_ACTOR_MODULE_GAP + moduleWidth;
        int groupLeft = (canvas.width - groupWidth) / 2;
        int actorCenterX = groupLeft + USE_CASE_ACTOR_LABEL_WIDTH / 2;
        int moduleCenterX = groupLeft + USE_CASE_ACTOR_LABEL_WIDTH + USE_CASE_ACTOR_MODULE_GAP + moduleWidth / 2;
        return new UseCaseLayout(actorCenterX, moduleCenterX, moduleWidth);
    }

    /**
     * 将图布局 point 坐标转换为 Visio 英寸坐标。
     *
     * @param points 图布局 point 坐标
     * @return Visio 英寸坐标
     */
    private double toInch(int points) {
        return points / POINTS_PER_INCH;
    }

    /**
     * 将图布局中向下增长的纵坐标转换为 Visio 向上增长的纵坐标。
     *
     * @param y 图布局纵坐标
     * @param canvas 画布参数
     * @return Visio 英寸纵坐标
     */
    private double toY(int y, Canvas canvas) {
        return (canvas.height - y) / POINTS_PER_INCH;
    }

    /**
     * 创建输出文件的父目录。
     *
     * @param outputPath 输出路径
     * @throws IOException 当父目录无法创建时抛出
     */
    private void ensureParentDirectory(Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * ER 图布局计算结果。
     */
    private static final class ErLayoutPlan {

        /** 由实体节点标识索引的卡片边界。 */
        private final Map<String, NodeBox> boxes;
        /** 使所有实体完整落入页面所需的最小画布高度。 */
        private final int requiredHeight;

        /**
         * 创建 ER 图布局结果。
         *
         * @param boxes 实体边界映射
         * @param requiredHeight 需要的最小画布高度
         */
        private ErLayoutPlan(Map<String, NodeBox> boxes, int requiredHeight) {
            this.boxes = boxes;
            this.requiredHeight = requiredHeight;
        }

        /**
         * 获取实体边界映射。
         *
         * @return 实体边界映射
         */
        private Map<String, NodeBox> getBoxes() {
            return boxes;
        }

        /**
         * 获取布局所需的最小画布高度。
         *
         * @return 最小画布高度
         */
        private int getRequiredHeight() {
            return requiredHeight;
        }
    }

    /**
     * ER 图中通过关系线互相可达的一组实体。
     */
    private static final class ErComponent {

        /** 保持定义顺序的实体节点集合。 */
        private final List<DiagramNode> nodes;
        /** 当前分量包含的实体标识集合。 */
        private final Set<String> nodeIds;
        /** 按依赖方向划分的横向实体层。 */
        private List<List<DiagramNode>> layers;

        /**
         * 创建 ER 连通分量。
         *
         * @param nodes 分量内实体节点
         * @param nodeIds 分量内实体标识
         */
        private ErComponent(List<DiagramNode> nodes, Set<String> nodeIds) {
            this.nodes = nodes;
            this.nodeIds = nodeIds;
            this.layers = new ArrayList<>();
        }

        /**
         * 获取分量内实体节点。
         *
         * @return 实体节点集合
         */
        private List<DiagramNode> getNodes() {
            return nodes;
        }

        /**
         * 判断实体标识是否属于当前分量。
         *
         * @param nodeId 实体标识
         * @return 属于当前分量时返回 {@code true}
         */
        private boolean contains(String nodeId) {
            return nodeIds.contains(nodeId);
        }

        /**
         * 设置分量内按关系层级分组的实体。
         *
         * @param layers 从左到右的实体层
         */
        private void setLayers(List<List<DiagramNode>> layers) {
            this.layers = layers;
        }

        /**
         * 获取分量内按关系层级分组的实体。
         *
         * @return 从左到右的实体层
         */
        private List<List<DiagramNode>> getLayers() {
            return layers;
        }
    }

    /**
     * 用例图中参与者与模块边界的组合布局。
     */
    private static final class UseCaseLayout {

        /** 参与者中心横坐标。 */
        private final int actorCenterX;
        /** 模块边界中心横坐标。 */
        private final int moduleCenterX;
        /** 模块边界宽度。 */
        private final int moduleWidth;

        /**
         * 创建用例图整体布局。
         *
         * @param actorCenterX 参与者中心横坐标
         * @param moduleCenterX 模块边界中心横坐标
         * @param moduleWidth 模块边界宽度
         */
        private UseCaseLayout(int actorCenterX, int moduleCenterX, int moduleWidth) {
            this.actorCenterX = actorCenterX;
            this.moduleCenterX = moduleCenterX;
            this.moduleWidth = moduleWidth;
        }

        /**
         * 获取参与者中心横坐标。
         *
         * @return 参与者中心横坐标
         */
        private int getActorCenterX() {
            return actorCenterX;
        }

        /**
         * 获取模块边界中心横坐标。
         *
         * @return 模块边界中心横坐标
         */
        private int getModuleCenterX() {
            return moduleCenterX;
        }

        /**
         * 获取模块边界宽度。
         *
         * @return 模块边界宽度
         */
        private int getModuleWidth() {
            return moduleWidth;
        }
    }

    /**
     * 总体功能逻辑图中已校验的三层树结构。
     */
    private static final class FunctionStructure {

        /** 唯一系统根节点。 */
        private final DiagramNode system;
        /** 按声明顺序排列的一级功能模块。 */
        private final List<DiagramNode> modules;
        /** 由功能模块标识索引的末级功能项。 */
        private final Map<String, List<DiagramNode>> itemsByModule;

        /**
         * 创建总体功能逻辑树结构。
         *
         * @param system 系统根节点
         * @param modules 一级功能模块
         * @param itemsByModule 各功能模块包含的末级功能项
         */
        private FunctionStructure(DiagramNode system, List<DiagramNode> modules,
                                  Map<String, List<DiagramNode>> itemsByModule) {
            this.system = system;
            this.modules = modules;
            this.itemsByModule = itemsByModule;
        }

        /**
         * 获取系统根节点。
         *
         * @return 系统根节点
         */
        private DiagramNode getSystem() {
            return system;
        }

        /**
         * 获取一级功能模块。
         *
         * @return 一级功能模块列表
         */
        private List<DiagramNode> getModules() {
            return modules;
        }

        /**
         * 获取指定功能模块的末级功能项。
         *
         * @param moduleId 功能模块标识
         * @return 末级功能项列表
         */
        private List<DiagramNode> getItems(String moduleId) {
            return itemsByModule.get(moduleId);
        }
    }

    /**
     * 总体功能逻辑图的节点边界与分组布局结果。
     */
    private static final class FunctionLogicPlan {

        /** 所有功能节点的边界索引。 */
        private final Map<String, NodeBox> boxes;
        /** 按声明顺序排列的一级功能模块。 */
        private final List<DiagramNode> modules;
        /** 系统根节点边界。 */
        private final NodeBox systemBox;
        /** 按横向顺序排列的一级功能模块边界。 */
        private final List<NodeBox> moduleBoxes;
        /** 由功能模块标识索引的末级功能项边界。 */
        private final Map<String, List<NodeBox>> itemBoxesByModule;

        /**
         * 创建总体功能逻辑图布局结果。
         *
         * @param boxes 所有节点边界
         * @param modules 一级功能模块
         * @param systemBox 系统根节点边界
         * @param moduleBoxes 一级功能模块边界
         * @param itemBoxesByModule 各模块的末级功能项边界
         */
        private FunctionLogicPlan(Map<String, NodeBox> boxes, List<DiagramNode> modules, NodeBox systemBox,
                                  List<NodeBox> moduleBoxes, Map<String, List<NodeBox>> itemBoxesByModule) {
            this.boxes = boxes;
            this.modules = modules;
            this.systemBox = systemBox;
            this.moduleBoxes = moduleBoxes;
            this.itemBoxesByModule = itemBoxesByModule;
        }

        /** @return 所有节点边界索引 */
        private Map<String, NodeBox> getBoxes() {
            return boxes;
        }

        /** @return 一级功能模块列表 */
        private List<DiagramNode> getModules() {
            return modules;
        }

        /** @return 系统根节点边界 */
        private NodeBox getSystemBox() {
            return systemBox;
        }

        /** @return 一级功能模块边界 */
        private List<NodeBox> getModuleBoxes() {
            return moduleBoxes;
        }

        /**
         * 获取指定一级功能模块下的末级功能项边界。
         *
         * @param moduleId 功能模块标识
         * @return 末级功能项边界
         */
        private List<NodeBox> getItemBoxes(String moduleId) {
            return itemBoxesByModule.get(moduleId);
        }
    }

    /**
     * 图布局 point 画布参数。
     */
    private static final class Canvas {

        /** 画布宽度。 */
        private final int width;
        /** 画布高度。 */
        private final int height;

        /**
         * 创建画布参数。
         *
         * @param width 画布宽度
         * @param height 画布高度
         */
        private Canvas(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /**
     * 节点在画布上的矩形边界。
     */
    private static final class NodeBox {

        /** 左上角横坐标。 */
        private final int x;
        /** 左上角纵坐标。 */
        private final int y;
        /** 节点宽度。 */
        private final int width;
        /** 节点高度。 */
        private final int height;

        /**
         * 创建节点边界。
         *
         * @param x 左上角横坐标
         * @param y 左上角纵坐标
         * @param width 节点宽度
         * @param height 节点高度
         */
        private NodeBox(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        /**
         * 获取左边界横坐标。
         *
         * @return 左边界横坐标
         */
        private int left() {
            return x;
        }

        /**
         * 获取右边界横坐标。
         *
         * @return 右边界横坐标
         */
        private int right() {
            return x + width;
        }

        /**
         * 获取上边界纵坐标。
         *
         * @return 上边界纵坐标
         */
        private int top() {
            return y;
        }

        /**
         * 获取下边界纵坐标。
         *
         * @return 下边界纵坐标
         */
        private int bottom() {
            return y + height;
        }

        /**
         * 获取中心横坐标。
         *
         * @return 中心横坐标
         */
        private int centerX() {
            return x + width / 2;
        }

        /**
         * 获取中心纵坐标。
         *
         * @return 中心纵坐标
         */
        private int centerY() {
            return y + height / 2;
        }

        /**
         * 判断当前矩形边界是否与另一边界相交。
         *
         * @param other 待比较边界
         * @return 两个边界存在交集时返回 {@code true}
         */
        private boolean intersects(NodeBox other) {
            return left() < other.right() && right() > other.left()
                    && top() < other.bottom() && bottom() > other.top();
        }
    }

    /**
     * 二维连接点。
     */
    private static final class Point {

        /** 横坐标。 */
        private final int x;
        /** 纵坐标。 */
        private final int y;

        /**
         * 创建二维连接点。
         *
         * @param x 横坐标
         * @param y 纵坐标
         */
        private Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
