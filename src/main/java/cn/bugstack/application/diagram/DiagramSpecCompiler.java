package cn.bugstack.application.diagram;

import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramNodeType;
import cn.bugstack.office.diagram.model.DiagramType;
import cn.bugstack.protocol.diagram.DiagramEdgeSpec;
import cn.bugstack.protocol.diagram.DiagramNodeSpec;
import cn.bugstack.protocol.diagram.DiagramNodeTypeSpec;
import cn.bugstack.protocol.diagram.DiagramSpec;

/** 将对外 DiagramSpec 编译为底层不可变图定义。 */
public final class DiagramSpecCompiler {

    public DiagramDefinition compile(DiagramSpec spec) {
        if (spec == null || spec.getType() == null) {
            throw new IllegalArgumentException("diagram spec and type must not be null");
        }
        DiagramDefinition.Builder builder = DiagramDefinition.builder(
                DiagramType.valueOf(spec.getType().name()), spec.getTitle());
        for (DiagramNodeSpec node : spec.getNodes()) {
            builder.node(compileNode(node));
        }
        for (DiagramEdgeSpec edge : spec.getEdges()) {
            builder.edge(new DiagramEdge(edge.getFrom(), edge.getTo(), edge.getLabel()));
        }
        return builder.build();
    }

    private DiagramNode compileNode(DiagramNodeSpec source) {
        DiagramNodeType type = DiagramNodeType.valueOf(source.getType().name());
        if (source.getType() == DiagramNodeTypeSpec.CLASS) {
            return DiagramNode.classNode(source.getId(), source.getLabel(),
                    source.getClassAttributes(), source.getClassMethods());
        }
        if (source.getType() == DiagramNodeTypeSpec.ENTITY
                || source.getType() == DiagramNodeTypeSpec.COMPONENT) {
            return new DiagramNode(source.getId(), source.getLabel(), type, source.getFields());
        }
        return new DiagramNode(source.getId(), source.getLabel(), type);
    }
}
