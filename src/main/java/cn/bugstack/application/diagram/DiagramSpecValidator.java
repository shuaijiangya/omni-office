package cn.bugstack.application.diagram;

import cn.bugstack.protocol.diagram.DiagramEdgeSpec;
import cn.bugstack.protocol.diagram.DiagramNodeSpec;
import cn.bugstack.protocol.diagram.DiagramNodeTypeSpec;
import cn.bugstack.protocol.diagram.DiagramSpec;
import cn.bugstack.protocol.diagram.DiagramSpecVersion;
import cn.bugstack.protocol.diagram.DiagramTypeSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** DiagramSpec 的结构、引用和规模校验器。 */
public final class DiagramSpecValidator {

    private static final int MAX_NODES = 200;
    private static final int MAX_EDGES = 500;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_DETAILS = 100;
    private static final EnumMap<DiagramTypeSpec, Set<DiagramNodeTypeSpec>> ALLOWED_NODE_TYPES
            = allowedNodeTypes();

    public DiagramSpecValidationResult validate(DiagramSpec spec) {
        List<DiagramSpecViolation> errors = new ArrayList<>();
        if (spec == null) {
            add(errors, "/", "REQUIRED", "diagram spec must not be null");
            return new DiagramSpecValidationResult(errors);
        }
        if (!DiagramSpecVersion.V1.equals(spec.getSchemaVersion())) {
            add(errors, "/schemaVersion", "UNSUPPORTED_VERSION", "supported diagram spec version is 1.0");
        }
        if (spec.getType() == null) {
            add(errors, "/type", "REQUIRED", "diagram type must not be null");
        }
        validateText(spec.getTitle(), "/title", true, errors);
        List<DiagramNodeSpec> nodes = spec.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            add(errors, "/nodes", "REQUIRED", "diagram must contain at least one node");
            return new DiagramSpecValidationResult(errors);
        }
        if (nodes.size() > MAX_NODES) {
            add(errors, "/nodes", "LIMIT_EXCEEDED", "diagram node count exceeds " + MAX_NODES);
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            DiagramNodeSpec node = nodes.get(i);
            String path = "/nodes/" + i;
            if (node == null) {
                add(errors, path, "REQUIRED", "diagram node must not be null");
                continue;
            }
            validateText(node.getId(), path + "/id", true, errors);
            validateText(node.getLabel(), path + "/label", true, errors);
            if (node.getType() == null) {
                add(errors, path + "/type", "REQUIRED", "diagram node type must not be null");
            } else if (spec.getType() != null
                    && !ALLOWED_NODE_TYPES.get(spec.getType()).contains(node.getType())) {
                add(errors, path + "/type", "NODE_TYPE_NOT_ALLOWED",
                        "node type " + node.getType() + " is not allowed for " + spec.getType());
            }
            if (hasText(node.getId()) && !ids.add(node.getId())) {
                add(errors, path + "/id", "DUPLICATE_ID", "diagram node id must be unique");
            }
            validateDetails(node.getFields(), path + "/fields", errors);
            validateDetails(node.getClassAttributes(), path + "/classAttributes", errors);
            validateDetails(node.getClassMethods(), path + "/classMethods", errors);
        }
        List<DiagramEdgeSpec> edges = spec.getEdges();
        if (edges == null) {
            add(errors, "/edges", "REQUIRED", "diagram edges must not be null");
            return new DiagramSpecValidationResult(errors);
        }
        if (edges.size() > MAX_EDGES) {
            add(errors, "/edges", "LIMIT_EXCEEDED", "diagram edge count exceeds " + MAX_EDGES);
        }
        for (int i = 0; i < edges.size(); i++) {
            DiagramEdgeSpec edge = edges.get(i);
            String path = "/edges/" + i;
            if (edge == null) {
                add(errors, path, "REQUIRED", "diagram edge must not be null");
                continue;
            }
            validateText(edge.getFrom(), path + "/from", true, errors);
            validateText(edge.getTo(), path + "/to", true, errors);
            validateText(edge.getLabel(), path + "/label", false, errors);
            if (hasText(edge.getFrom()) && !ids.contains(edge.getFrom())) {
                add(errors, path + "/from", "UNKNOWN_NODE", "edge source references an unknown node");
            }
            if (hasText(edge.getTo()) && !ids.contains(edge.getTo())) {
                add(errors, path + "/to", "UNKNOWN_NODE", "edge target references an unknown node");
            }
        }
        return new DiagramSpecValidationResult(errors);
    }

    private void validateDetails(List<String> values, String path, List<DiagramSpecViolation> errors) {
        if (values == null) {
            add(errors, path, "REQUIRED", "node detail list must not be null");
            return;
        }
        if (values.size() > MAX_DETAILS) {
            add(errors, path, "LIMIT_EXCEEDED", "node detail count exceeds " + MAX_DETAILS);
        }
        for (int i = 0; i < values.size(); i++) {
            validateText(values.get(i), path + "/" + i, true, errors);
        }
    }

    private void validateText(String value, String path, boolean required, List<DiagramSpecViolation> errors) {
        if (!hasText(value)) {
            if (required) {
                add(errors, path, "REQUIRED", "text must not be blank");
            }
        } else if (value.length() > MAX_TEXT_LENGTH) {
            add(errors, path, "LIMIT_EXCEEDED", "text length exceeds " + MAX_TEXT_LENGTH);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void add(List<DiagramSpecViolation> errors, String path, String code, String message) {
        errors.add(new DiagramSpecViolation(path, code, message));
    }

    private static EnumMap<DiagramTypeSpec, Set<DiagramNodeTypeSpec>> allowedNodeTypes() {
        EnumMap<DiagramTypeSpec, Set<DiagramNodeTypeSpec>> values = new EnumMap<>(DiagramTypeSpec.class);
        values.put(DiagramTypeSpec.USE_CASE, types(DiagramNodeTypeSpec.ACTOR, DiagramNodeTypeSpec.USE_CASE));
        values.put(DiagramTypeSpec.FLOW, types(DiagramNodeTypeSpec.START, DiagramNodeTypeSpec.PROCESS,
                DiagramNodeTypeSpec.DECISION, DiagramNodeTypeSpec.PARALLEL_SPLIT,
                DiagramNodeTypeSpec.PARALLEL_JOIN, DiagramNodeTypeSpec.END));
        values.put(DiagramTypeSpec.ER, types(DiagramNodeTypeSpec.ENTITY));
        values.put(DiagramTypeSpec.SYSTEM_ER, types(DiagramNodeTypeSpec.ENTITY,
                DiagramNodeTypeSpec.RELATIONSHIP, DiagramNodeTypeSpec.ATTRIBUTE));
        values.put(DiagramTypeSpec.CLASS, types(DiagramNodeTypeSpec.CLASS));
        values.put(DiagramTypeSpec.COMPONENT, types(DiagramNodeTypeSpec.COMPONENT));
        values.put(DiagramTypeSpec.OVERALL_FUNCTION_LOGIC, types(DiagramNodeTypeSpec.FUNCTION_SYSTEM,
                DiagramNodeTypeSpec.FUNCTION_MODULE, DiagramNodeTypeSpec.FUNCTION_ITEM));
        return values;
    }

    private static Set<DiagramNodeTypeSpec> types(DiagramNodeTypeSpec... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
