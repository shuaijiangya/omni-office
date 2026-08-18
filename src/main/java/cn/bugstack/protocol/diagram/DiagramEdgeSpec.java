package cn.bugstack.protocol.diagram;

/** DiagramSpec 中的有向边。 */
public final class DiagramEdgeSpec {

    private String from;
    private String to;
    private String label;

    public DiagramEdgeSpec() {
    }

    public DiagramEdgeSpec(String from, String to, String label) {
        this.from = from;
        this.to = to;
        this.label = label;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
