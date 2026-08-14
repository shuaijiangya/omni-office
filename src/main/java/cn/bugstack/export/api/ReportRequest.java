package cn.bugstack.export.api;

import cn.bugstack.export.definition.ReportDefinition;

/**
 * 一次报告导出请求。
 *
 * @param <T> 报告入口业务数据类型
 */
public final class ReportRequest<T> {

    /** 本次导出使用的报告定义。 */
    private final ReportDefinition<T> definition;
    /** 报告入口业务数据。 */
    private final T input;
    /** 目标输出格式。 */
    private final ReportOutputFormat outputFormat;

    private ReportRequest(Builder<T> builder) {
        this.definition = builder.definition;
        this.input = builder.input;
        this.outputFormat = builder.outputFormat;
    }

    /**
     * 创建报告导出请求构建器。
     *
     * @param <T> 报告入口业务数据类型
     * @return 请求构建器
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * 获取本次导出使用的报告定义。
     *
     * @return 报告定义
     */
    public ReportDefinition<T> getDefinition() {
        return definition;
    }

    /**
     * 获取报告入口业务数据。
     *
     * @return 业务数据；允许为 {@code null}，由具体报告定义决定其含义
     */
    public T getInput() {
        return input;
    }

    /**
     * 获取目标输出格式。
     *
     * @return 输出格式
     */
    public ReportOutputFormat getOutputFormat() {
        return outputFormat;
    }

    /**
     * 报告请求构建器。
     *
     * @param <T> 报告入口业务数据类型
     */
    public static final class Builder<T> {

        /** 待构建请求的报告定义。 */
        private ReportDefinition<T> definition;
        /** 待构建请求的业务数据。 */
        private T input;
        /** 待构建请求的输出格式，默认 DOCX。 */
        private ReportOutputFormat outputFormat = ReportOutputFormat.DOCX;

        /**
         * 设置报告定义。
         *
         * @param definition 报告定义
         * @return 当前构建器
         */
        public Builder<T> definition(ReportDefinition<T> definition) {
            this.definition = definition;
            return this;
        }

        /**
         * 设置报告入口业务数据。
         *
         * @param input 业务数据
         * @return 当前构建器
         */
        public Builder<T> input(T input) {
            this.input = input;
            return this;
        }

        /**
         * 设置目标输出格式，默认值为 {@link ReportOutputFormat#DOCX}。
         *
         * @param outputFormat 目标输出格式
         * @return 当前构建器
         */
        public Builder<T> outputFormat(ReportOutputFormat outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        /**
         * 校验并创建导出请求。
         *
         * @return 不可变的报告导出请求
         */
        public ReportRequest<T> build() {
            if (definition == null) {
                throw new IllegalArgumentException("report definition must not be null");
            }
            if (outputFormat == null) {
                throw new IllegalArgumentException("report output format must not be null");
            }
            return new ReportRequest<>(this);
        }
    }
}
