package cn.bugstack.application.csci;

import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import com.aspose.words.Document;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Shape;
import com.aspose.words.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 原模板格式与源码/DDL 动态填充的端到端验证。 */
class CsciDetailedDesignIntegrationTest {

    /** JUnit 为每个测试提供的临时文件目录。 */
    @TempDir Path temp;

    /**
     * 验证源码与 DDL 解析结果，以及多类依赖关系。
     *
     * @throws Exception 测试样本创建或解析失败时抛出
     */
    @Test
    void parsesJavaAndDdlWithoutExecutingUploadedCode() throws Exception {
        Path archive = sourceZip();
        CsciDesign design = new CsciSourceArchiveReader().read(archive);
        assertEquals(design.software, new CsciSourceArchiveReader()
                .read(Files.newInputStream(archive)).software);
        assertEquals("智能体训练软件", design.software);
        assertEquals(2, design.modules.size());
        assertEquals(2, design.databaseTables.size());
        assertEquals("basic_equip", design.databaseTables.get(0).physicalName);
        assertEquals("equip_id", design.databaseTables.get(0).columns.get(0).name);
        assertTrue(design.databaseTables.get(0).columns.get(0).constraint.contains("PRIMARY KEY"));
        CsciDesign.JavaClass clazz = design.modules.stream().flatMap(m -> m.features.stream())
                .flatMap(f -> f.classes.stream()).filter(c -> c.name.equals("TestPlanManager"))
                .findFirst().orElseThrow();
        assertEquals("TestPlanManager", clazz.name);
        assertEquals("plans", clazz.fields.get(0).name);
        assertEquals("创建方案。", clazz.methods.get(0).description);
        assertEquals("方案ID", clazz.methods.get(0).parameters.get(0).description);
        assertEquals("private", clazz.fields.get(0).visibility);
        assertEquals("public", clazz.methods.get(0).visibility);
        assertEquals(4, clazz.methods.get(0).flowSteps.size());
        CsciDesign.Feature query = design.modules.stream().flatMap(m -> m.features.stream())
                .filter(f -> f.name.equals("查询任务")).findFirst().orElseThrow();
        assertEquals(3, query.classes.size());
        assertEquals(List.of("Task", "TaskReader", "TaskRepository"),
                query.classes.stream().map(c -> c.name).collect(java.util.stream.Collectors.toList()));
        CsciDesign.Method find = query.classes.get(1).methods.get(0);
        assertEquals("查询任务。", find.description);
        assertEquals("任务编号", find.returnDescription);
        assertEquals("{ return taskId; }", find.sourceBody);
        var classDiagram = CsciClassDiagramFactory.create(query.name, query.classes);
        assertEquals(3, classDiagram.getNodes().size());
        assertEquals(2, classDiagram.getEdges().size());
        assertEquals(List.of("Task", "TaskReader", "TaskRepository"), classDiagram.getNodes()
                .stream().map(n -> n.getId()).collect(java.util.stream.Collectors.toList()));
        assertTrue(classDiagram.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("TaskReader") && e.getTo().equals("TaskRepository")));
        assertTrue(classDiagram.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("TaskRepository") && e.getTo().equals("Task")));
        assertEquals("任务编号", query.classes.get(2).methods.get(0).parameters.get(0).description);
        assertEquals("任务实体", query.classes.get(2).methods.get(0).returnDescription);
        assertEquals("public", query.classes.get(2).methods.get(0).visibility);
    }

    /**
     * 验证 PostgreSQL 独立注释与不安全 ZIP 路径的处理。
     *
     * @throws Exception 测试 ZIP 创建或解析失败时抛出
     */
    @Test
    void readsPostgresCommentsAndRejectsUnsafeZipPaths() throws Exception {
        String ddl = "CREATE TABLE action (id BIGINT PRIMARY KEY, note VARCHAR(64));\n"
                + "COMMENT ON TABLE action IS '业务''动作';\n"
                + "COMMENT ON COLUMN action.note IS '说明';";
        CsciDesign.DbTable table = new CsciDdlParser().parse(ddl).get(0);
        assertEquals("业务'动作", table.logicalName);
        assertEquals("说明", table.columns.get(1).comment);
        Path zip = temp.resolve("unsafe.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            add(output, "../unsafe.java", "public class Unsafe {}");
        }
        assertThrows(IllegalArgumentException.class, () -> new CsciSourceArchiveReader().read(zip));
    }

    /** 验证源码注释中的 Unicode 转义在文档数据中恢复为中文。 */
    @Test
    void decodesUnicodeEscapesInJavadoc() throws Exception {
        Path archive = temp.resolve("unicode-javadoc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(output, "CSCI详细设计/测试软件/任务模块/查询任务/TaskReader.java",
                    "/** \\u4efb\\u52a1\\u67e5\\u8be2。 */ public class TaskReader {"
                            + "/** \\u8fd4\\u56de\\u4efb\\u52a1。 @return \\u4efb\\u52a1\\u7f16\\u53f7 */"
                            + " public long find() { return 1; } }");
        }
        CsciDesign.JavaClass clazz = new CsciSourceArchiveReader().read(archive)
                .modules.get(0).features.get(0).classes.get(0);
        assertEquals("任务查询。", clazz.description);
        assertEquals("返回任务。", clazz.methods.get(0).description);
        assertEquals("任务编号", clazz.methods.get(0).returnDescription);
    }

    /**
     * 验证单个软件可载入大型数据库设计，同时保留物理表数量上限。
     *
     * @throws Exception 测试 ZIP 创建或解析失败时抛出
     */
    @Test
    void acceptsOneHundredFortyFiveTablesAndRejectsMoreThanTwoHundred() throws Exception {
        Path accepted = temp.resolve("145-tables.zip");
        writeTableArchive(accepted, 145);
        assertEquals(145, new CsciSourceArchiveReader().read(accepted).databaseTables.size());
        Path rejected = temp.resolve("201-tables.zip");
        writeTableArchive(rejected, 201);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CsciSourceArchiveReader().read(rejected));
        assertTrue(error.getMessage().contains("物理表数量超过 200"));
    }

    /** 验证 MySQL 普通索引不会被误识别为数据字段。 */
    @Test
    void doesNotTreatMysqlIndexesAsColumns() {
        String ddl = "CREATE TABLE sample (id BIGINT COMMENT '编号', "
                + "INDEX idx_id (id), KEY key_id (id), FULLTEXT INDEX ft_id (id));";
        CsciDesign.DbTable table = new CsciDdlParser().parse(ddl).get(0);
        assertEquals(1, table.columns.size());
        assertEquals("id", table.columns.get(0).name);
    }

    /** 验证模式限定表名与达梦聚簇主键不会被误识别成字段。 */
    @Test
    void readsSchemaQualifiedDamengTables() {
        String ddl = "CREATE TABLE \"FLOWCHART_BKY\".\"SAMPLE\" ("
                + "\"ID\" VARCHAR(50) NOT NULL, \"NAME\" VARCHAR(50), "
                + "NOT CLUSTER PRIMARY KEY(\"ID\")) STORAGE(ON \"MAIN\", CLUSTERBTR);"
                + "COMMENT ON TABLE \"FLOWCHART_BKY\".\"SAMPLE\" IS '样本表';"
                + "COMMENT ON COLUMN \"FLOWCHART_BKY\".\"SAMPLE\".\"NAME\" IS '名称';";
        CsciDesign.DbTable table = new CsciDdlParser().parse(ddl).get(0);
        assertEquals("FLOWCHART_BKY.SAMPLE", table.physicalName);
        assertEquals("样本表", table.logicalName);
        assertEquals(2, table.columns.size());
        assertTrue(table.columns.get(0).constraint.contains("PRIMARY KEY"));
        assertEquals("名称", table.columns.get(1).comment);
    }

    /** 验证名称草稿不覆盖已确认说明，并为每个非 private 方法绘制流程。 */
    @Test
    void nameBasedDraftsAreExplicitAndBounded() {
        CsciDesign design = new CsciDesign();
        design.software = "评估软件";
        CsciDesign.Module module = new CsciDesign.Module();
        module.name = "计算模块";
        design.modules.add(module);
        CsciDesign.Feature feature = new CsciDesign.Feature();
        feature.name = "指标计算";
        module.features.add(feature);
        CsciDesign.JavaClass clazz = new CsciDesign.JavaClass();
        clazz.name = "Calculator";
        feature.classes.add(clazz);
        for (int index = 0; index < 5; index++) {
            CsciDesign.Method method = new CsciDesign.Method();
            method.name = "calculateScore" + index;
            method.visibility = "public";
            method.returnType = "double";
            method.sourceBody = "{ if (input != null) { for (int i = 0; i < values.size(); i++) "
                    + "{ result += values.get(i); } return result; } return 0; }";
            if (index == 0) method.algorithm = "经确认的算法规则。";
            clazz.methods.add(method);
        }
        CsciDesign.Method hidden = new CsciDesign.Method();
        hidden.name = "calculateInternal";
        hidden.visibility = "private";
        hidden.returnType = "double";
        hidden.sourceBody = "{ return 1; }";
        clazz.methods.add(hidden);
        new CsciNameBasedDraftEnricher().enrich(design);
        assertEquals("经确认的算法规则。", clazz.methods.get(0).algorithm);
        assertTrue(clazz.methods.get(1).algorithm.startsWith("设计草稿"));
        assertTrue(clazz.methods.get(1).caution.startsWith("设计草稿"));
        assertEquals(5, clazz.methods.stream().filter(method -> !method.flowSteps.isEmpty()).count());
        assertTrue(hidden.flowSteps.isEmpty());
        assertTrue(clazz.methods.stream().flatMap(method -> method.flowSteps.stream())
                .anyMatch(step -> step.equals("计算评估结果")));
    }

    /** 验证超大类图仅摘要展示成员，避免 Word 中的预览图过长。 */
    @Test
    void summarizesOversizedClassDiagramNodes() {
        CsciDesign.JavaClass clazz = new CsciDesign.JavaClass();
        clazz.name = "LargeService";
        for (int index = 0; index < 20; index++) {
            CsciDesign.Field field = new CsciDesign.Field();
            field.name = "field" + index;
            field.type = "String";
            field.visibility = "private";
            clazz.fields.add(field);
            CsciDesign.Method method = new CsciDesign.Method();
            method.name = "operation" + index;
            method.returnType = "String";
            method.visibility = "public";
            clazz.methods.add(method);
        }
        CsciDesign.Method hidden = new CsciDesign.Method();
        hidden.name = "internalOnly";
        hidden.returnType = "void";
        hidden.visibility = "private";
        clazz.methods.add(hidden);
        var node = CsciClassDiagramFactory.create("大类", List.of(clazz)).getNodes().get(0);
        assertEquals(15, node.getClassAttributes().size());
        assertEquals(15, node.getClassMethods().size());
        assertTrue(node.getClassAttributes().get(14).contains("其余 6 个字段见正文"));
        assertTrue(node.getClassMethods().get(14).contains("其余 6 个方法见正文"));
        assertTrue(node.getClassMethods().stream().noneMatch(method -> method.contains("internalOnly")));
    }

    /**
     * 为数据库表容量边界构造最小的合法源码 ZIP。
     *
     * @param archive 输出 ZIP 路径
     * @param tableCount 要生成的物理表数量
     * @throws Exception ZIP 写入失败时抛出
     */
    private static void writeTableArchive(Path archive, int tableCount) throws Exception {
        StringBuilder ddl = new StringBuilder();
        for (int index = 0; index < tableCount; index++) {
            ddl.append("CREATE TABLE sample_").append(index).append(" (id BIGINT PRIMARY KEY);\n");
        }
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(output, "CSCI详细设计/测试软件/测试模块/测试功能/Sample.java",
                    "/** 样例类。 */ public class Sample { }");
            add(output, "数据库设计/逻辑结构设计/测试软件数据库设计/sample.sql", ddl.toString());
        }
    }

    /**
     * 验证 AI 仅补全缺失说明，且不覆盖源码中的显式说明。
     *
     * @throws Exception 测试样本创建或解析失败时抛出
     */
    @Test
    void enrichesOnlyMissingMethodDesignFromActualBody() throws Exception {
        CsciDesign design = new CsciSourceArchiveReader().read(sourceZip());
        CsciDesign.JavaClass reader = design.modules.stream().flatMap(m -> m.features.stream())
                .flatMap(f -> f.classes.stream()).filter(c -> c.name.equals("TaskReader"))
                .findFirst().orElseThrow();
        assertEquals(2, reader.methods.size(), "private 方法不进入设计数据");
        CsciDesign.Method exists = reader.methods.get(1);
        List<String> bodies = new ArrayList<>();
        new CsciMethodDesignEnricher(request -> {
            bodies.add(request.getContext().path("methodBody").asText());
            assertEquals("exists", request.getContext().path("methodName").asText());
            return "{\"algorithm\":\"若 taskId 为负则返回 false，否则调用 repository.findById 判断任务是否存在。\","
                    + "\"caution\":\"\"}";
        }).enrich(design);
        assertEquals(List.of(exists.sourceBody), bodies);
        CsciDesign.Method find = reader.methods.get(0);
        assertEquals("直接返回类字段 taskId 的当前值。", find.algorithm);
        assertEquals("该方法未校验 taskId；调用方需确认字段初始值与业务约定一致。", find.caution);
        assertEquals("若 taskId 为负则返回 false，否则调用 repository.findById 判断任务是否存在。", exists.algorithm);
        assertEquals("taskId 为负时返回 false；调用方应区分无效编号与不存在的任务。", exists.caution);
        CsciDesign.Method explicit = design.modules.stream().flatMap(m -> m.features.stream())
                .flatMap(f -> f.classes.stream()).filter(c -> c.name.equals("TestPlanManager"))
                .findFirst().orElseThrow().methods.get(0);
        assertEquals("检查编号后保存方案记录。", explicit.algorithm);
        assertEquals("编号不能为空。", explicit.caution);
    }

    /**
     * 验证生成文档沿用模板表格结构并重新编号题注。
     *
     * @throws Exception 文档生成或校验失败时抛出
     */
    @Test
    void keepsTemplateTableStructureAndRegeneratesCaptions() throws Exception {
        String source = System.getProperty("csci.template");
        assumeTrue(source != null && Files.isRegularFile(Path.of(source)),
                "传入 -Dcsci.template=/absolute/path/详细设计模板表格.docx 运行模板集成测试");
        Path template = Path.of(source);
        byte[] templateHash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(template));
        Path output = System.getProperty("csci.output") == null
                ? temp.resolve("CSCI详细设计-源码填充.docx") : Path.of(System.getProperty("csci.output"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        CsciDesign design = new CsciSourceArchiveReader().read(sourceZip());
        String model = System.getProperty("csci.ollama.model");
        if (model != null && !model.isBlank()) {
            new CsciMethodDesignEnricher(new OllamaStructuredAiClient(model)).enrich(design);
        }
        new CsciTemplateRenderer().render(template, design, output);
        assertTrue(Files.size(output) > 20_000, "文件大小=" + Files.size(output));
        assertArrayEquals(templateHash,
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(template)));
        Document doc = new Document(output.toString());
        List<String> headings = new ArrayList<>();
        List<String> captions = new ArrayList<>();
        for (Object node : doc.getChildNodes(NodeType.PARAGRAPH, true)) {
            Paragraph p = (Paragraph) node;
            String text = p.getText().trim();
            String style = p.getParagraphFormat().getStyleName();
            if (style.startsWith("Heading ")) headings.add(style + ":" + text);
            if (style.equals("表注") || style.equals("图注")) captions.add(text);
        }
        assertTrue(headings.contains("Heading 2:智能体训练软件"), headings.toString());
        assertTrue(headings.contains("Heading 3:方案管理模块"));
        assertTrue(headings.contains("Heading 4:创建方案"));
        assertTrue(headings.contains("Heading 4:武器装备表/basic_equip"));
        assertTrue(headings.contains("Heading 4:试验方案表/test_plan"));
        assertTrue(captions.stream().anyMatch(s -> s.startsWith("表 1-1")));
        assertTrue(captions.stream().anyMatch(s -> s.startsWith("表 2-1")));
        assertEquals(24, captions.size());
        assertTrue(doc.getText().contains("long：任务编号"));
        assertFalse(doc.getText().contains("long：待补充"));
        assertFalse(doc.getText().contains("void：待补充"));
        assertFalse(doc.getText().contains("basic_action"));
        assertFalse(doc.getText().contains("（补充：每个功能类设计）"));
        assertFalse(doc.getText().contains("表 7-1"));
        List<Table> tables = new ArrayList<>();
        for (Object node : doc.getChildNodes(NodeType.TABLE, true)) tables.add((Table) node);
        assertEquals(18, tables.size());
        int[] columns = {5, 5, 8, 4, 8, 4, 4, 4, 8, 4, 4, 5, 5, 8, 4, 4, 4, 4};
        for (int i = 0; i < 16; i++) {
            assertEquals(columns[i], tables.get(i).getFirstRow().getCells().getCount(), "table=" + i);
        }
        Table database = tables.get(tables.size() - 2);
        assertEquals("basic_equip", database.getRows().get(0).getCells().get(1).getText().trim());
        assertEquals(1, database.getRows().get(1).getCells().getCount());
        assertEquals(5, database.getRows().getCount());
        int oleCount = 0;
        for (Object node : doc.getChildNodes(NodeType.SHAPE, true)) {
            if (((Shape) node).getOleFormat() != null) oleCount++;
        }
        assertEquals(6, oleCount);
        assertFalse(doc.getText().contains("internalTaskId"));
        Path queryDiagram = output.toAbsolutePath().getParent()
                .resolve("csci-diagrams/feature-001-class-01.vsdx");
        assertTrue(Files.isRegularFile(queryDiagram));
        assertTrue(Files.size(queryDiagram) > 0);
        assertEquals(1, doc.getSections().getCount());
        assertEquals(595.3, doc.getFirstSection().getPageSetup().getPageWidth(), 1.0);
        Document reference = new Document(template.toString());
        assertEquals(reference.getFirstSection().getPageSetup().getLeftMargin(),
                doc.getFirstSection().getPageSetup().getLeftMargin(), 0.1);
        assertEquals(reference.getFirstSection().getPageSetup().getRightMargin(),
                doc.getFirstSection().getPageSetup().getRightMargin(), 0.1);
        assertEquals(reference.getFirstSection().getPageSetup().getTopMargin(),
                doc.getFirstSection().getPageSetup().getTopMargin(), 0.1);
        assertEquals(reference.getFirstSection().getPageSetup().getBottomMargin(),
                doc.getFirstSection().getPageSetup().getBottomMargin(), 0.1);
        List<Table> sourceTables = new ArrayList<>();
        for (Object node : reference.getChildNodes(NodeType.TABLE, true)) sourceTables.add((Table) node);
        List<List<String>> sourceGrids = grids(template);
        List<List<String>> outputGrids = grids(output);
        assertEquals(18, outputGrids.size());
        int[] sourceIndexes = {0, 1, 2, 3, 2, 3, 4, 4, 2, 3, 4, 0, 1, 2, 3, 4, 5, 5};
        for (int i = 0; i < tables.size(); i++) {
            int sourceIndex = sourceIndexes[i];
            assertEquals(sourceTables.get(sourceIndex).getStyleName(), tables.get(i).getStyleName());
            assertEquals(sourceGrids.get(sourceIndex), outputGrids.get(i), "表格网格不一致: " + i);
        }
    }

    /**
     * 创建覆盖多类、方法设计和数据库定义的测试 ZIP。
     *
     * @return 测试 ZIP 路径
     * @throws Exception 创建或写入 ZIP 失败时抛出
     */
    private Path sourceZip() throws Exception {
        Path zip = temp.resolve("source.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            add(output, "CSCI详细设计/智能体训练软件/方案管理模块/创建方案/TestPlanManager.java",
                    "/** 方案管理器。\n * @csci.ui 用户填写方案名称并提交创建。\n */\n"
                            + "public class TestPlanManager {\n"
                            + "  /** 方案列表。 */ private java.util.List<String> plans;\n"
                            + "  /** 创建方案。\n   * @param testID 方案ID\n   * @return 创建结果\n"
                            + "   * @implNote 检查编号后保存方案记录。\n   * @csci.caution 编号不能为空。\n"
                            + "   * @csci.flow 开始 -> 校验编号 -> 保存方案 -> 结束\n"
                            + "   */ public boolean createTestPlan(String testID) { return true; }\n"
                            + "}\n");
            add(output, "CSCI详细设计/智能体训练软件/任务管理模块/查询任务/Task.java",
                    "/** 任务实体。 */ public class Task {\n"
                            + "  /** 任务编号。 */ private long id;\n"
                            + "}\n");
            add(output, "CSCI详细设计/智能体训练软件/任务管理模块/查询任务/TaskReader.java",
                    "/** 任务查询。 */ public class TaskReader {\n"
                            + "  /** 任务编号。 */ private long taskId;\n"
                            + "  /** 任务仓储。 */ private TaskRepository repository;\n"
                            + "  /** 查询任务。 @return 任务编号 */ public long find() { return taskId; }\n"
                            + "  /** 判断任务是否存在。 @return 任务是否存在\n"
                            + "   * @csci.caution taskId 为负时返回 false；调用方应区分无效编号与不存在的任务。 */\n"
                            + "  public boolean exists() { if (taskId < 0) return false; return repository.findById(taskId) != null; }\n"
                            + "  /** 内部编号。 */ private long internalTaskId() { return taskId; }\n"
                            + "}\n");
            add(output, "CSCI详细设计/智能体训练软件/任务管理模块/查询任务/TaskRepository.java",
                    "/** 任务仓储接口。 */ public interface TaskRepository {\n"
                            + "  /** 按编号查询任务。 @param id 任务编号 @return 任务实体 */\n"
                            + "  Task findById(long id);\n"
                            + "}\n");
            add(output, "数据库设计/逻辑结构设计/智能体训练软件数据库设计/schema.sql",
                    "CREATE TABLE basic_equip (\n"
                            + " equip_id INT NOT NULL COMMENT '装备ID',\n"
                            + " equip_name VARCHAR(64) NOT NULL COMMENT '装备名称',\n"
                            + " PRIMARY KEY (equip_id)\n"
                            + ") COMMENT='武器装备表';\n"
                            + "CREATE TABLE test_plan (plan_id BIGINT PRIMARY KEY COMMENT '方案ID') COMMENT='试验方案表';");
        }
        return zip;
    }

    /**
     * 向测试 ZIP 写入 UTF-8 文本条目。
     *
     * @param output ZIP 输出流
     * @param name 条目路径
     * @param content 条目内容
     * @throws Exception 写入失败时抛出
     */
    private static void add(ZipOutputStream output, String name, String content) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.closeEntry();
    }

    /**
     * 从 DOCX 的 OOXML 中读取每张表格的列网格宽度。
     *
     * @param docx 待检查的 DOCX 文件
     * @return 各表格按顺序排列的列宽列表
     * @throws Exception DOCX 或 XML 无法读取时抛出
     */
    private static List<List<String>> grids(Path docx) throws Exception {
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.Document xml = factory.newDocumentBuilder().parse(
                    zip.getInputStream(zip.getEntry("word/document.xml")));
            String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            NodeList tables = xml.getElementsByTagNameNS(ns, "tbl");
            List<List<String>> result = new ArrayList<>();
            for (int i = 0; i < tables.getLength(); i++) {
                Element table = (Element) tables.item(i);
                Element grid = (Element) table.getElementsByTagNameNS(ns, "tblGrid").item(0);
                NodeList columns = grid.getElementsByTagNameNS(ns, "gridCol");
                List<String> widths = new ArrayList<>();
                for (int j = 0; j < columns.getLength(); j++) {
                    widths.add(((Element) columns.item(j)).getAttributeNS(ns, "w"));
                }
                result.add(widths);
            }
            return result;
        }
    }
}
