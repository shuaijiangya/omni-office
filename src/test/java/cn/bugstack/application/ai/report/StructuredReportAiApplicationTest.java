package cn.bugstack.application.ai.report;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.StructuredAiRequest;
import cn.bugstack.application.ai.InternalAiDocumentApplication;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.block.BulletListBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import com.aspose.words.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredReportAiApplicationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path output;

    @Test
    void fillsEachHeadingWithScopedFactsAndExportsWord() throws Exception {
        RecordingClient client = new RecordingClient();
        StructuredReportAiApplication app = new StructuredReportAiApplication(client);
        DocumentSpec spec = app.generate(template(), facts());

        assertEquals(4, spec.getSections().size());
        assertEquals("评估概述", spec.getSections().get(0).getTitle());
        assertEquals("模块评估", spec.getSections().get(1).getTitle());
        assertEquals(2, spec.getSections().get(1).getBlocks().size());
        assertEquals("任务管理", ((SubsectionBlockSpec) spec.getSections().get(1).getBlocks().get(0)).getTitle());
        assertEquals("数据管理", ((SubsectionBlockSpec) spec.getSections().get(1).getBlocks().get(1)).getTitle());
        assertEquals("模块评估汇总", spec.getSections().get(2).getTitle());
        assertTrue(spec.getSections().get(2).getBlocks().get(0) instanceof TableBlockSpec);
        assertEquals(2, ((TableBlockSpec) spec.getSections().get(2).getBlocks().get(0)).getRows().size());
        assertEquals(5, client.requests.size());
        assertTrue(client.requests.get(0).getContext().path("facts").has("/project"));
        assertFalse(client.requests.get(0).getContext().path("facts").has("/conclusion"));
        assertEquals("task", client.requests.get(1).getContext().path("facts").path("$item").path("id").asText());
        assertEquals("data", client.requests.get(2).getContext().path("facts").path("$item").path("id").asText());

        Path docx = output.resolve("assessment.docx");
        app.generateDocx(template(), facts(), docx);
        assertTrue(Files.size(docx) > 0);
        Document word = new Document(docx.toString());
        assertTrue(word.getText().contains("任务管理"));
        assertTrue(word.getText().contains("评估结论"));
    }

    @Test
    void retriesInvalidSectionWithoutRestartingEarlierSections() throws Exception {
        RecordingClient client = new RecordingClient();
        client.invalidFirst = true;
        StructuredReportAiApplication app = new StructuredReportAiApplication(client);

        app.generate(template(), facts());

        assertEquals(6, client.requests.size());
        assertEquals("overview", client.requests.get(0).getContext().path("sectionId").asText());
        assertEquals("overview", client.requests.get(1).getContext().path("sectionId").asText());
        assertEquals(2, client.requests.get(1).getAttempt());
        assertFalse(client.requests.get(1).getValidationFeedback().isEmpty());
    }

    @Test
    void rejectsMissingFactBeforeCallingModel() throws Exception {
        RecordingClient client = new RecordingClient();
        JsonNode data = facts();
        ((com.fasterxml.jackson.databind.node.ObjectNode) data).remove("conclusion");

        assertThrows(IllegalArgumentException.class,
                () -> new StructuredReportAiApplication(client).generate(template(), data));
        assertEquals(0, client.requests.size());
    }

    @Test
    void surfacesNeedsInputInsteadOfExportingPartialWord() throws Exception {
        RecordingClient client = new RecordingClient();
        client.needsInput = true;
        Path target = output.resolve("should-not-exist.docx");

        ReportNeedsInputException error = assertThrows(ReportNeedsInputException.class,
                () -> new StructuredReportAiApplication(client).generateDocx(template(), facts(), target));
        assertEquals("overview", error.getSectionId());
        assertEquals("请补充评估范围", error.getQuestions().get(0));
        assertFalse(Files.exists(target));
    }

    @Test
    void acceptsListAndTableBlocksButRejectsWrongTableWidth() throws Exception {
        JsonNode oneSection = mapper.readTree("{\"schemaVersion\":\"1.0\",\"templateId\":\"table\","
                + "\"version\":\"1.0\",\"title\":\"表格报告\",\"sections\":[{"
                + "\"id\":\"details\",\"level\":1,\"title\":\"明细\",\"prompt\":\"据实列出\","
                + "\"dataRefs\":[\"/project\"],\"allowedBlocks\":[\"bulletList\",\"table\"]}]}" );
        RecordingClient client = new RecordingClient();
        client.tableResponse = true;
        DocumentSpec spec = new StructuredReportAiApplication(client).generate(oneSection, facts());
        assertTrue(spec.getSections().get(0).getBlocks().get(0) instanceof BulletListBlockSpec);
        assertTrue(spec.getSections().get(0).getBlocks().get(1) instanceof TableBlockSpec);
        assertEquals(2, ((TableBlockSpec) spec.getSections().get(0).getBlocks().get(1)).getHeaders().size());

        client.badTableWidth = true;
        assertThrows(IllegalStateException.class,
                () -> new StructuredReportAiApplication(client).generate(oneSection, facts()));
    }

    @Test
    void existingInternalAiFacadeExposesStructuredReportMode() throws Exception {
        RecordingClient client = new RecordingClient();
        DocumentSpec spec = new InternalAiDocumentApplication(client)
                .generateStructuredReport(template(), facts());

        assertEquals("系统评估报告", spec.getMetadata().getTitle());
        assertEquals(5, client.requests.size());
    }

    @Test
    void acceptsExistingNestedFactReference() throws Exception {
        RecordingClient client = new RecordingClient();
        client.nestedRef = true;

        DocumentSpec spec = new StructuredReportAiApplication(client).generate(template(), facts());

        assertEquals(4, spec.getSections().size());
    }

    @Test
    void requiredTableCannotBeReplacedByParagraph() throws Exception {
        JsonNode oneSection = mapper.readTree("{\"schemaVersion\":\"1.0\",\"templateId\":\"table-required\","
                + "\"version\":\"1.0\",\"title\":\"表格报告\",\"sections\":[{"
                + "\"id\":\"details\",\"level\":1,\"title\":\"明细\",\"prompt\":\"生成表格\","
                + "\"dataRefs\":[\"/project\"],\"allowedBlocks\":[\"paragraph\",\"table\"],"
                + "\"requiredBlocks\":[\"table\"]}]}" );
        StructuredAiClient paragraphOnly = request -> "{\"sectionId\":\"details\",\"status\":\"READY\","
                + "\"blocks\":[{\"type\":\"paragraph\",\"text\":\"只有文字\","
                + "\"factRefs\":[\"/project\"]}],\"questions\":[]}";

        assertThrows(IllegalStateException.class,
                () -> new StructuredReportAiApplication(paragraphOnly).generate(oneSection, facts()));
    }

    @Test
    void rejectsSectionThatOmitsRequiredBusinessValue() throws Exception {
        JsonNode oneSection = mapper.readTree("{\"schemaVersion\":\"1.0\",\"templateId\":\"facts-required\","
                + "\"version\":\"1.0\",\"title\":\"事实报告\",\"sections\":[{"
                + "\"id\":\"details\",\"level\":1,\"title\":\"明细\",\"prompt\":\"据实说明\","
                + "\"dataRefs\":[\"/project\"],\"allowedBlocks\":[\"paragraph\"],"
                + "\"requiredFactValues\":[\"/project/name\"]}]}" );
        RecordingClient client = new RecordingClient();

        assertThrows(IllegalStateException.class,
                () -> new StructuredReportAiApplication(client).generate(oneSection, facts()));
        assertEquals(2, client.requests.size());
    }

    private JsonNode template() throws Exception {
        return mapper.readTree(Files.readString(Path.of("docs/examples/structured-report-template.json"),
                StandardCharsets.UTF_8));
    }

    private JsonNode facts() throws Exception {
        return mapper.readTree(Files.readString(Path.of("docs/examples/structured-report-business-data.json"),
                StandardCharsets.UTF_8));
    }

    private static final class RecordingClient implements StructuredAiClient {
        private final List<StructuredAiRequest> requests = new ArrayList<>();
        private boolean invalidFirst;
        private boolean needsInput;
        private boolean tableResponse;
        private boolean badTableWidth;
        private boolean nestedRef;

        @Override
        public String generateJson(StructuredAiRequest request) {
            requests.add(request);
            String id = request.getContext().path("sectionId").asText();
            if (needsInput) {
                return "{\"sectionId\":\"" + id + "\",\"status\":\"NEEDS_INPUT\","
                        + "\"blocks\":[],\"questions\":[\"请补充评估范围\"]}";
            }
            if (invalidFirst && requests.size() == 1) {
                return "{\"sectionId\":\"" + id + "\",\"status\":\"READY\","
                        + "\"blocks\":[{\"type\":\"paragraph\",\"text\":\"越界\","
                        + "\"factRefs\":[\"/unknown\"]}],\"questions\":[]}";
            }
            if (tableResponse) {
                String row = badTableWidth ? "[\"任务管理\"]" : "[\"任务管理\",\"通过\"]";
                return "{\"sectionId\":\"" + id + "\",\"status\":\"READY\",\"blocks\":["
                        + "{\"type\":\"bulletList\",\"items\":[\"接口测试通过\"],\"factRefs\":[\"/project\"]},"
                        + "{\"type\":\"table\",\"headers\":[\"模块\",\"结论\"],\"rows\":[" + row
                        + "],\"factRefs\":[\"/project\"]}],\"questions\":[]}";
            }
            if ("module-summary".equals(id)) {
                return "{\"sectionId\":\"module-summary\",\"status\":\"READY\",\"blocks\":["
                        + "{\"type\":\"table\",\"headers\":[\"模块\",\"职责\",\"已有证据\",\"待验证风险\"],"
                        + "\"rows\":[[\"任务管理\",\"创建、查询和归档训练任务\",\"任务创建与查询接口测试通过\","
                        + "\"归档接口尚未覆盖并发场景\"],[\"数据管理\",\"登记和检索训练数据集\","
                        + "\"数据集检索用例通过\",\"大规模数据集性能待验证\"]],"
                        + "\"factRefs\":[\"/modules\"]}],\"questions\":[]}";
            }
            if (nestedRef && "overview".equals(id)) {
                return "{\"sectionId\":\"overview\",\"status\":\"READY\",\"blocks\":["
                        + "{\"type\":\"paragraph\",\"text\":\"项目概述\","
                        + "\"factRefs\":[\"/project/name\"]}],\"questions\":[]}";
            }
            String ref = request.getContext().path("facts").fieldNames().next();
            String text = "已依据业务事实完成评估。";
            if (id.startsWith("module/")) {
                text += request.getContext().path("facts").path("$item").path("risk").asText();
            }
            return "{\"sectionId\":\"" + id + "\",\"status\":\"READY\","
                    + "\"blocks\":[{\"type\":\"paragraph\",\"text\":\"" + text + "\","
                    + "\"factRefs\":[\"" + ref + "\"]}],\"questions\":[]}";
        }
    }
}
