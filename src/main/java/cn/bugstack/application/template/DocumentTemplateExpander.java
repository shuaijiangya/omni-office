package cn.bugstack.application.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将数据安全代入 JSON 形式的 DocumentSpec 骨架。
 *
 * <p>支持文本占位符、数组中的 {@code $each} 循环和 {@code $if} 条件；不执行脚本、
 * 反射或任意表达式。</p>
 */
public final class DocumentTemplateExpander {

    private static final Pattern EXACT_EXPRESSION = Pattern.compile(
            "^\\{\\{\\s*([A-Za-z_][A-Za-z0-9_-]*(?:\\.[A-Za-z0-9_-]+)*)\\s*}}$");
    private static final Pattern EMBEDDED_EXPRESSION = Pattern.compile(
            "\\{\\{\\s*([A-Za-z_][A-Za-z0-9_-]*(?:\\.[A-Za-z0-9_-]+)*)\\s*}}");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    private static final int MAX_DEPTH = 64;
    private static final int MAX_OUTPUT_NODES = 20_000;
    private static final int MAX_LOOP_ITEMS = 2_000;

    public JsonNode expand(JsonNode documentTemplate, JsonNode data) {
        if (documentTemplate == null || data == null || !data.isObject()) {
            throw new IllegalArgumentException("document template and object data must not be null");
        }
        Map<String, JsonNode> variables = new HashMap<>();
        variables.put("root", data);
        ExpansionState state = new ExpansionState(data, variables);
        return expandNode(documentTemplate, state, "/documentTemplate", 0);
    }

