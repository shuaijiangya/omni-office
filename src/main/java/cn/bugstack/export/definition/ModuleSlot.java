package cn.bugstack.export.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 报告蓝图中的一个模块槽位。
 */
public final class ModuleSlot {

    /** 已注册模块的唯一编码。 */
    private final String moduleCode;
    /** 当前报告中的章节标题覆盖值。 */
    private final String titleOverride;
    /** 缺少模块数据时是否中断导出。 */
    private final boolean required;
    /** 决定模块是否参与导出的条件编码。 */
    private final String conditionKey;
    /** 必须先执行的前置模块编码。 */
    private final List<String> dependsOn;

    private ModuleSlot(Builder builder) {
        this.moduleCode = builder.moduleCode;
        this.titleOverride = builder.titleOverride;
        this.required = builder.required;
        this.conditionKey = builder.conditionKey;
        this.dependsOn = Collections.unmodifiableList(new ArrayList<>(builder.dependsOn));
    }

    /**
     * 创建指定模块编码的槽位构建器。
     *
     * @param moduleCode 已注册报告模块的编码
     * @return 模块槽位构建器
     */
    public static Builder builder(String moduleCode) {
        return new Builder(moduleCode);
    }

    /**
     * 获取关联模块编码。
     *
     * @return 模块编码
     */
    public String getModuleCode() {
        return moduleCode;
    }

    /**
     * 获取模块标题覆盖值。
     *
     * @return 覆盖标题；未指定时为 {@code null}
     */
    public String getTitleOverride() {
        return titleOverride;
    }

    /**
     * 判断缺少模块数据时是否中断导出。
     *
     * @return 必填模块时返回 {@code true}
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * 获取模块参与导出的命名条件。
     *
     * @return 条件编码；未配置时为 {@code null}
     */
    public String getConditionKey() {
        return conditionKey;
    }

    /**
     * 获取必须先执行的模块编码。
     *
     * @return 不可修改的依赖模块编码列表
     */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    /** 模块槽位构建器。 */
    public static final class Builder {

        /** 待构建槽位关联的模块编码。 */
        private final String moduleCode;
        /** 待构建槽位的标题覆盖值。 */
        private String titleOverride;
        /** 待构建槽位是否必填，默认必填。 */
        private boolean required = true;
        /** 待构建槽位的参与条件编码。 */
        private String conditionKey;
        private final List<String> dependsOn = new ArrayList<>();

        private Builder(String moduleCode) {
            if (moduleCode == null || moduleCode.trim().isEmpty()) {
                throw new IllegalArgumentException("module code must not be blank");
            }
            this.moduleCode = moduleCode.trim();
        }

        /**
         * 覆盖模块默认章节标题。
         *
         * @param titleOverride 本次报告显示的章节标题
         * @return 当前构建器
         */
        public Builder title(String titleOverride) {
            this.titleOverride = titleOverride;
            return this;
        }

        /**
         * 设置模块数据是否必填。
         *
         * @param required 缺少数据时是否终止导出
         * @return 当前构建器
         */
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * 设置模块参与导出的条件编码。
         *
         * @param conditionKey 已注册的条件编码
         * @return 当前构建器
         */
        public Builder condition(String conditionKey) {
            this.conditionKey = conditionKey;
            return this;
        }

        /**
         * 声明当前模块依赖的前置模块。
         *
         * @param moduleCode 前置模块编码
         * @return 当前构建器
         */
        public Builder dependsOn(String moduleCode) {
            if (moduleCode == null || moduleCode.trim().isEmpty()) {
                throw new IllegalArgumentException("dependency module code must not be blank");
            }
            this.dependsOn.add(moduleCode.trim());
            return this;
        }

        /**
         * 创建不可变模块槽位。
         *
         * @return 模块槽位
         */
        public ModuleSlot build() {
            return new ModuleSlot(this);
        }
    }
}
