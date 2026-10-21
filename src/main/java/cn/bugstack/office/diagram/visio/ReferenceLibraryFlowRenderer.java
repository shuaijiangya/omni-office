package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramType;
import com.aspose.diagram.Char;
import com.aspose.diagram.ArrowSize;
import com.aspose.diagram.ArrowSizeValue;
import com.aspose.diagram.ColorValue;
import com.aspose.diagram.Connect;
import com.aspose.diagram.Connection;
import com.aspose.diagram.Cp;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.DoubleValue;
import com.aspose.diagram.HorzAlign;
import com.aspose.diagram.HorzAlignValue;
import com.aspose.diagram.IntValue;
import com.aspose.diagram.LinePattern;
import com.aspose.diagram.LineTo;
import com.aspose.diagram.Page;
import com.aspose.diagram.Para;
import com.aspose.diagram.Pp;
import com.aspose.diagram.SaveFileFormat;
import com.aspose.diagram.Shape;
import com.aspose.diagram.StrValue;
import com.aspose.diagram.Text;
import com.aspose.diagram.Txt;
import com.aspose.diagram.VerticalAlign;
import com.aspose.diagram.VerticalAlignValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据固定的“菜单栏→库管理→数据项→增删改查→结束”流程图模板生成 Visio。
 *
 * <p>复用业务 JSON 中的功能名及任意数量的数据名；其他文字、节点、连线、白底黑线样式固定。
 * 所有图形与文字在 VSDX 中仍是可编辑对象。</p>
 */
public final class ReferenceLibraryFlowRenderer {

