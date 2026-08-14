package cn.bugstack.export.definition;

import cn.bugstack.export.module.ReportDataContext;

/**
 * 外部定义一种完整报告的扩展点。
 *
 * @param <T> 报告入口业务数据类型
 */
public interface ReportDefinition<T> {

    /**
     * 根据业务数据创建本次导出使用的报告蓝图。
     *
     * @param input 报告入口业务数据
     * @return 报告蓝图
     */
    ReportBlueprint blueprint(T input);

    /**
     * 向类型化数据上下文写入各模块使用的数据。
     *
     * @param context 报告数据上下文
     * @param input 报告入口业务数据
     */
    default void contributeData(ReportDataContext context, T input) {
    }
}
