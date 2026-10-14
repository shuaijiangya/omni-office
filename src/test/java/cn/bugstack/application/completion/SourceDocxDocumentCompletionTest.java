package cn.bugstack.application.completion;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.external.ExternalArtifactReference;
import cn.bugstack.application.external.LocalExternalArtifactStore;
import cn.bugstack.application.external.function.FunctionCallingDocumentAdapter;
import com.aspose.words.BreakType;
import com.aspose.words.BookmarkEnd;
import com.aspose.words.BookmarkStart;
import com.aspose.words.Cell;
import com.aspose.words.CellMerge;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.Paragraph;
import com.aspose.words.Run;
import com.aspose.words.Table;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 多章节、多段落、多表格的文档级补全回归测试。 */
class SourceDocxDocumentCompletionTest {

    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsNoOpParagraphReplacementBeforeModelPlanCanBeApplied() throws Exception {
        Path source = directory.resolve("no-op-source.docx");
        createSource(source);
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        DocumentCompletionPlan plan = new DocumentCompletionPlan(snapshot.getSha256(),
                java.util.Collections.singletonList(new ParagraphPatchPlan(
                        paragraphId(snapshot, "待补充风险说明"), ParagraphPatchOperation.REPLACE_TEXT,
                        TableOverwritePolicy.OVERWRITE_ALL,
                        java.util.Collections.singletonList("待补充风险说明"))),
                java.util.Collections.emptyList(), 0.9D, "unchanged", java.util.Collections.emptyList());
        assertTrue(new DocumentCompletionPlanValidator().validate(snapshot, plan).stream()
                .anyMatch(error -> error.contains("identical to the source")));
    }

    @Test
    void identifiesRaggedMergedHeaderForCellOnlyCompletion() throws Exception {
        Path source = directory.resolve("ragged-header.docx");
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        Table table = builder.startTable();
        builder.insertCell(); builder.write("基本信息");
        builder.insertCell(); builder.endRow();
        builder.insertCell(); builder.write("负责人");
        builder.insertCell(); builder.endRow();
        builder.endTable();
        table.getRows().get(0).getCells().get(0).getCellFormat().setHorizontalMerge(CellMerge.FIRST);
        table.getRows().get(0).getCells().get(1).getCellFormat().setHorizontalMerge(CellMerge.PREVIOUS);
        document.save(source.toString());
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        assertTrue(snapshot.getTables().get(0).isMergedCells());
    }