    /** 画布宽度，单位为绘图点。 */
    private static final int WIDTH = 1200;
    /** 相邻数据节点之间的水平间距，单位为绘图点。 */
    private static final int DATA_GAP = 110;
    /** 画布点到 Visio 英寸的换算值。 */
    private static final double POINTS_PER_INCH = 72D;
    /** 所有轮廓与连接线的统一宽度。 */
    private static final int STROKE_WIDTH = 3;
    /** 节点文字默认字体。 */
    private static final String FONT_FAMILY = "黑体";
    /** 节点文字大小，单位为磅。 */
    private static final int FONT_SIZE = 12;
    /** JSON 解析器。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 将与数据流图相同的简化 JSON 绘制为固定库管理流程图。
     *
     * @param inputJson 包含 {@code function} 和至少一个 {@code inputs} 的 JSON 文件
     * @param vsdxPath Visio 输出路径
     * @return 可编辑 Visio 与预览 PNG
     * @throws Exception 输入不合法或文件无法生成时抛出
     */
    public VisioDiagramArtifact render(Path inputJson, Path vsdxPath) throws Exception {
        JsonNode input = mapper.readTree(inputJson.toFile());
        String[] values = inputs(input);
        Plan plan = plan(values);
        Path absolute = vsdxPath.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        String name = absolute.getFileName().toString();
        String stem = name.endsWith(".vsdx") ? name.substring(0, name.length() - 5) : name;
        Path previewPath = absolute.resolveSibling(stem + ".preview.png");
        Diagram diagram = new Diagram();
        try {
            Page page = diagram.getPages().getPage(0);
            page.getPageSheet().getPageProps().getPageWidth().setValue(plan.width / POINTS_PER_INCH);
            page.getPageSheet().getPageProps().getPageHeight().setValue(plan.height / POINTS_PER_INCH);
            // 页面本身就是白色；整页白色 Shape 会被 Visio/Word 当作可选择对象。
            Map<String, Shape> nodes = new HashMap<>();
            for (Box box : plan.boxes) {
                if ("operations".equals(box.id)) {
                    Shape outer = rectangle(page, box.x, box.y, box.width, box.height, plan.height);
                    outer.getFill().setFillForegnd(new ColorValue("#FFFFFF", 0));
                    outer.getLine().setLineColor(new ColorValue("#000000", 0));
                    outer.getLine().setLineWeight(new DoubleValue(STROKE_WIDTH / POINTS_PER_INCH, 0));
                    outer.setNameU("LibraryBox_operations");
                    nodes.put(box.id, outer);
                    for (int index = 0; index < 4; index++) {
                        Box cell = new Box("operation" + index, box.x + index * 65,
                                box.y, 65, box.height, false);
                        Shape label = drawVisioBox(page, cell, plan.height,
                                "增删改查".substring(index, index + 1));
                        // 相邻完整矩形会把每条分隔边绘制两次；单元格仅承载可编辑文字。
                        label.getLine().setLinePattern(new LinePattern(0));
                        label.getFill().setFillPattern(new IntValue(0, 0));
                    }
                } else {
                    nodes.put(box.id, drawVisioBox(page, box, plan.height, labelText(plan, box.id)));
                }
            }
            for (PathLine path : plan.lines) {
                Shape connector = drawVisioPath(page, path, plan.height);
                if ("MenuToFunction".equals(path.name)) {
                    connectEndpoint(page, connector, nodes.get("menu"), path.points[0], true, false);
                    connectEndpoint(page, connector, nodes.get("function"), path.points[2], false, true);
                } else if (path.name.startsWith("FunctionToData")) {
                    String index = path.name.substring("FunctionToData".length());
                    connectEndpoint(page, connector, nodes.get("function"), path.points[0], true, false);
                    connectEndpoint(page, connector, nodes.get("data" + index),
                            path.points[path.points.length - 2], false, true);
                } else if (path.name.matches("Data[0-9]+ToOperations")) {
                    String index = path.name.substring("Data".length(), path.name.indexOf("ToOperations"));
                    connectEndpoint(page, connector, nodes.get("data" + index), path.points[0], true, false);
                    connectEndpoint(page, connector, nodes.get("operations"),
                            path.points[path.points.length - 2], false, true);
                } else if ("OperationsToEnd".equals(path.name)) {
                    connectEndpoint(page, connector, nodes.get("operations"), path.points[0], true, false);
                    connectEndpoint(page, connector, nodes.get("end"),
                            path.points[path.points.length - 2], false, true);
                }
            }
            diagram.save(absolute.toString(), SaveFileFormat.VSDX);
        } finally {
            diagram.dispose();
        }
        preview(plan, previewPath);
        return new VisioDiagramArtifact(DiagramType.FLOW, absolute, previewPath);
    }

    /** 校验仅包含功能名和至少一份数据名的业务输入。 */
    private String[] inputs(JsonNode input) {
        if (input == null || !input.isObject() || input.size() != (input.has("persistenceLabel") ? 3 : 2)
                || !input.has("function") || !input.has("inputs")) {
            throw new IllegalArgumentException("library-flow JSON only accepts function, inputs and persistenceLabel");
        }
        if (input.has("persistenceLabel") && (!input.get("persistenceLabel").isTextual()
                || input.get("persistenceLabel").asText().length() > 40)) {
            throw new IllegalArgumentException("persistenceLabel must be text with at most 40 characters");
        }
        JsonNode names = input.get("inputs");
        if (names == null || !names.isArray() || names.isEmpty()) {
            throw new IllegalArgumentException("library-flow requires at least one input name");
        }
        String[] result = new String[names.size() + 1];
        result[0] = label(input.get("function"), "function");
        for (int index = 0; index < names.size(); index++) result[index + 1] = label(names.get(index), "input");
        BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D measure = measureImage.createGraphics();
        try {
            FontMetrics metrics = measure.getFontMetrics(new Font(FONT_FAMILY, Font.PLAIN, FONT_SIZE));
            if (metrics.stringWidth(result[0]) > 230) {
                throw new IllegalArgumentException("function name exceeds fixed box width");
            }
            for (int index = 1; index < result.length; index++) {
                if (metrics.stringWidth(result[index]) > 230) {
                    throw new IllegalArgumentException("input name exceeds fixed box width");
                }
            }
        } finally {
            measure.dispose();
        }
        return result;
    }

