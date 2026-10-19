package cn.bugstack.application.csci;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramNodeType;
import cn.bugstack.office.diagram.model.DiagramType;
import cn.bugstack.office.diagram.visio.VsdxDiagramRenderer;
import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.Body;
import com.aspose.words.BorderType;
import com.aspose.words.Cell;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.Node;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Row;
import com.aspose.words.Run;
import com.aspose.words.Shape;
import com.aspose.words.Table;
import com.aspose.words.LineStyle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 使用原始 DOCX 的段落、表格和页面样式构建动态 CSCI 详细设计。
 *
 * <p>模板中的旧示例、旧题注字段和数据库表名均不进入最终文档。</p>
 */
public final class CsciTemplateRenderer {

    /** 负责生成可编辑 Visio 图及其预览图片。 */
    private final VsdxDiagramRenderer diagrams = new VsdxDiagramRenderer();

    /**
     * 按模板生成 Word，并在输出目录保留可编辑 VSDX 与 PNG 预览。
     *
     * @param template 用户提供的原始模板 DOCX
     * @param design 已解析的源码与 DDL 数据
     * @param output 输出 DOCX
     * @throws Exception 模板或图形生成失败时抛出
     */
    public void render(Path template, CsciDesign design, Path output) throws Exception {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(design, "design");
        Objects.requireNonNull(output, "output");
        if (design.software == null || design.modules.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个软件及一个功能模块");
        }
        AsposeWordsLicenseLoader.applyConfiguredLicense();
        Document document = new Document(template.toString());
        Template parts = Template.capture(document);
        Body body = document.getFirstSection().getBody();
        body.removeAllChildren();
        Path outputDirectory = output.toAbsolutePath().getParent();
        Files.createDirectories(outputDirectory);
        Path diagramDirectory = outputDirectory.resolve("csci-diagrams");
        Files.createDirectories(diagramDirectory);
        Append append = new Append(document, body, parts);

        append.heading(1, "CSCI详细设计");
        append.heading(2, design.software);
        int featureIndex = 0;
        for (CsciDesign.Module module : design.modules) {
            append.heading(3, module.name);
            for (CsciDesign.Feature feature : module.features) {
                featureIndex++;
                append.feature(feature, diagramDirectory, featureIndex);
            }
        }
        if (!design.databaseTables.isEmpty()) {
            append.heading(1, "数据库设计");
            append.resetCaptions();
            append.heading(2, "逻辑结构设计");
            append.heading(3, design.software + "数据库设计");
            for (CsciDesign.DbTable table : design.databaseTables) {
                append.database(table);
            }
        }
        document.updatePageLayout();
        document.save(output.toString());
        CsciDocxFinalizer.removeTrailingEmptyParagraph(output);
    }

    /** 将设计章节追加到已清空正文的模板中，并维护题注编号。 */
    private final class Append {
        /** 当前输出文档。 */
        private final Document doc;
        /** 当前输出文档的正文容器。 */
        private final Body body;
        /** 从原始模板提取的可复用样式节点。 */
        private final Template template;
        /** 当前一级章节编号。 */
        private int chapter = 0;
        /** 当前章节的表格题注编号。 */
        private int tableNumber = 0;
        /** 当前章节的图片题注编号。 */
        private int figureNumber = 0;
        /** 流程图文件名序号，避免同名方法覆盖文件。 */
        private int flowArtifactNumber = 0;

        /**
         * 创建正文追加器。
         *
         * @param doc 输出文档
         * @param body 输出正文
         * @param template 原模板中的样式节点
         */
        private Append(Document doc, Body body, Template template) {
            this.doc = doc;
            this.body = body;
            this.template = template;
        }

        /** 将表格和图片题注计数重置为当前章节起点。 */
        private void resetCaptions() {
            tableNumber = 0;
            figureNumber = 0;
        }

        /**
         * 按原模板指定级别的标题样式追加标题。
         *
         * @param level 标题级别，范围为 1 至 9
         * @param title 标题文字
         */
        private void heading(int level, String title) {
            if (level == 1) {
                chapter++;
                resetCaptions();
            }
            Paragraph paragraph = textParagraph(template.headings[level], title);
            paragraph.getParagraphFormat().setKeepWithNext(true);
            body.appendChild(paragraph);
        }

