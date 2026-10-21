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
import com.aspose.diagram.IntValue;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 根据任意数量的输入名称和一个功能名称绘制参考样式的可编辑数据流图和 PNG 预览。
 *
 * <p>保留白底、黑线、波浪底边文档、矩形处理、圆柱数据库和圆形渐变图标。
 * 拍摄屏幕产生的摩尔纹不属于图形底色，不在输出中复制。</p>
 */
public final class ReferenceDataFlowRenderer {

    /** Visio 页面使用英寸，JSON 画布使用点。 */
    private static final double POINTS_PER_INCH = 72D;
    /** 数据库圆柱两端的可见弧宽。 */
    private static final int CYLINDER_CURVE = 24;
    /** 文档底边波浪的最大起伏。 */
    private static final int DOCUMENT_WAVE = 12;
    /** 内置且不向调用方暴露的参考图布局。 */
    private static final String LAYOUT_RESOURCE = "/diagram/data-flow-reference-layout.json";
    /** JSON 读取器。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 读取至少一个输入名称及功能名称，套用参考样式生成 VSDX 与 PNG。
     *
     * @param jsonPath 仅含 {@code inputs} 数组与 {@code function} 名称的业务输入文件
     * @param vsdxPath 可编辑 Visio 输出路径
     * @return 图形文件和预览文件路径
     * @throws Exception JSON、图形或文件写入不合法时抛出
     */
    public VisioDiagramArtifact render(Path jsonPath, Path vsdxPath) throws Exception {
        JsonNode input = mapper.readTree(jsonPath.toFile());
        JsonNode spec = applyInputs(input);
        validate(spec);
        Path absolute = vsdxPath.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        String fileName = absolute.getFileName().toString();
        String stem = fileName.endsWith(".vsdx") ? fileName.substring(0, fileName.length() - 5) : fileName;
        Path preview = absolute.resolveSibling(stem + ".preview.png");
        Diagram diagram = new Diagram();
        try {
            Page page = diagram.getPages().getPage(0);
            int width = spec.get("width").asInt();
            int height = spec.get("height").asInt();
            page.getPageSheet().getPageProps().getPageWidth().setValue(width / POINTS_PER_INCH);
            page.getPageSheet().getPageProps().getPageHeight().setValue(height / POINTS_PER_INCH);
            // 页面默认白底；不要加入会干扰选中和复制的整页背景 Shape。
            for (JsonNode edge : spec.get("edges")) drawVisioEdge(page, edge, spec);
            for (JsonNode node : spec.get("nodes")) drawVisioNode(page, node, spec);
            diagram.save(absolute.toString(), SaveFileFormat.VSDX);
            preview(spec, preview);
            return new VisioDiagramArtifact(DiagramType.DATA_FLOW, absolute, preview);
        } finally {
            diagram.dispose();
        }
    }