    /** 读取非空且不会占用过多版面的名称。 */
    private String label(JsonNode value, String field) {
        if (value == null || !value.isTextual() || value.asText().isBlank()
                || value.asText().length() > 40) {
            throw new IllegalArgumentException("invalid library-flow " + field + " name");
        }
        return value.asText().trim();
    }

    /** 根据输入数量构建单排水平布局，VSDX 和 PNG 共用同一计划。 */
    private Plan plan(String[] values) {
        int count = values.length - 1;
        int groupWidth = Math.toIntExact(count * 260L + (count - 1L) * DATA_GAP);
        int width = Math.toIntExact(Math.max(WIDTH, groupWidth + 130L));
        int operationsTop = 620;
        int endTop = 1030;
        Plan plan = new Plan(width, 1150);
        int middle = width / 2;
        int firstCenter = (width - groupWidth) / 2 + 130;
        plan.boxes.add(new Box("menu", middle - 100, 55, 200, 80, true));
        plan.boxes.add(new Box("function", middle - 130, 200, 260, 90, false));
        plan.boxes.add(new Box("operations", middle - 130, operationsTop, 260, 310, false));
        plan.boxes.add(new Box("end", middle - 100, endTop, 200, 80, true));
        plan.labels.add(new Label("menu", "菜单栏", middle, 95));
        plan.labels.add(new Label("function", values[0], middle, 245));
        for (int index = 0; index < 4; index++) {
            plan.labels.add(new Label("operation" + index, "增删改查".substring(index, index + 1),
                    middle - 98 + index * 65, operationsTop + 155));
        }
        plan.labels.add(new Label("end", "结束", middle, endTop + 40));
        plan.lines.add(new PathLine("MenuToFunction", true, middle, 135, middle, 196));
        for (int index = 0; index < count; index++) {
            String id = "data" + (index + 1);
            int x = firstCenter + index * (260 + DATA_GAP);
            int portX = middle - 130 + (index + 1) * 260 / (count + 1);
            int maxDepth = (count - 1) / 2;
            int depth = Math.min(index, count - 1 - index);
            int offset = maxDepth == 0 ? 0 : depth * 45 / maxDepth;
            plan.boxes.add(new Box(id, x - 130, 405, 260, 90, false));
            plan.labels.add(new Label(id, values[index + 1], x, 450));
            // 每对节点有独立的直角路线。不同层使用不同横向走线高度，避免共用母线。
            plan.lines.add(orthogonalPath("FunctionToData" + (index + 1),
                    portX, 290, x, 401, 320 + offset));
            plan.lines.add(orthogonalPath("Data" + (index + 1) + "ToOperations",
                    x, 495, portX, operationsTop - 4, 560 - offset));
        }
        // 进出连线均沿操作区和结束节点的中心轴，箭头准确指向节点中央。
        int operationPortX = middle;
        for (int index = 1; index <= 3; index++) {
            int dividerX = middle - 130 + 65 * index;
            plan.lines.add(new PathLine("OperationDivider" + index, false,
                    dividerX, operationsTop, dividerX, operationsTop + 310));
        }
        plan.lines.add(new PathLine("OperationsToEnd", true,
                operationPortX, operationsTop + 310, operationPortX, endTop - 4));
        return plan;
    }

    /** 一根 Visio 连接线以垂直—水平—垂直的折线路由连接两个节点。 */
    private PathLine orthogonalPath(String name, int fromX, int fromY,
                                    int toX, int toY, int horizontalY) {
        if (fromX == toX) return new PathLine(name, true, fromX, fromY, toX, toY);
        return new PathLine(name, true, fromX, fromY, fromX, horizontalY,
                toX, horizontalY, toX, toY);
    }

