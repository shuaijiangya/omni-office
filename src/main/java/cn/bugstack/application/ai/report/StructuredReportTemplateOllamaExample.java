package cn.bugstack.application.ai.report;

import cn.bugstack.application.ai.AiGenerationPolicy;
import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 从自然语言生成或修改标题模板 JSON 的 Ollama 命令行示例。 */
public final class StructuredReportTemplateOllamaExample {

    private StructuredReportTemplateOllamaExample() {
    }

    /**
     * @param args {@code generate <text.txt> <templateId> <output.json> [model]} 或
     *             {@code revise <existing.json> <changes.txt> <output.json> [model]}
     * @throws Exception 文件或模型调用失败时抛出
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 4 || args.length > 5) {
            throw new IllegalArgumentException("usage: generate <text.txt> <templateId> <output.json> [model]"
                    + " | revise <existing.json> <changes.txt> <output.json> [model]");
        }
        String model = args.length == 5 ? args[4] : "qwen3.5:2b";
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(
                new OllamaStructuredAiClient(model), new AiGenerationPolicy(4, 20_000, 100_000, 2_000_000));
        final JsonNode template;
        final Path output;
        if ("generate".equals(args[0])) {
            template = app.generate(Files.readString(Path.of(args[1]), StandardCharsets.UTF_8), args[2]);
            output = Path.of(args[3]);
        } else if ("revise".equals(args[0])) {
            JsonNode existing = new ObjectMapper().readTree(Files.readString(Path.of(args[1]),
                    StandardCharsets.UTF_8));
            template = app.revise(existing, Files.readString(Path.of(args[2]), StandardCharsets.UTF_8));
            output = Path.of(args[3]);
        } else {
            throw new IllegalArgumentException("first argument must be generate or revise");
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, app.toPrettyJson(template) + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("Editable report template: " + output.toAbsolutePath());
    }
}
