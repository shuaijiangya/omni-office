package cn.bugstack.application.external.mcp;

import cn.bugstack.application.external.ExternalDocumentToolApplication;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** 可由 MCP Host 作为子进程启动的 Omni Office stdio 服务入口。 */
public final class McpStdioServerMain {

    private McpStdioServerMain() {
    }

    public static void main(String[] args) throws IOException {
        Path artifactRoot = resolveArtifactRoot(args);
        ExternalDocumentToolApplication application = new ExternalDocumentToolApplication(artifactRoot);
        registerClasspathTemplate(application, "/document-template/1.0/example-assessment-template.json");
        registerClasspathTemplate(application, "/internal-ai/1.0/omni-office-demo-template.json");
        try (McpJsonRpcServer server = new McpJsonRpcServer(application)) {
            server.run(System.in, System.out, System.err);
        }
    }

    private static Path resolveArtifactRoot(String[] args) {
        if (args.length > 0 && !args[0].trim().isEmpty()) {
            return Path.of(args[0]);
        }
        String configured = System.getenv("OMNI_OFFICE_ARTIFACT_ROOT");
        return configured == null || configured.trim().isEmpty()
                ? Path.of("target", "omni-office-mcp") : Path.of(configured);
    }

    private static void registerClasspathTemplate(ExternalDocumentToolApplication application, String resource) {
        try (InputStream input = McpStdioServerMain.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing MCP template resource: " + resource);
            }
            application.registerTemplate(input);
        } catch (IOException e) {
            throw new IllegalStateException("failed to close MCP template resource: " + resource, e);
        }
    }
}