    /** 返回指定节点的文字，避免创建与节点分离的文本框。 */
    private String labelText(Plan plan, String id) {
        for (Label label : plan.labels) {
            if (id.equals(label.id)) return label.text;
        }
        throw new IllegalStateException("missing library-flow label: " + id);
    }

    /** 每对节点只创建一根原生一维 Visio 连接线，拐角是同一图形内的几何点。 */
    private Shape drawVisioPath(Page page, PathLine path, int height) throws Exception {
        int last = path.points.length - 2;
        double beginX = path.points[0] / POINTS_PER_INCH;
        double beginY = (height - path.points[1]) / POINTS_PER_INCH;
        double endX = path.points[last] / POINTS_PER_INCH;
        double endY = (height - path.points[last + 1]) / POINTS_PER_INCH;
        Shape line = shape(page, page.drawLine(beginX, beginY, endX, endY));
        line.getLine().setLineColor(new ColorValue("#000000", 0));
        line.getLine().setLinePattern(new LinePattern(1));
        line.getLine().setLineWeight(new DoubleValue(STROKE_WIDTH / POINTS_PER_INCH, 0));
        if (path.arrow) {
            line.getLine().setEndArrow(new IntValue(4, 0));
            line.getLine().setEndArrowSize(new ArrowSize(ArrowSizeValue.LARGE));
        }
        // 一维线的 Geometry 使用随 Begin/End 旋转的局部坐标。将每个折点投影到
        // 该坐标系，Visio 中选中时才会是“一根连接线”，而非几条独立的直线。
        if (path.points.length > 4) {
            double dx = endX - beginX;
            double dy = endY - beginY;
            double distance = Math.hypot(dx, dy);
            double cos = dx / distance;
            double sin = dy / distance;
            LineTo originalEnd = (LineTo) line.getGeoms().get(0).getCoordinateCol().get(1);
            line.getGeoms().get(0).getCoordinateCol().remove(originalEnd);
            for (int index = 2; index < last; index += 2) {
                double worldX = path.points[index] / POINTS_PER_INCH - beginX;
                double worldY = (height - path.points[index + 1]) / POINTS_PER_INCH - beginY;
                LineTo turn = new LineTo();
                turn.setIX(line.getGeoms().get(0).getCoordinateCol().getCount() + 1);
                turn.getX().setValue(worldX * cos + worldY * sin);
                turn.getY().setValue(-worldX * sin + worldY * cos);
                line.getGeoms().get(0).getCoordinateCol().add(turn);
            }
            // The arrow belongs to the final leg, and every Geometry row needs a unique IX.
            originalEnd.setIX(line.getGeoms().get(0).getCoordinateCol().getCount() + 1);
            line.getGeoms().get(0).getCoordinateCol().add(originalEnd);
        }
        line.setNameU("LibraryLine_" + path.name + "_" + line.getID());
        return line;
    }

    /**
     * 在不改写连线几何的前提下建立 Visio 的节点连接关系。
     *
     * <p>通过连接点与 Connect 关系胶合一根完整的一维连接线的两端。</p>
     */
    private void connectEndpoint(Page page, Shape line, Shape node, int pointX,
                                 boolean atBegin, boolean atTop) {
        if (node == null) throw new IllegalStateException("missing Visio flowchart node");
        Connection port = new Connection();
        port.setIX(node.getConnections().getCount());
        double nodeLeft = node.getXForm().getPinX().getValue()
                - node.getXForm().getWidth().getValue() / 2D;
        port.getX().setValue(pointX / POINTS_PER_INCH - nodeLeft);
        // Visio 重新计算胶合公式后，终点仍须停在边框外 4pt；落在边框上会遮住原生箭头。
        port.getY().setValue(atTop ? node.getXForm().getHeight().getValue()
                + 4D / POINTS_PER_INCH : 0D);
        node.getConnections().add(port);
        String cell = atBegin ? "BeginX" : "EndX";
        Connect connect = new Connect();
        connect.setFromSheet(line.getID());
        connect.setFromCell(cell);
        connect.setFromPart(atBegin ? 9 : 12);
        connect.setToSheet(node.getID());
        connect.setToCell("Connections.X" + (port.getIX() + 1));
        connect.setToPart(100);
        page.getConnects().add(connect);
        String formula = "PAR(PNT(Sheet." + node.getID() + "!Connections.X"
                + (port.getIX() + 1) + ",Sheet." + node.getID() + "!Connections.Y"
                + (port.getIX() + 1) + "))";
        if (atBegin) {
            line.getXForm1D().getBeginX().getUfe().setF(formula);
            line.getXForm1D().getBeginY().getUfe().setF(formula);
        } else {
            line.getXForm1D().getEndX().getUfe().setF(formula);
            line.getXForm1D().getEndY().getUfe().setF(formula);
        }
    }

