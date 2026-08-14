package cn.bugstack.export.example.composable;

import cn.bugstack.export.definition.ReportCoverTemplate;
import cn.bugstack.export.example.composable.model.ComposableReportCoverModel;
import cn.bugstack.export.example.composable.model.ComposableReportModuleModel;

/**
 * 可组合文本报告的业务入参。
 *
 * <p>入口只负责组合封面模型和模块模型：封面写在目录之前，模块内容写在目录之后。</p>
 */
public final class ComposableReportInput {

    /** 目录之前写入的封面模型。 */
    private final ReportCoverTemplate coverModel;
    /** 目录之后写入的模块页模型。 */
    private final ComposableReportModuleModel moduleModel;
    /** 报告编制人。 */
    private final String preparedBy;

    private ComposableReportInput(Builder builder) {
        this.coverModel = builder.coverModel == null
                ? ComposableReportCoverModel.defaultCover()
                : builder.coverModel;
        this.moduleModel = builder.moduleModel;
        this.preparedBy = builder.preparedBy;
    }

    /**
     * 创建报告入参构建器。
     *
     * @return 入参构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 使用默认封面创建报告入参构建器。
     *
     * @param moduleModel 模块页模型
     * @return 入参构建器
     */
    public static Builder builder(ComposableReportModuleModel moduleModel) {
        return new Builder().modules(moduleModel);
    }

    /**
     * 使用两个独立模型创建报告入参构建器。
     *
     * @param coverModel 封面模型
     * @param moduleModel 模块页模型
     * @return 入参构建器
     */
    public static Builder builder(ReportCoverTemplate coverModel,
                                  ComposableReportModuleModel moduleModel) {
        return new Builder().cover(coverModel).modules(moduleModel);
    }

    /**
     * 获取封面模型。
     *
     * @return 封面模型
     */
    public ReportCoverTemplate getCoverModel() {
        return coverModel;
    }

    /**
     * 获取模块页模型。
     *
     * @return 模块页模型
     */
    public ComposableReportModuleModel getModuleModel() {
        return moduleModel;
    }

    /**
     * 获取报告编制人。
     *
     * @return 报告编制人；未设置时为 {@code null}
     */
    public String getPreparedBy() {
        return preparedBy;
    }

    /** 可组合报告入参构建器。 */
    public static final class Builder {

        /** 待构建封面模型。 */
        private ReportCoverTemplate coverModel;
        /** 待构建模块页模型。 */
        private ComposableReportModuleModel moduleModel;
        /** 待构建报告编制人。 */
        private String preparedBy;

        private Builder() {
        }

        /**
         * 设置目录之前写入的自定义封面模型；不调用时使用标准默认封面。
         *
         * @param coverModel 封面模型
         * @return 当前构建器
         */
        public Builder cover(ReportCoverTemplate coverModel) {
            this.coverModel = requiredModel(coverModel, "report cover model");
            return this;
        }

        /**
         * 设置目录之后写入的模块页模型。
         *
         * @param moduleModel 模块页模型
         * @return 当前构建器
         */
        public Builder modules(ComposableReportModuleModel moduleModel) {
            this.moduleModel = requiredModel(moduleModel, "report module model");
            return this;
        }

        /**
         * 设置报告编制人，仅作为文档元数据，不会生成基础信息表格。
         *
         * @param preparedBy 报告编制人
         * @return 当前构建器
         */
        public Builder preparedBy(String preparedBy) {
            this.preparedBy = requiredText(preparedBy, "prepared by");
            return this;
        }

        /**
         * 校验并创建不可变业务入参。
         *
         * @return 可组合报告入参
         */
        public ComposableReportInput build() {
            if (moduleModel == null) {
                throw new IllegalArgumentException("report module model must not be null");
            }
            return new ComposableReportInput(this);
        }

        private static <T> T requiredModel(T value, String name) {
            if (value == null) {
                throw new IllegalArgumentException(name + " must not be null");
            }
            return value;
        }

        private static String requiredText(String value, String name) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}