        /**
         * 追加普通正文段落。
         *
         * @param text 正文文字；空白时显示待补充
         */
        private void normal(String text) {
            body.appendChild(textParagraph(template.normal, nonEmpty(text)));
        }

        /**
         * 追加与后续图片保持同页的说明段落。
         *
         * @param text 图片前的说明文字
         */
        private void normalBeforeFigure(String text) {
            Paragraph paragraph = textParagraph(template.normal, nonEmpty(text));
            paragraph.getParagraphFormat().setKeepWithNext(true);
            body.appendChild(paragraph);
        }

        /**
         * 追加使用当前章节编号的表格题注。
         *
         * @param title 表格题注文字
         */
        private void tableCaption(String title) {
            Paragraph paragraph = textParagraph(template.tableCaption,
                    "表 " + chapter + "-" + (++tableNumber) + title);
            paragraph.getParagraphFormat().setKeepWithNext(true);
            body.appendChild(paragraph);
        }

        /**
         * 追加使用当前章节编号的图片题注。
         *
         * @param title 图片题注文字
         */
        private void figureCaption(String title) {
            body.appendChild(textParagraph(template.figureCaption,
                    "图 " + chapter + "-" + (++figureNumber) + title));
        }

        /**
         * 追加功能设计、数据结构、类图和类设计章节。
         *
         * @param feature 待渲染的功能
         * @param diagramDirectory Visio 文件输出目录
         * @param index 功能序号，用于生成唯一图文件名
         * @throws Exception 插图或 Visio 文件生成失败时抛出
         */
        private void feature(CsciDesign.Feature feature, Path diagramDirectory, int index) throws Exception {
            heading(4, feature.name);
            heading(5, "界面设计");
            String ui = feature.uiDescription == null || feature.uiDescription.isBlank()
                    ? "界面设计资料待补充。" : feature.uiDescription;
            if (feature.uiImagePng == null) normal(ui);
            else normalBeforeFigure(ui);
            if (feature.uiImagePng != null) {
                Paragraph picture = pictureParagraph();
                DocumentBuilder builder = new DocumentBuilder(doc);
                builder.moveTo(picture);
                Shape image = builder.insertImage(feature.uiImagePng);
                BufferedImage pixels = ImageIO.read(new ByteArrayInputStream(feature.uiImagePng));
                fit(image, pixels, 420, 330);
                figureCaption(feature.name + "图");
            }

            heading(5, "数据结构设计");
            heading(6, "数据结构");
            tableCaption("数据结构描述表");
            List<String[]> structures = new ArrayList<>();
            for (CsciDesign.JavaClass clazz : feature.classes) {
                structures.add(new String[]{nonEmpty(clazz.description), clazz.name, clazz.kind, "-",
                        nonEmpty(clazz.description)});
            }
            body.appendChild(table(template.tables[0], structures, 1));

            heading(6, "数据元素");
            tableCaption("数据元素描述表");
            List<String[]> elements = new ArrayList<>();
            int sequence = 0;
            for (CsciDesign.JavaClass clazz : feature.classes) {
                for (CsciDesign.Field field : clazz.fields) {
                    elements.add(new String[]{String.valueOf(++sequence), nonEmpty(field.description),
                            clazz.name + "." + field.name, field.type, nonEmpty(field.description)});
                }
            }
            body.appendChild(table(template.tables[1], elements, 1));

            heading(5, "类图");
            normalBeforeFigure(feature.name + "类图如下图所示：");
            int groups = (feature.classes.size() + 5) / 6;
            for (int group = 0; group < groups; group++) {
                List<CsciDesign.JavaClass> current = feature.classes.subList(group * 6,
                        Math.min(feature.classes.size(), (group + 1) * 6));
                VisioDiagramArtifact artifact = diagrams.render(CsciClassDiagramFactory.create(feature.name, current),
                        diagramDirectory.resolve(String.format("feature-%03d-class-%02d.vsdx", index, group + 1)));
                visioPicture(artifact, 420, 330);
                figureCaption(feature.name + "类图" + (groups > 1 ? "（" + (group + 1) + "/" + groups + "）" : ""));
            }

            heading(5, "类设计");
            for (CsciDesign.JavaClass clazz : feature.classes) {
                classDesign(feature, clazz, diagramDirectory, index);
            }
        }