    /** 绘制一个可编辑的矩形或四角圆弧框。 */
    private Shape drawVisioBox(Page page, Box box, int height, String label) throws Exception {
        if (!box.rounded) {
            Shape shape = rectangle(page, box.x, box.y, box.width, box.height, height);
            shape.getFill().setFillForegnd(new ColorValue("#FFFFFF", 0));
            shape.getLine().setLineColor(new ColorValue("#000000", 0));
            shape.getLine().setLineWeight(new DoubleValue(STROKE_WIDTH / POINTS_PER_INCH, 0));
            shape.setNameU("LibraryBox_" + box.id);
            textOnShape(shape, label);
            return shape;
        }
        Shape shape = roundedBox(page, box, height);
        shape.getFill().setFillForegnd(new ColorValue("#FFFFFF", 0));
        shape.getLine().setLineColor(new ColorValue("#000000", 0));
        shape.getLine().setLineWeight(new DoubleValue(STROKE_WIDTH / POINTS_PER_INCH, 0));
        shape.setNameU("LibraryBox_" + box.id);
        textOnShape(shape, label);
        shape.getTextXForm().getTxtPinX().setValue(box.width / (2D * POINTS_PER_INCH));
        shape.getTextXForm().getTxtPinY().setValue(box.height / (2D * POINTS_PER_INCH));
        shape.getTextXForm().getTxtWidth().setValue(box.width / POINTS_PER_INCH);
        shape.getTextXForm().getTxtHeight().setValue(box.height / POINTS_PER_INCH);
        shape.getTextXForm().getTxtLocPinX().setValue(box.width / (2D * POINTS_PER_INCH));
        shape.getTextXForm().getTxtLocPinY().setValue(box.height / (2D * POINTS_PER_INCH));
        return shape;
    }

    /** 绘制一个闭合的原生圆角节点，文字可直接写在该节点内。 */
    private Shape roundedBox(Page page, Box box, int height) throws Exception {
        List<Double> points = new ArrayList<>();
        int radius = 36;
        addPoint(points, box.x + radius, box.y, height);
        addPoint(points, box.x + box.width - radius, box.y, height);
        addArc(points, box.x + box.width - radius, box.y + radius, radius,
                -Math.PI / 2D, 0D, height);
        addPoint(points, box.x + box.width, box.y + box.height - radius, height);
        addArc(points, box.x + box.width - radius, box.y + box.height - radius,
                radius, 0D, Math.PI / 2D, height);
        addPoint(points, box.x + radius, box.y + box.height, height);
        addArc(points, box.x + radius, box.y + box.height - radius,
                radius, Math.PI / 2D, Math.PI, height);
        addPoint(points, box.x, box.y + radius, height);
        addArc(points, box.x + radius, box.y + radius,
                radius, Math.PI, 3D * Math.PI / 2D, height);
        addPoint(points, box.x + radius, box.y, height);
        double[] xy = new double[points.size()];
        double left = box.x / POINTS_PER_INCH;
        double bottom = (height - box.y - box.height) / POINTS_PER_INCH;
        for (int index = 0; index < xy.length; index += 2) {
            xy[index] = points.get(index) - left;
            xy[index + 1] = points.get(index + 1) - bottom;
        }
        Shape result = shape(page, page.drawPolyline(xy));
        result.getXForm().getPinX().setValue((box.x + box.width / 2D) / POINTS_PER_INCH);
        result.getXForm().getPinY().setValue((height - box.y - box.height / 2D) / POINTS_PER_INCH);
        result.getXForm().getLocPinX().setValue(box.width / (2D * POINTS_PER_INCH));
        result.getXForm().getLocPinY().setValue(box.height / (2D * POINTS_PER_INCH));
        return result;
    }

