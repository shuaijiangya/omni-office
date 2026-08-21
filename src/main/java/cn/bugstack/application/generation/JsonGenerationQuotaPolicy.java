package cn.bugstack.application.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 从管理员文件加载默认配额和租户覆盖，不接受调用方提交配额。 */
public final class JsonGenerationQuotaPolicy implements GenerationQuotaPolicy {

    private final GenerationQuota defaultQuota;
    private final Map<String, GenerationQuota> tenants;

    /**
     * 从管理员配置文件加载配额。
     *
     * @param path 已存在的绝对 JSON 文件路径
     */
    public JsonGenerationQuotaPolicy(Path path) {
        if (path == null || !path.isAbsolute() || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("generation quota configuration must be an existing absolute file");
        }
        try {
            JsonNode root = new ObjectMapper().readTree(path.toFile());
            requireObject(root, "generation quota configuration");
            rejectUnknown(root, "default", "tenants");
            this.defaultQuota = root.has("default") ? parseQuota(root.path("default"))
                    : GenerationQuota.unlimited();
            Map<String, GenerationQuota> values = new LinkedHashMap<>();
            JsonNode configuredTenants = root.path("tenants");
            if (!configuredTenants.isMissingNode()) {
                requireObject(configuredTenants, "generation quota tenants");
                configuredTenants.fields().forEachRemaining(entry -> {
                    if (!entry.getKey().matches("[A-Za-z0-9._-]{1,64}")) {
                        throw new IllegalArgumentException("generation quota tenant id is invalid");
                    }
                    values.put(entry.getKey(), parseQuota(entry.getValue()));
                });
            }
            this.tenants = Collections.unmodifiableMap(values);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read generation quota configuration", e);
        }
    }

    @Override
    public GenerationQuota quota(String tenantId) {
        return tenants.getOrDefault(tenantId, defaultQuota);
    }

    private static GenerationQuota parseQuota(JsonNode value) {
        requireObject(value, "generation quota");
        rejectUnknown(value, "maxActiveJobs", "maxJobsPerDay");
        int active = positiveInteger(value, "maxActiveJobs");
        int daily = positiveInteger(value, "maxJobsPerDay");
        return new GenerationQuota(active, daily);
    }

    private static int positiveInteger(JsonNode value, String field) {
        JsonNode item = value.path(field);
        if (!item.isIntegralNumber() || !item.canConvertToInt() || item.asInt() < 1) {
            throw new IllegalArgumentException("generation quota " + field + " must be a positive integer");
        }
        return item.asInt();
    }

    private static void requireObject(JsonNode value, String field) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException(field + " must be an object");
    }

    private static void rejectUnknown(JsonNode value, String... allowed) {
        java.util.Set<String> names = new java.util.HashSet<>(java.util.Arrays.asList(allowed));
        value.fieldNames().forEachRemaining(name -> {
            if (!names.contains(name)) {
                throw new IllegalArgumentException("generation quota configuration contains unknown field: " + name);
            }
        });
    }
}
