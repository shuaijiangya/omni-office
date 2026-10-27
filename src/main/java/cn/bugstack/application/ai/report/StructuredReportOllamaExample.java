package cn.bugstack.application.ai.report;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.AiGenerationPolicy;
import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/** 从结构化标题模板、业务 JSON 和本地 Ollama 生成 DOCX 的命令行示例。 */
public final class StructuredReportOllamaExample {

    private StructuredReportOllamaExample() {
    }

    /**
     * @param args 模板 JSON、业务数据 JSON、输出 DOCX，以及可选模型名
     * @throws Exception 文件读取或生成失败时抛出
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("usage: <template.json> <business-data.json> <output.docx> [model]");
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode template = mapper.readTree(Files.readString(Path.of(args[0])));
        JsonNode facts = mapper.readTree(Files.readString(Path.of(args[1])));
        String model = args.length == 4 ? args[3] : "qwen3.5:2b";
        StructuredAiClient client = new OllamaStructuredAiClient(model);
        if (Boolean.getBoolean("omni.ai.debug")) {
            StructuredAiClient delegate = client;
            client = request -> {
                String response = delegate.generateJson(request);
                System.err.println("AI section " + request.getContext().path("sectionId").asText()
                        + ", attempt " + request.getAttempt() + ": " + response);
                return response;
            };
        }
        Path output = Path.of(args[2]);
        if (output.toAbsolutePath().getParent() != null) {
            Files.createDirectories(output.toAbsolutePath().getParent());
        }
        AiGenerationPolicy smallModelPolicy = new AiGenerationPolicy(4, 20_000, 100_000, 2_000_000);
        new StructuredReportAiApplication(client, smallModelPolicy).generateDocx(template, facts, output);
        System.out.println("Generated Word report: " + output.toAbsolutePath());
    }
}