    /** 将圆弧分解为原生 Shape 中连续的折线点。 */
    private void addArc(List<Double> points, int cx, int cy, int radius,
                        double from, double to, int height) {
        for (int step = 1; step <= 8; step++) {
            double angle = from + (to - from) * step / 8D;
            addPoint(points, cx + radius * Math.cos(angle),
                    cy + radius * Math.sin(angle), height);
        }
    }

    /** 将页面顶部为原点的坐标转为 Visio 英寸坐标。 */
    private void addPoint(List<Double> points, double x, double y, int height) {
        points.add(x / POINTS_PER_INCH);
        points.add((height - y) / POINTS_PER_INCH);
    }

    /** 把文字及字体样式写入节点 Shape 自身。 */
    private void textOnShape(Shape shape, String value) {
        shape.getTextBlock().setLeftMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setRightMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setTopMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setBottomMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setVerticalAlign(new VerticalAlign(VerticalAlignValue.MIDDLE));
        shape.setText(new Text());
        shape.getParas().clear();
        shape.getChars().clear();
        Para paragraph = new Para();
        paragraph.setIX(0);
        paragraph.setHorzAlign(new HorzAlign(HorzAlignValue.CENTER));
        shape.getParas().add(paragraph);
        Char character = new Char();
        character.setIX(0);
        character.setFontName(new StrValue(FONT_FAMILY));
        character.setAsianFontName(new StrValue(FONT_FAMILY));
        character.setColor(new ColorValue("#000000", 0));
        character.setSize(new DoubleValue(FONT_SIZE / POINTS_PER_INCH, 0));
        shape.getChars().add(character);
        shape.getText().getValue().add(new Pp(0));
        shape.getText().getValue().add(new Cp(0));
        shape.getText().getValue().add(new Txt(value));
    }

    /** 创建矩形 Visio Shape。 */
    private Shape rectangle(Page page, int x, int y, int width, int height, int canvasHeight) throws Exception {
        long id = page.drawRectangle(x / POINTS_PER_INCH, (canvasHeight - y - height) / POINTS_PER_INCH,
                (x + width) / POINTS_PER_INCH, (canvasHeight - y) / POINTS_PER_INCH);
        return shape(page, id);
    }

    /** 根据 Visio ID 返回刚创建的图形。 */
    private Shape shape(Page page, long id) {
        for (int index = 0; index < page.getShapes().getCount(); index++) {
            Shape shape = page.getShapes().get(index);
            if (shape.getID() == id) return shape;
        }
        throw new IllegalStateException("Visio shape does not exist: " + id);
    }

