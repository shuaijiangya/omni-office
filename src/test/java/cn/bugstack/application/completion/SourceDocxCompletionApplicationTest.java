package cn.bugstack.application.completion;

import cn.bugstack.application.ai.StructuredAiClient;
import com.aspose.words.Cell;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.Row;
import com.aspose.words.Table;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SourceDocxCompletionApplication} 的源 DOCX 表格补全闭环测试。 */
class SourceDocxCompletionApplicationTest {

    @TempDir
    Path directory;

    @Test
    void plansAndAppliesPreciseTableUpsertWithoutOverwritingManualContent() throws Exception {
        Path source = directory.resolve("source.docx");
        Path output = directory.resolve("completed.docx");
        createSourceDocument(source);

        SourceDocxStructureExtractor extractor = new SourceDocxStructureExtractor();
        DocumentStructureSnapshot snapshot = extractor.extract(source);
        String tableId = snapshot.getTables().get(0).getTableId();
        StructuredAiClient model = request -> "{"
                + "\"status\":\"READY\","
                + "\"tableId\":\"" + tableId + "\","
                + "\"overwritePolicy\":\"BLANKS_ONLY\","
                + "\"keyColumnIds\":[\"column_1\",\"column_2\"],"
                + "\"rows\":["
                + "{\"column_0\":\"不应覆盖人工名称\",\"column_1\":\"POST\","
                + "\"column_2\":\"/api/orders\",\"column_3\":\"创建一笔新订单\"},"
                + "{\"column_0\":\"查询订单\",\"column_1\":\"GET\","
                + "\"column_2\":\"/api/orders/{id}\",\"column_3\":\"查询订单详情\"}],"
                + "\"confidence\":0.96,"
                + "\"reason\":\"接口清单存在空白说明并缺少查询接口\","
                + "\"questions\":[]}";
        SourceDocxCompletionApplication application = new SourceDocxCompletionApplication(model);
        ObjectNode businessContext = new ObjectMapper().createObjectNode();
        businessContext.put("source", "controller-scan");

        TableCompletionPlanningResult plan = application.plan(source,
                "根据项目接口数据补充接口清单，只填写空白项并追加缺失接口。", businessContext);
        TablePatchResult result = application.apply(source, output, plan);

        assertEquals(TableCompletionStatus.READY, plan.getStatus());
        assertEquals(1, result.getUpdatedRows());
        assertEquals(1, result.getAppendedRows());
        Document rendered = new Document(output.toString());
        Table table = rendered.getFirstSection().getBody().getTables().get(0);
        assertEquals(3, table.getRows().getCount());
        Row existing = table.getRows().get(1);
        Row appended = table.getRows().get(2);
        assertEquals("创建订单", text(existing.getCells().get(0)));
        assertEquals("创建一笔新订单", text(existing.getCells().get(3)));
        assertEquals("查询订单", text(appended.getCells().get(0)));
        assertEquals("GET", text(appended.getCells().get(1)));
        assertEquals("/api/orders/{id}", text(appended.getCells().get(2)));
        assertEquals("查询订单详情", text(appended.getCells().get(3)));
    }

    @Test
    void exposesExtractedTableStructureWithoutAsposeNodes() throws Exception {
        Path source = directory.resolve("analyze.docx");
        createSourceDocument(source);

        DocumentStructureSnapshot snapshot = new SourceDocxStructureExtractor().extract(source);

        assertFalse(snapshot.getSha256().isEmpty());
        assertEquals(1, snapshot.getTables().size());
        SourceTableSnapshot table = snapshot.getTables().get(0);
        assertEquals("表 1 接口清单", table.getCaption());
        assertEquals(4, table.getHeaders().size());
        assertEquals("请求路径", table.getHeaders().get(2));
        assertEquals(1, table.getRows().size());
        assertFalse(table.isMergedCells());
        assertFalse(table.isNestedTables());
    }

