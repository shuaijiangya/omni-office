package cn.bugstack.office.docx.design.model;

/**
 * 字段设计说明模型。
 */
public class FieldDesignDoc {

    /** 属性名称。 */
    private String name = "";
    /** 属性类型。 */
    private String type = "";
    /** 属性修饰符。 */
    private String modifiers = "";
    /** 属性 Javadoc 描述。 */
    private String description = "";
    /** 属性默认值表达式。 */
    private String defaultValue = "";

    /**
     * 创建空的字段设计说明模型。
     */
    public FieldDesignDoc() {
    }

    /**
     * 获取字段名。
     *
     * @return 字段名
     */
    public String getName() {
        return name;
    }

    /**
     * 设置字段名。
     *
     * @param name 字段名
     */
    public void setName(String name) {
        this.name = value(name);
    }

    /**
     * 获取字段类型。
     *
     * @return 字段类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置字段类型。
     *
     * @param type 字段类型
     */
    public void setType(String type) {
        this.type = value(type);
    }

    /**
     * 获取访问修饰符。
     *
     * @return 访问修饰符
     */
    public String getModifiers() {
        return modifiers;
    }

    /**
     * 设置访问修饰符。
     *
     * @param modifiers 访问修饰符
     */
    public void setModifiers(String modifiers) {
        this.modifiers = value(modifiers);
    }

    /**
     * 获取字段说明。
     *
     * @return 字段说明
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置字段说明。
     *
     * @param description 字段说明
     */
    public void setDescription(String description) {
        this.description = value(description);
    }

    /**
     * 获取默认值表达式。
     *
     * @return 默认值表达式
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * 设置默认值表达式。
     *
     * @param defaultValue 默认值表达式
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = value(defaultValue);
    }

    /**
     * 将空值规范化为空字符串。
     *
     * @param value 原始文本
     * @return 非 {@code null} 的文本
     */
    private String value(String value) {
        return value == null ? "" : value;
    }
}
