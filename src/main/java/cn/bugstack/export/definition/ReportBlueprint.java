package cn.bugstack.export.definition;

import cn.bugstack.export.context.ReportBasicInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可版本化的报告模板定义。
 *
 * <p>该对象是导出核心与数据库实体、配置文件之间的防腐边界。数据库模板需要先转换成
 * 蓝图，导出核心不直接依赖任何持久化对象。</p>
 */
public final class ReportBlueprint {

    /** 稳定报告编码。 */
    private final String code;
    /** 报告名称。 */
    private final String name;
    /** 蓝图版本。 */
    private final String version;
    /** 文档主标题。 */
    private final String title;
    /** 文档作者。 */
    private final String author;
    /** 文档主题。 */
    private final String subject;
    /** 报告基础信息。 */
    private final ReportBasicInfo basicInfo;
    /** 报告版式配置。 */
    private final ReportLayout layout;
    /** 按声明顺序配置的模块槽位。 */
    private final List<ModuleSlot> moduleSlots;

    private ReportBlueprint(Builder builder) {
        this.code = builder.code;
        this.name = builder.name;
        this.version = builder.version;
        this.title = builder.title;
        this.author = builder.author;
        this.subject = builder.subject;
        this.basicInfo = builder.basicInfo;
        this.layout = builder.layout;
        this.moduleSlots = Collections.unmodifiableList(new ArrayList<>(builder.moduleSlots));
    }

    /**
     * 创建报告蓝图构建器。
     *
     * @param code 稳定的报告编码
     * @param name 报告名称
     * @param version 蓝图版本
     * @return 报告蓝图构建器
     */
    public static Builder builder(String code, String name, String version) {
        return new Builder(code, name, version);
    }

    /**
     * 获取报告编码。
     *
     * @return 报告编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取报告名称。
     *
     * @return 报告名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取蓝图版本。
     *
     * @return 蓝图版本
     */
    public String getVersion() {
        return version;
    }

    /**
     * 获取文档主标题。
     *
     * @return 文档主标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取文档作者。
     *
     * @return 文档作者；未设置时为 {@code null}
     */
    public String getAuthor() {
        return author;
    }

    /**
     * 获取文档主题。
     *
     * @return 文档主题；未设置时为 {@code null}
     */
    public String getSubject() {
        return subject;
    }

    /**
     * 获取报告基础信息。
     *
     * @return 报告基础信息；未设置时为 {@code null}
     */
    public ReportBasicInfo getBasicInfo() {
        return basicInfo;
    }

    /**
     * 获取报告版式配置。
     *
     * @return 报告版式配置
     */
    public ReportLayout getLayout() {
        return layout;
    }

    /**
     * 获取按声明顺序配置的模块槽位。
     *
     * @return 不可修改的模块槽位列表
     */
    public List<ModuleSlot> getModuleSlots() {
        return moduleSlots;
    }

    /** 报告蓝图构建器。 */
    public static final class Builder {

        /** 待构建蓝图的报告编码。 */
        private final String code;
        /** 待构建蓝图的报告名称。 */
        private final String name;
        /** 待构建蓝图的版本。 */
        private final String version;
        /** 待构建蓝图的文档标题。 */
        private String title;
        /** 待构建蓝图的文档作者。 */
        private String author;
        /** 待构建蓝图的文档主题。 */
        private String subject;
        /** 待构建蓝图的报告基础信息。 */
        private ReportBasicInfo basicInfo;
        private ReportLayout layout = ReportLayout.builder().build();
        private final List<ModuleSlot> moduleSlots = new ArrayList<>();

        private Builder(String code, String name, String version) {
            this.code = requiredText(code, "report code");
            this.name = requiredText(name, "report name");
            this.version = requiredText(version, "report blueprint version");
        }

        /**
         * 设置文档主标题；为空时默认使用报告名称。
         *
         * @param title 文档主标题
         * @return 当前构建器
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * 设置文档元数据。
         *
         * @param author 文档作者
         * @param subject 文档主题
         * @return 当前构建器
         */
        public Builder metadata(String author, String subject) {
            this.author = author;
            this.subject = subject;
            return this;
        }

        /**
         * 设置报告基础信息。
         *
         * @param basicInfo 报告基础信息
         * @return 当前构建器
         */
        public Builder basicInfo(ReportBasicInfo basicInfo) {
            this.basicInfo = basicInfo;
            return this;
        }

        /**
         * 设置报告版式配置。
         *
         * @param layout 报告版式配置
         * @return 当前构建器
         */
        public Builder layout(ReportLayout layout) {
            this.layout = layout;
            return this;
        }

        /**
         * 按声明顺序追加模块槽位。
         *
         * @param moduleSlot 模块槽位
         * @return 当前构建器
         */
        public Builder module(ModuleSlot moduleSlot) {
            if (moduleSlot == null) {
                throw new IllegalArgumentException("module slot must not be null");
            }
            this.moduleSlots.add(moduleSlot);
            return this;
        }

        /**
         * 追加使用默认配置的模块槽位。
         *
         * @param moduleCode 已注册模块编码
         * @return 当前构建器
         */
        public Builder module(String moduleCode) {
            return module(ModuleSlot.builder(moduleCode).build());
        }

        /**
         * 校验并创建不可变报告蓝图。
         *
         * @return 报告蓝图
         */
        public ReportBlueprint build() {
            if (title == null || title.trim().isEmpty()) {
                title = name;
            }
            if (layout == null) {
                throw new IllegalArgumentException("report layout must not be null");
            }
            return new ReportBlueprint(this);
        }

        /**
         * 校验并规范化 Builder 的必填文本配置。
         *
         * @param value 原始文本
         * @param name 配置项名称
         * @return 去除首尾空白后的文本
         */
        private static String requiredText(String value, String name) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}
