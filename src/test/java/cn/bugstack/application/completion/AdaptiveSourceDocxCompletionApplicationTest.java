package cn.bugstack.application.completion;

import cn.bugstack.application.ai.StructuredAiClient;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.NodeType;
import com.aspose.words.SaveFormat;
import com.aspose.words.Shape;
import com.aspose.words.StyleIdentifier;
import com.aspose.words.Table;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 无预埋槽位的正文、表格和可编辑图表补全回归测试。 */
class AdaptiveSourceDocxCompletionApplicationTest {

    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 模型自行定位三个模块，保留原文并一次写入正文、表格和原生图表。 */
    @Test
    void supplementsParagraphTableAndChartWithoutMarkers() throws Exception {
        Path source = directory.resolve("source.docx");
        Path output = directory.resolve("completed.docx");
        createSource(source);
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        String chartAnchor = paragraphId(snapshot, "风险数量图表");
        StructuredAiClient model = request -> {
            if ("select_source_docx_fact_groups".equals(request.getOperation())) {
                String heading = request.getContext().path("heading").asText();
                if (heading.contains("风险") || heading.contains("建议")) {
                    return "{\"selectedPaths\":[\"/risks\"]}";
                }
                return "{\"selectedPaths\":[\"/project\"]}";
            }
            if ("fill_source_docx_module_prose".equals(request.getOperation())) {
                String heading = request.getContext().path("heading").asText();
                if ("项目概况".equals(heading)) {
                    return "{\"status\":\"FILL\",\"text\":\"服务甲本期完成接口联调。\","
                            + "\"evidencePaths\":[\"/project/name\",\"/project/progress\"],\"question\":\"\"}";
                }
                if ("改进建议".equals(heading)) {
                    return "{\"status\":\"FILL\",\"text\":\"对接口超时增加重试与告警。\","
                            + "\"evidencePaths\":[\"/risks/0/name\",\"/risks/0/advice\"],\"question\":\"\"}";
                }
                return "{\"status\":\"SKIP\",\"text\":\"\",\"evidencePaths\":[],\"question\":\"\"}";
            }
            if ("fill_source_docx_module_table".equals(request.getOperation())) {
                return "{\"status\":\"FILL\",\"rows\":[[\"接口超时\",\"3\"],"
                        + "[\"资源告警\",\"2\"]],\"question\":\"\"}";
            }
            assertEquals("plan_source_docx_charts", request.getOperation());
            return "{\"status\":\"READY\",\"charts\":[{\"anchorParagraphId\":\""
                    + chartAnchor + "\",\"chartType\":\"COLUMN\",\"title\":\"风险数量\","
                    + "\"categories\":[\"接口超时\",\"资源告警\"],\"seriesName\":\"数量\","
                    + "\"values\":[3,2],\"valueEvidencePaths\":[\"/risks/0/count\","
                    + "\"/risks/1/count\"]}],\"questions\":[],\"reason\":\"风险数量可视化\"}";
        };
        JsonNode facts = businessFacts();
        AdaptiveSourceDocxCompletionApplication app = new AdaptiveSourceDocxCompletionApplication(model);
        DocumentCompletionPlan plan = app.plan(source, "按照业务数据补充文章中能补充的正文、表格和图表", facts);
        assertTrue(plan.isReady());
        assertEquals(1, plan.getCharts().size());
        assertEquals(1, mapper.readTree(new DocumentCompletionPlanJsonCodec().write(plan).toString())
                .path("chartPatches").size());
        DocumentCompletionResult result = app.apply(source, output, plan);
        assertEquals(2, result.getParagraphsChanged());
        assertEquals(1, result.getRowsAppended());
        assertEquals(1, result.getChartsInserted());
        Document word = new Document(output.toString());
        assertTrue(word.getText().contains("服务甲本期完成接口联调"));
        assertTrue(word.getText().contains("对接口超时增加重试与告警"));
        assertTrue(word.getText().contains("原文保留：项目名称为服务甲"));
        Table completed = word.getFirstSection().getBody().getTables().get(0);
        assertEquals(3, completed.getRows().getCount());
        int chartCount = 0;
        for (Object node : word.getChildNodes(NodeType.SHAPE, true)) {
            if (((Shape) node).hasChart()) chartCount++;
        }
        assertEquals(1, chartCount);
        assertFalse(new Document(source.toString()).getText().contains("服务甲本期完成接口联调"));
    }

