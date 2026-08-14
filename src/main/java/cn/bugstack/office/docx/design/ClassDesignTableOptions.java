package cn.bugstack.office.docx.design;

import java.nio.file.Path;

/**
 * 类设计表格生成选项。
 *
 * <p>该对象承载“从哪里读取源码、读取哪个类、表格中包含哪些成员”等配置。
 * 它是 {@link ClassDesignTableBuilder} 和解析器之间的参数模型，避免后续扩展时
 * 在公开 API 上堆叠大量方法参数。</p>
 */
public class ClassDesignTableOptions {

    /** Java 源码根目录。 */
    private Path sourceRoot;
    /** 要生成设计表的完整类名。 */
    private String className;
    /** 是否输出属性设计表。 */
    private boolean includeFields = true;
    /** 是否输出方法设计表。 */
    private boolean includeMethods = true;
    /** 是否包含私有成员。 */
    private boolean includePrivate;
    /** 是否包含 Getter 与 Setter 方法。 */
    private boolean includeGetterSetter = true;

    private ClassDesignTableOptions() {
    }

    /**
     * 创建类设计表格选项。
     *
     * @return 新的选项对象
     */
    public static ClassDesignTableOptions create() {
        return new ClassDesignTableOptions();
    }

    /**
     * 设置源码根目录，例如 {@code src/main/java}。
     *
     * @param sourceRoot 源码根目录
     * @return 当前选项对象，便于链式调用
     */
    public ClassDesignTableOptions sourceRoot(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
        return this;
    }

    /**
     * 设置需要生成设计表格的类全限定名。
     *
     * @param className 类全限定名，例如 {@code cn.bugstack.demo.UserService}
     * @return 当前选项对象，便于链式调用
     */
    public ClassDesignTableOptions className(String className) {
        this.className = className;
        return this;
    }

    /**
     * 设置是否包含字段。
     *
     * @param includeFields {@code true} 表示包含字段
     * @return 当前选项对象，便于链式调用
     */
    public ClassDesignTableOptions includeFields(boolean includeFields) {
        this.includeFields = includeFields;
        return this;
    }

    /**
     * 设置是否包含方法。
     *
     * @param includeMethods {@code true} 表示包含方法
     * @return 当前选项对象，便于链式调用
     */
    public ClassDesignTableOptions includeMethods(boolean includeMethods) {
        this.includeMethods = includeMethods;
        return this;
    }

    /**
     * 设置是否包含私有成员。
     *
     * @param includePrivate {@code true} 表示包含 private 字段和方法
     * @return 当前选项对象，便于链式调用
     */
    public ClassDesignTableOptions includePrivate(boolean includePrivate) {
        this.includePrivate = includePrivate;
        return this;
    }

    /**
     * 设置是否包含常规 getter/setter 方法。
     *
     * @param includeGetterSetter {@code true} 表示包含 getter/setter
     * @return 当前选项对象，便于链式调用
     */
    public ClassDesignTableOptions includeGetterSetter(boolean includeGetterSetter) {
        this.includeGetterSetter = includeGetterSetter;
        return this;
    }

    /**
     * 获取源码根目录。
     *
     * @return 源码根目录
     */
    public Path getSourceRoot() {
        return sourceRoot;
    }

    /**
     * 获取类全限定名。
     *
     * @return 类全限定名
     */
    public String getClassName() {
        return className;
    }

    /**
     * 判断是否包含字段。
     *
     * @return {@code true} 表示包含字段
     */
    public boolean isIncludeFields() {
        return includeFields;
    }

    /**
     * 判断是否包含方法。
     *
     * @return {@code true} 表示包含方法
     */
    public boolean isIncludeMethods() {
        return includeMethods;
    }

    /**
     * 判断是否包含私有成员。
     *
     * @return {@code true} 表示包含 private 字段和方法
     */
    public boolean isIncludePrivate() {
        return includePrivate;
    }

    /**
     * 判断是否包含常规 getter/setter。
     *
     * @return {@code true} 表示包含 getter/setter
     */
    public boolean isIncludeGetterSetter() {
        return includeGetterSetter;
    }
}
