package cn.bugstack.export.definition;

import cn.bugstack.export.module.ReportDataContext;

/**
 * 报告定义模板方法基类。
 *
 * <p>外部实现通常只需覆盖 {@link #configure(ReportBlueprint.Builder, Object)}，
 * 并按需覆盖 {@link #contributeData(ReportDataContext, Object)} 提供模块数据。</p>
 *
 * @param <T> 报告入口业务数据类型
 */
public abstract class AbstractReportDefinition<T> implements ReportDefinition<T> {

    /** 固定报告编码。 */
    private final String code;
    /** 固定报告名称。 */
    private final String name;
    /** 固定蓝图版本。 */
    private final String version;

    /**
     * 创建固定编码、名称和版本的报告定义基类。
     *
     * @param code 报告编码
     * @param name 报告名称
     * @param version 蓝图版本
     */
    protected AbstractReportDefinition(String code, String name, String version) {
        this.code = code;
        this.name = name;
        this.version = version;
    }

    /**
     * 基于输入创建报告蓝图并委派子类补充配置。
     *
     * @param input 报告入口业务数据
     * @return 完整报告蓝图
     */
    @Override
    public final ReportBlueprint blueprint(T input) {
        ReportBlueprint.Builder builder = ReportBlueprint.builder(code, name, version);
        configure(builder, input);
        return builder.build();
    }

    /**
     * 配置报告蓝图。
     *
     * @param builder 报告蓝图构建器
     * @param input 报告入口业务数据
     */
    protected abstract void configure(ReportBlueprint.Builder builder, T input);
}
