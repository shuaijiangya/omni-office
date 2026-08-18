package cn.bugstack.application.diagram;

import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramType;
import cn.bugstack.protocol.diagram.DiagramEdgeSpec;
import cn.bugstack.protocol.diagram.DiagramNodeSpec;
import cn.bugstack.protocol.diagram.DiagramNodeTypeSpec;
import cn.bugstack.protocol.diagram.DiagramSpec;
import cn.bugstack.protocol.diagram.DiagramTypeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramSpecValidatorAndCompilerTest {

    @Test
    void validatesReferencesAndCompilesToExistingDiagramModel() {
        DiagramSpec spec = flow();
        DiagramSpecValidationResult result = new DiagramSpecValidator().validate(spec);
        DiagramDefinition definition = new DiagramSpecCompiler().compile(spec);

        assertTrue(result.isValid());
        assertEquals(DiagramType.FLOW, definition.getType());
        assertEquals(2, definition.getNodes().size());
        assertEquals(1, definition.getEdges().size());
    }

    @Test
    void reportsUnknownEdgeNodeWithJsonPath() {
        DiagramSpec spec = flow();
        spec.getEdges().add(new DiagramEdgeSpec("missing", "end", null));

        DiagramSpecValidationResult result = new DiagramSpecValidator().validate(spec);

        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream().anyMatch(error ->
                "/edges/1/from".equals(error.getPath()) && "UNKNOWN_NODE".equals(error.getCode())));
    }

    @Test
    void rejectsNodeTypesThatDoNotBelongToDiagramType() {
        DiagramSpec spec = flow();
        spec.getNodes().add(new DiagramNodeSpec("actor", "用户", DiagramNodeTypeSpec.ACTOR));

        DiagramSpecValidationResult result = new DiagramSpecValidator().validate(spec);

        assertTrue(result.getViolations().stream().anyMatch(error ->
                "NODE_TYPE_NOT_ALLOWED".equals(error.getCode())));
    }

    private DiagramSpec flow() {
        DiagramSpec spec = new DiagramSpec();
        spec.setType(DiagramTypeSpec.FLOW);
        spec.setTitle("最小流程");
        spec.getNodes().add(new DiagramNodeSpec("start", "开始", DiagramNodeTypeSpec.START));
        spec.getNodes().add(new DiagramNodeSpec("end", "结束", DiagramNodeTypeSpec.END));
        spec.getEdges().add(new DiagramEdgeSpec("start", "end", null));
        return spec;
    }
}
