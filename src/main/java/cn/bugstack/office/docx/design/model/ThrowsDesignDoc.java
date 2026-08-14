package cn.bugstack.office.docx.design.model;

/**
 * 方法异常设计说明模型。
 */
public class ThrowsDesignDoc {

    /** 异常类型。 */
    private String type = "";
    /** 异常抛出条件说明。 */
    private String description = "";

    /**
     * 创建空的方法异常设计说明模型。
     */
    public ThrowsDesignDoc() {
    }

    /**
     * 获取异常类型。
     *
     * @return 异常类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置异常类型。
     *
     * @param type 异常类型
     */
    public void setType(String type) {
        this.type = value(type);
    }

    /**
     * 获取异常说明。
     *
     * @return 异常说明
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置异常说明。
     *
     * @param description 异常说明
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
