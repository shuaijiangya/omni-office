package cn.bugstack.application.completion;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 文档补全阶段允许 LLM 调用的显式函数白名单。 */
public final class DocumentCompletionFunctionRegistry {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,127}");
    private final Map<String, DocumentCompletionFunction> functions = new LinkedHashMap<>();
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    /** 注册一个业务数据函数。 */
    public DocumentCompletionFunctionRegistry register(DocumentCompletionFunction function) {
        if (function == null || function.name() == null || !SAFE_NAME.matcher(function.name()).matches()
                || function.description() == null || function.description().trim().isEmpty()
                || function.inputSchema() == null || !function.inputSchema().isObject()) {
            throw new IllegalArgumentException("completion function requires a safe name, description and schema");
        }
        if (functions.putIfAbsent(function.name(), function) != null) {
            throw new IllegalArgumentException("duplicate completion function: " + function.name());
        }
        return this;
    }

    /** @return 按注册顺序排列的不可变函数列表 */
    public Collection<DocumentCompletionFunction> functions() {
        return Collections.unmodifiableCollection(new ArrayList<>(functions.values()));
    }

    /** 调用已注册函数。 */
    public JsonNode invoke(String name, JsonNode arguments) {
        DocumentCompletionFunction function = functions.get(name);
        if (function == null) {
            throw new IllegalArgumentException("completion function is not allowed: " + name);
        }
        if (arguments == null || !arguments.isObject()) {
            throw new IllegalArgumentException("completion function arguments must be an object: " + name);
        }
        java.util.Set<ValidationMessage> violations = schemaFactory.getSchema(function.inputSchema())
                .validate(arguments);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("completion function arguments violate schema: " + name
                    + " (" + violations.iterator().next().getMessage() + ")");
        }
        JsonNode result = function.invoke(arguments);
        if (result == null) {
            throw new IllegalStateException("completion function returned null: " + name);
        }
        return result.deepCopy();
    }

    /** @return 已注册函数名 */
    public List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(functions.keySet()));
    }
}