        /**
         * 追加单个类的属性、操作及各方法设计。
         *
         * @param feature 类所属功能
         * @param clazz 待渲染的类
         * @param diagramDirectory Visio 文件输出目录
         * @param featureIndex 功能序号
         * @throws Exception 方法流程图生成失败时抛出
         */
        private void classDesign(CsciDesign.Feature feature, CsciDesign.JavaClass clazz,
                                 Path diagramDirectory, int featureIndex) throws Exception {
            heading(6, clazz.name);
            heading(7, "类属性描述");
            normal(feature.name + clazz.name + "类属性描述如下表所示：");
            tableCaption(clazz.name + "类属性描述表");
            List<String[]> attributes = new ArrayList<>();
            for (CsciDesign.Field field : clazz.fields) {
                attributes.add(new String[]{nonEmpty(field.description), field.name, field.type,
                        "-", "-", "-", field.visibility, nonEmpty(field.description)});
            }
            body.appendChild(table(template.tables[2], attributes, 1));

            heading(7, "类操作描述");
            normal(feature.name + clazz.name + "类操作描述如下表所示：");
            tableCaption(clazz.name + "类操作描述表");
            List<String[]> operations = new ArrayList<>();
            int sequence = 0;
            for (CsciDesign.Method method : clazz.methods) {
                if ("private".equals(method.visibility)) continue;
                operations.add(new String[]{String.valueOf(++sequence), method.visibility,
                        method.name, nonEmpty(method.description)});
            }
            body.appendChild(table(template.tables[3], operations, 1));

            for (CsciDesign.Method method : clazz.methods) {
                if ("private".equals(method.visibility)) continue;
                methodDesign(method, diagramDirectory, featureIndex);
            }
        }

        /**
         * 追加方法说明、参数表、返回值、算法及注意事项。
         *
         * @param method 待渲染的方法
         * @param diagramDirectory Visio 文件输出目录
         * @param featureIndex 功能序号
         * @throws Exception 方法流程图生成失败时抛出
         */
        private void methodDesign(CsciDesign.Method method, Path diagramDirectory,
                                  int featureIndex) throws Exception {
            CsciNameBasedDraftEnricher.ensureFlowSteps(method);
            heading(8, method.name);
            heading(9, "函数描述");
            normal(method.description);
            heading(9, "参数说明");
            normal("类操作的参数说明如下表所示：");
            tableCaption(method.name + "参数说明");
            List<String[]> parameters = new ArrayList<>();
            for (CsciDesign.Parameter parameter : method.parameters) {
                parameters.add(new String[]{parameter.name, parameter.type, "入参",
                        nonEmpty(parameter.description)});
            }
            body.appendChild(table(template.tables[4], parameters, 1));
            heading(9, "返回值说明");
            normal(method.returnType + "：" + ("void".equals(method.returnType)
                    ? "无返回值" : nonEmpty(method.returnDescription)));
            heading(9, "算法/逻辑");
            if (method.flowSteps.isEmpty()) normal(method.algorithm);
            else normalBeforeFigure(method.algorithm);
            if (!method.flowSteps.isEmpty()) {
                VisioDiagramArtifact flow = diagrams.render(flowDiagram(method),
                        diagramDirectory.resolve(String.format("feature-%03d-flow-%03d.vsdx",
                                featureIndex, ++flowArtifactNumber)));
                visioPicture(flow, 420, 330);
                figureCaption(method.name + "流程图");
            }
            heading(9, "编程注意事项");
            normal(method.caution);
        }

        /**
         * 从模板复制用于放置图片的段落并追加到正文。
         *
         * @return 已插入正文的图片段落
         */
        private Paragraph pictureParagraph() {
            Paragraph picture = (Paragraph) template.figure.deepClone(true);
            picture.removeAllChildren();
            picture.getParagraphFormat().setKeepWithNext(true);
            body.appendChild(picture);
            return picture;
        }