    @Test
    void externalToolsCompleteOwnedSourceAfterExplicitReview() throws Exception {
        Path source = directory.resolve("external-source.docx");
        createSource(source);
        StructuredAiClient model = request -> {
            JsonNode snapshot = request.getContext().path("document");
            String anchor = "";
            for (JsonNode paragraph : snapshot.path("paragraphs")) {
                if (paragraph.path("text").asText().contains("待补充风险说明")) {
                    anchor = paragraph.path("paragraphId").asText();
                }
            }
            assertFalse(anchor.isBlank());
            return "{\"status\":\"READY\",\"paragraphPatches\":[{\"paragraphId\":\"" + anchor
                    + "\",\"operation\":\"REPLACE_TEXT\",\"overwritePolicy\":\"OVERWRITE_ALL\","
                    + "\"texts\":[\"已核实的风险与措施\"]}],\"tablePatches\":[],"
                    + "\"confidence\":0.94,\"reason\":\"用户事实\",\"questions\":[]}";
        };
        LocalExternalArtifactStore store = new LocalExternalArtifactStore(directory.resolve("external-artifacts"));
        ExternalDocumentToolApplication tools = new ExternalDocumentToolApplication(directory.resolve("tenant"),
                null, store, java.time.Duration.ofDays(1), model);
        assertTrue(tools.listTools().stream().anyMatch(definition ->
                definition.getName().equals(ExternalDocumentToolApplication.PLAN_SOURCE_DOCX)));
        ExternalArtifactReference uploaded = tools.uploadSourceDocx(Files.readAllBytes(source), "alice");
        ObjectNode key = mapper.createObjectNode().put("sourceArtifactId", uploaded.getArtifactId());
        FunctionCallingDocumentAdapter functions = new FunctionCallingDocumentAdapter(tools);
        assertTrue(functions.listFunctionToolsJson().contains(ExternalDocumentToolApplication.PLAN_SOURCE_DOCX));
        assertThrows(IllegalArgumentException.class, () -> functions.invoke(
                ExternalDocumentToolApplication.ANALYZE_SOURCE_DOCX, key, "bob"));
        assertTrue(functions.invoke(ExternalDocumentToolApplication.ANALYZE_SOURCE_DOCX,
                key, "alice").getStructuredContent().has("tables"));
        JsonNode analyzed = tools.call(ExternalDocumentToolApplication.ANALYZE_SOURCE_DOCX, key, "alice")
                .getStructuredContent();
        assertEquals(uploaded.getSha256(), analyzed.path("sha256").asText());
        ObjectNode scopedKey = key.deepCopy().put("sectionIndex", 1);
        JsonNode scoped = tools.call(ExternalDocumentToolApplication.ANALYZE_SOURCE_DOCX,
                scopedKey, "alice").getStructuredContent();
        assertEquals(1, scoped.path("tables").size());
        assertEquals(1, scoped.path("tables").get(0).path("sectionIndex").asInt());
        assertThrows(IllegalArgumentException.class,
                () -> tools.call(ExternalDocumentToolApplication.ANALYZE_SOURCE_DOCX, key, "bob"));
        ObjectNode planning = key.deepCopy().put("instruction", "根据事实补全风险说明");
        planning.put("sectionIndex", 1);
        planning.set("businessFacts", mapper.createObjectNode().put("risk", "已核实的风险与措施"));
        JsonNode plan = tools.call(ExternalDocumentToolApplication.PLAN_SOURCE_DOCX, planning, "alice")
                .getStructuredContent();
        assertEquals("READY", plan.path("status").asText());
        assertEquals(uploaded.getSha256(), plan.path("sourceSha256").asText());
        ObjectNode apply = key.deepCopy();
        apply.set("reviewedPlan", plan);
        assertThrows(IllegalArgumentException.class,
                () -> tools.call(ExternalDocumentToolApplication.APPLY_SOURCE_DOCX, apply, "alice"));
        apply.put("approved", true);
        ObjectNode tampered = apply.deepCopy();
        ((ObjectNode) tampered.path("reviewedPlan")).put("sourceSha256", "tampered");
        assertThrows(IllegalArgumentException.class,
                () -> tools.call(ExternalDocumentToolApplication.APPLY_SOURCE_DOCX, tampered, "alice"));
        JsonNode result = tools.call(ExternalDocumentToolApplication.APPLY_SOURCE_DOCX, apply, "alice")
                .getStructuredContent();
        String resourceUri = result.path("artifact").path("resourceUri").asText();
        Path completedPath = store.resolveForPrincipal(resourceUri, "alice", false).getContentPath();
        assertTrue(new Document(completedPath.toString()).getText().contains("已核实的风险与措施"));
        assertTrue(functions.readResource(resourceUri, "alice").length > 0);
        assertThrows(IllegalArgumentException.class, () -> functions.readResource(resourceUri));
        assertThrows(IllegalArgumentException.class, () -> functions.readResource(resourceUri, "bob"));
        assertTrue(new Document(source.toString()).getText().contains("待补充风险说明"));
        String previewDirectory = System.getProperty("completion.preview.dir");
        if (previewDirectory != null && !previewDirectory.trim().isEmpty()) {
            Path target = Path.of(previewDirectory).resolve("external-reviewed-preview.docx");
            Files.createDirectories(target.getParent());
            Files.copy(completedPath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void fillsTwoSectionsAndTwoTablesWithOneReviewedPlan() throws Exception {
        Path source = directory.resolve("design-source.docx");
        Path output = directory.resolve("design-preview.docx");
        createSource(source);
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        assertEquals(2, snapshot.getTables().size());
        assertFalse(snapshot.getParagraphs().isEmpty());
        String headingId = paragraphId(snapshot, "1 接口设计");
        String placeholderId = paragraphId(snapshot, "待补充风险说明");
        String firstTableId = snapshot.getTables().get(0).getTableId();
        String secondTableId = snapshot.getTables().get(1).getTableId();

        StructuredAiClient model = request -> {
            assertEquals("plan_source_docx_completion", request.getOperation());
            return "{\"status\":\"READY\",\"paragraphPatches\":["
                    + "{\"paragraphId\":\"" + headingId + "\",\"operation\":\"INSERT_AFTER\","
                    + "\"overwritePolicy\":\"BLANKS_ONLY\",\"texts\":[\"订单服务对外提供创建接口。\"]},"
                    + "{\"paragraphId\":\"" + placeholderId + "\",\"operation\":\"REPLACE_TEXT\","
                    + "\"overwritePolicy\":\"OVERWRITE_ALL\",\"texts\":[\"接口超时风险由重试策略缓解。\"]}],"
                    + "\"tablePatches\":["
                    + "{\"tableId\":\"" + firstTableId + "\",\"overwritePolicy\":\"BLANKS_ONLY\","
                    + "\"keyColumnIds\":[\"column_0\"],\"rows\":["
                    + "{\"column_0\":\"POST /orders\",\"column_1\":\"创建订单\"},"
                    + "{\"column_0\":\"GET /orders/{id}\",\"column_1\":\"查询订单\"}]},"
                    + "{\"tableId\":\"" + secondTableId + "\",\"overwritePolicy\":\"BLANKS_ONLY\","
                    + "\"keyColumnIds\":[\"column_0\"],\"rows\":["
                    + "{\"column_0\":\"接口超时\",\"column_1\":\"重试与告警\"}]}],"
                    + "\"confidence\":0.96,\"reason\":\"按提供的事实补全\",\"questions\":[]}";
        };
        SourceDocxCompletionApplication application = new SourceDocxCompletionApplication(model);
        ObjectNode facts = mapper.createObjectNode().put("project", "订单服务");
        DocumentCompletionPlan plan = application.planDocument(source, "填充接口和风险章节", facts);
        assertTrue(plan.isReady());
        assertEquals(0, new DocumentCompletionPlanValidator().validate(snapshot, plan).size());
        DocumentCompletionResult result = application.applyDocument(source, output, plan);
        assertEquals(2, result.getParagraphsChanged());
        assertEquals(2, result.getRowsUpdated());
        assertEquals(1, result.getRowsAppended());
        Document completed = new Document(output.toString());
        assertTrue(completed.getText().contains("订单服务对外提供创建接口"));
        assertTrue(completed.getText().contains("接口超时风险由重试策略缓解"));
        assertFalse(completed.getText().contains("待补充风险说明"));
        Table first = completed.getFirstSection().getBody().getTables().get(0);
        assertEquals(3, first.getRows().getCount());
        assertEquals("创建订单", cellText(first.getRows().get(1).getCells().get(1)));
        assertEquals("查询订单", cellText(first.getRows().get(2).getCells().get(1)));
        Table second = completed.getSections().get(1).getBody().getTables().get(0);
        assertEquals("重试与告警", cellText(second.getRows().get(1).getCells().get(1)));
        assertTrue(new Document(source.toString()).getText().contains("待补充风险说明"));

        String previewDirectory = System.getProperty("completion.preview.dir");
        if (previewDirectory != null && !previewDirectory.trim().isEmpty()) {
            Path target = Path.of(previewDirectory).resolve("design-preview.docx");
            Files.createDirectories(target.getParent());
            Files.copy(output, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void rejectsStalePlanAndKeepsOutputAbsent() throws Exception {
        Path source = directory.resolve("stale.docx");
        Path output = directory.resolve("must-not-exist.docx");
        createSource(source);
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        DocumentCompletionPlan plan = new DocumentCompletionPlan(snapshot.getSha256(),
                java.util.Collections.singletonList(new ParagraphPatchPlan(
                        paragraphId(snapshot, "待补充风险说明"), ParagraphPatchOperation.REPLACE_TEXT,
                        TableOverwritePolicy.OVERWRITE_ALL,
                        java.util.Collections.singletonList("已识别风险"))),
                java.util.Collections.emptyList(), 1D, "测试", java.util.Collections.emptyList());
        Document changed = new Document(source.toString());
        changed.getFirstSection().getBody().appendParagraph("人为修改");
        changed.save(source.toString());
        assertThrows(IllegalArgumentException.class,
                () -> new SourceDocxDocumentPatchExecutor().apply(source, output, plan));
        assertFalse(Files.exists(output));
    }

    @Test
    void reviewedPlanRoundTripsAcrossRequestsAndRejectsTamperedHash() throws Exception {
        Path source = directory.resolve("review-source.docx");
        Path output = directory.resolve("reviewed-output.docx");
        createSource(source);
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        String anchor = paragraphId(snapshot, "待补充风险说明");
        DocumentCompletionPlan planned = new DocumentCompletionPlan(snapshot.getSha256(),
                java.util.Collections.singletonList(new ParagraphPatchPlan(anchor,
                        ParagraphPatchOperation.REPLACE_TEXT, TableOverwritePolicy.OVERWRITE_ALL,
                        java.util.Collections.singletonList("已审核风险说明"))),
                java.util.Collections.emptyList(), 0.95D, "经业务确认", java.util.Collections.emptyList());
        SourceDocxCompletionApplication application = new SourceDocxCompletionApplication(
                request -> "{}");
        ObjectNode json = application.reviewablePlan(planned);
        JsonNode transported = mapper.readTree(mapper.writeValueAsString(json));
        DocumentCompletionResult result = application.applyReviewedPlan(source, output, transported);
        assertEquals(1, result.getParagraphsChanged());
        assertTrue(new Document(output.toString()).getText().contains("已审核风险说明"));

        ObjectNode tampered = json.deepCopy().put("sourceSha256", "not-the-source-hash");
        assertThrows(IllegalArgumentException.class, () -> application.applyReviewedPlan(
                source, directory.resolve("must-not-write.docx"), tampered));
        assertFalse(Files.exists(directory.resolve("must-not-write.docx")));
    }

    @Test
    void uploadIsValidatedAndScopedToOwner() throws Exception {
        Path source = directory.resolve("upload.docx");
        createSource(source);
        SourceDocxUploadStore store = new SourceDocxUploadStore(directory.resolve("uploads"));
        String id = store.upload("tenant-a", "design.docx",
                new ByteArrayInputStream(Files.readAllBytes(source)));
        assertTrue(Files.isRegularFile(store.resolve("tenant-a", id)));
        assertThrows(IllegalArgumentException.class, () -> store.resolve("tenant-b", id));
        assertThrows(IllegalArgumentException.class,
                () -> store.upload("tenant-a", "bad.docx", new ByteArrayInputStream(new byte[]{1, 2, 3})));
    }

    @Test
    void rejectsUploadedPackageWithZipTraversalBeforeWordParsing() throws Exception {
        Path source = directory.resolve("unsafe.docx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../escaped.txt"));
            zip.write("unsafe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SourceDocxStructureExtractor().extract(source));
        assertTrue(error.getMessage().contains("unsafe DOCX ZIP structure"));
    }

    @Test
    void validatesFunctionArgumentsBeforeInvocation() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DocumentCompletionFunctionRegistry registry = new DocumentCompletionFunctionRegistry()
                .register(new DocumentCompletionFunction() {
                    @Override public String name() { return "list_apis"; }
                    @Override public String description() { return "读取接口"; }
                    @Override public JsonNode inputSchema() {
                        ObjectNode schema = mapper.createObjectNode().put("type", "object");
                        schema.putObject("properties").putObject("projectId").put("type", "string");
                        schema.putArray("required").add("projectId");
                        return schema;
                    }
                    @Override public JsonNode invoke(JsonNode arguments) {
                        calls.incrementAndGet();
                        return mapper.createObjectNode();
                    }
                });
        assertThrows(IllegalArgumentException.class,
                () -> registry.invoke("list_apis", mapper.createObjectNode()));
        assertEquals(0, calls.get());
    }

    @Test
    void functionResultCanDriveParagraphAndTablePlanning() throws Exception {
        Path source = directory.resolve("function-document.docx");
        createSource(source);
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        String paragraphId = paragraphId(snapshot, "待补充风险说明");
        AtomicInteger calls = new AtomicInteger();
        StructuredAiClient model = request -> {
            if ("select_document_completion_functions".equals(request.getOperation())) {
                return "{\"action\":\"CALL_FUNCTIONS\",\"calls\":[{\"name\":\"get_risks\","
                        + "\"arguments\":{\"projectId\":\"orders\"}}],\"confidence\":0.99,"
                        + "\"reason\":\"读取业务事实\",\"questions\":[]}";
            }
            assertEquals("orders", request.getContext().path("businessFacts")
                    .path("functionResults").get(0).path("result").path("projectId").asText());
            return "{\"status\":\"READY\",\"paragraphPatches\":[{\"paragraphId\":\""
                    + paragraphId + "\",\"operation\":\"REPLACE_TEXT\","
                    + "\"overwritePolicy\":\"OVERWRITE_ALL\",\"texts\":[\"接口超时风险\"]}],"
                    + "\"tablePatches\":[],\"confidence\":0.99,\"reason\":\"依据函数结果\",\"questions\":[]}";
        };
        DocumentCompletionFunctionRegistry registry = new DocumentCompletionFunctionRegistry()
                .register(new DocumentCompletionFunction() {
                    @Override public String name() { return "get_risks"; }
                    @Override public String description() { return "读取项目风险"; }
                    @Override public JsonNode inputSchema() {
                        ObjectNode schema = mapper.createObjectNode().put("type", "object");
                        schema.putObject("properties").putObject("projectId").put("type", "string");
                        schema.putArray("required").add("projectId");
                        return schema;
                    }
                    @Override public JsonNode invoke(JsonNode arguments) {
                        calls.incrementAndGet();
                        return mapper.createObjectNode().put("projectId", arguments.path("projectId").asText())
                                .put("risk", "接口超时风险");
                    }
                });
        DocumentCompletionPlan plan = new SourceDocxCompletionApplication(model)
                .planDocumentWithFunctions(source, "根据风险事实补充文档", mapper.createObjectNode(), registry);
        assertTrue(plan.isReady());
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsComplexTableInsteadOfDeletingItsAdditionalParagraph() throws Exception {
        Path source = directory.resolve("complex-table.docx");
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.startTable();
        row(builder, "字段", "说明");
        row(builder, "orderId", "人工备注");
        builder.endTable();
        Table table = document.getFirstSection().getBody().getTables().get(0);
        Cell cell = table.getRows().get(1).getCells().get(1);
        Paragraph extra = new Paragraph(document);
        extra.appendChild(new com.aspose.words.Run(document, "第二段不可丢失"));
        cell.appendChild(extra);
        document.save(source.toString());
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        assertTrue(snapshot.getTables().get(0).isComplexContent());
        TableBindingSpec binding = new TableBindingInferer()
                .infer(snapshot.getTables().get(0), snapshot.getSha256())
                .withRowKeyColumnIds(java.util.Collections.singletonList("column_0"));
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("column_0", "orderId");
        values.put("column_1", "替换说明");
        TablePatchPlan plan = new TablePatchPlan(snapshot.getTables().get(0).getTableId(),
                TableOverwritePolicy.OVERWRITE_ALL, java.util.Collections.singletonList(values));
        assertTrue(new TablePatchValidator().validate(snapshot.getTables().get(0), binding, plan)
                .stream().anyMatch(error -> error.contains("multiple paragraphs")));
        assertThrows(IllegalArgumentException.class, () -> new SourceDocxTablePatchExecutor()
                .apply(source, directory.resolve("complex-output.docx"), binding, plan));
    }

    @Test
    void fillsFixedFormCellsWithoutRequiringARowKey() throws Exception {
        Path source = directory.resolve("fixed-form.docx");
        Path output = directory.resolve("fixed-form-result.docx");
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.startTable();
        row(builder, "版本", "");
        row(builder, "负责人", "");
        builder.endTable();
        document.save(source.toString());
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        String tableId = snapshot.getTables().get(0).getTableId();
        StructuredAiClient model = request -> "{\"status\":\"READY\",\"paragraphPatches\":[],"
                + "\"tablePatches\":[],\"cellPatches\":["
                + "{\"tableId\":\"" + tableId + "\",\"rowIndex\":0,\"columnIndex\":1,"
                + "\"overwritePolicy\":\"BLANKS_ONLY\",\"text\":\"V2.0\"},"
                + "{\"tableId\":\"" + tableId + "\",\"rowIndex\":1,\"columnIndex\":1,"
                + "\"overwritePolicy\":\"BLANKS_ONLY\",\"text\":\"张三\"}],"
                + "\"confidence\":0.95,\"reason\":\"固定格式表格按位置填写\",\"questions\":[]}";
        SourceDocxCompletionApplication application = new SourceDocxCompletionApplication(model);
        DocumentCompletionPlan plan = application.planDocument(source, "填写版本和负责人", mapper.createObjectNode());
        DocumentCompletionResult result = application.applyDocument(source, output, plan);
        assertEquals(2, result.getCellsChanged());
        Table completed = new Document(output.toString()).getFirstSection().getBody().getTables().get(0);
        assertEquals("V2.0", cellText(completed.getRows().get(0).getCells().get(1)));
        assertEquals("张三", cellText(completed.getRows().get(1).getCells().get(1)));
        assertEquals("版本", cellText(completed.getRows().get(0).getCells().get(0)));
    }

    @Test
    void fillsSafeCellBesideMergedAndRichCellsWithoutDeletingTheirContent() throws Exception {
        Path source = directory.resolve("rich-source.docx");
        Path output = directory.resolve("rich-preview.docx");
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("设计参数");
        builder.startTable();
        row(builder, "设计信息", "");
        row(builder, "状态", "");
        row(builder, "人工备注", "第一段");
        builder.endTable();
        Table table = document.getFirstSection().getBody().getTables().get(0);
        table.getRows().get(0).getCells().get(0).getCellFormat()
                .setVerticalMerge(CellMerge.FIRST);
        table.getRows().get(1).getCells().get(0).getCellFormat()
                .setVerticalMerge(CellMerge.PREVIOUS);
        Paragraph extra = new Paragraph(document);
        extra.appendChild(new Run(document, "第二段必须保留"));
        table.getRows().get(2).getCells().get(1).appendChild(extra);
        document.save(source.toString());

        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        SourceTableSnapshot extracted = snapshot.getTables().get(0);
        assertTrue(extracted.isMergedCells());
        assertTrue(extracted.isComplexContent());
        assertTrue(extracted.isCellWritable(1, 1));
        assertFalse(extracted.isCellWritable(1, 0));
        assertFalse(extracted.isCellWritable(2, 1));

        CellPatchPlan safe = new CellPatchPlan(extracted.getTableId(), 1, 1,
                TableOverwritePolicy.BLANKS_ONLY, "已通过");
        DocumentCompletionPlan plan = new DocumentCompletionPlan(snapshot.getSha256(),
                java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Collections.singletonList(safe), 1D, "定点填充", java.util.Collections.emptyList());
        assertTrue(new DocumentCompletionPlanValidator().validate(snapshot, plan).isEmpty());
        new SourceDocxDocumentPatchExecutor().apply(source, output, plan);
        Table completed = new Document(output.toString()).getFirstSection().getBody().getTables().get(0);
        assertEquals("已通过", cellText(completed.getRows().get(1).getCells().get(1)));
        assertTrue(cellText(completed.getRows().get(2).getCells().get(1)).contains("第二段必须保留"));
        assertEquals(2, completed.getRows().get(2).getCells().get(1).getParagraphs().getCount());
        assertEquals(CellMerge.FIRST,
                completed.getRows().get(0).getCells().get(0).getCellFormat().getVerticalMerge());
        assertEquals(CellMerge.PREVIOUS,
                completed.getRows().get(1).getCells().get(0).getCellFormat().getVerticalMerge());

        CellPatchPlan unsafe = new CellPatchPlan(extracted.getTableId(), 1, 0,
                TableOverwritePolicy.OVERWRITE_ALL, "不允许");
        DocumentCompletionPlan rejected = new DocumentCompletionPlan(snapshot.getSha256(),
                java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Collections.singletonList(unsafe), 1D, "测试", java.util.Collections.emptyList());
        assertFalse(new DocumentCompletionPlanValidator().validate(snapshot, rejected).isEmpty());

        String previewDirectory = System.getProperty("completion.preview.dir");
        if (previewDirectory != null && !previewDirectory.trim().isEmpty()) {
            Path target = Path.of(previewDirectory).resolve("rich-preview.docx");
            Files.createDirectories(target.getParent());
            Files.copy(output, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void insertsAfterStructuredParagraphWithoutRemovingItsBookmark() throws Exception {
        Path source = directory.resolve("bookmark-source.docx");
        Path output = directory.resolve("bookmark-result.docx");
        Document document = new Document();
        Paragraph paragraph = document.getFirstSection().getBody().getFirstParagraph();
        paragraph.appendChild(new BookmarkStart(document, "designAnchor"));
        paragraph.appendChild(new Run(document, "设计图示"));
        paragraph.appendChild(new BookmarkEnd(document, "designAnchor"));
        document.save(source.toString());
        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        SourceParagraphSnapshot anchor = snapshot.getParagraphs().get(0);
        assertTrue(anchor.isContainsObjects());
        ParagraphPatchPlan patch = new ParagraphPatchPlan(anchor.getParagraphId(),
                ParagraphPatchOperation.INSERT_AFTER, TableOverwritePolicy.BLANKS_ONLY,
                java.util.Collections.singletonList("图示说明由已确认资料补充。"));
        DocumentCompletionPlan plan = new DocumentCompletionPlan(snapshot.getSha256(),
                java.util.Collections.singletonList(patch), java.util.Collections.emptyList(),
                1D, "插入说明", java.util.Collections.emptyList());
        new SourceDocxDocumentPatchExecutor().apply(source, output, plan);
        Document completed = new Document(output.toString());
        assertTrue(completed.getText().contains("图示说明由已确认资料补充"));
        assertEquals(1, completed.getChildNodes(com.aspose.words.NodeType.BOOKMARK_START, true).getCount());
        assertEquals(1, completed.getChildNodes(com.aspose.words.NodeType.BOOKMARK_END, true).getCount());
    }

    @Test
    void replacesOneStyledRunAndPreservesOtherTextRanges() throws Exception {
        Path source = directory.resolve("styled-runs-source.docx");
        Path output = directory.resolve("styled-runs-preview.docx");
        Document document = new Document();
        Paragraph paragraph = document.getFirstSection().getBody().getFirstParagraph();
        Run first = new Run(document, "待补充");
        first.getFont().setBold(true);
        first.getFont().setColor(Color.RED);
        Run second = new Run(document, " 重要提示");
        second.getFont().setItalic(true);
        paragraph.appendChild(first);
        paragraph.appendChild(second);
        document.save(source.toString());

        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);
        String paragraphId = snapshot.getParagraphs().get(0).getParagraphId();
        assertEquals(2, snapshot.getParagraphs().get(0).getRuns().size());
        StructuredAiClient model = request -> "{\"status\":\"READY\",\"paragraphPatches\":[],"
                + "\"tablePatches\":[],\"cellPatches\":[],\"runPatches\":[{"
                + "\"paragraphId\":\"" + paragraphId + "\",\"runIndex\":0,"
                + "\"expectedText\":\"待补充\",\"text\":\"风险等级高\","
                + "\"overwritePolicy\":\"OVERWRITE_ALL\"}],"
                + "\"confidence\":0.94,\"reason\":\"替换占位 Run\",\"questions\":[]}";
        SourceDocxCompletionApplication application = new SourceDocxCompletionApplication(model);
        DocumentCompletionPlan plan = application.planDocument(source, "将占位文本填为风险等级高", mapper.createObjectNode());
        DocumentCompletionResult result = application.applyDocument(source, output, plan);
        assertEquals(1, result.getRunsChanged());
        Paragraph completed = new Document(output.toString()).getFirstSection().getBody().getFirstParagraph();
        assertEquals(2, completed.getRuns().getCount());
        assertEquals("风险等级高", ((Run) completed.getRuns().get(0)).getText());
        assertTrue(((Run) completed.getRuns().get(0)).getFont().getBold());
        assertEquals(Color.RED.getRGB(), ((Run) completed.getRuns().get(0)).getFont().getColor().getRGB());
        assertEquals(" 重要提示", ((Run) completed.getRuns().get(1)).getText());
        assertTrue(((Run) completed.getRuns().get(1)).getFont().getItalic());

        ParagraphPatchPlan destructive = new ParagraphPatchPlan(paragraphId,
                ParagraphPatchOperation.REPLACE_TEXT, TableOverwritePolicy.OVERWRITE_ALL,
                java.util.Collections.singletonList("整个段落"));
        DocumentCompletionPlan rejected = new DocumentCompletionPlan(snapshot.getSha256(),
                java.util.Collections.singletonList(destructive), java.util.Collections.emptyList(),
                1D, "测试", java.util.Collections.emptyList());
        assertTrue(new DocumentCompletionPlanValidator().validate(snapshot, rejected).stream()
                .anyMatch(error -> error.contains("independently styled Runs")));

        String previewDirectory = System.getProperty("completion.preview.dir");
        if (previewDirectory != null && !previewDirectory.trim().isEmpty()) {
            Path target = Path.of(previewDirectory).resolve("styled-runs-preview.docx");
            Files.createDirectories(target.getParent());
            Files.copy(output, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String paragraphId(DocumentStructureSnapshot snapshot, String text) {
        for (SourceParagraphSnapshot paragraph : snapshot.getParagraphs()) {
            if (text.equals(paragraph.getText())) return paragraph.getParagraphId();
        }
        throw new IllegalArgumentException("paragraph not found: " + text);
    }

    private void createSource(Path output) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("1 接口设计");
        builder.writeln("表 1 接口清单");
        builder.startTable();
        row(builder, "接口", "说明");
        row(builder, "POST /orders", "");
        builder.endTable();
        builder.insertBreak(BreakType.SECTION_BREAK_NEW_PAGE);
        builder.writeln("2 风险分析");
        builder.writeln("待补充风险说明");
        builder.writeln("表 2 风险清单");
        builder.startTable();
        row(builder, "风险", "措施");
        row(builder, "接口超时", "");
        builder.endTable();
        document.save(output.toString());
    }

    private void row(DocumentBuilder builder, String first, String second) throws Exception {
        builder.insertCell(); builder.write(first);
        builder.insertCell(); builder.write(second);
        builder.endRow();
    }

    private String cellText(Cell cell) {
        return cell.getText().replace("\u0007", "").replace('\r', ' ')
                .replace('\n', ' ').trim().replaceAll("\\s+", " ");
    }
}