    /** 由同一绘制计划生成白底 PNG，避免评估版 Visio 对中文预览的限制。 */
    private void preview(Plan plan, Path output) throws Exception {
        BufferedImage image = new BufferedImage(plan.width, plan.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, plan.width, plan.height);
            graphics.setStroke(new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(Color.BLACK);
            for (PathLine line : plan.lines) {
                if (!line.name.startsWith("OperationDivider")) previewPath(graphics, line);
            }
            for (Box box : plan.boxes) previewBox(graphics, box);
            for (PathLine line : plan.lines) {
                if (line.name.startsWith("OperationDivider")) previewPath(graphics, line);
            }
            graphics.setFont(new Font(FONT_FAMILY, Font.PLAIN, FONT_SIZE));
            for (Label label : plan.labels) previewText(graphics, label);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", output.toFile());
    }

    /** 绘制 PNG 连线；箭头按最后一段方向放置，与 VSDX 的原生箭头一致。 */
    private void previewPath(Graphics2D graphics, PathLine line) {
        Path2D path = new Path2D.Double();
        path.moveTo(line.points[0], line.points[1]);
        for (int index = 2; index < line.points.length; index += 2) {
            path.lineTo(line.points[index], line.points[index + 1]);
        }
        graphics.draw(path);
        if (line.arrow) {
            int length = line.points.length;
            int x = line.points[length - 2];
            int y = line.points[length - 1];
            double dx = x - line.points[length - 4];
            double dy = y - line.points[length - 3];
            double magnitude = Math.hypot(dx, dy);
            double alongX = dx / magnitude;
            double alongY = dy / magnitude;
            double baseX = x - alongX * 15D;
            double baseY = y - alongY * 15D;
            double normalX = -alongY * 8D;
            double normalY = alongX * 8D;
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(x, y);
            arrow.lineTo(baseX + normalX, baseY + normalY);
            arrow.lineTo(baseX - normalX, baseY - normalY);
            arrow.closePath();
            graphics.fill(arrow);
        }
    }

    /** 绘制与 Visio 一致的矩形或圆角端点。 */
    private void previewBox(Graphics2D graphics, Box box) {
        if (box.rounded) {
            RoundRectangle2D shape = new RoundRectangle2D.Double(box.x, box.y, box.width, box.height, 72, 72);
            graphics.setColor(Color.WHITE);
            graphics.fill(shape);
            graphics.setColor(Color.BLACK);
            graphics.draw(shape);
        } else {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(box.x, box.y, box.width, box.height);
            graphics.setColor(Color.BLACK);
            graphics.drawRect(box.x, box.y, box.width, box.height);
        }
    }

    /** 绘制居中的 PNG 节点文字。 */
    private void previewText(Graphics2D graphics, Label label) {
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(label.text, label.x - metrics.stringWidth(label.text) / 2,
                label.y + (metrics.getAscent() - metrics.getDescent()) / 2);
    }

    /** 共享的图形绘制计划。 */
    private static final class Plan {
        /** 根据数据节点数量选择的画布宽度。 */
        private final int width;
        /** 单排数据节点使用的固定画布高度。 */
        private final int height;
        /** 矩形和圆角框。 */
        private final List<Box> boxes = new ArrayList<>();
        /** 正交连线及分隔线。 */
        private final List<PathLine> lines = new ArrayList<>();
        /** 所有可见文字。 */
        private final List<Label> labels = new ArrayList<>();

        private Plan(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /** 固定位置的节点边框。 */
    private static final class Box {
        private final String id;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final boolean rounded;

        /** 创建固定模板中的边框。 */
        private Box(String id, int x, int y, int width, int height, boolean rounded) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rounded = rounded;
        }
    }

    /** 按顺序连接多个坐标点的线。 */
    private static final class PathLine {
        private final String name;
        private final boolean arrow;
        private final int[] points;

        /** 创建固定模板中的正交折线。 */
        private PathLine(String name, boolean arrow, int... points) {
            if (points.length < 4 || points.length % 2 != 0) {
                throw new IllegalArgumentException("flow path needs at least two coordinate pairs");
            }
            this.name = name;
            this.arrow = arrow;
            this.points = points;
        }
    }

    /** 居中显示的节点文字。 */
    private static final class Label {
        private final String id;
        private final String text;
        private final int x;
        private final int y;

        /** 创建文字和画布中心坐标。 */
        private Label(String id, String text, int x, int y) {
            this.id = id;
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
}