    private JsonNode expandNode(JsonNode source, ExpansionState state, String path, int depth) {
        state.increment(path);
        if (depth > MAX_DEPTH) {
            throw failure(path, "template nesting exceeds " + MAX_DEPTH);
        }
        if (source == null || source.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (source.isTextual()) {
            return expandText(source.asText(), state, path);
        }
        if (source.isArray()) {
            return expandArray((ArrayNode) source, state, path, depth + 1);
        }
        if (source.isObject()) {
            ObjectNode target = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getKey().startsWith("$")) {
                    throw failure(path + "/" + field.getKey(),
                            "template directives are only allowed as array elements");
                }
                target.set(field.getKey(), expandNode(field.getValue(), state,
                        path + "/" + escapePointer(field.getKey()), depth + 1));
            }
            return target;
        }
        return source.deepCopy();
    }

    private ArrayNode expandArray(ArrayNode source, ExpansionState state, String path, int depth) {
        ArrayNode target = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < source.size(); i++) {
            JsonNode item = source.get(i);
            String itemPath = path + "/" + i;
            if (isDirective(item, "$each")) {
                expandEach(item, target, state, itemPath, depth);
            } else if (isDirective(item, "$if")) {
                expandIf(item, target, state, itemPath, depth);
            } else {
                target.add(expandNode(item, state, itemPath, depth));
            }
        }
        return target;
    }

    private void expandEach(JsonNode directive, ArrayNode target, ExpansionState state,
                            String path, int depth) {
        requireOnlyFields(directive, path, "$each", "$as", "$template");
        String expression = requiredText(directive, "$each", path);
        String variable = directive.has("$as") ? requiredText(directive, "$as", path) : "item";
        if (!VARIABLE_NAME.matcher(variable).matches() || "root".equals(variable)) {
            throw failure(path + "/$as", "loop variable must be a safe identifier other than root");
        }
        JsonNode template = directive.get("$template");
        if (template == null) {
            throw failure(path + "/$template", "loop directive requires $template");
        }
        JsonNode values = resolve(expression, state, path + "/$each");
        if (!values.isArray()) {
            throw failure(path + "/$each", "loop expression must resolve to an array: " + expression);
        }
        if (values.size() > MAX_LOOP_ITEMS) {
            throw failure(path + "/$each", "loop item count exceeds " + MAX_LOOP_ITEMS);
        }
        for (int i = 0; i < values.size(); i++) {
            Map<String, JsonNode> nestedVariables = new HashMap<>(state.variables);
            nestedVariables.put(variable, values.get(i));
            ExpansionState nested = state.withVariables(nestedVariables);
            target.add(expandNode(template, nested, path + "/$template[" + i + "]", depth));
        }
    }

    private void expandIf(JsonNode directive, ArrayNode target, ExpansionState state,
                          String path, int depth) {
        requireOnlyFields(directive, path, "$if", "$template");
        String expression = requiredText(directive, "$if", path);
        JsonNode template = directive.get("$template");
        if (template == null) {
            throw failure(path + "/$template", "conditional directive requires $template");
        }
        JsonNode condition = resolveOptional(expression, state);
        if (condition == null || condition.isNull() || (condition.isBoolean() && !condition.asBoolean())) {
            return;
        }
        if (!condition.isBoolean()) {
            throw failure(path + "/$if", "conditional expression must resolve to a boolean: " + expression);
        }
        target.add(expandNode(template, state, path + "/$template", depth));
    }

    private JsonNode expandText(String text, ExpansionState state, String path) {
        Matcher exact = EXACT_EXPRESSION.matcher(text);
        if (exact.matches()) {
            return resolve(exact.group(1), state, path).deepCopy();
        }
        Matcher matcher = EMBEDDED_EXPRESSION.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            JsonNode value = resolve(matcher.group(1), state, path);
            if (!value.isValueNode() || value.isNull()) {
                throw failure(path, "embedded expression must resolve to a non-null scalar: " + matcher.group(1));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value.asText()));
        }
        matcher.appendTail(result);
        return TextNode.valueOf(result.toString());
    }

    private JsonNode resolve(String expression, ExpansionState state, String path) {
        JsonNode value = resolveOptional(expression, state);
        if (value == null || value.isMissingNode()) {
            throw failure(path, "expression does not exist: " + expression);
        }
        return value;
    }

    private JsonNode resolveOptional(String expression, ExpansionState state) {
        String[] segments = expression.split("\\.");
        int index = 0;
        JsonNode current = state.variables.get(segments[0]);
        if (current != null) {
            index = 1;
        } else {
            current = state.root;
        }
        for (; index < segments.length; index++) {
            if (current == null || current.isNull()) {
                return null;
            }
            current = current.path(segments[index]);
            if (current.isMissingNode()) {
                return null;
            }
        }
        return current;
    }

    private boolean isDirective(JsonNode node, String name) {
        return node != null && node.isObject() && node.has(name);
    }

    private String requiredText(JsonNode object, String field, String path) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty()) {
            throw failure(path + "/" + field, field + " must be a non-blank string");
        }
        return value.asText().trim();
    }

    private void requireOnlyFields(JsonNode object, String path, String... allowed) {
        Map<String, Boolean> names = new HashMap<>();
        for (String name : allowed) {
            names.put(name, Boolean.TRUE);
        }
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!names.containsKey(field)) {
                throw failure(path + "/" + field, "unknown template directive property");
            }
        }
    }

    private String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private DocumentTemplateMappingException failure(String path, String message) {
        return new DocumentTemplateMappingException(path, message);
    }

    private static final class ExpansionState {

        private final JsonNode root;
        private final Map<String, JsonNode> variables;
        private final NodeCounter counter;

        private ExpansionState(JsonNode root, Map<String, JsonNode> variables) {
            this(root, variables, new NodeCounter());
        }

        private ExpansionState(JsonNode root, Map<String, JsonNode> variables, NodeCounter counter) {
            this.root = root;
            this.variables = variables;
            this.counter = counter;
        }

        private ExpansionState withVariables(Map<String, JsonNode> nestedVariables) {
            return new ExpansionState(root, nestedVariables, counter);
        }

        private void increment(String path) {
            counter.value++;
            if (counter.value > MAX_OUTPUT_NODES) {
                throw new DocumentTemplateMappingException(path,
                        "expanded template node count exceeds " + MAX_OUTPUT_NODES);
            }
        }
    }

    private static final class NodeCounter {
        private int value;
    }
}
