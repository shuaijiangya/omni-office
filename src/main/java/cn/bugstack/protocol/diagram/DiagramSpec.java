package cn.bugstack.protocol.diagram;

import java.util.ArrayList;
import java.util.List;

/** 可由 AI、Function Call 或 MCP 填充的图形协议。 */
public final class DiagramSpec {

    private String schemaVersion = DiagramSpecVersion.V1;
    private DiagramTypeSpec type;
    private String title;
    private List<DiagramNodeSpec> nodes = new ArrayList<>();
    private List<DiagramEdgeSpec> edges = new ArrayList<>();

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public DiagramTypeSpec getType() {
        return type;
    }

    public void setType(DiagramTypeSpec type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<DiagramNodeSpec> getNodes() {
        return nodes;
    }

    public void setNodes(List<DiagramNodeSpec> nodes) {
        this.nodes = nodes == null ? new ArrayList<>() : nodes;
    }

    public List<DiagramEdgeSpec> getEdges() {
        return edges;
    }

    public void setEdges(List<DiagramEdgeSpec> edges) {
        this.edges = edges == null ? new ArrayList<>() : edges;
    }
}
