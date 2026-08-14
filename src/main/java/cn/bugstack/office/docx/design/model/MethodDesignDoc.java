package cn.bugstack.office.docx.design.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 方法设计说明模型。
 */
public class MethodDesignDoc {

    /** 方法名称。 */
    private String name = "";
    /** 方法返回类型。 */
    private String returnType = "";
    /** 方法修饰符。 */
    private String modifiers = "";
    /** 方法 Javadoc 描述。 */
    private String description = "";
    /** 方法 Javadoc 中的返回值说明。 */
    private String returnDescription = "";
    private final List<ParameterDesignDoc> parameters = new ArrayList<>();
    private final List<ThrowsDesignDoc> throwsList = new ArrayList<>();

    /**
     * 创建空的方法设计说明模型。
     */
    public MethodDesignDoc() {
    }

    /**
     * 获取方法名。
     *
     * @return 方法名
     */
    public String getName() {
        return name;
    }

    /**
     * 设置方法名。
     *
     * @param name 方法名
     */
    public void setName(String name) {
        this.name = value(name);
    }

    /**
     * 获取返回值类型。
     *
     * @return 返回值类型
     */
    public String getReturnType() {
        return returnType;
    }

    /**
     * 设置返回值类型。
     *
     * @param returnType 返回值类型
     */
    public void setReturnType(String returnType) {
        this.returnType = value(returnType);
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
     * 获取方法说明。
     *
     * @return 方法说明
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置方法说明。
     *
     * @param description 方法说明
     */
    public void setDescription(String description) {
        this.description = value(description);
    }

    /**
     * 获取返回值说明。
     *
     * @return 返回值说明
     */
    public String getReturnDescription() {
        return returnDescription;
    }

    /**
     * 设置返回值说明。
     *
     * @param returnDescription 返回值说明
     */
    public void setReturnDescription(String returnDescription) {
        this.returnDescription = value(returnDescription);
    }

    /**
     * 添加参数说明。
     *
     * @param parameter 参数说明
     */
    public void addParameter(ParameterDesignDoc parameter) {
        parameters.add(parameter);
    }

    /**
     * 获取参数说明列表。
     *
     * @return 不可修改的参数说明列表
     */
    public List<ParameterDesignDoc> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    /**
     * 添加异常说明。
     *
     * @param throwsDoc 异常说明
     */
    public void addThrows(ThrowsDesignDoc throwsDoc) {
        throwsList.add(throwsDoc);
    }

    /**
     * 获取异常说明列表。
     *
     * @return 不可修改的异常说明列表
     */
    public List<ThrowsDesignDoc> getThrowsList() {
        return Collections.unmodifiableList(throwsList);
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