        /**
         * 将 VSDX 作为可编辑 OLE 对象嵌入，并用 PNG 作为预览。
         *
         * @param artifact 已生成的 Visio 文件及预览
         * @param width 最大显示宽度
         * @param height 最大显示高度
         * @throws Exception 读取文件或插入对象失败时抛出
         */
        private void visioPicture(VisioDiagramArtifact artifact, double width, double height) throws Exception {
            Paragraph picture = pictureParagraph();
            DocumentBuilder builder = new DocumentBuilder(doc);
            builder.moveTo(picture);
            try (java.io.InputStream preview = Files.newInputStream(artifact.getPreviewPngPath())) {
                Shape shape = builder.insertOleObject(artifact.getVsdxPath().toString(), false, false, preview);
                fit(shape, artifact.getPreviewPngPath(), width, height);
            }
        }

        /**
         * 将方法的有序步骤转换为流程图定义。
         *
         * @param method 含有流程步骤的方法
         * @return 从起点到终点依次连接的流程图
         */
        private DiagramDefinition flowDiagram(CsciDesign.Method method) {
            DiagramDefinition.Builder builder = DiagramDefinition.builder(DiagramType.FLOW,
                    method.name + "流程图");
            for (int i = 0; i < method.flowSteps.size(); i++) {
                DiagramNodeType type = i == 0 ? DiagramNodeType.START
                        : i == method.flowSteps.size() - 1 ? DiagramNodeType.END : DiagramNodeType.PROCESS;
                builder.node(new DiagramNode("step-" + i, method.flowSteps.get(i), type));
                if (i > 0) builder.edge(new DiagramEdge("step-" + (i - 1), "step-" + i));
            }
            return builder.build();
        }

        /**
         * 按模板数据库表样式追加单个物理表的设计说明。
         *
         * @param database 待渲染的数据库表
         */
        private void database(CsciDesign.DbTable database) {
            String name = database.logicalName + "/" + database.physicalName;
            heading(4, name);
            tableCaption(database.logicalName + database.physicalName + "设计说明");
            Table output = (Table) template.tables[5].deepClone(true);
            lockTableGrid(output);
            setCell(output.getRows().get(0).getCells().get(1), database.physicalName);
            setCell(output.getRows().get(0).getCells().get(3), nonEmpty(database.description));
            Row rowPrototype = (Row) output.getRows().get(3).deepClone(true);
            while (output.getRows().getCount() > 3) output.getRows().removeAt(3);
            for (CsciDesign.Column column : database.columns) {
                Row row = (Row) rowPrototype.deepClone(true);
                String[] values = {column.name, column.type, nonEmpty(column.constraint), nonEmpty(column.comment)};
                for (int i = 0; i < values.length; i++) setCell(row.getCells().get(i), values[i]);
                output.appendChild(row);
            }
            for (int i = 0; i < output.getRows().getCount(); i++) {
                Row row = output.getRows().get(i);
                row.getRowFormat().setAllowBreakAcrossPages(false);
                if (i < 3) {
                    row.getRowFormat().setHeadingFormat(true);
                    keepRowWithNext(row);
                }
            }
            body.appendChild(output);
        }

    }

    /**
     * 克隆模板表格，并用数据行替换示例行。
     *
     * @param prototype 原始模板表格
     * @param data 输出数据行
     * @param rowIndex 示例数据行的索引
     * @return 保留模板格式的新表格
     * @throws IllegalArgumentException 数据列数与模板不一致时抛出
     */
    private static Table table(Table prototype, List<String[]> data, int rowIndex) {
        Table result = (Table) prototype.deepClone(true);
        lockTableGrid(result);
        Row sample = (Row) result.getRows().get(rowIndex).deepClone(true);
        while (result.getRows().getCount() > rowIndex) result.getRows().removeAt(rowIndex);
        if (data.isEmpty()) {
            String[] empty = new String[sample.getCells().getCount()];
            Arrays.fill(empty, "-");
            data = java.util.Collections.singletonList(empty);
        }
        for (String[] values : data) {
            if (values.length != sample.getCells().getCount()) {
                throw new IllegalArgumentException("表格列数不匹配: " + values.length);
            }
            Row row = (Row) sample.deepClone(true);
            for (int i = 0; i < values.length; i++) setCell(row.getCells().get(i), values[i]);
            result.appendChild(row);
        }
        result.getFirstRow().getRowFormat().setHeadingFormat(true);
        keepRowWithNext(result.getFirstRow());
        for (int i = 0; i < result.getRows().getCount(); i++) {
            result.getRows().get(i).getRowFormat().setAllowBreakAcrossPages(false);
        }
        if ("RC-表格样式".equals(result.getStyleName())) {
            materializeRcBorders(result);
        }
        return result;
    }

