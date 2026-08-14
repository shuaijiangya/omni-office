package cn.bugstack.office.docx.design.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 类设计文档模型。
 *
 * <p>该模型是源码解析结果的稳定表达，文档生成层只依赖它，不直接依赖具体源码解析库
 * 或 AST 类型。</p>
 */
public class ClassDesignDoc {

    /** 类所在包名。 */
    private String packageName = "";
    /** 类的简单名称。 */
    private String className = "";
    /** 类型类别，例如 class、interface 或 enum。 */
    private String kind = "class";
    /** 类修饰符。 */
    private String modifiers = "";
    /** 类级 Javadoc 描述。 */
    private String description = "";
    /** 类级 Javadoc 中的作者。 */
    private String author = "";
    /** 类级 Javadoc 中的版本起始信息。 */
    private String since = "";
    /** 直接父类名称。 */
    private String superClass = "";
    private final List<String> interfaces = new ArrayList<>();
    private final List<FieldDesignDoc> fields = new ArrayList<>();
    private final List<MethodDesignDoc> methods = new ArrayList<>();

    /**
     * 创建空的类设计文档模型。
     */
    public ClassDesignDoc() {
    }

    /**
     * 获取包名。
     *
     * @return 包名
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * 设置包名。
     *
     * @param packageName 包名
     */
    public void setPackageName(String packageName) {
        this.packageName = value(packageName);
    }

    /**
     * 获取类名。
     *
     * @return 类名
     */
    public String getClassName() {
        return className;
    }

    /**
     * 设置类名。
     *
     * @param className 类名
     */
    public void setClassName(String className) {
        this.className = value(className);
    }

    /**
     * 获取类型种类。
     *
     * @return 类型种类，例如 {@code class}、{@code interface}、{@code enum}
     */
    public String getKind() {
        return kind;
    }

    /**
     * 设置类型种类。
     *
     * @param kind 类型种类
     */
    public void setKind(String kind) {
        this.kind = value(kind);
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
     * 获取类说明。
     *
     * @return 类说明
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置类说明。
     *
     * @param description 类说明
     */
    public void setDescription(String description) {
        this.description = value(description);
    }

    /**
     * 获取作者。
     *
     * @return 作者
     */
    public String getAuthor() {
        return author;
    }

    /**
     * 设置作者。
     *
     * @param author 作者
     */
    public void setAuthor(String author) {
        this.author = value(author);
    }

    /**
     * 获取起始版本。
     *
     * @return 起始版本
     */
    public String getSince() {
        return since;
    }

    /**
     * 设置起始版本。
     *
     * @param since 起始版本
     */
    public void setSince(String since) {
        this.since = value(since);
    }

    /**
     * 获取父类名称。
     *
     * @return 父类名称
     */
    public String getSuperClass() {
        return superClass;
    }

    /**
     * 设置父类名称。
     *
     * @param superClass 父类名称
     */
    public void setSuperClass(String superClass) {
        this.superClass = value(superClass);
    }

    /**
     * 添加接口名称。
     *
     * @param interfaceName 接口名称
     */
    public void addInterface(String interfaceName) {
        interfaces.add(value(interfaceName));
    }

    /**
     * 获取接口名称列表。
     *
     * @return 不可修改的接口名称列表
     */
    public List<String> getInterfaces() {
        return Collections.unmodifiableList(interfaces);
    }

    /**
     * 添加字段说明。
     *
     * @param field 字段说明
     */
    public void addField(FieldDesignDoc field) {
        fields.add(field);
    }

    /**
     * 获取字段说明列表。
     *
     * @return 不可修改的字段说明列表
     */
    public List<FieldDesignDoc> getFields() {
        return Collections.unmodifiableList(fields);
    }

    /**
     * 添加方法说明。
     *
     * @param method 方法说明
     */
    public void addMethod(MethodDesignDoc method) {
        methods.add(method);
    }

    /**
     * 获取方法说明列表。
     *
     * @return 不可修改的方法说明列表
     */
    public List<MethodDesignDoc> getMethods() {
        return Collections.unmodifiableList(methods);
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