    /**
     * 把业务输入、功能名称及可选的持久化标注填入动态布局。
     *
     * @param input 调用方输入配置
     * @return 已替换文档节点名称的完整布局
     * @throws IOException 内置布局资源不存在或格式错误时抛出
     */
    private JsonNode applyInputs(JsonNode input) throws IOException {
        if (input == null || !input.isObject() || input.size() != (input.has("persistenceLabel") ? 3 : 2)
                || !input.has("inputs") || !input.has("function")) {
            throw new IllegalArgumentException("data-flow JSON only accepts inputs, function and persistenceLabel");
        }
        JsonNode names = input.get("inputs");
        if (names == null || !names.isArray() || names.isEmpty()) {
            throw new IllegalArgumentException("data-flow requires at least one input name");
        }
        for (JsonNode name : names) {
            if (!name.isTextual() || name.asText().isBlank() || name.asText().length() > 40) {
                throw new IllegalArgumentException("data-flow input name must contain 1-40 characters");
            }
        }
        String function = required(input, "function").trim();
        if (function.length() > 40) {
            throw new IllegalArgumentException("data-flow function name must contain 1-40 characters");
        }
        String persistenceLabel = null;
        if (input.has("persistenceLabel")) {
            JsonNode value = input.get("persistenceLabel");
            if (!value.isTextual() || value.asText().length() > 40) {
                throw new IllegalArgumentException("persistenceLabel must be text with at most 40 characters");
            }
            persistenceLabel = value.asText().trim();
        }
        InputStream resource = getClass().getResourceAsStream(LAYOUT_RESOURCE);
        if (resource == null) throw new IOException("missing data-flow layout resource: " + LAYOUT_RESOURCE);
        try (InputStream stream = resource) {
            JsonNode spec = mapper.readTree(stream);
            if (spec == null || !spec.isObject()) throw new IOException("invalid data-flow layout resource");
            layoutInputs(spec, names.size());
            BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics2D measure = measureImage.createGraphics();
            try {
                Font font = new Font(required(spec, "fontFamily"), Font.PLAIN, integer(spec, "fontSize"));
                FontMetrics metrics = measure.getFontMetrics(font);
                for (JsonNode node : spec.get("nodes")) {
                    String id = node.get("id").asText();
                    int inputIndex = id.matches("data[0-9]+") ? Integer.parseInt(id.substring(4)) - 1 : -1;
                    if (inputIndex < 0 && !"function".equals(id)) continue;
                    String label = inputIndex < 0 ? function : names.get(inputIndex).asText().trim();
                    if (metrics.stringWidth(label) > integer(node, "width") - 24) {
                        throw new IllegalArgumentException("data-flow label exceeds fixed shape width: " + label);
                    }
                    ((ObjectNode) node).put("label", label);
                }
                if (persistenceLabel != null) {
                    JsonNode storageEdge = null;
                    for (JsonNode edge : spec.get("edges")) {
                        if ("function".equals(edge.path("from").asText())
                                && "database".equals(edge.path("to").asText())) {
                            storageEdge = edge;
                            break;
                        }
                    }
                    if (storageEdge == null) throw new IOException("reference data-flow layout lacks storage edge");
                    if (persistenceLabel.isEmpty()) {
                        ((ObjectNode) storageEdge).remove("label");
                        ((ObjectNode) storageEdge).remove("labelCenter");
                    } else {
                        if (metrics.stringWidth(persistenceLabel) > 330) {
                            throw new IllegalArgumentException("persistenceLabel exceeds storage edge width");
                        }
                        ((ObjectNode) storageEdge).put("label", persistenceLabel);
                    }
                }
            } finally {
                measure.dispose();
            }
            return spec;
        }
    }

    /** 所有输入文档按 JSON 顺序在左侧纵向排列；增加输入时扩展画布高度。 */
    private void layoutInputs(JsonNode spec, int count) {
        long lastDocumentTop = 40L + (count - 1L) * 190L;
        int functionTop = Math.toIntExact(Math.max(210L, 97L + (count - 1L) * 95L - 40L));
        int functionCenter = functionTop + 40;
        int canvasHeight = Math.toIntExact(Math.max(800L,
                Math.max(lastDocumentTop + 200L, functionTop + 494L)));
        ((ObjectNode) spec).put("height", canvasHeight);
        ArrayNode oldNodes = (ArrayNode) spec.get("nodes");
        ObjectNode documentTemplate = ((ObjectNode) oldNodes.get(0)).deepCopy();
        ObjectNode function = ((ObjectNode) oldNodes.get(2)).deepCopy();
        ObjectNode database = ((ObjectNode) oldNodes.get(3)).deepCopy();
        ObjectNode control = ((ObjectNode) oldNodes.get(4)).deepCopy();
        function.put("y", functionTop);
        database.put("y", functionTop - 37);
        control.put("y", functionTop + 240);
        ArrayNode nodes = mapper.createArrayNode();
        ArrayNode edges = mapper.createArrayNode();
        for (int index = 0; index < count; index++) {
            int top = Math.toIntExact(40L + index * 190L);
            int center = top + 58;
            ObjectNode document = documentTemplate.deepCopy();
            document.put("id", "data" + (index + 1));
            document.put("y", top);
            nodes.add(document);
            ObjectNode edge = mapper.createObjectNode();
            edge.put("from", "data" + (index + 1));
            edge.put("to", "function");
            ArrayNode points = edge.putArray("points");
            points.addArray().add(365).add(center);
            points.addArray().add(440).add(center);
            points.addArray().add(440).add(functionCenter);
            points.addArray().add(526).add(functionCenter);
            edges.add(edge);
        }
        nodes.add(function);
        nodes.add(database);
        nodes.add(control);
        ObjectNode storage = ((ObjectNode) spec.get("edges").get(2)).deepCopy();
        ArrayNode storagePoints = (ArrayNode) storage.get("points");
        for (JsonNode point : storagePoints) {
            ((ArrayNode) point).set(1, mapper.getNodeFactory().numberNode(functionCenter));
        }
        ((ArrayNode) storage.get("labelCenter")).set(1,
                mapper.getNodeFactory().numberNode(functionCenter - 2));
        edges.add(storage);
        ObjectNode controlEdge = ((ObjectNode) spec.get("edges").get(3)).deepCopy();
        ArrayNode controlPoints = (ArrayNode) controlEdge.get("points");
        ((ArrayNode) controlPoints.get(0)).set(1, mapper.getNodeFactory().numberNode(functionTop + 80));
        ((ArrayNode) controlPoints.get(1)).set(1, mapper.getNodeFactory().numberNode(functionTop + 240));
        edges.add(controlEdge);
        ((ObjectNode) spec).set("nodes", nodes);
        ((ObjectNode) spec).set("edges", edges);
    }

