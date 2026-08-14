package cn.bugstack.office.diagram.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 图中的节点定义。
 */
public final class DiagramNode {

    /** 节点唯一标识。 */
    private final String id;
    /** 节点显示文本。 */
    private final String label;
    /** 节点展示类型。 */
    private final DiagramNodeType type;
    /** ER 实体的字段列表。 */
    private final List<String> fields;
    /** UML 类图中类的属性列表。 */
    private final List<String> classAttributes;
    /** UML 类图中类的方法列表。 */
    private final List<String> classMethods;

    /**
     * 创建不包含实体字段的图节点。
     *
     * @param id 节点唯一标识
     * @param label 节点显示文本
     * @param type 节点展示类型
     */
    public DiagramNode(String id, String label, DiagramNodeType type) {
        this(id, label, type, Collections.emptyList());
    }

    /**
     * 创建包含实体字段的图节点。
     *
     * @param id 节点唯一标识
     * @param label 节点显示文本
     * @param type 节点展示类型
     * @param fields 实体字段列表
     */
    public DiagramNode(String id, String label, DiagramNodeType type, List<String> fields) {
        this(id, label, type, fields, Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    /**
     * 创建完整的图节点定义。
     *
     * @param id 节点唯一标识
     * @param label 节点显示文本
     * @param type 节点展示类型
     * @param fields ER 实体字段列表
     * @param classAttributes UML 类属性列表
     * @param classMethods UML 类方法列表
     */
    private DiagramNode(String id, String label, DiagramNodeType type, List<String> fields,
                        List<String> classAttributes, List<String> classMethods) {
        this.id = requiredText(id, "diagram node id");
        this.label = requiredText(label, "diagram node label");
        if (type == null) {
            throw new IllegalArgumentException("diagram node type must not be null");
        }
        this.type = type;
        this.fields = immutableValues(fields);
        this.classAttributes = immutableValues(classAttributes);
        this.classMethods = immutableValues(classMethods);
    }

    /**
     * 创建 ER 实体节点。
     *
     * @param id 实体唯一标识
     * @param label 实体显示名称
     * @param fields 实体字段列表
     * @return ER 实体节点
     */
    public static DiagramNode entity(String id, String label, String... fields) {
        List<String> values = new ArrayList<>();
        if (fields != null) {
            Collections.addAll(values, fields);
        }
        return new DiagramNode(id, label, DiagramNodeType.ENTITY, values);
    }

    /**
     * 创建系统 E-R 图中的概念实体节点。
     *
     * <p>系统 E-R 图使用实体矩形、关系菱形及属性椭圆分别表达业务对象、业务关系和
     * 概念属性；实体自身不包含数据库字段明细。</p>
     *
     * @param id 实体唯一标识
     * @param label 实体显示名称
     * @return 系统 E-R 概念实体节点
     */
    public static DiagramNode systemEntity(String id, String label) {
        return new DiagramNode(id, label, DiagramNodeType.ENTITY);
    }

    /**
     * 创建系统 E-R 图中的关系节点。
     *
     * @param id 关系唯一标识
     * @param label 关系显示名称
     * @return 系统 E-R 关系菱形节点
     */
    public static DiagramNode relationship(String id, String label) {
        return new DiagramNode(id, label, DiagramNodeType.RELATIONSHIP);
    }

    /**
     * 创建系统 E-R 图中的属性节点。
     *
     * @param id 属性唯一标识
     * @param label 属性显示名称
     * @return 系统 E-R 属性椭圆节点
     */
    public static DiagramNode attribute(String id, String label) {
        return new DiagramNode(id, label, DiagramNodeType.ATTRIBUTE);
    }

    /**
     * 创建 UML 类图中的类节点。
     *
     * @param id 类唯一标识
     * @param label 类名称
     * @param attributes 类属性列表
     * @param methods 类方法列表
     * @return UML 类节点
     */
    public static DiagramNode classNode(String id, String label, List<String> attributes, List<String> methods) {
        return new DiagramNode(id, label, DiagramNodeType.CLASS, Collections.<String>emptyList(), attributes, methods);
    }

    /**
     * 创建 UML 类图中的类节点。
     *
     * @param id 类唯一标识
     * @param label 类名称
     * @param attributes 类属性数组
     * @param methods 类方法数组
     * @return UML 类节点
     */
    public static DiagramNode classNode(String id, String label, String[] attributes, String[] methods) {
        List<String> attributeValues = new ArrayList<>();
        List<String> methodValues = new ArrayList<>();
        if (attributes != null) {
            Collections.addAll(attributeValues, attributes);
        }
        if (methods != null) {
            Collections.addAll(methodValues, methods);
        }
        return classNode(id, label, attributeValues, methodValues);
    }

    /**
     * 创建 CSCI 等软件部件节点。
     *
     * <p>部件信息以固定行形式展示，可用于标注职责、提供接口、依赖接口或数据交换内容。</p>
     *
     * @param id 部件唯一标识
     * @param label 部件显示名称
     * @param details 部件职责或接口说明
     * @return CSCI 软件部件节点
     */
    public static DiagramNode component(String id, String label, String... details) {
        List<String> values = new ArrayList<>();
        if (details != null) {
            Collections.addAll(values, details);
        }
        return new DiagramNode(id, label, DiagramNodeType.COMPONENT, values);
    }

    /**
     * 创建总体功能逻辑图中的系统根节点。
     *
     * @param id 系统节点唯一标识
     * @param label 系统显示名称
     * @return 系统根节点
     */
    public static DiagramNode functionSystem(String id, String label) {
        return new DiagramNode(id, label, DiagramNodeType.FUNCTION_SYSTEM);
    }

    /**
     * 创建总体功能逻辑图中的一级功能模块节点。
     *
     * @param id 功能模块唯一标识
     * @param label 功能模块显示名称
     * @return 一级功能模块节点
     */
    public static DiagramNode functionModule(String id, String label) {
        return new DiagramNode(id, label, DiagramNodeType.FUNCTION_MODULE);
    }

    /**
     * 创建总体功能逻辑图中的末级功能项节点。
     *
     * @param id 功能项唯一标识
     * @param label 功能项显示名称
     * @return 末级功能项节点
     */
    public static DiagramNode functionItem(String id, String label) {
        return new DiagramNode(id, label, DiagramNodeType.FUNCTION_ITEM);
    }

    /**
     * 获取节点唯一标识。
     *
     * @return 节点唯一标识
     */
    public String getId() {
        return id;
    }

    /**
     * 获取节点显示文本。
     *
     * @return 节点显示文本
     */
    public String getLabel() {
        return label;
    }

    /**
     * 获取节点展示类型。
     *
     * @return 节点展示类型
     */
    public DiagramNodeType getType() {
        return type;
    }

    /**
     * 获取 ER 实体字段列表。
     *
     * @return 不可修改的字段列表
     */
    public List<String> getFields() {
        return fields;
    }

    /**
     * 获取 UML 类图中类的属性列表。
     *
     * @return 不可修改的类属性列表
     */
    public List<String> getClassAttributes() {
        return classAttributes;
    }

    /**
     * 获取 UML 类图中类的方法列表。
     *
     * @return 不可修改的类方法列表
     */
    public List<String> getClassMethods() {
        return classMethods;
    }

    /**
     * 创建不可修改的文本列表副本。
     *
     * @param values 原始文本列表
     * @return 不可修改的文本列表
     */
    private static List<String> immutableValues(List<String> values) {
        return values == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * 校验并规范化必填文本。
     *
     * @param value 原始文本
     * @param name 参数名称
     * @return 规范化后的文本
     */
    private static String requiredText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