    @Test
    void invokesOnlyRegisteredFunctionBeforePlanningTablePatch() throws Exception {
        Path source = directory.resolve("function-source.docx");
        createSourceDocument(source);
        String tableId = new SourceDocxStructureExtractor().extract(source)
                .getTables().get(0).getTableId();
        AtomicInteger invocations = new AtomicInteger();
        ObjectMapper mapper = new ObjectMapper();
        StructuredAiClient model = request -> {
            if ("select_document_completion_functions".equals(request.getOperation())) {
                return "{\"action\":\"CALL_FUNCTIONS\",\"calls\":[{\"name\":\"list_api_definitions\","
                        + "\"arguments\":{\"projectId\":\"order-center\"}}],\"confidence\":0.98,"
                        + "\"reason\":\"需要读取接口事实\",\"questions\":[]}";
            }
            assertTrue(request.getContext().path("businessContext")
                    .path("functionResults").isArray());
            return "{\"status\":\"READY\",\"tableId\":\"" + tableId + "\","
                    + "\"overwritePolicy\":\"BLANKS_ONLY\","
                    + "\"keyColumnIds\":[\"column_1\",\"column_2\"],"
                    + "\"rows\":[{\"column_0\":\"创建订单\",\"column_1\":\"POST\","
                    + "\"column_2\":\"/api/orders\",\"column_3\":\"创建一笔新订单\"}],"
                    + "\"confidence\":0.96,\"reason\":\"依据接口扫描结果补充\",\"questions\":[]}";
        };
        DocumentCompletionFunctionRegistry functions = new DocumentCompletionFunctionRegistry()
                .register(new DocumentCompletionFunction() {
                    @Override
                    public String name() {
                        return "list_api_definitions";
                    }

                    @Override
                    public String description() {
                        return "读取项目接口定义";
                    }

                    @Override
                    public JsonNode inputSchema() {
                        ObjectNode schema = mapper.createObjectNode();
                        schema.put("type", "object");
                        schema.putObject("properties").putObject("projectId").put("type", "string");
                        schema.putArray("required").add("projectId");
                        return schema;
                    }

                    @Override
                    public JsonNode invoke(JsonNode arguments) {
                        invocations.incrementAndGet();
                        ObjectNode result = mapper.createObjectNode();
                        result.put("projectId", arguments.path("projectId").asText());
                        result.putArray("apis").addObject()
                                .put("name", "创建订单")
                                .put("method", "POST")
                                .put("path", "/api/orders")
                                .put("description", "创建一笔新订单");
                        return result;
                    }
                });

        TableCompletionPlanningResult result = new SourceDocxCompletionApplication(model)
                .planWithFunctions(source, "扫描项目并补充接口清单", mapper.createObjectNode(), functions);

        assertEquals(TableCompletionStatus.READY, result.getStatus());
        assertEquals(1, invocations.get());
        assertEquals("创建一笔新订单", result.getPatchPlan().getRows().get(0).get("column_3"));
    }

    @Test
    void rejectsFunctionNameOutsideTheRegisteredAllowlist() throws Exception {
        Path source = directory.resolve("denied-function.docx");
        createSourceDocument(source);
        ObjectMapper mapper = new ObjectMapper();
        StructuredAiClient model = request -> "{\"action\":\"CALL_FUNCTIONS\","
                + "\"calls\":[{\"name\":\"delete_files\",\"arguments\":{}}],"
                + "\"confidence\":0.5,\"reason\":\"invalid request\",\"questions\":[]}";
        DocumentCompletionFunctionRegistry functions = new DocumentCompletionFunctionRegistry()
                .register(new DocumentCompletionFunction() {
                    @Override public String name() { return "list_api_definitions"; }
                    @Override public String description() { return "读取项目接口定义"; }
                    @Override public JsonNode inputSchema() {
                        return mapper.createObjectNode().put("type", "object");
                    }
                    @Override public JsonNode invoke(JsonNode arguments) {
                        return mapper.createObjectNode();
                    }
                });

        assertThrows(IllegalArgumentException.class, () -> new SourceDocxCompletionApplication(model)
                .planWithFunctions(source, "补充接口清单", mapper.createObjectNode(), functions));
    }

    private void createSourceDocument(Path output) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("4 接口设计");
        builder.writeln("表 1 接口清单");
        builder.startTable();
        writeRow(builder, "接口名称", "请求方式", "请求路径", "说明");
        writeRow(builder, "创建订单", "POST", "/api/orders", "");
        builder.endTable();
        document.save(output.toString());
    }

    private void writeRow(DocumentBuilder builder, String... values) throws Exception {
        for (String value : values) {
            builder.insertCell();
            builder.write(value);
        }
        builder.endRow();
    }

    private String text(Cell cell) {
        return cell.getText().replace("\u0007", "").replace('\r', ' ')
                .replace('\n', ' ').trim().replaceAll("\\s+", " ");
    }
}
