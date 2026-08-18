package cn.bugstack.protocol.diagram;

import java.util.ArrayList;
import java.util.List;

/** DiagramSpec 中的节点。 */
public final class DiagramNodeSpec {

    private String id;
    private String label;
    private DiagramNodeTypeSpec type;
    private List<String> fields = new ArrayList<>();
    private List<String> classAttributes = new ArrayList<>();
    private List<String> classMethods = new ArrayList<>();

    public DiagramNodeSpec() {
    }

    public DiagramNodeSpec(String id, String label, DiagramNodeTypeSpec type) {
        this.id = id;
        this.label = label;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public DiagramNodeTypeSpec getType() {
        return type;
    }

    public void setType(DiagramNodeTypeSpec type) {
        this.type = type;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields == null ? new ArrayList<>() : fields;
    }

    public List<String> getClassAttributes() {
        return classAttributes;
    }

    public void setClassAttributes(List<String> classAttributes) {
        this.classAttributes = classAttributes == null ? new ArrayList<>() : classAttributes;
    }

    public List<String> getClassMethods() {
        return classMethods;
    }

    public void setClassMethods(List<String> classMethods) {
        this.classMethods = classMethods == null ? new ArrayList<>() : classMethods;
    }
}
