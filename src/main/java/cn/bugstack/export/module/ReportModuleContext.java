package cn.bugstack.export.module;

import cn.bugstack.export.definition.ModuleSlot;
import cn.bugstack.export.definition.ReportBlueprint;

/**
 * 单个报告模块的执行上下文。
 */
public final class ReportModuleContext {

    /** 本次导出的报告蓝图。 */
    private final ReportBlueprint blueprint;
    /** 当前执行的模块槽位。 */
    private final ModuleSlot slot;
    /** 本次导出的数据上下文。 */
    private final ReportDataContext dataContext;

    /**
     * 创建模块执行上下文。
     *
     * @param blueprint 本次报告蓝图
     * @param slot 当前执行模块槽位
     * @param dataContext 本次导出数据上下文
     */
    public ReportModuleContext(ReportBlueprint blueprint, ModuleSlot slot, ReportDataContext dataContext) {
        this.blueprint = blueprint;
        this.slot = slot;
        this.dataContext = dataContext;
    }

    /**
     * 获取本次导出使用的报告蓝图。
     *
     * @return 报告蓝图
     */
    public ReportBlueprint getBlueprint() {
        return blueprint;
    }

    /**
     * 获取当前模块槽位配置。
     *
     * @return 当前模块槽位
     */
    public ModuleSlot getSlot() {
        return slot;
    }

    /**
     * 获取本次导出共享的数据上下文。
     *
     * @return 数据上下文
     */
    public ReportDataContext getDataContext() {
        return dataContext;
    }

    /**
     * 获取报告级变量。
     *
     * @param name 变量名称
     * @return 变量值；未设置时为 {@code null}
     */
    public Object getVariable(String name) {
        return dataContext.getVariable(name);
    }
}
