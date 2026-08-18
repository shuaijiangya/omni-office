package cn.bugstack.application.external.client;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** 对外 Java SDK 示例：异步生成 DocumentSpec 文档并下载。 */
public final class OmniOfficeMcpClientExample {

    private OmniOfficeMcpClientExample() { }

    public static void main(String[] args) throws Exception {
        URI endpoint = URI.create(System.getenv().getOrDefault("OMNI_OFFICE_MCP_URL", "http://127.0.0.1:8080/mcp"));
        String key = System.getenv().getOrDefault("OMNI_OFFICE_API_KEY", "local-dev-key");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode arguments;
        try (java.io.InputStream input = OmniOfficeMcpClientExample.class
                .getResourceAsStream("/document-spec/1.0/example-simple.json")) {
            arguments = (ObjectNode) mapper.readTree(input);
        }
        arguments.put("outputFormat", "HTML");
        try (OmniOfficeMcpHttpClient client = OmniOfficeMcpHttpClient.apiKey(endpoint, key)) {
            client.initialize();
            String taskId = client.callTool(ExternalDocumentToolApplication.EXPORT_DOCUMENT, arguments, true)
                    .path("result").path("task").path("taskId").asText();
            while ("working".equals(client.task("tasks/get", taskId).path("result").path("status").asText())) {
                Thread.sleep(1_000);
            }
            String uri = client.task("tasks/result", taskId).path("result")
                    .path("structuredContent").path("artifact").path("resourceUri").asText();
            Path output = Path.of("target", "mcp-client-example.html");
            Files.createDirectories(output.getParent());
            Files.write(output, client.download(uri));
            System.out.println(output.toAbsolutePath());
        }
    }
}
