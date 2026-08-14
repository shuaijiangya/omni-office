package cn.bugstack.office.docx.design;

import java.nio.file.Path;

/**
 * 类设计表格 Builder。
 *
 * <p>该 Builder 面向使用者暴露链式配置 API，内部维护
 * {@link ClassDesignTableOptions}。调用方通常不直接创建它，而是通过
 * {@code SectionBuilder#classDesignTable(...)} 使用。</p>
 */
public class ClassDesignTableBuilder {

    private final ClassDesignTableOptions options = ClassDesignTableOptions.create();

    /**
     * 创建类设计表格 Builder。
     */
    public ClassDesignTableBuilder() {
    }

    /**
     * 设置源码根目录，例如 {@code src/main/java}。
     *
     * @param sourceRoot 源码根目录
     * @return 当前 Builder，便于链式调用
     */
    public ClassDesignTableBuilder sourceRoot(Path sourceRoot) {
        options.sourceRoot(sourceRoot);
        return this;
    }

    /**
     * 设置需要生成设计表格的类全限定名。
     *
     * @param className 类全限定名
     * @return 当前 Builder，便于链式调用
     */
    public ClassDesignTableBuilder className(String className) {
        options.className(className);
        return this;
    }

    /**
     * 设置是否包含字段。
     *
     * @param includeFields {@code true} 表示包含字段
     * @return 当前 Builder，便于链式调用
     */
    public ClassDesignTableBuilder includeFields(boolean includeFields) {
        options.includeFields(includeFields);
        return this;
    }

    /**
     * 设置是否包含方法。
     *
     * @param includeMethods {@code true} 表示包含方法
     * @return 当前 Builder，便于链式调用
     */
    public ClassDesignTableBuilder includeMethods(boolean includeMethods) {
        options.includeMethods(includeMethods);
        return this;
    }

    /**
     * 设置是否包含 private 字段和方法。
     *
     * @param includePrivate {@code true} 表示包含 private 成员
     * @return 当前 Builder，便于链式调用
     */
    public ClassDesignTableBuilder includePrivate(boolean includePrivate) {
        options.includePrivate(includePrivate);
        return this;
    }

    /**
     * 设置是否包含常规 getter/setter 方法。
     *
     * @param includeGetterSetter {@code true} 表示包含 getter/setter
     * @return 当前 Builder，便于链式调用
     */
    public ClassDesignTableBuilder includeGetterSetter(boolean includeGetterSetter) {
        options.includeGetterSetter(includeGetterSetter);
        return this;
    }

    /**
     * 获取当前 Builder 汇总后的选项。
     *
     * @return 类设计表格选项
     */
    public ClassDesignTableOptions getOptions() {
        return options;
    }
}
