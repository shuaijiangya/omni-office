package cn.bugstack.application.template.importer;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import com.aspose.words.Document;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 使用上传的 DOCX 字节及真实 Ollama 返回的全部内容生成报告。 */
class UploadedDocxAiReportApplicationLiveTest {

    /** 示例模板的仓库位置。 */
    private static final Path SOURCE = Path.of("docs", "examples", "项目源码结构评估报告模板.docx");
    /** 真实模型响应写出的目标文档。 */
    private static final Path OUTPUT = Path.of("target", "uploaded-template-all-ai-report.docx");

    /**
     * 原始业务文字使用运行时仓库统计；模型返回段落、表格行和三个图表的数据。
     *
     * @throws Exception 模型或文档生成失败时抛出
     */
    @Test
    void uploadsTemplateAndUsesOnlyOllamaReturnedContent() throws Exception {
        assumeTrue(Boolean.getBoolean("omni.office.example.allAiOllama"),
                "Run with -Domni.office.example.allAiOllama=true");
        String businessText = repositoryText();
        byte[] uploadedTemplate = Files.readAllBytes(SOURCE);
        AtomicInteger calls = new AtomicInteger();
        StructuredAiClient ollama = new OllamaStructuredAiClient("qwen3.5:2b");
        StructuredAiClient counted = request -> {
            calls.incrementAndGet();
            String response = ollama.generateJson(request);
            System.out.println("AI slot response " + request.getOperation() + " #" + calls.get() + ": " + response);
            return response;
        };
        Files.createDirectories(OUTPUT.getParent());
        Files.deleteIfExists(OUTPUT);
        new UploadedDocxAiReportApplication(counted)
                .generate(uploadedTemplate, "uploaded-codebase-live", businessText, OUTPUT);

        Document output = new Document(OUTPUT.toString());
        assertFalse(output.getText().contains("{{"));
        assertTrue(output.getText().contains("项目源码结构评估报告"));
        assertTrue(output.getSections().get(0).getBody().getTables().get(0).getRows().getCount() > 1);
        int chartCount = 0;
        for (Object item : output.getChildNodes(NodeType.SHAPE, true)) {
            if (((Shape) item).hasChart()) chartCount++;
        }
        assertEquals(3, chartCount);
        assertTrue(calls.get() >= 6, "each fill slot must invoke the real Ollama client");
        System.out.println("Source text provided to Ollama:\n" + businessText);
        System.out.println("Real Ollama requests: " + calls.get());
        System.out.println("Generated report: " + OUTPUT.toAbsolutePath());
    }

    /** 只从实际源码目录采集数值，再交给模型组织，不预先组装报告块。 */
    private String repositoryText() throws Exception {
        StringBuilder text = new StringBuilder();
        text.append("本报告统计当前仓库四个核心包的 Java 文件数量。\n");
        appendModule(text, "应用编排", "application");
        appendModule(text, "报告导出", "export");
        appendModule(text, "Word 渲染", "office");
        appendModule(text, "协议模型", "protocol");
        text.append("图表展示上述四个模块，类别顺序与上文一致。主代码文件分布使用柱状图，"
                + "测试文件分布使用横向条形图，主代码构成使用饼图；图表数值直接取对应文件数。\n");
        return text.toString();
    }

    /** 为一个业务模块写入可核验的原始统计事实。 */
    private void appendModule(StringBuilder text, String label, String path) throws Exception {
        long source = countJava(Path.of("src/main/java/cn/bugstack", path));
        long tests = countJava(Path.of("src/test/java/cn/bugstack", path));
        text.append(label).append("：主代码 Java 文件 ").append(source)
                .append(" 个，测试 Java 文件 ").append(tests).append(" 个。\n");
    }

    /** 统计指定包中的 Java 文件。 */
    private long countJava(Path root) throws Exception {
        if (!Files.isDirectory(root)) return 0;
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).count();
        }
    }
}