    /**
     * 防止指定行中的段落与后续内容分页断开。
     *
     * @param row 待处理的表格行
     */
    private static void keepRowWithNext(Row row) {
        for (Object item : row.getCells()) {
            Cell cell = (Cell) item;
            for (Object paragraph : cell.getParagraphs()) {
                ((Paragraph) paragraph).getParagraphFormat().setKeepWithNext(true);
            }
        }
    }

    /**
     * 保留单元格首段落格式并替换其文字。
     *
     * @param cell 待填充的单元格
     * @param value 新文字；空白时显示待补充
     */
    private static void setCell(Cell cell, String value) {
        Paragraph first = cell.getFirstParagraph();
        Paragraph replacement = textParagraph(first, nonEmpty(value));
        cell.removeAllChildren();
        cell.appendChild(replacement);
    }

    /**
     * 关闭自动列宽，保持原模板的表格网格。
     *
     * @param table 待处理的表格
     * @throws IllegalStateException 无法设置表格布局时抛出
     */
    private static void lockTableGrid(Table table) {
        try {
            table.setAllowAutoFit(false);
        } catch (Exception exception) {
            throw new IllegalStateException("无法固定模板表格网格", exception);
        }
    }

    /**
     * 将 RC 模板表格的外框和内框线显式写入单元格。
     *
     * @param table 待处理的 RC 表格
     * @throws IllegalStateException 设置边框失败时抛出
     */
    private static void materializeRcBorders(Table table) {
        try {
            int lastRow = table.getRows().getCount() - 1;
            for (int r = 0; r <= lastRow; r++) {
                Row row = table.getRows().get(r);
                int lastCell = row.getCells().getCount() - 1;
                for (int c = 0; c <= lastCell; c++) {
                    com.aspose.words.BorderCollection borders = row.getCells().get(c).getCellFormat().getBorders();
                    setBorder(borders, BorderType.TOP, r == 0 ? 1.5 : 1.0);
                    setBorder(borders, BorderType.BOTTOM, r == lastRow ? 1.5 : 1.0);
                    setBorder(borders, BorderType.LEFT, c == 0 ? 1.5 : 1.0);
                    setBorder(borders, BorderType.RIGHT, c == lastCell ? 1.5 : 1.0);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法应用模板 RC 表格边框", exception);
        }
    }

    /**
     * 将指定边框设置为黑色实线。
     *
     * @param borders 单元格边框集合
     * @param type Aspose 边框类型
     * @param width 线宽
     * @throws Exception Aspose 无法修改边框时抛出
     */
    private static void setBorder(com.aspose.words.BorderCollection borders, int type,
                                  double width) throws Exception {
        borders.getByBorderType(type).setLineStyle(LineStyle.SINGLE);
        borders.getByBorderType(type).setLineWidth(width);
        borders.getByBorderType(type).setColor(java.awt.Color.BLACK);
    }

    /**
     * 复制模板段落及首个文字片段的格式，并替换内容。
     *
     * @param prototype 模板段落
     * @param value 新段落文字
     * @return 继承模板样式的新段落
     */
    private static Paragraph textParagraph(Paragraph prototype, String value) {
        Paragraph result = (Paragraph) prototype.deepClone(true);
        Run run = result.getRuns().getCount() > 0
                ? (Run) result.getRuns().get(0).deepClone(true) : new Run(result.getDocument());
        result.removeAllChildren();
        run.setText(normalizeText(value));
        result.appendChild(run);
        return result;
    }

    /**
     * 将 Javadoc 行内空值标记转换为面向文档读者的文字。
     *
     * @param value 待写入的文字
     * @return 处理后的非空文字
     */
    private static String normalizeText(String value) {
        return nonEmpty(value).replace("{@code null}", "空值");
    }

    /**
     * 将缺失内容转为统一占位文字。
     *
     * @param value 原始文字
     * @return 非空文字或“待补充”
     */
    private static String nonEmpty(String value) {
        return value == null || value.isBlank() ? "待补充" : value;
    }

    /**
     * 按 PNG 文件的实际尺寸等比缩放图片。
     *
     * @param shape Word 中的图片或 OLE 预览形状
     * @param png PNG 文件路径
     * @param maxWidth 最大显示宽度
     * @param maxHeight 最大显示高度
     * @throws Exception 预览文件无法读取或缩放失败时抛出
     */
    private static void fit(Shape shape, Path png, double maxWidth, double maxHeight) throws Exception {
        BufferedImage image = ImageIO.read(png.toFile());
        if (image == null) throw new IllegalArgumentException("类图预览不是 PNG: " + png);
        fit(shape, image, maxWidth, maxHeight);
    }

    /**
     * 按已解码图片的实际尺寸等比缩放形状。
     *
     * @param shape Word 中的图片或 OLE 预览形状
     * @param image 已解码的图片
     * @param maxWidth 最大显示宽度
     * @param maxHeight 最大显示高度
     * @throws Exception Aspose 无法设置形状尺寸时抛出
     */
    private static void fit(Shape shape, BufferedImage image, double maxWidth, double maxHeight) throws Exception {
        double scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
        shape.setWidth(image.getWidth() * scale);
        shape.setHeight(image.getHeight() * scale);
    }

    /** 从原模板提取的标题、段落和六种表格原型。 */
    private static final class Template {
        /** 一级至九级标题段落原型；索引 0 不使用。 */
        private final Paragraph[] headings = new Paragraph[10];
        /** 普通正文段落原型。 */
        private Paragraph normal;
        /** 图片容器段落原型。 */
        private Paragraph figure;
        /** 图片题注段落原型。 */
        private Paragraph figureCaption;
        /** 表格题注段落原型。 */
        private Paragraph tableCaption;
        /** 按模板出现顺序保存的六种表格原型。 */
        private final Table[] tables = new Table[6];

        /**
         * 从用户模板中提取并校验必需的样式节点。
         *
         * @param document 原始 DOCX 文档
         * @return 可复用的模板节点
         * @throws IllegalArgumentException 缺少规定标题、段落或表格时抛出
         */
        private static Template capture(Document document) {
            Template result = new Template();
            int table = 0;
            for (Object item : document.getFirstSection().getBody().getChildNodes(NodeType.ANY, false)) {
                Node node = (Node) item;
                if (node instanceof Table && table < result.tables.length) {
                    result.tables[table++] = (Table) node.deepClone(true);
                } else if (node instanceof Paragraph) {
                    Paragraph paragraph = (Paragraph) node;
                    String style = paragraph.getParagraphFormat().getStyleName();
                    for (int level = 1; level <= 9; level++) {
                        if (style.equals("Heading " + level) && result.headings[level] == null) {
                            result.headings[level] = (Paragraph) node.deepClone(true);
                        }
                    }
                    if (style.equals("Normal") && result.normal == null) result.normal = paragraph;
                    if (style.equals("图") && result.figure == null) result.figure = paragraph;
                    if (style.equals("图注") && result.figureCaption == null) result.figureCaption = paragraph;
                    if (style.equals("表注") && result.tableCaption == null) result.tableCaption = paragraph;
                }
            }
            if (table != 6 || result.normal == null || result.figure == null
                    || result.figureCaption == null || result.tableCaption == null) {
                throw new IllegalArgumentException("模板不是预期的六表 CSCI 详细设计模板");
            }
            for (int level = 1; level <= 9; level++) {
                if (result.headings[level] == null) {
                    throw new IllegalArgumentException("模板缺少 Heading " + level);
                }
            }
            return result;
        }
    }
}
