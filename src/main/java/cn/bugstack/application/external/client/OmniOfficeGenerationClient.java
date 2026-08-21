package cn.bugstack.application.external.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Omni Office REST Java SDK：异步任务、模板治理及受控工件下载。 */
public final class OmniOfficeGenerationClient {

    private final URI serviceRoot;
    private final String credentialHeader;
    private final String credentialValue;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 创建使用 API Key 的 REST 客户端。
     *
     * @param serviceRoot 服务根地址，例如 {@code https://documents.example.com}
     * @param apiKey API Key
     * @return REST 客户端
     */
    public static OmniOfficeGenerationClient apiKey(URI serviceRoot, String apiKey) {
        return new OmniOfficeGenerationClient(serviceRoot, "X-API-Key", apiKey);
    }

    /**
     * 创建使用 Bearer Token 的 REST 客户端。
     *
     * @param serviceRoot 服务根地址
     * @param token Bearer Token，不包含 {@code Bearer } 前缀
     * @return REST 客户端
     */
    public static OmniOfficeGenerationClient bearer(URI serviceRoot, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("bearer token is required");
        }
        return new OmniOfficeGenerationClient(serviceRoot, "Authorization", "Bearer " + token);
    }

    private OmniOfficeGenerationClient(URI serviceRoot, String credentialHeader, String credentialValue) {
        if (serviceRoot == null || !serviceRoot.isAbsolute() || serviceRoot.getHost() == null
                || serviceRoot.getQuery() != null || serviceRoot.getFragment() != null
                || credentialValue == null || credentialValue.isBlank()) {
            throw new IllegalArgumentException("absolute service root and credential are required");
        }
        String path = serviceRoot.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            throw new IllegalArgumentException("service root must not contain a path");
        }
        this.serviceRoot = serviceRoot.resolve("/");
        this.credentialHeader = credentialHeader;
        this.credentialValue = credentialValue;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * 提交异步文档生成任务。
     *
     * @param request {@code DOCUMENT_SPEC} 或 {@code TEMPLATE_DATA} 请求
     * @param idempotencyKey 可选幂等键
     * @param correlationId 可选关联 ID
     * @return 服务端任务 JSON
     * @throws OmniOfficeApiException 服务端返回非 2xx 状态时抛出
     */
    public JsonNode submit(JsonNode request, String idempotencyKey, String correlationId) {
        HttpRequest.Builder builder = request("/v1/generation-jobs")
                .header("Content-Type", "application/json");
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if (correlationId != null) builder.header("X-Correlation-Id", correlationId);
        return json(send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(write(request))).build()));
    }

    /**
     * 查询生成任务。
     *
     * @param jobId 任务 UUID
     * @return 服务端任务 JSON
     */
    public JsonNode get(String jobId) {
        return json(send(request("/v1/generation-jobs/" + uuid(jobId)).GET().build()));
    }

    /**
     * 取消尚未终结的生成任务。
     *
     * @param jobId 任务 UUID
     * @return 取消后的任务 JSON
     */
    public JsonNode cancel(String jobId) {
        return json(send(request("/v1/generation-jobs/" + uuid(jobId) + "/cancel")
                .POST(HttpRequest.BodyPublishers.noBody()).build()));
    }

    /**
     * 按状态和稳定游标查询任务。
     *
     * @param status 可选任务状态
     * @param cursor 可选的不透明分页游标
     * @param limit 返回数量，范围为 1～100
     * @return 任务分页 JSON
     */
    public JsonNode list(String status, String cursor, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        StringBuilder path = new StringBuilder("/v1/generation-jobs?limit=").append(limit);
        if (status != null && !status.isBlank()) path.append("&status=").append(encode(status));
        if (cursor != null && !cursor.isBlank()) path.append("&cursor=").append(encode(cursor));
        return json(send(request(path.toString()).GET().build()));
    }

    /**
     * 轮询任务直至进入终态。
     *
     * @param jobId 任务 UUID
     * @param timeout 最大等待时间
     * @param pollInterval 轮询间隔
     * @return 终态任务 JSON
     * @throws IllegalStateException 等待超时或线程被中断时抛出
     */
    public JsonNode awaitTerminal(String jobId, Duration timeout, Duration pollInterval) {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || pollInterval == null
                || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("positive timeout and poll interval are required");
        }
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            JsonNode job = get(jobId);
            String status = job.path("status").asText();
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                return job;
            }
            if (!Instant.now().isBefore(deadline)) {
                throw new IllegalStateException("generation job polling timed out");
            }
            try {
                Thread.sleep(Math.min(pollInterval.toMillis(),
                        Math.max(1L, Duration.between(Instant.now(), deadline).toMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("generation job polling interrupted", e);
            }
        }
    }

    /**
     * 创建模板草稿。
     *
     * @param template 完整 DocumentTemplate JSON
     * @return 模板版本 JSON
     */
    public JsonNode createTemplateDraft(JsonNode template) {
        return postJson("/v1/admin/templates", template);
    }

    /**
     * 查询指定模板版本。
     *
     * @param templateId 模板 ID
     * @param version 模板版本
     * @return 模板版本 JSON
     */
    public JsonNode getTemplate(String templateId, String version) {
        return json(send(request(templatePath(templateId, version)).GET().build()));
    }

    /**
     * 查询模板版本。
     *
     * @param status 可选生命周期状态
     * @param limit 返回数量，范围为 1～100
     * @return 模板列表 JSON
     */
    public JsonNode listTemplates(String status, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        String path = "/v1/admin/templates?limit=" + limit
                + (status == null || status.isBlank() ? "" : "&status=" + encode(status));
        return json(send(request(path).GET().build()));
    }

    /**
     * 提交模板版本进入审核。
     *
     * @param templateId 模板 ID
     * @param version 模板版本
     * @return 更新后的模板版本 JSON
     */
    public JsonNode submitTemplate(String templateId, String version) {
        return json(send(request(templatePath(templateId, version) + "/submit")
                .POST(HttpRequest.BodyPublishers.noBody()).build()));
    }

    /**
     * 审批并发布模板版本。
     *
     * @param templateId 模板 ID
     * @param version 模板版本
     * @param comment 可选审核意见
     * @return 已发布模板版本 JSON
     */
    public JsonNode approveTemplate(String templateId, String version, String comment) {
        return templateAction(templateId, version, "approve", comment);
    }

    /**
     * 驳回模板版本。
     *
     * @param templateId 模板 ID
     * @param version 模板版本
     * @param reason 驳回原因
     * @return 已驳回模板版本 JSON
     */
    public JsonNode rejectTemplate(String templateId, String version, String reason) {
        return templateAction(templateId, version, "reject", reason);
    }

    /**
     * 退役已发布模板版本。
     *
     * @param templateId 模板 ID
     * @param version 模板版本
     * @param reason 退役原因
     * @return 已退役模板版本 JSON
     */
    public JsonNode retireTemplate(String templateId, String version, String reason) {
        return templateAction(templateId, version, "retire", reason);
    }

    /**
     * 比较同一模板两个版本的数据 Schema。
     *
     * @param templateId 模板 ID
     * @param fromVersion 基准版本
     * @param toVersion 候选版本
     * @return 向后兼容性结果 JSON
     */
    public JsonNode compareTemplates(String templateId, String fromVersion, String toVersion) {
        String path = "/v1/admin/templates/" + segment(templateId) + "/compare?fromVersion="
                + encode(fromVersion) + "&toVersion=" + encode(toVersion);
        return json(send(request(path).GET().build()));
    }

    /**
     * 查询当前身份所属租户的运维汇总。
     *
     * @return 任务、Webhook 和依赖状态 JSON
     */
    public JsonNode operationsSummary() {
        return json(send(request("/v1/admin/operations/summary").GET().build()));
    }

    /**
     * 下载受控工件。
     *
     * @param resourceUri {@code omni-office://artifacts/{uuid}} 资源 URI
     * @return 工件字节
     */
    public byte[] download(String resourceUri) {
        URI resource = URI.create(resourceUri);
        if (!"omni-office".equals(resource.getScheme()) || !"artifacts".equals(resource.getHost())
                || resource.getQuery() != null || resource.getFragment() != null) {
            throw new IllegalArgumentException("invalid Omni Office resource URI");
        }
        String id = uuid(resource.getPath().substring(1));
        return send(request("/artifacts/" + id).GET().build()).body();
    }

    private JsonNode templateAction(String templateId, String version, String action, String comment) {
        ObjectNode body = mapper.createObjectNode();
        if (comment != null) body.put("comment", comment);
        return postJson(templatePath(templateId, version) + "/" + action, body);
    }

    private JsonNode postJson(String path, JsonNode body) {
        return json(send(request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(write(body))).build()));
    }

    private String templatePath(String templateId, String version) {
        return "/v1/admin/templates/" + segment(templateId) + "/versions/" + segment(version);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(serviceRoot.resolve(path)).timeout(Duration.ofSeconds(70))
                .header(credentialHeader, credentialValue).header("Accept", "application/json");
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return response;
            JsonNode problem;
            try { problem = mapper.readTree(response.body()); }
            catch (IOException ignored) { problem = mapper.createObjectNode(); }
            throw new OmniOfficeApiException(response.statusCode(), problem.path("code").asText(null),
                    problem.path("detail").asText(new String(response.body(), StandardCharsets.UTF_8)),
                    response.headers().firstValue("Retry-After").orElse(null));
        } catch (IOException e) {
            throw new IllegalStateException("Omni Office API request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Omni Office API request interrupted", e);
        }
    }

    private byte[] write(JsonNode value) {
        if (value == null) throw new IllegalArgumentException("JSON request body is required");
        try { return mapper.writeValueAsBytes(value); }
        catch (IOException e) { throw new IllegalStateException("failed to serialize API request", e); }
    }

    private JsonNode json(HttpResponse<byte[]> response) {
        try { return mapper.readTree(response.body()); }
        catch (IOException e) { throw new IllegalStateException("failed to parse API response", e); }
    }

    private String segment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("API path segment is invalid");
        }
        return value;
    }

    private String uuid(String value) { return UUID.fromString(value).toString(); }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
