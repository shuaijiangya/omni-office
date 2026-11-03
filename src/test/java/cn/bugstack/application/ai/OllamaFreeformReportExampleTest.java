package cn.bugstack.application.ai;

import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import cn.bugstack.export.api.ReportOutputFormat;
import com.aspose.words.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仅凭一段纯文本调用真实 Ollama，生成并导出自由结构 Word 报告。 */
class OllamaFreeformReportExampleTest {

    /** 显式启用的现场测试；不注入模板、业务 JSON 或模拟 AI 响应。 */
    @Test
    @EnabledIfSystemProperty(named = "omni.ai.ollama.integration", matches = "true")
    void generatesAndExportsWordFromPlainText() throws Exception {
        String model = System.getProperty("omni.ai.model", "qwen3.5:2b");
        Path output = Path.of(System.getProperty("omni.ai.output",
                "target/ai-demo/ollama-freeform-report.docx")).toAbsolutePath().normalize();
        String text = "请把下面的业务文字整理成中文报告，标题为任务归档性能评估报告。"
                + "只生成一个标题为评估内容的章节，章节下只写一个正文段落。"
                + "按原文顺序陈述事实和建议，所有数字和单位必须保留；"
                + "不要加入原文没有的原因、结论或效果。"
                + "不要封面、列表、表格和图片。"
                + "业务文字：上线后每个事务处理的归档任务数由 100 增至 1000，"
                + "导致任务归档接口的 p95 响应时间由 180 毫秒升至 3.2 秒。"
                + "将每事务任务数恢复为 100 后，接口超时率由 8.1% 降至 0.3%。"
                + "建议暂时保持每事务 100 条，并继续验证不同并发条件。";

        InternalAiDocumentApplication application = new InternalAiDocumentApplication(
                new OllamaStructuredAiClient(model));
        final AiDocumentResult result;
        try {
            result = application.generateFreeform(text);
        } catch (AiGenerationException error) {
            error.getValidationErrors().forEach(value -> System.err.println("Ollama output issue: " + value));
            throw error;
        }
        Files.createDirectories(output.getParent());
        application.export(result, ReportOutputFormat.DOCX, output);

        assertEquals(AiGenerationMode.FREEFORM_DOCUMENT, result.getMode());
        assertTrue(Files.isRegularFile(output));
        assertTrue(Files.size(output) > 0);
        Document word = new Document(output.toString());
        String generatedText = word.getText();
        assertTrue(generatedText.contains("归档任务"));
        assertTrue(generatedText.contains("180"));
        assertTrue(generatedText.contains("3.2"));
        assertTrue(generatedText.contains("1000"));
        assertTrue(generatedText.contains("8.1"));
        assertTrue(generatedText.contains("0.3"));
        assertTrue(result.getDocumentSpec().getSections().size() >= 1);
        System.out.println("Ollama model: " + model);
        System.out.println("AI attempts: " + result.getAttempts());
        System.out.println("Generated text: " + generatedText);
        System.out.println("Generated DOCX: " + output);
    }
}
