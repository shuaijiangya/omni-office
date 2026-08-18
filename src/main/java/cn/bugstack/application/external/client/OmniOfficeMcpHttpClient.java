package cn.bugstack.application.external.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** 不依赖模型 SDK 的 Java Streamable HTTP 调用示例，可作为外部服务接入起点。 */
public final class OmniOfficeMcpHttpClient implements AutoCloseable {

    private static final String PROTOCOL = "2025-11-25";
    private final URI endpoint;
    private final String credentialHeader;
    private final String credentialValue;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong ids = new AtomicLong(1);
    private String sessionId;

    public static OmniOfficeMcpHttpClient apiKey(URI endpoint, String apiKey) {
        return new OmniOfficeMcpHttpClient(endpoint, "X-API-Key", apiKey);
    }

    public static OmniOfficeMcpHttpClient bearer(URI endpoint, String token) {
        return new OmniOfficeMcpHttpClient(endpoint, "Authorization", "Bearer " + token);
    }

    private OmniOfficeMcpHttpClient(URI endpoint, String credentialHeader, String credentialValue) {
        if (endpoint == null || !endpoint.isAbsolute() || credentialValue == null || credentialValue.isBlank()) {
            throw new IllegalArgumentException("absolute MCP endpoint and credential are required");
        }
        this.endpoint = endpoint;
        this.credentialHeader = credentialHeader;
        this.credentialValue = credentialValue;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public JsonNode initialize() {
        ObjectNode request = request("initialize");
        ObjectNode params = request.putObject("params");
        params.put("protocolVersion", PROTOCOL);
        params.putObject("capabilities").putObject("tasks").putObject("requests")
                .putObject("tools").putObject("call");
        params.putObject("clientInfo").put("name", "omni-office-java-example").put("version", "1.0.0");
        HttpResponse<byte[]> response = send(request, false);
        sessionId = response.headers().firstValue("MCP-Session-Id")
                .orElseThrow(() -> new IllegalStateException("MCP server did not create a session"));
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        send(notification, true);
        return parse(response.body());
    }

    public JsonNode callTool(String name, JsonNode arguments, boolean asynchronous) {
        requireSession();
        ObjectNode request = request("tools/call");
        ObjectNode params = request.putObject("params");
        params.put("name", name);
        params.set("arguments", arguments == null ? mapper.createObjectNode() : arguments.deepCopy());
        if (asynchronous) params.putObject("task").put("ttl", 3_600_000);
        return parse(send(request, true).body());
    }

    public JsonNode task(String method, String taskId) {
        requireSession();
        if (!"tasks/get".equals(method) && !"tasks/result".equals(method)
                && !"tasks/cancel".equals(method)) throw new IllegalArgumentException("unsupported task method");
        ObjectNode request = request(method);
        request.putObject("params").put("taskId", taskId);
        return parse(send(request, true).body());
    }

    public byte[] download(String resourceUri) {
        URI resource = URI.create(resourceUri);
        if (!"omni-office".equals(resource.getScheme()) || !"artifacts".equals(resource.getHost())) {
            throw new IllegalArgumentException("invalid Omni Office resource URI");
        }
        String id = UUID.fromString(resource.getPath().substring(1)).toString();
        URI download = endpoint.resolve("/artifacts/" + id);
        HttpRequest request = HttpRequest.newBuilder(download).timeout(Duration.ofSeconds(60))
                .header(credentialHeader, credentialValue).GET().build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IllegalStateException("artifact download failed: HTTP "
                    + response.statusCode());
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("artifact download failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("artifact download interrupted", e);
        }
    }

    @Override
    public void close() {
        if (sessionId == null) return;
        HttpRequest request = base(endpoint).header("MCP-Session-Id", sessionId).DELETE().build();
        try { client.send(request, HttpResponse.BodyHandlers.discarding()); }
        catch (IOException ignored) { }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        sessionId = null;
    }

    private HttpResponse<byte[]> send(JsonNode body, boolean sessionRequired) {
        HttpRequest.Builder builder = base(endpoint).header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(write(body)));
        if (sessionRequired) {
            requireSession();
            builder.header("MCP-Session-Id", sessionId).header("MCP-Protocol-Version", PROTOCOL);
        }
        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("MCP request failed: HTTP " + response.statusCode()
                        + " " + new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
            }
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("MCP request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP request interrupted", e);
        }
    }

    private HttpRequest.Builder base(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(70))
                .header(credentialHeader, credentialValue);
    }

    private ObjectNode request(String method) {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", ids.getAndIncrement());
        request.put("method", method);
        return request;
    }

    private byte[] write(JsonNode value) {
        try { return mapper.writeValueAsBytes(value); }
        catch (IOException e) { throw new IllegalStateException("failed to serialize MCP request", e); }
    }

    private JsonNode parse(byte[] value) {
        try { return value.length == 0 ? mapper.createObjectNode() : mapper.readTree(value); }
        catch (IOException e) { throw new IllegalStateException("failed to parse MCP response", e); }
    }

    private void requireSession() {
        if (sessionId == null) throw new IllegalStateException("initialize MCP client first");
    }
}
