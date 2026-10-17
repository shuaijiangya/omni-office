package cn.bugstack.application.csci;

import cn.bugstack.application.ai.StructuredAiClient;
import com.aspose.words.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 正式 CSCI 生成入口的方法设计补全与 Word 输出测试。 */
class CsciDetailedDesignApplicationTest {

    /** JUnit 提供的独立临时目录。 */
    @TempDir Path temp;

    /**
     * 验证业务入口自动补全缺失说明，同时保留显式注释并避免不必要的模型调用。
     *
     * @throws Exception 测试 ZIP 创建或解析失败时抛出
     */
    @Test
    void enrichesMissingMethodDesignInProductionApplication() throws Exception {
        Path archive = sampleArchive();
        AtomicInteger calls = new AtomicInteger();
        CsciDetailedDesignApplication application = new CsciDetailedDesignApplication(client(calls));

        CsciDesign design = application.analyze(Files.newInputStream(archive));
        CsciDesign.JavaClass service = service(design);
        assertEquals(1, calls.get(), "复杂方法只调用一次模型");
        assertEquals("AI：先校验编号，再从仓储查询任务。", method(service, "find").algorithm);
        assertEquals("AI：调用方需处理查询结果为空的情况。", method(service, "find").caution);
        assertEquals("直接返回类字段 taskId 的当前值。", method(service, "getTaskId").algorithm);
        assertEquals("显式算法。", method(service, "explicit").algorithm);
        assertEquals("显式注意事项。", method(service, "explicit").caution);

        CsciDesign withoutAi = CsciDetailedDesignApplication.withoutAi().analyze(archive);
        assertTrue(method(service(withoutAi), "find").algorithm.isBlank());
        assertEquals(1, calls.get(), "明确关闭 AI 后不得产生模型调用");
    }

    /**
     * 验证模板无效时在调用模型之前失败，避免无用的 AI 请求。
     *
     * @throws Exception 测试 ZIP 创建失败时抛出
     */
    @Test
    void rejectsMissingTemplateBeforeCallingAi() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CsciDetailedDesignApplication application = new CsciDetailedDesignApplication(client(calls));
        assertThrows(IllegalArgumentException.class, () -> application.generate(
                temp.resolve("missing.docx"), sampleArchive(), temp.resolve("output.docx")));
        assertEquals(0, calls.get());
    }

    /**
     * 验证正式生成方法在写入 Word 前自动运行同一补全逻辑。
     *
     * @throws Exception 模板读取、模型模拟或 Word 生成失败时抛出
     */
    @Test
    void writesEnrichedMethodDesignIntoGeneratedWord() throws Exception {
        String templateProperty = System.getProperty("csci.template");
        assumeTrue(templateProperty != null && Files.isRegularFile(Path.of(templateProperty)),
                "传入 -Dcsci.template=/absolute/path/详细设计模板表格.docx");
        AtomicInteger calls = new AtomicInteger();
        Path output = System.getProperty("csci.application.output") == null
                ? temp.resolve("CSCI详细设计.docx")
                : Path.of(System.getProperty("csci.application.output")).toAbsolutePath();
        Path actual = new CsciDetailedDesignApplication(client(calls))
                .generate(Path.of(templateProperty), sampleArchive(), output);

        assertEquals(output.toAbsolutePath(), actual);
        assertEquals(1, calls.get());
        Document word = new Document(actual.toString());
        assertTrue(word.getText().contains("AI：先校验编号，再从仓储查询任务。"));
        assertTrue(word.getText().contains("AI：调用方需处理查询结果为空的情况。"));
        assertTrue(word.getText().contains("显式算法。"));
        assertTrue(word.getText().contains("显式注意事项。"));
    }

    /**
     * 提供只接受查询方法的可控模型响应。
     *
     * @param calls 模型调用计数器
     * @return 用于验证调用时机的结构化客户端
     */
    private static StructuredAiClient client(AtomicInteger calls) {
        return request -> {
            calls.incrementAndGet();
            assertEquals("find", request.getContext().path("methodName").asText());
            assertTrue(request.getContext().path("methodBody").asText().contains("repository.findById"));
            return "{\"algorithm\":\"AI：先校验编号，再从仓储查询任务。\","
                    + "\"caution\":\"AI：调用方需处理查询结果为空的情况。\"}";
        };
    }

    /**
     * 从设计对象中取得测试服务类。
     *
     * @param design 待检查的设计
     * @return 服务类
     */
    private static CsciDesign.JavaClass service(CsciDesign design) {
        return design.modules.stream().flatMap(module -> module.features.stream())
                .flatMap(feature -> feature.classes.stream())
                .filter(clazz -> "TaskService".equals(clazz.name)).findFirst().orElseThrow();
    }

    /**
     * 从服务类中取得指定测试方法。
     *
     * @param service 服务类
     * @param name 方法名
     * @return 对应方法
     */
    private static CsciDesign.Method method(CsciDesign.JavaClass service, String name) {
        return service.methods.stream().filter(method -> name.equals(method.name))
                .findFirst().orElseThrow();
    }

    /**
     * 创建包含显式说明、简单 getter 和待 AI 补全方法的源码 ZIP。
     *
     * @return 测试 ZIP 路径
     * @throws Exception ZIP 创建失败时抛出
     */
    private Path sampleArchive() throws Exception {
        Path archive = temp.resolve("source.zip");
        String root = "CSCI详细设计/智能体训练软件/任务管理模块/查询任务/";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, root + "Task.java", "/** 任务实体。 */ public class Task {}\n");
            add(zip, root + "TaskRepository.java",
                    "/** 任务仓储。 */ public interface TaskRepository { Task findById(long id); }\n");
            add(zip, root + "TaskService.java", "/** 查询服务。 */ public class TaskService {\n"
                    + "  /** 任务编号。 */ private long taskId;\n"
                    + "  /** 仓储。 */ private TaskRepository repository;\n"
                    + "  /** 按编号查询任务。 */\n"
                    + "  public Task find(long id) { if (id < 0) return null; return repository.findById(id); }\n"
                    + "  /** 获取任务编号。 */ public long getTaskId() { return taskId; }\n"
                    + "  /** 已明确说明的方法。\n"
                    + "   * @implNote 显式算法。\n"
                    + "   * @csci.caution 显式注意事项。\n"
                    + "   */ public boolean explicit() { return true; }\n"
                    + "}\n");
        }
        return archive;
    }

    /**
     * 向测试 ZIP 写入一个 UTF-8 源文件。
     *
     * @param zip ZIP 输出流
     * @param name 条目路径
     * @param content 源文件内容
     * @throws Exception ZIP 写入失败时抛出
     */
    private static void add(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
