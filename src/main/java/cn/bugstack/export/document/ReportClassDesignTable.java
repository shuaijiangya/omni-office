package cn.bugstack.export.document;

import java.nio.file.Path;

/**
 * 从 Java 源码自动生成的类设计表格块。
 */
public final class ReportClassDesignTable implements ReportElement {

    /** 表格标题。 */
    private final String title;
    /** Java 源码根目录。 */
    private final Path sourceRoot;
    /** 要解析的完整类名。 */
    private final String className;
    /** 是否输出属性设计表。 */
    private final boolean includeFields;
    /** 是否输出方法设计表。 */
    private final boolean includeMethods;
    /** 是否收录私有成员。 */
    private final boolean includePrivate;
    /** 是否收录 Getter 与 Setter 方法。 */
    private final boolean includeGetterSetter;

    /**
     * 创建类设计表格块。
     *
     * @param title 小节标题
     * @param sourceRoot Java 源码根目录
     * @param className 目标类全限定名
     * @param includePrivate 是否包含私有成员
     * @param includeGetterSetter 是否包含 getter/setter
     */
    public ReportClassDesignTable(String title, Path sourceRoot, String className,
                                  boolean includePrivate, boolean includeGetterSetter) {
        this(builder(title, sourceRoot, className)
                .includePrivate(includePrivate)
                .includeGetterSetter(includeGetterSetter));
    }

    /**
     * 使用构建器创建可配置的类设计表格块。
     *
     * @param title 小节标题
     * @param sourceRoot Java 源码根目录
     * @param className 目标类全限定名
     * @return 类设计表格构建器
     */
    public static Builder builder(String title, Path sourceRoot, String className) {
        return new Builder(title, sourceRoot, className);
    }

    /**
     * 使用构建器配置创建类设计表格块。
     *
     * @param builder 已配置构建器
     */
    private ReportClassDesignTable(Builder builder) {
        this.title = builder.title;
        this.sourceRoot = builder.sourceRoot;
        this.className = builder.className;
        this.includeFields = builder.includeFields;
        this.includeMethods = builder.includeMethods;
        this.includePrivate = builder.includePrivate;
        this.includeGetterSetter = builder.includeGetterSetter;
    }

    /**
     * 获取当前元素的语义类型。
     *
     * @return 类设计表格类型
     */
    @Override
    public ReportElementType getElementType() {
        return ReportElementType.CLASS_DESIGN_TABLE;
    }

    /**
     * 获取类设计表格的小节标题。
     *
     * @return 小节标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取 Java 源码根目录。
     *
     * @return Java 源码根目录
     */
    public Path getSourceRoot() {
        return sourceRoot;
    }

    /**
     * 获取待解析类的全限定名。
     *
     * @return 目标类全限定名
     */
    public String getClassName() {
        return className;
    }

    /**
     * 判断是否输出属性设计表。
     *
     * @return 输出属性设计表时返回 {@code true}
     */
    public boolean isIncludeFields() {
        return includeFields;
    }

    /**
     * 判断是否输出方法设计表。
     *
     * @return 输出方法设计表时返回 {@code true}
     */
    public boolean isIncludeMethods() {
        return includeMethods;
    }

    /**
     * 判断是否导出私有字段和方法。
     *
     * @return 包含私有成员时返回 {@code true}
     */
    public boolean isIncludePrivate() {
        return includePrivate;
    }

    /**
     * 判断是否导出常规 getter/setter 方法。
     *
     * @return 包含 getter/setter 时返回 {@code true}
     */
    public boolean isIncludeGetterSetter() {
        return includeGetterSetter;
    }

    /**
     * 类设计表格配置构建器。
     */
    public static final class Builder {

        /** 类设计表格标题。 */
        private final String title;
        /** Java 源码根目录。 */
        private final Path sourceRoot;
        /** 要解析的完整类名。 */
        private final String className;
        /** 是否输出属性设计表。 */
        private boolean includeFields = true;
        /** 是否输出方法设计表。 */
        private boolean includeMethods = true;
        /** 是否包含私有成员。 */
        private boolean includePrivate = true;
        /** 是否包含 Getter 与 Setter 方法。 */
        private boolean includeGetterSetter = true;

        /**
         * 创建类设计表格配置构建器。
         *
         * @param title 类设计表格标题
         * @param sourceRoot Java 源码根目录
         * @param className 要解析的完整类名
         */
        private Builder(String title, Path sourceRoot, String className) {
            this.title = title;
            this.sourceRoot = sourceRoot;
            this.className = className;
        }

        /**
         * 设置是否输出属性设计表。
         *
         * @param includeFields 是否输出属性设计表
         * @return 当前构建器
         */
        public Builder includeFields(boolean includeFields) {
            this.includeFields = includeFields;
            return this;
        }

        /**
         * 设置是否输出方法设计表。
         *
         * @param includeMethods 是否输出方法设计表
         * @return 当前构建器
         */
        public Builder includeMethods(boolean includeMethods) {
            this.includeMethods = includeMethods;
            return this;
        }

        /**
         * 设置是否包含私有成员。
         *
         * @param includePrivate 是否包含私有成员
         * @return 当前构建器
         */
        public Builder includePrivate(boolean includePrivate) {
            this.includePrivate = includePrivate;
            return this;
        }

        /**
         * 设置是否包含 Getter 与 Setter 方法。
         *
         * @param includeGetterSetter 是否包含 Getter 与 Setter 方法
         * @return 当前构建器
         */
        public Builder includeGetterSetter(boolean includeGetterSetter) {
            this.includeGetterSetter = includeGetterSetter;
            return this;
        }

        /**
         * 创建不可变类设计表格元素。
         *
         * @return 类设计表格元素
         */
        public ReportClassDesignTable build() {
            return new ReportClassDesignTable(this);
        }
    }
}
