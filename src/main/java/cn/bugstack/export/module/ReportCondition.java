package cn.bugstack.export.module;

/**
 * 命名报告模块条件。
 *
 * <p>条件通过注册表按名称解析，核心层不会执行来自数据库的脚本或表达式。</p>
 */
public interface ReportCondition {

    /**
     * 获取条件编码。
     *
     * @return 条件编码
     */
    String key();

    /**
     * 判断模块槽位是否需要参与当前导出。
     *
     * @param dataContext 报告数据上下文
     * @return 需要参与时返回 {@code true}
     */
    boolean matches(ReportDataContext dataContext);
}