    /** 图表的每个点必须能指向同类别业务事实中的相同数字。 */
    @Test
    void refusesChartNumberWithoutMatchingBusinessEvidence() throws Exception {
        Path source = directory.resolve("unsafe-source.docx");
        createSource(source);
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        String anchor = paragraphId(snapshot, "风险数量图表");
        StructuredAiClient model = request -> {
            if ("select_source_docx_fact_groups".equals(request.getOperation())) {
                return "{\"selectedPaths\":[\"/risks\"]}";
            }
            if ("fill_source_docx_module_prose".equals(request.getOperation())) {
                return "{\"status\":\"SKIP\",\"text\":\"\",\"evidencePaths\":[],\"question\":\"\"}";
            }
            if ("fill_source_docx_module_table".equals(request.getOperation())) {
                return "{\"status\":\"SKIP\",\"rows\":[],\"question\":\"\"}";
            }
            return "{\"status\":\"READY\",\"charts\":[{\"anchorParagraphId\":\"" + anchor
                    + "\",\"chartType\":\"COLUMN\",\"title\":\"风险数量\","
                    + "\"categories\":[\"接口超时\"],\"seriesName\":\"数量\","
                    + "\"values\":[99],\"valueEvidencePaths\":[\"/risks/0/count\"]}],"
                    + "\"questions\":[],\"reason\":\"图表\"}";
        };
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveSourceDocxCompletionApplication(model)
                        .plan(source, "按业务数据补充", businessFacts()));
        assertFalse(Files.exists(directory.resolve("completed.docx")));
    }

    /** 未使用 Word 标题样式的普通文章按全文模块补全，仍保留上传原件。 */
    @Test
    void supplementsArticleWithoutHeadingStyles() throws Exception {
        Path source = directory.resolve("plain-article.docx");
        Path output = directory.resolve("plain-completed.docx");
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("订单平台说明");
        builder.writeln("项目名称：订单平台。");
        document.save(source.toString());
        StructuredAiClient model = request -> {
            switch (request.getOperation()) {
                case "select_source_docx_fact_groups":
                    return "{\"selectedPaths\":[\"/project\"]}";
                case "fill_source_docx_module_prose":
                    return "{\"status\":\"FILL\",\"text\":\"已完成接口联调。\",\"question\":\"\"}";
                default:
                    return "{\"status\":\"SKIP\",\"charts\":[],\"questions\":[]}";
            }
        };
        JsonNode facts = mapper.readTree("{\"project\":{\"progress\":\"已完成接口联调\"}}");
        AdaptiveSourceDocxCompletionApplication app = new AdaptiveSourceDocxCompletionApplication(model);
        DocumentCompletionPlan plan = app.plan(source, "按业务数据补充正文", facts);
        assertTrue(plan.isReady());
        assertEquals(1, plan.getParagraphs().size());
        app.apply(source, output, plan);
        assertTrue(new Document(output.toString()).getText().contains("已完成接口联调"));
        assertFalse(new Document(source.toString()).getText().contains("已完成接口联调"));
    }

    /** 有下级标题且无正文的父标题只组织章节，不被重复填入业务段落。 */
    @Test
    void keepsHeadingOnlyParentEmpty() throws Exception {
        Path source = directory.resolve("nested-headings.docx");
        Document document = new Document();
        DocumentBuilder writer = new DocumentBuilder(document);
        writer.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_1);
        writer.writeln("项目章节");
        writer.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_2);
        writer.writeln("实施现状");
        document.save(source.toString());
        StructuredAiClient model = request -> {
            if ("select_source_docx_fact_groups".equals(request.getOperation())) {
                assertEquals("实施现状", request.getContext().path("heading").asText());
                return "{\"selectedPaths\":[\"/project\"]}";
            }
            if ("fill_source_docx_module_prose".equals(request.getOperation())) {
                return "{\"status\":\"FILL\",\"text\":\"已完成接口联调。\",\"question\":\"\"}";
            }
            return "{\"status\":\"SKIP\",\"charts\":[],\"questions\":[]}";
        };
        JsonNode facts = mapper.readTree("{\"project\":{\"progress\":\"已完成接口联调\"}}");
        DocumentCompletionPlan plan = new AdaptiveSourceDocxCompletionApplication(model)
                .plan(source, "按业务事实补充实施现状", facts);
        assertEquals(1, plan.getParagraphs().size());
        assertEquals(paragraphId(new SourceDocxStructureExtractor().extract(source), "实施现状"),
                plan.getParagraphs().get(0).getParagraphId());
    }

    /** 唯一数值序列若与图表标题无关，也不能被确定性兜底误插入。 */
    @Test
    void doesNotInsertUnrelatedNumericSeries() throws Exception {
        Path source = directory.resolve("chart-unrelated.docx");
        createSource(source);
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        JsonNode facts = mapper.readTree("{\"sales\":[{\"name\":\"甲\",\"count\":3},"
                + "{\"name\":\"乙\",\"count\":2}]}");
        StructuredAiClient model = request -> {
            if ("verify_source_docx_chart_relevance".equals(request.getOperation())) {
                return "{\"relevant\":false}";
            }
            return "{\"status\":\"NEEDS_INPUT\",\"charts\":[],"
                    + "\"questions\":[\"风险数量未提供\"]}";
        };
        DocumentCompletionPlan plan = new StructuredAiChartCompletionPlanner(model)
                .plan("需要补充风险数量图表", snapshot, facts);
        assertTrue(plan.getCharts().isEmpty());
        assertFalse(plan.getQuestions().isEmpty());
    }

    /** “能补多少补多少”不因一个模块缺资料而阻止其它有依据的补全。 */
    @Test
    void bestEffortSkipsMissingModuleWithoutBlockingSafePatch() throws Exception {
        Path source = directory.resolve("best-effort.docx");
        Document document = new Document();
        DocumentBuilder writer = new DocumentBuilder(document);
        heading(writer, "实施现状");
        heading(writer, "后续计划");
        document.save(source.toString());
        StructuredAiClient model = request -> {
            if ("select_source_docx_fact_groups".equals(request.getOperation())) {
                return "{\"selectedPaths\":[\"/project\"]}";
            }
            if ("fill_source_docx_module_prose".equals(request.getOperation())) {
                if ("实施现状".equals(request.getContext().path("heading").asText())) {
                    return "{\"status\":\"FILL\",\"text\":\"已完成接口联调。\",\"question\":\"\"}";
                }
                return "{\"status\":\"NEEDS_INPUT\",\"text\":\"\","
                        + "\"question\":\"后续计划未提供\"}";
            }
            return "{\"status\":\"SKIP\",\"charts\":[],\"questions\":[]}";
        };
        JsonNode facts = mapper.readTree("{\"project\":{\"progress\":\"已完成接口联调\"}}");
        DocumentCompletionPlan plan = new AdaptiveSourceDocxCompletionApplication(model)
                .plan(source, "按业务数据把能补充的内容补充一下", facts);
        assertTrue(plan.isReady());
        assertEquals(1, plan.getParagraphs().size());
        assertTrue(plan.getReason().contains("后续计划未提供"));
    }

    /** 一个模块同时含正文与表格时，表格填值后仍可补充未被表格表达的事实。 */
    @Test
    void fillsProseAndTableWithinSameModule() throws Exception {
        Path source = directory.resolve("mixed-module.docx");
        Document document = new Document();
        DocumentBuilder writer = new DocumentBuilder(document);
        heading(writer, "实施情况");
        writer.writeln("已有概述。");
        writer.startTable();
        writer.insertCell(); writer.write("项目");
        writer.insertCell(); writer.write("数量");
        writer.endRow();
        writer.insertCell(); writer.write("接口超时");
        writer.insertCell();
        writer.endRow();
        writer.endTable();
        document.save(source.toString());
        StructuredAiClient model = request -> {
            if ("select_source_docx_fact_groups".equals(request.getOperation())) {
                return "{\"selectedPaths\":[\"/project\"]}";
            }
            if ("fill_source_docx_module_table".equals(request.getOperation())) {
                return "{\"status\":\"FILL\",\"rows\":[[\"接口超时\",\"3\"]],"
                        + "\"question\":\"\"}";
            }
            if ("fill_source_docx_module_prose".equals(request.getOperation())) {
                assertTrue(request.getContext().path("unrepresentedFactValues")
                        .toString().contains("接口已完成联调"));
                return "{\"status\":\"FILL\",\"text\":\"接口已完成联调。\",\"question\":\"\"}";
            }
            return "{\"status\":\"SKIP\",\"charts\":[],\"questions\":[]}";
        };
        JsonNode facts = mapper.readTree("{\"project\":{\"summary\":\"接口已完成联调\","
                + "\"records\":[{\"name\":\"接口超时\",\"count\":3}]}}");
        DocumentCompletionPlan plan = new AdaptiveSourceDocxCompletionApplication(model)
                .plan(source, "把能够补充的正文和表格补充完整", facts);
        assertEquals(1, plan.getTables().size());
        assertEquals(1, plan.getParagraphs().size());
    }

    /** 表头书签不参与行补丁，不能因此跳过可安全填充的数据行。 */
    @Test
    void fillsTableWithHeaderBookmarkAndPreservesIt() throws Exception {
        Path source = directory.resolve("bookmarked-header.docx");
        Path output = directory.resolve("bookmarked-header-completed.docx");
        Document document = new Document();
        DocumentBuilder writer = new DocumentBuilder(document);
        heading(writer, "风险统计");
        writer.startTable();
        writer.insertCell(); writer.write("风险项");
        writer.insertCell(); writer.startBookmark("countHeader"); writer.write("数量");
        writer.endBookmark("countHeader");
        writer.insertCell(); writer.write("处置建议");
        writer.endRow();
        writer.insertCell(); writer.write("接口超时");
        writer.insertCell();
        writer.insertCell();
        writer.endRow();
        writer.endTable();
        document.save(source.toString());
        SourceTableSnapshot table = new SourceDocxStructureExtractor().extract(source).getTables().get(0);
        assertTrue(table.isComplexContent());
        assertFalse(table.hasUnsafeDataCells());
        StructuredAiClient model = request -> {
            if ("select_source_docx_fact_groups".equals(request.getOperation())) {
                return "{\"selectedPaths\":[\"/risks\"]}";
            }
            if ("fill_source_docx_module_table".equals(request.getOperation())) {
                return "{\"status\":\"FILL\",\"rows\":["
                        + "[\"接口超时\",\"3\",\"增加重试与告警\"],"
                        + "[\"资源告警\",\"2\",\"调整阈值并巡检\"]],\"question\":\"\"}";
            }
            return "{\"status\":\"SKIP\",\"text\":\"\",\"question\":\"\","
                    + "\"charts\":[],\"questions\":[]}";
        };
        JsonNode facts = mapper.readTree("{\"risks\":[{\"name\":\"接口超时\",\"count\":3,"
                + "\"advice\":\"增加重试与告警\"},{\"name\":\"资源告警\",\"count\":2,"
                + "\"advice\":\"调整阈值并巡检\"}]}");
        AdaptiveSourceDocxCompletionApplication app = new AdaptiveSourceDocxCompletionApplication(model);
        DocumentCompletionPlan plan = app.plan(source, "把能够补充的表格内容补充完整", facts);
        assertEquals(1, plan.getTables().size());
        DocumentCompletionResult result = app.apply(source, output, plan);
        assertEquals(1, result.getRowsUpdated());
        assertEquals(1, result.getRowsAppended());
        Document completed = new Document(output.toString());
        assertTrue(completed.getRange().getBookmarks().get("countHeader") != null);
        Table completedTable = completed.getFirstSection().getBody().getTables().get(0);
        assertEquals(3, completedTable.getRows().getCount());
        assertTrue(completedTable.getText().contains("增加重试与告警"));
        assertTrue(completedTable.getText().contains("调整阈值并巡检"));
    }

    /** 旧版二进制 DOC 可直接动态识别，审核计划仍绑定原件并输出可编辑 DOCX。 */
    @Test
    void recognizesLegacyDocAndCompletesItsTable() throws Exception {
        Path source = directory.resolve("legacy.doc");
        Path output = directory.resolve("legacy-completed.docx");
        Document document = new Document();
        DocumentBuilder writer = new DocumentBuilder(document);
        heading(writer, "Risks");
        writer.startTable();
        writer.insertCell(); writer.write("Risk");
        writer.insertCell(); writer.write("Count");
        writer.endRow();
        writer.insertCell(); writer.write("Timeout");
        writer.insertCell();
        writer.endRow();
        writer.endTable();
        document.save(source.toString(), SaveFormat.DOC);
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        assertEquals(1, snapshot.getTables().size());
        assertEquals("Risks", snapshot.getParagraphs().get(0).getText());

        SourceDocxUploadStore uploads = new SourceDocxUploadStore(directory.resolve("uploads"));
        String id = uploads.upload("tenant-a", "legacy.doc",
                new ByteArrayInputStream(Files.readAllBytes(source)));
        assertTrue(uploads.resolve("tenant-a", id).getFileName().toString().endsWith(".doc"));
        assertThrows(IllegalArgumentException.class, () -> uploads.resolve("tenant-b", id));

        StructuredAiClient model = request -> {
            if ("select_source_docx_fact_groups".equals(request.getOperation())) {
                return "{\"selectedPaths\":[\"/risks\"]}";
            }
            if ("fill_source_docx_module_table".equals(request.getOperation())) {
                return "{\"status\":\"FILL\",\"rows\":[[\"Timeout\",\"3\"]],"
                        + "\"question\":\"\"}";
            }
            return "{\"status\":\"SKIP\",\"text\":\"\",\"question\":\"\","
                    + "\"charts\":[],\"questions\":[]}";
        };
        JsonNode facts = mapper.readTree("{\"risks\":[{\"name\":\"Timeout\",\"count\":3}]}");
        AdaptiveSourceDocxCompletionApplication app = new AdaptiveSourceDocxCompletionApplication(model);
        DocumentCompletionPlan plan = app.plan(source, "Fill the table from facts", facts);
        assertEquals(snapshot.getSha256(), plan.getSourceSha256());
        assertEquals(1, plan.getTables().size());
        assertThrows(IllegalArgumentException.class, () -> app.apply(source, source, plan));
        assertThrows(IllegalArgumentException.class,
                () -> app.apply(source, directory.resolve("wrong-extension.doc"), plan));
        assertEquals(1, app.apply(source, output, plan).getRowsUpdated());
        assertEquals("3", new Document(output.toString()).getFirstSection().getBody()
                .getTables().get(0).getRows().get(1).getCells().get(1).getText().trim());
        assertEquals(snapshot.getSha256(), new SourceDocxStructureExtractor().extract(source).getSha256());
    }

    /** 扩展名不能代替真实 Word 二进制格式校验。 */
    @Test
    void rejectsFakeLegacyDoc() throws Exception {
        Path source = directory.resolve("fake.doc");
        Files.write(source, new byte[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class,
                () -> new SourceDocxStructureExtractor().extract(source));
        SourceDocxUploadStore uploads = new SourceDocxUploadStore(directory.resolve("uploads"));
        assertThrows(IllegalArgumentException.class,
                () -> uploads.upload("tenant-a", "fake.doc", new ByteArrayInputStream(new byte[]{1, 2, 3})));
    }

    /** 生成不含任何特殊填充标记的源文档。 */
    private void createSource(Path path) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.getFont().setNameFarEast("Arial Unicode MS");
        builder.getFont().setSize(11);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.TITLE);
        builder.writeln("项目运行评估报告");
        heading(builder, "项目概况");
        builder.writeln("原文保留：项目名称为服务甲。");
        heading(builder, "风险清单");
        builder.startTable();
        builder.insertCell(); builder.write("风险项");
        builder.insertCell(); builder.write("数量");
        builder.endRow();
        builder.insertCell(); builder.write("接口超时");
        builder.insertCell(); builder.write("");
        builder.endRow();
        builder.endTable();
        builder.writeln();
        heading(builder, "风险数量图表");
        builder.writeln("按风险类别展示数量。");
        heading(builder, "改进建议");
        document.save(path.toString());
    }

    /** 写入语义标题，并将后续段落恢复为正文样式。 */
    private void heading(DocumentBuilder builder, String title) throws Exception {
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_1);
        builder.writeln(title);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
    }

    /** 提供每个图表数据点都有明确来源的业务事实。 */
    private JsonNode businessFacts() throws Exception {
        return mapper.readTree("{\"project\":{\"name\":\"服务甲\","
                + "\"progress\":\"本期完成接口联调\"},"
                + "\"risks\":[{\"name\":\"接口超时\",\"count\":3,"
                + "\"advice\":\"增加重试与告警\"},"
                + "{\"name\":\"资源告警\",\"count\":2}]}");
    }

    /** 按可见文本定位语义段落 ID。 */
    private String paragraphId(DocumentStructureSnapshot snapshot, String text) {
        for (SourceParagraphSnapshot paragraph : snapshot.getParagraphs()) {
            if (text.equals(paragraph.getText())) return paragraph.getParagraphId();
        }
        throw new IllegalArgumentException("missing paragraph: " + text);
    }
}
