package cn.bugstack.office.docx.design.model;

/**
 * 方法参数设计说明模型。
 */
public class ParameterDesignDoc {

    /** 参数名称。 */
    private String name = "";
    /** 参数类型。 */
    private String type = "";
    /** 参数 Javadoc 描述。 */
    private String description = "";

    /**
     * 创建空的方法参数设计说明模型。
     */
    public ParameterDesignDoc() {
    }

    /**
     * 获取参数名。
     *
     * @return 参数名
     */
    public String getName() {
        return name;
    }

    /**
     * 设置参数名。
     *
     * @param name 参数名
     */
    public void setName(String name) {
        this.name = value(name);
    }

    /**
     * 获取参数类型。
     *
     * @return 参数类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置参数类型。
     *
     * @param type 参数类型
     */
    public void setType(String type) {
        this.type = value(type);
    }

    /**
     * 获取参数说明。
     *
     * @return 参数说明
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置参数说明。
     *
     * @param description 参数说明
     */
    public void setDescription(String description) {
        this.description = value(description);
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
