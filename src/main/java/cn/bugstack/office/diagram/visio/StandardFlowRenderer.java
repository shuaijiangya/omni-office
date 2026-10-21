package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramType;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.SaveFileFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 基于 Visio 原生“开始/结束、流程、动态连接线”母版生成库管理流程图。
 *
 * <p>母版来自用户提供的 Visio 文件，已移除个人文档属性。一个有向关系对应一个
 * {@code Dynamic connector} Shape，拐角保存在该 Shape 的 Geometry 中，而不是
 * 拼接多条直线。业务输入只决定功能名称及数据节点数量。</p>
 */
public final class StandardFlowRenderer {

    /** 标准流程图母版资源。 */
    private static final String TEMPLATE = "/diagram/visio-standard-flow-template.vsdx";
    /** Visio 2012 XML 命名空间。 */
    private static final String VISIO = "http://schemas.microsoft.com/office/visio/2012/main";
    /** 开始/结束节点宽度，单位为英寸。 */
    private static final double TERMINAL_WIDTH = 1.4;
    /** 开始/结束节点高度，单位为英寸。 */
    private static final double TERMINAL_HEIGHT = 0.55;
    /** 业务节点最小宽度，单位为英寸。 */
    private static final double MIN_PROCESS_WIDTH = 1.6;
    /** 业务节点最大宽度，单位为英寸。 */
    private static final double MAX_PROCESS_WIDTH = 3.8;
    /** JSON 读取器。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 按标准 Visio 母版创建“菜单栏→功能→数据项→增删改查→结束”的可编辑流程图。
     *
     * @param inputJson 含 {@code function} 与非空 {@code inputs} 数组的业务 JSON
     * @param vsdxPath 输出 VSDX 路径
     * @return VSDX 与 PNG 预览
     * @throws Exception JSON、母版或输出文件处理失败时抛出
     */
    public VisioDiagramArtifact render(Path inputJson, Path vsdxPath) throws Exception {
        JsonNode data = mapper.readTree(inputJson.toFile());
        List<String> labels = validate(data);
        Layout layout = layout(labels);
        Path absolute = vsdxPath.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Map<String, byte[]> parts = readTemplate();
        resizeMaster(parts, "visio/masters/master2.xml", TERMINAL_WIDTH, TERMINAL_HEIGHT, true);
        resizeMaster(parts, "visio/masters/master3.xml", layout.processWidth, layout.processHeight, false);
        Document page = parse(parts.get("visio/pages/page1.xml"));
        buildPage(page, labels, layout);
        parts.put("visio/pages/page1.xml", serialize(page));
        Document pages = parse(parts.get("visio/pages/pages.xml"));
        resizePage(pages, layout);
        parts.put("visio/pages/pages.xml", serialize(pages));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(absolute))) {
            for (Map.Entry<String, byte[]> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue());
                zip.closeEntry();
            }
        }
        String name = absolute.getFileName().toString();
        String stem = name.endsWith(".vsdx") ? name.substring(0, name.length() - 5) : name;
        Path preview = absolute.resolveSibling(stem + ".preview.png");
        Diagram diagram = new Diagram(absolute.toString());
        try {
            diagram.save(preview.toString(), SaveFileFormat.PNG);
        } finally {
            diagram.dispose();
        }
        return new VisioDiagramArtifact(DiagramType.FLOW, absolute, preview);
    }

    /** 只接受业务字段；输入数量保持动态，不把母版样式交给调用方配置。 */
    private List<String> validate(JsonNode data) {
        if (data == null || !data.isObject() || !data.has("function") || !data.has("inputs")
                || data.size() != (data.has("persistenceLabel") ? 3 : 2)
                || !data.get("inputs").isArray() || data.get("inputs").isEmpty()) {
            throw new IllegalArgumentException("standard flow requires function and non-empty inputs");
        }
        List<String> labels = new ArrayList<>();
        labels.add(label(data.get("function")));
        for (JsonNode input : data.get("inputs")) labels.add(label(input));
        return labels;
    }

    /** 确认节点文字不会溢出标准流程节点。 */
    private String label(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()
                || node.asText().length() > 32) {
            throw new IllegalArgumentException("invalid standard flow node label");
        }
        return node.asText().trim();
    }

    /** 从类路径读取去标识化的 Visio 原生母版包。 */
    private Map<String, byte[]> readTemplate() throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (InputStream resource = StandardFlowRenderer.class.getResourceAsStream(TEMPLATE)) {
            if (resource == null) throw new IllegalStateException("standard Visio flow template is missing");
            try (ZipInputStream zip = new ZipInputStream(resource)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.isDirectory()) parts.put(entry.getName(), zip.readAllBytes());
                    zip.closeEntry();
                }
            }
        }
        if (!parts.containsKey("visio/pages/page1.xml") || !parts.containsKey("visio/pages/pages.xml")) {
            throw new IllegalStateException("standard Visio flow template is incomplete");
        }
        return parts;
    }

    /** 同步更新 Visio 母版里的数值、几何及文字框，兼容不计算母版公式的预览器。 */
    private void resizeMaster(Map<String, byte[]> parts, String path,
                              double width, double height, boolean terminal) throws Exception {
        Document document = parse(parts.get(path));
        Element shape = first(document.getDocumentElement(), "Shape");
        fixedCell(shape, "Width", width);
        fixedCell(shape, "Height", height);
        for (String horizontal : new String[]{"LocPinX", "TxtPinX", "TxtLocPinX"}) {
            cell(shape, horizontal, width / 2D, null);
        }
        for (String vertical : new String[]{"LocPinY", "TxtPinY", "TxtLocPinY"}) {
            cell(shape, vertical, height / 2D, null);
        }
        cell(shape, "TxtWidth", width, null);
        cell(shape, "TxtHeight", height, null);

        Element connection = section(shape, "Connection");
        if (terminal) {
            masterPort(connection, 0, width / 2D, 0D);
            masterPort(connection, 1, width / 2D, height);
            masterPort(connection, 2, 0D, height / 2D);
            masterPort(connection, 3, width, height / 2D);
            double round = Math.min(height / 2D, width / 4D);
            Element geometry = section(shape, "Geometry");
            masterGeometry(geometry, 1, round, 0D);
            masterGeometry(geometry, 2, width - round, 0D);
            masterGeometry(geometry, 3, width - round, height);
            cell(rowByIndex(geometry, 3), "A", width, null);
            cell(rowByIndex(geometry, 3), "B", height / 2D, null);
            cell(rowByIndex(geometry, 3), "D", 2D * round / height, null);
            masterGeometry(geometry, 4, round, height);
            masterGeometry(geometry, 5, round, 0D);
            cell(rowByIndex(geometry, 5), "B", height / 2D, null);
            cell(rowByIndex(geometry, 5), "D", 2D * round / height, null);
        } else {
            masterPort(connection, 0, 0D, height / 2D);
            masterPort(connection, 1, width, height / 2D);
            masterPort(connection, 2, width / 2D, 0D);
            masterPort(connection, 3, width / 2D, height);
            Element geometry = section(shape, "Geometry");
            masterGeometry(geometry, 1, 0D, 0D);
            masterGeometry(geometry, 2, width, 0D);
            masterGeometry(geometry, 3, width, height);
            masterGeometry(geometry, 4, 0D, height);
            masterGeometry(geometry, 5, 0D, 0D);
        }
        parts.put(path, serialize(document));
    }

    /** 固定母版基础尺寸，避免旧 User.DefaultWidth 公式覆盖新的宽高。 */
    private void fixedCell(Element shape, String name, double value) {
        cell(shape, name, value, null);
        for (Node child = shape.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && "Cell".equals(child.getLocalName())
                    && name.equals(((Element) child).getAttribute("N"))) {
                ((Element) child).removeAttribute("F");
                return;
            }
        }
    }

    /** 更新母版的一处胶合连接点。 */
    private void masterPort(Element connection, int index, double x, double y) {
        Element port = rowByIndex(connection, index);
        cell(port, "X", x, null);
        cell(port, "Y", y, null);
    }

    /** 更新母版几何点的缓存值，公式仍由 Visio 在交互编辑时使用。 */
    private void masterGeometry(Element geometry, int index, double x, double y) {
        Element point = rowByIndex(geometry, index);
        cell(point, "X", x, null);
        cell(point, "Y", y, null);
    }

    /** 克隆标准文件里的节点与真正的动态连接线，并重建连接关系。 */
    private void buildPage(Document document, List<String> labels, Layout layout) {
        Element shapes = first(document.getDocumentElement(), "Shapes");
        Element connects = first(document.getDocumentElement(), "Connects");
        Element startPrototype = byId(shapes, "31");
        Element functionPrototype = byId(shapes, "32");
        Element dataPrototype = byId(shapes, "33");
        Element endPrototype = byId(shapes, "35");
        Element straightPrototype = byId(shapes, "36");
        Element leftPrototype = byId(shapes, "37");
        Element rightPrototype = byId(shapes, "38");
        clear(shapes);
        clear(connects);

        int count = labels.size() - 1;
        double center = layout.pageWidth / 2D;
        double startY = layout.startY;
        double functionY = layout.functionY;
        double dataY = layout.dataY;
        double operationsY = layout.operationsY;
        double operationsWidth = layout.operationsWidth;
        double operationsHeight = layout.operationsHeight;
        double endY = layout.endY;
        int nextId = 31;
        Element start = node(shapes, startPrototype, nextId++, "StandardFlow_Start", center, startY,
                TERMINAL_WIDTH, TERMINAL_HEIGHT, "菜单栏");
        Element function = node(shapes, functionPrototype, nextId++, "StandardFlow_Function",
                center, functionY, layout.processWidth, layout.processHeight, labels.get(0));
        List<Element> dataNodes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double x = dataX(layout, index);
            dataNodes.add(node(shapes, dataPrototype, nextId++, "StandardFlow_Data" + (index + 1),
                    x, dataY, layout.processWidth, layout.processHeight, labels.get(index + 1)));
        }
        Element operations = plainBox(shapes, nextId++, "StandardFlow_Operations",
                center, operationsY, operationsWidth, operationsHeight, null, false);
        for (int index = 0; index < 4; index++) {
            double cellX = center - operationsWidth / 2D + operationsWidth * (index + 0.5D) / 4D;
            plainBox(shapes, nextId++, "StandardFlow_Operation" + (index + 1), cellX,
                    operationsY, operationsWidth / 4D, operationsHeight,
                    "增删改查".substring(index, index + 1), true);
        }
        Element end = node(shapes, endPrototype, nextId++, "StandardFlow_End", center, endY,
                TERMINAL_WIDTH, TERMINAL_HEIGHT, "结束");
        for (int index = 0; index < count; index++) {
            connectionPort(operations, index, operationsWidth * (index + 1D) / (count + 1D),
                    operationsHeight);
        }
        connectionPort(operations, count, operationsWidth / 2D, 0D);

        Element firstLine = connector(shapes, straightPrototype, nextId++, "StartToFunction",
                center, startY - TERMINAL_HEIGHT / 2D,
                center, functionY + layout.processHeight / 2D, Double.NaN);
        glue(connects, firstLine, start, "BeginX", "Connections.X1", 100);
        glue(connects, firstLine, function, "EndX", "PinX", 3);
        endpointFormula(firstLine, start, true, "Connections.X1");
        walkGlue(firstLine, start, function);

        for (int index = 0; index < count; index++) {
            Element data = dataNodes.get(index);
            double x = dataX(layout, index);
            boolean left = x < center - 0.000001;
            boolean right = x > center + 0.000001;
            Element toData = connector(shapes, left ? leftPrototype : right ? rightPrototype : straightPrototype,
                    nextId++, "FunctionToData" + (index + 1), center,
                    functionY - layout.processHeight / 2D,
                    x, dataY + layout.processHeight / 2D,
                    (functionY + dataY) / 2D);
            glue(connects, toData, function, "BeginX", "Connections.X3", 102);
            glue(connects, toData, data, "EndX", "Connections.X4", 103);
            endpointFormula(toData, function, true, "Connections.X3");
            endpointFormula(toData, data, false, "Connections.X4");

            double operationPortX = center - operationsWidth / 2D
                    + operationsWidth * (index + 1D) / (count + 1D);
            Element toOperations = connector(shapes,
                    left ? leftPrototype : right ? rightPrototype : straightPrototype,
                    nextId++, "Data" + (index + 1) + "ToOperations", x,
                    dataY - layout.processHeight / 2D, operationPortX,
                    operationsY + operationsHeight / 2D,
                    (dataY - layout.processHeight / 2D
                            + operationsY + operationsHeight / 2D) / 2D);
            String port = "Connections.X" + (index + 1);
            glue(connects, toOperations, data, "BeginX", "Connections.X3", 102);
            glue(connects, toOperations, operations, "EndX", port, 100 + index);
            endpointFormula(toOperations, data, true, "Connections.X3");
            endpointFormula(toOperations, operations, false, port);
        }
        Element toEnd = connector(shapes, straightPrototype, nextId, "OperationsToEnd",
                center, operationsY - operationsHeight / 2D,
                center, endY + TERMINAL_HEIGHT / 2D, Double.NaN);
        String operationsPort = "Connections.X" + (count + 1);
        glue(connects, toEnd, operations, "BeginX", operationsPort, 100 + count);
        glue(connects, toEnd, end, "EndX", "Connections.X2", 101);
        endpointFormula(toEnd, operations, true, operationsPort);
        endpointFormula(toEnd, end, false, "Connections.X2");
    }

    /** 构建“增删改查”外框锚点及四个可独立编辑文字的矩形单元。 */
    private Element plainBox(Element shapes, int id, String name, double x, double y,
                             double width, double height, String label, boolean visible) {
        Document document = shapes.getOwnerDocument();
        Element shape = document.createElementNS(VISIO, "Shape");
        shape.setAttribute("ID", Integer.toString(id));
        shape.setAttribute("NameU", name);
        shape.setAttribute("Type", "Shape");
        cell(shape, "PinX", x, null);
        cell(shape, "PinY", y, null);
        cell(shape, "Width", width, null);
        cell(shape, "Height", height, null);
        cell(shape, "LocPinX", width / 2D, "Width*0.5");
        cell(shape, "LocPinY", height / 2D, "Height*0.5");
        cell(shape, "LinePattern", visible ? 1 : 0, null);
        cell(shape, "FillPattern", visible ? 1 : 0, null);
        cell(shape, "LineColor", "#000000", null);
        cell(shape, "LineWeight", 1D / 72D, null);
        cell(shape, "FillForegnd", "#FFFFFF", null);
        Element geometry = document.createElementNS(VISIO, "Section");
        geometry.setAttribute("N", "Geometry");
        geometry.setAttribute("IX", "0");
        rectangleRow(geometry, "MoveTo", 1, 0, 0);
        rectangleRow(geometry, "LineTo", 2, width, 0);
        rectangleRow(geometry, "LineTo", 3, width, height);
        rectangleRow(geometry, "LineTo", 4, 0, height);
        rectangleRow(geometry, "LineTo", 5, 0, 0);
        shape.appendChild(geometry);
        if (label != null) {
            cell(shape, "VerticalAlign", 1, null);
            Element characters = document.createElementNS(VISIO, "Section");
            characters.setAttribute("N", "Character");
            Element character = document.createElementNS(VISIO, "Row");
            character.setAttribute("IX", "0");
            cell(character, "Font", "SimHei", null);
            cell(character, "AsianFont", "SimHei", null);
            cell(character, "Size", 12D / 72D, null);
            cell(character, "Color", "#000000", null);
            characters.appendChild(character);
            shape.appendChild(characters);
            Element paragraphs = document.createElementNS(VISIO, "Section");
            paragraphs.setAttribute("N", "Paragraph");
            Element paragraph = document.createElementNS(VISIO, "Row");
            paragraph.setAttribute("IX", "0");
            cell(paragraph, "HorzAlign", 1, null);
            paragraphs.appendChild(paragraph);
            shape.appendChild(paragraphs);
            Element text = document.createElementNS(VISIO, "Text");
            Element cp = document.createElementNS(VISIO, "cp");
            cp.setAttribute("IX", "0");
            text.appendChild(cp);
            text.appendChild(document.createTextNode(label));
            shape.appendChild(text);
        }
        shapes.appendChild(shape);
        return shape;
    }

    /** 添加矩形轮廓的一处原生 Geometry 顶点。 */
    private void rectangleRow(Element geometry, String type, int index, double x, double y) {
        Element row = geometry.getOwnerDocument().createElementNS(VISIO, "Row");
        row.setAttribute("T", type);
        row.setAttribute("IX", Integer.toString(index));
        cell(row, "X", x, null);
        cell(row, "Y", y, null);
        geometry.appendChild(row);
    }

    /** 为数据项与操作区的每根连接器提供独立的 Visio 胶合端口。 */
    private void connectionPort(Element shape, int index, double x, double y) {
        Element ports = optionalSection(shape, "Connection");
        if (ports == null) {
            ports = shape.getOwnerDocument().createElementNS(VISIO, "Section");
            ports.setAttribute("N", "Connection");
            shape.appendChild(ports);
        }
        Element row = shape.getOwnerDocument().createElementNS(VISIO, "Row");
        row.setAttribute("T", "Connection");
        row.setAttribute("IX", Integer.toString(index));
        cell(row, "X", x, null);
        cell(row, "Y", y, null);
        ports.appendChild(row);
    }

    /** 创建一个继承原生母版的可编辑节点。 */
    private Element node(Element shapes, Element prototype, int id, String name,
                         double x, double y, double width, double height, String value) {
        Element node = (Element) prototype.cloneNode(true);
        node.setAttribute("ID", Integer.toString(id));
        node.setAttribute("NameU", name);
        cell(node, "PinX", x, null);
        cell(node, "PinY", y, null);
        cell(node, "Width", width, null);
        cell(node, "Height", height, null);
        cell(node, "LocPinX", width / 2D, "Width*0.5");
        cell(node, "LocPinY", height / 2D, "Height*0.5");
        Element text = first(node, "Text");
        for (Node child = text.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE) child.setTextContent(value + "\n");
        }
        Element character = first(node, "Section");
        if (character != null && "Character".equals(character.getAttribute("N"))) {
            Element row = first(character, "Row");
            cell(row, "AsianFont", "SimHei", null);
            cell(row, "Font", "SimHei", null);
            cell(row, "Size", 12D / 72D, null);
        }
        shapes.appendChild(node);
        return node;
    }

    /** 保留标准动态连接线母版；所有折角均位于该单个 Shape 内。 */
    private Element connector(Element shapes, Element prototype, int id, String name,
                              double beginX, double beginY, double endX, double endY, double bendY) {
        Element line = (Element) prototype.cloneNode(true);
        line.setAttribute("ID", Integer.toString(id));
        line.setAttribute("NameU", "StandardFlow_" + name);
        cell(line, "BeginX", beginX, null);
        cell(line, "BeginY", beginY, null);
        cell(line, "EndX", endX, null);
        cell(line, "EndY", endY, null);
        cell(line, "PinX", (beginX + endX) / 2D, "GUARD((BeginX+EndX)/2)");
        cell(line, "PinY", (beginY + endY) / 2D, "GUARD((BeginY+EndY)/2)");
        cell(line, "Width", Math.abs(endX - beginX) < 0.000001 ? 0.25 : endX - beginX,
                Math.abs(endX - beginX) < 0.000001 ? "GUARD(0.25DL)" : "GUARD(EndX-BeginX)");
        cell(line, "Height", endY - beginY, "GUARD(EndY-BeginY)");
        cell(line, "LocPinX", Math.abs(endX - beginX) < 0.000001 ? 0.125 : (endX - beginX) / 2D,
                "GUARD(Width*0.5)");
        cell(line, "LocPinY", (endY - beginY) / 2D, "GUARD(Height*0.5)");
        cell(line, "EndArrow", 13, null);
        Element geometry = section(line, "Geometry");
        if (Math.abs(endX - beginX) < 0.000001) {
            // The authored vertical connector suppresses the inherited third master row.
            row(geometry, 2, 0.125, endY - beginY, false);
            row(geometry, 3, 0, 0, true);
            Element fourth = rowByIndex(geometry, 4);
            if (fourth != null) geometry.removeChild(fourth);
        } else if (name.startsWith("FunctionToData") || Math.abs(bendY - endY) > 0.000001) {
            row(geometry, 2, 0, bendY - beginY, false);
            row(geometry, 3, endX - beginX, bendY - beginY, false);
            row(geometry, 4, endX - beginX, endY - beginY, false);
        } else {
            row(geometry, 2, 0, bendY - beginY, false);
            row(geometry, 3, endX - beginX, endY - beginY, false);
            Element fourth = rowByIndex(geometry, 4);
            if (fourth != null) geometry.removeChild(fourth);
        }
        shapes.appendChild(line);
        return line;
    }

    /** 记录原生 Visio 连线与目标 Shape 的连接关系。 */
    private void glue(Element connects, Element line, Element target, String fromCell,
                      String toCell, int toPart) {
        Element connect = connects.getOwnerDocument().createElementNS(VISIO, "Connect");
        connect.setAttribute("FromSheet", line.getAttribute("ID"));
        connect.setAttribute("FromCell", fromCell);
        connect.setAttribute("FromPart", "BeginX".equals(fromCell) ? "9" : "12");
        connect.setAttribute("ToSheet", target.getAttribute("ID"));
        connect.setAttribute("ToCell", toCell);
        connect.setAttribute("ToPart", Integer.toString(toPart));
        connects.appendChild(connect);
    }

    /** 让连接器端点引用 Visio 节点的原生连接点。 */
    private void endpointFormula(Element line, Element target, boolean begin, String port) {
        String number = port.substring(port.lastIndexOf('X') + 1);
        String formula = "PAR(PNT(Sheet." + target.getAttribute("ID") + "!" + port
                + ",Sheet." + target.getAttribute("ID") + "!Connections.Y" + number + "))";
        cell(line, begin ? "BeginX" : "EndX", null, formula);
        cell(line, begin ? "BeginY" : "EndY", null, formula);
    }

    /** 对结束节点采用母版自带的 walk-glue 路由，而不是固定的单一端口。 */
    private void walkGlue(Element line, Element source, Element target) {
        cell(line, "EndX", null, "_WALKGLUE(EndTrigger,BegTrigger,WalkPreference)");
        cell(line, "EndY", null, "_WALKGLUE(EndTrigger,BegTrigger,WalkPreference)");
        cell(line, "BegTrigger", 2, "_XFTRIGGER(Sheet." + source.getAttribute("ID") + "!EventXFMod)");
        cell(line, "EndTrigger", 2, "_XFTRIGGER(Sheet." + target.getAttribute("ID") + "!EventXFMod)");
    }

    /** 随数据项数量扩大 Visio 页面，使所有流程节点位于页面内。 */
    private void resizePage(Document pages, Layout layout) {
        Element pageSheet = first(pages.getDocumentElement(), "PageSheet");
        cell(pageSheet, "PageWidth", layout.pageWidth, null);
        cell(pageSheet, "PageHeight", layout.pageHeight, null);
        Element page = first(pages.getDocumentElement(), "Page");
        page.setAttribute("ViewCenterX", Double.toString(layout.pageWidth / 2D));
        page.setAttribute("ViewCenterY", Double.toString(layout.pageHeight / 2D));
        page.setAttribute("ViewScale", Double.toString(Math.min(1D, 10.5D / layout.pageWidth)));
    }

    /** 根据 JSON 节点文字计算统一节点尺寸和画布边界，样式无需在 JSON 中配置。 */
    private Layout layout(List<String> labels) {
        double longest = labels.stream().mapToDouble(this::visualUnits).max().orElse(0D);
        double processWidth = Math.max(MIN_PROCESS_WIDTH,
                Math.min(MAX_PROCESS_WIDTH, longest * 0.17D + 0.55D));
        int lines = Math.max(1, (int) Math.ceil(longest * 0.17D / (processWidth - 0.55D)));
        double processHeight = 0.65D + (lines - 1) * 0.32D;
        // 原始截图中的四栏框近似正方形，尺寸固定，不随功能名或数据项伸缩。
        double operationsWidth = 2.7D;
        double operationsHeight = 2.9D;
        double gap = processWidth + 0.75D;
        int count = labels.size() - 1;
        double pageWidth = Math.max(6.5D,
                Math.max((count - 1D) * gap + processWidth + 1.2D, operationsWidth + 1.2D));
        double endY = 0.78D;
        double operationsY = endY + TERMINAL_HEIGHT / 2D + 0.55D + operationsHeight / 2D;
        double dataY = operationsY + operationsHeight / 2D + 0.65D + processHeight / 2D;
        double functionY = dataY + processHeight + 0.7D;
        double startY = functionY + processHeight / 2D + 0.65D + TERMINAL_HEIGHT / 2D;
        double pageHeight = startY + TERMINAL_HEIGHT / 2D + 0.75D;
        return new Layout(pageWidth, pageHeight, gap, processWidth, processHeight,
                operationsWidth, operationsHeight, startY, functionY, dataY, operationsY, endY, count);
    }

    /** 估算中英文混排占用的水平字宽。 */
    private double visualUnits(String label) {
        return label.codePoints().mapToDouble(codePoint -> codePoint < 128 ? 0.58D : 1D).sum();
    }

    /** 返回指定数据节点在单排行列中的 X 坐标。 */
    private double dataX(Layout layout, int index) {
        return layout.pageWidth / 2D
                + (index - (layout.count - 1D) / 2D) * layout.dataGap;
    }

    /** 页面与节点尺寸；所有位置在同一坐标系中计算，避免线条和元素错位。 */
    private static final class Layout {
        private final double pageWidth;
        private final double pageHeight;
        private final double dataGap;
        private final double processWidth;
        private final double processHeight;
        private final double operationsWidth;
        private final double operationsHeight;
        private final double startY;
        private final double functionY;
        private final double dataY;
        private final double operationsY;
        private final double endY;
        private final int count;

        private Layout(double pageWidth, double pageHeight, double dataGap,
                       double processWidth, double processHeight,
                       double operationsWidth, double operationsHeight,
                       double startY, double functionY, double dataY,
                       double operationsY, double endY, int count) {
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.dataGap = dataGap;
            this.processWidth = processWidth;
            this.processHeight = processHeight;
            this.operationsWidth = operationsWidth;
            this.operationsHeight = operationsHeight;
            this.startY = startY;
            this.functionY = functionY;
            this.dataY = dataY;
            this.operationsY = operationsY;
            this.endY = endY;
            this.count = count;
        }

    }

    /** 读取 XML；禁用外部实体。 */
    private Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    /** 序列化更新后的 Visio 页面 XML。 */
    private byte[] serialize(Document xml) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(xml), new StreamResult(output));
        return output.toByteArray();
    }

    /** 按名称查找第一个后代元素。 */
    private Element first(Element parent, String name) {
        NodeList matches = parent.getElementsByTagNameNS(VISIO, name);
        return matches.getLength() == 0 ? null : (Element) matches.item(0);
    }

    /** 按 ID 查找模板中的原生形状。 */
    private Element byId(Element shapes, String id) {
        NodeList all = shapes.getElementsByTagNameNS(VISIO, "Shape");
        for (int index = 0; index < all.getLength(); index++) {
            Element shape = (Element) all.item(index);
            if (id.equals(shape.getAttribute("ID"))) return shape;
        }
        throw new IllegalStateException("Visio template shape not found: " + id);
    }

    /** 清空旧实例，保留母版和文档样式。 */
    private void clear(Element parent) {
        while (parent.hasChildNodes()) parent.removeChild(parent.getFirstChild());
    }

    /** 查找或设置一个 ShapeSheet Cell。 */
    private void cell(Element parent, String name, Object value, String formula) {
        Element found = null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && "Cell".equals(child.getLocalName())
                    && name.equals(((Element) child).getAttribute("N"))) {
                found = (Element) child;
                break;
            }
        }
        if (found == null) {
            found = parent.getOwnerDocument().createElementNS(VISIO, "Cell");
            found.setAttribute("N", name);
            parent.appendChild(found);
        }
        if (value != null) found.setAttribute("V", value.toString());
        if (formula != null) found.setAttribute("F", formula);
    }

    /** 根据名称查找形状内的 Section。 */
    private Element section(Element shape, String name) {
        Element found = optionalSection(shape, name);
        if (found != null) return found;
        throw new IllegalStateException("Visio template section not found: " + name);
    }

    /** 查找可选的 ShapeSheet Section。 */
    private Element optionalSection(Element shape, String name) {
        NodeList sections = shape.getElementsByTagNameNS(VISIO, "Section");
        for (int index = 0; index < sections.getLength(); index++) {
            Element result = (Element) sections.item(index);
            if (name.equals(result.getAttribute("N"))) return result;
        }
        return null;
    }

    /** 查找指定几何行。 */
    private Element rowByIndex(Element section, int index) {
        for (Node child = section.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && "Row".equals(child.getLocalName())
                    && Integer.toString(index).equals(((Element) child).getAttribute("IX"))) {
                return (Element) child;
            }
        }
        return null;
    }

    /** 设置连接器内的一处折点，保持顺序唯一的 Geometry IX。 */
    private void row(Element geometry, int index, double x, double y, boolean deleted) {
        Element row = rowByIndex(geometry, index);
        if (row == null) {
            row = geometry.getOwnerDocument().createElementNS(VISIO, "Row");
            row.setAttribute("T", "LineTo");
            row.setAttribute("IX", Integer.toString(index));
            geometry.appendChild(row);
        }
        if (deleted) {
            row.setAttribute("Del", "1");
        } else {
            row.removeAttribute("Del");
            cell(row, "X", x, null);
            cell(row, "Y", y, null);
        }
    }
}