    /**
     * 校验固定参考样式所需的节点、连线和画布边界。
     *
     * @param spec JSON 树
     */
    private void validate(JsonNode spec) {
        int width = integer(spec, "width");
        int height = integer(spec, "height");
        if (width < 400 || height < 300) {
            throw new IllegalArgumentException("data-flow canvas dimensions are out of range");
        }
        if (integer(spec, "strokeWidth") < 1 || integer(spec, "strokeWidth") > 10
                || integer(spec, "fontSize") < 8 || integer(spec, "fontSize") > 80) {
            throw new IllegalArgumentException("data-flow stroke or font size is out of range");
        }
        for (String key : new String[]{"background", "stroke", "accentTop", "accentBottom"}) {
            if (!required(spec, key).matches("#[0-9A-Fa-f]{6}")) {
                throw new IllegalArgumentException("invalid data-flow color: " + key);
            }
        }
        required(spec, "fontFamily");
        JsonNode nodes = spec.get("nodes");
        JsonNode edges = spec.get("edges");
        if (nodes == null || !nodes.isArray() || nodes.size() < 4
                || edges == null || !edges.isArray() || edges.size() != nodes.size() - 1) {
            throw new IllegalArgumentException("reference data-flow requires at least one input");
        }
        Set<String> ids = new HashSet<>();
        Set<String> kinds = new HashSet<>();
        for (JsonNode node : nodes) {
            String id = required(node, "id");
            String kind = required(node, "kind");
            if (!ids.add(id) || !Set.of("DOCUMENT", "PROCESS", "DATABASE", "CONTROL_ICON").contains(kind)) {
                throw new IllegalArgumentException("invalid data-flow node: " + id);
            }
            kinds.add(kind);
            if (!"CONTROL_ICON".equals(kind)) required(node, "label");
            int x = integer(node, "x");
            int y = integer(node, "y");
            int w = integer(node, "width");
            int h = integer(node, "height");
            if (x < 0 || y < 0 || w < 40 || h < 30 || x + w > width || y + h > height) {
                throw new IllegalArgumentException("data-flow node is outside canvas: " + id);
            }
        }
        if (kinds.size() != 4) throw new IllegalArgumentException("missing data-flow shape type");
        for (JsonNode edge : edges) {
            if (!ids.contains(required(edge, "from")) || !ids.contains(required(edge, "to"))) {
                throw new IllegalArgumentException("data-flow edge references an unknown node");
            }
            JsonNode points = edge.get("points");
            if (points == null || !points.isArray() || points.size() < 2) {
                throw new IllegalArgumentException("data-flow edge requires two or more points");
            }
            for (JsonNode value : points) point(value, width, height);
            if (edge.has("label")) {
                required(edge, "label");
                point(edge.get("labelCenter"), width, height);
            }
        }
    }

    /** 读取必填整数。 */
    private int integer(JsonNode parent, String name) {
        JsonNode value = parent == null ? null : parent.get(name);
        if (value == null || !value.isIntegralNumber()) throw new IllegalArgumentException("missing integer: " + name);
        return value.asInt();
    }

