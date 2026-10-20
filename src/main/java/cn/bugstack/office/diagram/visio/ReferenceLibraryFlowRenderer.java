package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramType;
import com.aspose.diagram.Char;
import com.aspose.diagram.ColorValue;
import com.aspose.diagram.Cp;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.DoubleValue;
import com.aspose.diagram.HorzAlign;
import com.aspose.diagram.HorzAlignValue;
import com.aspose.diagram.Page;
import com.aspose.diagram.Para;
import com.aspose.diagram.Pp;
import com.aspose.diagram.SaveFileFormat;
import com.aspose.diagram.Shape;
import com.aspose.diagram.StrValue;
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
import java.util.List;

/**
 * 根据固定的“菜单栏→库管理→数据项→增删改查→结束”流程图模板生成 Visio。
 *
 * <p>仅复用业务 JSON 中的功能名及三至四份数据名；其他文字、节点、连线、白底黑线样式固定。
 * 所有图形与文字在 VSDX 中仍是可编辑对象。</p>
 */
public final class ReferenceLibraryFlowRenderer {

    /** 画布宽度，单位为绘图点。 */
    private static final int WIDTH = 1200;
    /** 画布高度，单位为绘图点。 */
    private static final int HEIGHT = 1150;
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
     * @param inputJson 包含 {@code function} 和三至四个 {@code inputs} 的 JSON 文件
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
            page.getPageSheet().getPageProps().getPageHeight().setValue(HEIGHT / POINTS_PER_INCH);
            Shape background = rectangle(page, 0, 0, plan.width, HEIGHT);
            background.getFill().setFillForegnd(new ColorValue("#FFFFFF", 0));
            background.getLine().setLineColor(new ColorValue("#FFFFFF", 0));
            background.setNameU("LibraryFlowBackground");
            for (PathLine path : plan.lines) {
                if (!path.name.startsWith("OperationDivider")) drawVisioPath(page, path);
            }
            for (Box box : plan.boxes) drawVisioBox(page, box);
            for (PathLine path : plan.lines) {
                if (path.name.startsWith("OperationDivider")) drawVisioPath(page, path);
            }
            for (Label label : plan.labels) addVisioText(page, label);
            diagram.save(absolute.toString(), SaveFileFormat.VSDX);
        } finally {
            diagram.dispose();
        }
        preview(plan, previewPath);
        return new VisioDiagramArtifact(DiagramType.FLOW, absolute, previewPath);
    }

    /** 校验仅包含功能名和三至四份数据名的业务输入。 */
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
        if (names == null || !names.isArray() || names.size() < 3 || names.size() > 4) {
            throw new IllegalArgumentException("library-flow requires three or four input names");
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

    /** 根据参考图的相对位置建立单一绘制计划，供 VSDX 和 PNG 共用。 */
    private Plan plan(String[] values) {
        if (values.length == 5) return planFour(values);
        Plan plan = new Plan(WIDTH);
        plan.boxes.add(new Box("menu", 500, 55, 200, 80, true));
        plan.boxes.add(new Box("function", 470, 200, 260, 90, false));
        plan.boxes.add(new Box("data1", 105, 405, 265, 90, false));
        plan.boxes.add(new Box("data2", 468, 405, 265, 90, false));
        plan.boxes.add(new Box("data3", 830, 405, 265, 90, false));
        plan.boxes.add(new Box("operations", 470, 620, 260, 310, false));
        plan.boxes.add(new Box("end", 500, 1030, 200, 80, true));

        plan.labels.add(new Label("menu", "菜单栏", 600, 95));
        plan.labels.add(new Label("function", values[0], 600, 245));
        plan.labels.add(new Label("data1", values[1], 237, 450));
        plan.labels.add(new Label("data2", values[2], 600, 450));
        plan.labels.add(new Label("data3", values[3], 962, 450));
        plan.labels.add(new Label("add", "增", 502, 775));
        plan.labels.add(new Label("delete", "删", 567, 775));
        plan.labels.add(new Label("modify", "改", 632, 775));
        plan.labels.add(new Label("query", "查", 697, 775));
        plan.labels.add(new Label("end", "结束", 600, 1070));

        plan.lines.add(new PathLine("MenuToFunction", true, 600, 135, 600, 200));
        plan.lines.add(new PathLine("FunctionToLibraries", false, 600, 290, 600, 365));
        plan.lines.add(new PathLine("LibraryTrunk", false, 237, 365, 962, 365));
        plan.lines.add(new PathLine("ToData1", true, 237, 365, 237, 405));
        plan.lines.add(new PathLine("ToData2", true, 600, 365, 600, 405));
        plan.lines.add(new PathLine("ToData3", true, 962, 365, 962, 405));
        plan.lines.add(new PathLine("Data1ToOperations", false, 237, 495, 237, 555));
        plan.lines.add(new PathLine("Data2ToOperations", false, 600, 495, 600, 555));
        plan.lines.add(new PathLine("Data3ToOperations", false, 962, 495, 962, 555));
        plan.lines.add(new PathLine("OperationsCollector", false, 237, 555, 962, 555));
        plan.lines.add(new PathLine("CollectorToOperations", true, 600, 555, 600, 620));
        plan.lines.add(new PathLine("OperationDivider1", false, 535, 620, 535, 930));
        plan.lines.add(new PathLine("OperationDivider2", false, 600, 620, 600, 930));
        plan.lines.add(new PathLine("OperationDivider3", false, 665, 620, 665, 930));
        plan.lines.add(new PathLine("OperationsToEnd", true, 600, 930, 600, 1030));
        return plan;
    }

    /** 将同一固定模板扩展为四个并排的数据节点，并保持连接线为正交走线。 */
    private Plan planFour(String[] values) {
        Plan plan = new Plan(1500);
        plan.boxes.add(new Box("menu", 650, 55, 200, 80, true));
        plan.boxes.add(new Box("function", 620, 200, 260, 90, false));
        int[] centers = {175, 550, 950, 1325};
        for (int index = 0; index < centers.length; index++) {
            plan.boxes.add(new Box("data" + (index + 1), centers[index] - 130, 405, 260, 90, false));
            plan.labels.add(new Label("data" + (index + 1), values[index + 1], centers[index], 450));
            plan.lines.add(new PathLine("ToData" + (index + 1), true,
                    centers[index], 365, centers[index], 405));
        }
        plan.boxes.add(new Box("operations", 620, 620, 260, 310, false));
        plan.boxes.add(new Box("end", 650, 1030, 200, 80, true));

        plan.labels.add(new Label("menu", "菜单栏", 750, 95));
        plan.labels.add(new Label("function", values[0], 750, 245));
        plan.labels.add(new Label("add", "增", 652, 775));
        plan.labels.add(new Label("delete", "删", 717, 775));
        plan.labels.add(new Label("modify", "改", 782, 775));
        plan.labels.add(new Label("query", "查", 847, 775));
        plan.labels.add(new Label("end", "结束", 750, 1070));

        plan.lines.add(new PathLine("MenuToFunction", true, 750, 135, 750, 200));
        plan.lines.add(new PathLine("FunctionToLibraries", false, 750, 290, 750, 365));
        plan.lines.add(new PathLine("LibraryTrunk", false, 175, 365, 1325, 365));
        plan.lines.add(new PathLine("Data1ToOperations", false, 175, 495, 175, 555));
        plan.lines.add(new PathLine("Data2ToOperations", false, 550, 495, 550, 555));
        plan.lines.add(new PathLine("Data3ToOperations", false, 950, 495, 950, 555));
        plan.lines.add(new PathLine("Data4ToOperations", false, 1325, 495, 1325, 555));
        plan.lines.add(new PathLine("OperationsCollector", false, 175, 555, 1325, 555));
        plan.lines.add(new PathLine("CollectorToOperations", true, 750, 555, 750, 620));
        plan.lines.add(new PathLine("OperationDivider1", false, 685, 620, 685, 930));
        plan.lines.add(new PathLine("OperationDivider2", false, 750, 620, 750, 930));
        plan.lines.add(new PathLine("OperationDivider3", false, 815, 620, 815, 930));
        plan.lines.add(new PathLine("OperationsToEnd", true, 750, 930, 750, 1030));
        return plan;
    }

    /** 将折线及末端箭头绘制为可编辑 Visio 线段。 */
    private void drawVisioPath(Page page, PathLine path) throws Exception {
        for (int index = 2; index < path.points.length; index += 2) {
            segment(page, path.name, path.points[index - 2], path.points[index - 1],
                    path.points[index], path.points[index + 1]);
        }
        if (path.arrow) {
            int length = path.points.length;
            int x = path.points[length - 2];
            int y = path.points[length - 1];
            segment(page, path.name + "Arrow", x, y, x - 8, y - 15);
            segment(page, path.name + "Arrow", x, y, x + 8, y - 15);
        }
    }

    /** 绘制一个可编辑的矩形或四角圆弧框。 */
    private void drawVisioBox(Page page, Box box) throws Exception {
        if (!box.rounded) {
            Shape shape = rectangle(page, box.x, box.y, box.width, box.height);
            shape.getFill().setFillForegnd(new ColorValue("#FFFFFF", 0));
            shape.getLine().setLineColor(new ColorValue("#000000", 0));
            shape.getLine().setLineWeight(new DoubleValue(STROKE_WIDTH / POINTS_PER_INCH, 0));
            shape.setNameU("LibraryBox_" + box.id);
            return;
        }
        int radius = 36;
        segment(page, "Rounded_" + box.id, box.x + radius, box.y, box.x + box.width - radius, box.y);
        segment(page, "Rounded_" + box.id, box.x + radius, box.y + box.height,
                box.x + box.width - radius, box.y + box.height);
        segment(page, "Rounded_" + box.id, box.x, box.y + radius, box.x, box.y + box.height - radius);
        segment(page, "Rounded_" + box.id, box.x + box.width, box.y + radius,
                box.x + box.width, box.y + box.height - radius);
        for (int corner = 0; corner < 4; corner++) {
            double start = corner * Math.PI / 2D;
            int cx = corner == 0 || corner == 3 ? box.x + box.width - radius : box.x + radius;
            int cy = corner < 2 ? box.y + box.height - radius : box.y + radius;
            for (int step = 0; step < 8; step++) {
                double a = start + step * Math.PI / 16D;
                double b = start + (step + 1) * Math.PI / 16D;
                segment(page, "Rounded_" + box.id,
                        cx + (int) Math.round(radius * Math.cos(a)),
                        cy + (int) Math.round(radius * Math.sin(a)),
                        cx + (int) Math.round(radius * Math.cos(b)),
                        cy + (int) Math.round(radius * Math.sin(b)));
            }
        }
    }

    /** 在 Visio 中添加居中可编辑文本。 */
    private void addVisioText(Page page, Label label) {
        Shape shape = page.addText(label.x / POINTS_PER_INCH, (HEIGHT - label.y) / POINTS_PER_INCH,
                260D / POINTS_PER_INCH, 55D / POINTS_PER_INCH, "");
        shape.setNameU("LibraryText_" + label.id);
        shape.getLine().setLineColor(new ColorValue("#FFFFFF", 0));
        shape.getTextBlock().setLeftMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setRightMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setTopMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setBottomMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setVerticalAlign(new VerticalAlign(VerticalAlignValue.MIDDLE));
        shape.getText().getValue().clear();
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
        shape.getText().getValue().add(new Txt(label.text));
    }

    /** 创建一段命名的黑色 Visio 直线。 */
    private void segment(Page page, String name, int x1, int y1, int x2, int y2) throws Exception {
        long id = page.drawLine(x1 / POINTS_PER_INCH, (HEIGHT - y1) / POINTS_PER_INCH,
                x2 / POINTS_PER_INCH, (HEIGHT - y2) / POINTS_PER_INCH);
        Shape shape = shape(page, id);
        shape.getLine().setLineColor(new ColorValue("#000000", 0));
        shape.getLine().setLineWeight(new DoubleValue(STROKE_WIDTH / POINTS_PER_INCH, 0));
        shape.setNameU("LibraryLine_" + name + "_" + id);
    }

    /** 创建矩形 Visio Shape。 */
    private Shape rectangle(Page page, int x, int y, int width, int height) throws Exception {
        long id = page.drawRectangle((x + width / 2D) / POINTS_PER_INCH,
                (HEIGHT - y - height / 2D) / POINTS_PER_INCH,
                width / POINTS_PER_INCH, height / POINTS_PER_INCH);
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
        BufferedImage image = new BufferedImage(plan.width, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, plan.width, HEIGHT);
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

    /** 绘制 PNG 折线和实心箭头。 */
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
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(x, y);
            arrow.lineTo(x - 8, y - 15);
            arrow.lineTo(x + 8, y - 15);
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
        /** 矩形和圆角框。 */
        private final List<Box> boxes = new ArrayList<>();
        /** 正交连线及分隔线。 */
        private final List<PathLine> lines = new ArrayList<>();
        /** 所有可见文字。 */
        private final List<Label> labels = new ArrayList<>();

        private Plan(int width) {
            this.width = width;
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