    /** 读取必填非空字符串。 */
    private String required(JsonNode parent, String name) {
        JsonNode value = parent == null ? null : parent.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("missing text: " + name);
        }
        return value.asText();
    }

    /** 校验 JSON 二维坐标。 */
    private void point(JsonNode value, int width, int height) {
        if (value == null || !value.isArray() || value.size() != 2
                || !value.get(0).isIntegralNumber() || !value.get(1).isIntegralNumber()
                || value.get(0).asInt() < 0 || value.get(0).asInt() > width
                || value.get(1).asInt() < 0 || value.get(1).asInt() > height) {
            throw new IllegalArgumentException("data-flow point is outside canvas");
        }
    }

    /** 绘制带波浪底边的文档、处理框、圆柱存储或圆形图标。 */
    private void drawVisioNode(Page page, JsonNode node, JsonNode spec) throws Exception {
        int x = integer(node, "x");
        int y = integer(node, "y");
        int w = integer(node, "width");
        int h = integer(node, "height");
        int canvasHeight = integer(spec, "height");
        String kind = required(node, "kind");
        String name = required(node, "id");
        if ("DOCUMENT".equals(kind)) {
            List<Double> points = new ArrayList<>();
            addVisioPoint(points, x, y, canvasHeight);
            addVisioPoint(points, x + w, y, canvasHeight);
            addVisioPoint(points, x + w, y + h - DOCUMENT_WAVE, canvasHeight);
            for (int index = 35; index >= 0; index--) {
                addVisioPoint(points, x + w * index / 36D,
                        documentWaveY(y, h, index / 36D), canvasHeight);
            }
            addVisioPoint(points, x, y, canvasHeight);
            Shape document = polyline(page, points, x, y, w, h, canvasHeight);
            decorate(document, spec, name);
            textOnShape(document, required(node, "label"), spec);
            positionPolylineText(document, w, h);
        } else if ("PROCESS".equals(kind)) {
            Shape shape = rectangle(page, x, y, w, h, canvasHeight);
            decorate(shape, spec, name);
            textOnShape(shape, required(node, "label"), spec);
        } else if ("DATABASE".equals(kind)) {
            List<Double> points = new ArrayList<>();
            addVisioPoint(points, x + CYLINDER_CURVE, y, canvasHeight);
            addVisioPoint(points, x + w - CYLINDER_CURVE, y, canvasHeight);
            for (int index = 1; index <= 24; index++) {
                double angle = -Math.PI / 2D + Math.PI * index / 24D;
                addVisioPoint(points, x + w - CYLINDER_CURVE + CYLINDER_CURVE * Math.cos(angle),
                        y + h / 2D + h / 2D * Math.sin(angle), canvasHeight);
            }
            addVisioPoint(points, x + CYLINDER_CURVE, y + h, canvasHeight);
            for (int index = 1; index <= 24; index++) {
                double angle = Math.PI / 2D + Math.PI * index / 24D;
                addVisioPoint(points, x + CYLINDER_CURVE + CYLINDER_CURVE * Math.cos(angle),
                        y + h / 2D + h / 2D * Math.sin(angle), canvasHeight);
            }
            Shape database = polyline(page, points, x, y, w, h, canvasHeight);
            decorate(database, spec, name);
            textOnShape(database, required(node, "label"), spec);
            positionPolylineText(database, w, h);
            // 圆柱前端的内部弧独立绘制，文字仍属于圆柱主体 Shape。
            cylinderArc(page, x + w - CYLINDER_CURVE, y + h / 2,
                    CYLINDER_CURVE, h / 2, Math.PI / 2D, 3D * Math.PI / 2D,
                    spec, canvasHeight, name + "_InnerArc");
        } else {
            Shape outer = ellipse(page, x, y, w, h, canvasHeight);
            decorate(outer, spec, name);
            int square = Math.min(w, h) / 2;
            int sx = x + (w - square) / 2;
            int sy = y + (h - square) / 2;
            for (int i = 0; i < 16; i++) {
                Shape stripe = rectangle(page, sx, sy + i * square / 16, square,
                        (i + 1) * square / 16 - i * square / 16 + 1, canvasHeight);
                fill(stripe, blend(required(spec, "accentTop"), required(spec, "accentBottom"), i / 15D));
                stripe.getLine().setLineColor(new ColorValue(
                        blend(required(spec, "accentTop"), required(spec, "accentBottom"), i / 15D), 0));
                stripe.setNameU(name + "_Gradient_" + i);
            }
            line(page, sx, sy, sx + square, sy, spec, canvasHeight, name + "_Square");
            line(page, sx + square, sy, sx + square, sy + square, spec, canvasHeight, name + "_Square");
            line(page, sx + square, sy + square, sx, sy + square, spec, canvasHeight, name + "_Square");
            line(page, sx, sy + square, sx, sy, spec, canvasHeight, name + "_Square");
        }
    }

    /** 创建可填充、可直接编辑内部文字的闭合折线 Shape。 */
    private Shape polyline(Page page, List<Double> points,
                           int x, int y, int w, int h, int canvasHeight) throws Exception {
        double[] xy = new double[points.size()];
        double left = x / POINTS_PER_INCH;
        double bottom = (canvasHeight - y - h) / POINTS_PER_INCH;
        for (int index = 0; index < xy.length; index += 2) {
            xy[index] = points.get(index) - left;
            xy[index + 1] = points.get(index + 1) - bottom;
        }
        Shape result = shape(page, page.drawPolyline(xy));
        result.getXForm().getPinX().setValue((x + w / 2D) / POINTS_PER_INCH);
        result.getXForm().getPinY().setValue((canvasHeight - y - h / 2D) / POINTS_PER_INCH);
        result.getXForm().getLocPinX().setValue(w / (2D * POINTS_PER_INCH));
        result.getXForm().getLocPinY().setValue(h / (2D * POINTS_PER_INCH));
        return result;
    }

    /** 将绘图坐标添加为 Visio 页面的英寸坐标。 */
    private void addVisioPoint(List<Double> points, double x, double y, int height) {
        points.add(x / POINTS_PER_INCH);
        points.add((height - y) / POINTS_PER_INCH);
    }

    /** 折线 Shape 使用本地几何坐标，文本块也应相对节点居中。 */
    private void positionPolylineText(Shape shape, int w, int h) {
        shape.getTextXForm().getTxtPinX().setValue(w / (2D * POINTS_PER_INCH));
        shape.getTextXForm().getTxtPinY().setValue(h / (2D * POINTS_PER_INCH));
        shape.getTextXForm().getTxtWidth().setValue(w / POINTS_PER_INCH);
        shape.getTextXForm().getTxtHeight().setValue(h / POINTS_PER_INCH);
        shape.getTextXForm().getTxtLocPinX().setValue(w / (2D * POINTS_PER_INCH));
        shape.getTextXForm().getTxtLocPinY().setValue(h / (2D * POINTS_PER_INCH));
    }

    /** 绘制数据库椭圆端的局部弧。 */
    private void cylinderArc(Page page, int centerX, int centerY, int rx, int ry,
                             double start, double end, JsonNode spec, int height, String name) throws Exception {
        for (int i = 0; i < 24; i++) {
            double a = start + (end - start) * i / 24D;
            double b = start + (end - start) * (i + 1) / 24D;
            line(page, centerX + (int) Math.round(rx * Math.cos(a)), centerY + (int) Math.round(ry * Math.sin(a)),
                    centerX + (int) Math.round(rx * Math.cos(b)), centerY + (int) Math.round(ry * Math.sin(b)),
                    spec, height, name);
        }
    }

    /** 按 JSON 中的正交路径绘制无箭头的原生 Visio 线段。 */
    private void drawVisioEdge(Page page, JsonNode edge, JsonNode spec) throws Exception {
        JsonNode points = edge.get("points");
        int height = integer(spec, "height");
        String name = "Flow_" + required(edge, "from") + "_" + required(edge, "to");
        for (int index = 1; index < points.size(); index++) {
            JsonNode from = points.get(index - 1);
            JsonNode to = points.get(index);
            long id = page.drawLine(from.get(0).asDouble() / POINTS_PER_INCH,
                    (height - from.get(1).asDouble()) / POINTS_PER_INCH,
                    to.get(0).asDouble() / POINTS_PER_INCH,
                    (height - to.get(1).asDouble()) / POINTS_PER_INCH);
            Shape connector = shape(page, id);
            connector.getLine().setLineColor(new ColorValue(required(spec, "stroke"), 0));
            connector.getLine().setLineWeight(new DoubleValue(integer(spec, "strokeWidth") / POINTS_PER_INCH, 0));
            connector.setNameU(name + "_" + id);
        }
        if (edge.has("label")) {
            JsonNode center = edge.get("labelCenter");
            int labelWidth = measuredTextWidth(required(edge, "label"), spec) + 16;
            text(page, center.get(0).asInt(), center.get(1).asInt() - 12, labelWidth, 24,
                    required(edge, "label"), spec, height, "FlowLabel");
        }
    }

    /** 按模板字体计算连线标注的实际宽度，避免较长英文文字遮住图形。 */
    private int measuredTextWidth(String value, JsonNode spec) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            Font font = new Font(required(spec, "fontFamily"), Font.PLAIN, integer(spec, "fontSize"));
            return graphics.getFontMetrics(font).stringWidth(value);
        } finally {
            graphics.dispose();
        }
    }

    /** 创建有稳定名称的 Visio 线段。 */
    private void line(Page page, int x1, int y1, int x2, int y2, JsonNode spec,
                      int height, String name) throws Exception {
        long id = page.drawLine(x1 / POINTS_PER_INCH, (height - y1) / POINTS_PER_INCH,
                x2 / POINTS_PER_INCH, (height - y2) / POINTS_PER_INCH);
        Shape shape = shape(page, id);
        shape.getLine().setLineColor(new ColorValue(required(spec, "stroke"), 0));
        shape.getLine().setLineWeight(new DoubleValue(integer(spec, "strokeWidth") / POINTS_PER_INCH, 0));
        shape.setNameU(name + "_" + id);
    }

    /** 创建矩形原生 Shape。 */
    private Shape rectangle(Page page, int x, int y, int w, int h, int height) throws Exception {
        return shape(page, page.drawRectangle(x / POINTS_PER_INCH, (height - y - h) / POINTS_PER_INCH,
                (x + w) / POINTS_PER_INCH, (height - y) / POINTS_PER_INCH));
    }

    /** 创建椭圆原生 Shape。 */
    private Shape ellipse(Page page, int x, int y, int w, int h, int height) throws Exception {
        return shape(page, page.drawEllipse((x + w / 2D) / POINTS_PER_INCH,
                (height - y - h / 2D) / POINTS_PER_INCH, w / POINTS_PER_INCH, h / POINTS_PER_INCH));
    }

    /** 为外框设置白色填充与黑色线条。 */
    private void decorate(Shape shape, JsonNode spec, String name) {
        fill(shape, required(spec, "background"));
        shape.getLine().setLineColor(new ColorValue(required(spec, "stroke"), 0));
        shape.getLine().setLineWeight(new DoubleValue(integer(spec, "strokeWidth") / POINTS_PER_INCH, 0));
        shape.setNameU(name + "_" + shape.getID());
    }

    /** 为 Shape 设置不透明填充。 */
    private void fill(Shape shape, String color) {
        shape.getFill().setFillForegnd(new ColorValue(color, 0));
        shape.getFill().setFillBkgnd(new ColorValue(color, 0));
        shape.getFill().setFillForegndTrans(new DoubleValue(0D, 0));
        shape.getFill().setFillBkgndTrans(new DoubleValue(0D, 0));
        shape.getFill().setFillPattern(new IntValue(1, 0));
    }

    /** 添加居中可编辑的文字 Shape。 */
    private void text(Page page, int centerX, int topY, int width, int boxHeight,
                      String value, JsonNode spec, int height, String name) throws Exception {
        Shape shape = page.addText(centerX / POINTS_PER_INCH,
                (height - topY - boxHeight / 2D) / POINTS_PER_INCH,
                width / POINTS_PER_INCH, boxHeight / POINTS_PER_INCH, "");
        shape.setNameU(name + "_" + shape.getID());
        shape.getLine().setLineColor(new ColorValue(required(spec, "background"), 0));
        if ("FlowLabel".equals(name)) {
            shape.getFill().setFillForegnd(new ColorValue(required(spec, "background"), 0));
        }
        textOnShape(shape, value, spec);
    }

    /** 将文字和字体样式绑定到节点自身，而不是覆盖一个额外文本框。 */
    private void textOnShape(Shape shape, String value, JsonNode spec) {
        shape.getTextBlock().setLeftMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setRightMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setTopMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setBottomMargin(new DoubleValue(0D, 0));
        shape.getTextBlock().setVerticalAlign(new VerticalAlign(VerticalAlignValue.MIDDLE));
        shape.setText(new Text());
        shape.getParas().clear();
        shape.getChars().clear();
        Para para = new Para();
        para.setIX(0);
        para.setHorzAlign(new HorzAlign(HorzAlignValue.CENTER));
        shape.getParas().add(para);
        Char character = new Char();
        character.setIX(0);
        character.setFontName(new StrValue(required(spec, "fontFamily")));
        character.setAsianFontName(new StrValue(required(spec, "fontFamily")));
        character.setColor(new ColorValue(required(spec, "stroke"), 0));
        character.setSize(new DoubleValue(integer(spec, "fontSize") / POINTS_PER_INCH, 0));
        shape.getChars().add(character);
        shape.getText().getValue().add(new Pp(0));
        shape.getText().getValue().add(new Cp(0));
        shape.getText().getValue().add(new Txt(value));
    }

    /** 按 Shape ID 获取刚创建的 Visio 对象。 */
    private Shape shape(Page page, long id) {
        for (int i = 0; i < page.getShapes().getCount(); i++) {
            Shape value = page.getShapes().get(i);
            if (value.getID() == id) return value;
        }
        throw new IllegalStateException("Visio shape not found: " + id);
    }

    /** 文档底边采用参考图中一高一低的柔和波浪。 */
    private int documentWaveY(int y, int h, double fraction) {
        return y + h - DOCUMENT_WAVE - (int) Math.round(DOCUMENT_WAVE * Math.sin(2D * Math.PI * fraction));
    }

    /** 根据同一个 JSON 定义生成干净白底的 PNG 预览。 */
    private void preview(JsonNode spec, Path target) throws IOException {
        int width = integer(spec, "width");
        int height = integer(spec, "height");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.decode(required(spec, "background")));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.decode(required(spec, "stroke")));
            graphics.setStroke(new BasicStroke(integer(spec, "strokeWidth"), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            for (JsonNode edge : spec.get("edges")) drawPreviewEdge(graphics, edge, spec);
            for (JsonNode node : spec.get("nodes")) drawPreviewNode(graphics, node, spec);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", target.toFile());
    }

    /** 绘制 PNG 中与 Visio 一致的无箭头正交数据流。 */
    private void drawPreviewEdge(Graphics2D graphics, JsonNode edge, JsonNode spec) {
        JsonNode points = edge.get("points");
        Path2D path = new Path2D.Double();
        path.moveTo(points.get(0).get(0).asInt(), points.get(0).get(1).asInt());
        for (int i = 1; i < points.size(); i++) {
            path.lineTo(points.get(i).get(0).asInt(), points.get(i).get(1).asInt());
        }
        graphics.draw(path);
        if (edge.has("label")) {
            JsonNode center = edge.get("labelCenter");
            String label = required(edge, "label");
            Font font = new Font(required(spec, "fontFamily"), Font.PLAIN, integer(spec, "fontSize"));
            int labelWidth = graphics.getFontMetrics(font).stringWidth(label) + 16;
            int x = center.get(0).asInt();
            int y = center.get(1).asInt();
            graphics.setColor(Color.decode(required(spec, "background")));
            graphics.fillRect(x - labelWidth / 2, y - 12, labelWidth, 24);
            centeredText(graphics, label, x, y, spec);
        }
    }

    /** 绘制 PNG 中与可编辑 Visio 对象同坐标的图形。 */
    private void drawPreviewNode(Graphics2D graphics, JsonNode node, JsonNode spec) {
        int x = integer(node, "x");
        int y = integer(node, "y");
        int w = integer(node, "width");
        int h = integer(node, "height");
        String kind = required(node, "kind");
        if ("DOCUMENT".equals(kind)) {
            Path2D outline = new Path2D.Double();
            outline.moveTo(x, y + h - DOCUMENT_WAVE);
            outline.lineTo(x, y);
            outline.lineTo(x + w, y);
            outline.lineTo(x + w, y + h - DOCUMENT_WAVE);
            for (int i = 36; i >= 0; i--) {
                double fraction = i / 36D;
                outline.lineTo(x + w * fraction, documentWaveY(y, h, fraction));
            }
            graphics.setColor(Color.decode(required(spec, "background")));
            graphics.fill(outline);
            graphics.setColor(Color.decode(required(spec, "stroke")));
            graphics.draw(outline);
        } else if ("PROCESS".equals(kind)) {
            graphics.setColor(Color.decode(required(spec, "background")));
            graphics.fillRect(x, y, w, h);
            graphics.setColor(Color.decode(required(spec, "stroke")));
            graphics.drawRect(x, y, w, h);
        } else if ("DATABASE".equals(kind)) {
            graphics.setColor(Color.decode(required(spec, "background")));
            graphics.fillRect(x, y, w, h);
            graphics.setColor(Color.decode(required(spec, "stroke")));
            graphics.drawLine(x + CYLINDER_CURVE, y, x + w - CYLINDER_CURVE, y);
            graphics.drawLine(x + CYLINDER_CURVE, y + h, x + w - CYLINDER_CURVE, y + h);
            drawPreviewCylinderArc(graphics, x + CYLINDER_CURVE, y + h / 2, CYLINDER_CURVE, h / 2,
                    Math.PI / 2, 3 * Math.PI / 2);
            drawPreviewCylinderArc(graphics, x + w - CYLINDER_CURVE, y + h / 2, CYLINDER_CURVE, h / 2,
                    -Math.PI / 2, 3 * Math.PI / 2);
        } else {
            graphics.setColor(Color.decode(required(spec, "background")));
            graphics.fill(new Ellipse2D.Double(x, y, w, h));
            graphics.setColor(Color.decode(required(spec, "stroke")));
            graphics.draw(new Ellipse2D.Double(x, y, w, h));
            int square = Math.min(w, h) / 2;
            int sx = x + (w - square) / 2;
            int sy = y + (h - square) / 2;
            graphics.setPaint(new GradientPaint(sx, sy, Color.decode(required(spec, "accentTop")),
                    sx, sy + square, Color.decode(required(spec, "accentBottom"))));
            graphics.fillRect(sx, sy, square, square);
            graphics.setPaint(Color.decode(required(spec, "stroke")));
            graphics.drawRect(sx, sy, square, square);
        }
        if (!"CONTROL_ICON".equals(kind)) {
            centeredText(graphics, required(node, "label"), x + w / 2, y + h / 2, spec);
        }
    }

    /** 在预览图中连接数据库圆柱的弧形轮廓。 */
    private void drawPreviewCylinderArc(Graphics2D graphics, int cx, int cy, int rx, int ry,
                                        double start, double end) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i <= 24; i++) {
            double angle = start + (end - start) * i / 24D;
            double px = cx + rx * Math.cos(angle);
            double py = cy + ry * Math.sin(angle);
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        graphics.draw(path);
    }

    /** 使用 JSON 字体与黑色在给定中心点绘制文字。 */
    private void centeredText(Graphics2D graphics, String value, int centerX, int centerY, JsonNode spec) {
        Font font = new Font(required(spec, "fontFamily"), Font.PLAIN, integer(spec, "fontSize"));
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        int x = centerX - metrics.stringWidth(value) / 2;
        int y = centerY + (metrics.getAscent() - metrics.getDescent()) / 2;
        graphics.setColor(Color.decode(required(spec, "stroke")));
        graphics.drawString(value, x, y);
    }

    /** 对渐变方块的两个端点颜色进行线性插值。 */
    private String blend(String start, String end, double ratio) {
        Color a = Color.decode(start);
        Color b = Color.decode(end);
        int red = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * ratio);
        int green = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * ratio);
        int blue = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * ratio);
        return String.format("#%02X%02X%02X", red, green, blue);
    }
}
