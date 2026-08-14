package cn.bugstack.export.core;

import cn.bugstack.export.definition.ModuleSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 已完成预检和依赖排序的报告执行计划。
 */
public final class ReportPlan {

    /** 已按依赖顺序排列的模块槽位。 */
    private final List<ModuleSlot> moduleSlots;
    /** 计划生成期间产生的非阻断告警。 */
    private final List<String> warnings;

    /**
     * 创建已完成校验的执行计划。
     *
     * @param moduleSlots 已按依赖顺序排列的模块槽位
     * @param warnings 计划阶段产生的非阻断告警
     */
    public ReportPlan(List<ModuleSlot> moduleSlots, List<String> warnings) {
        this.moduleSlots = Collections.unmodifiableList(new ArrayList<>(moduleSlots));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    /**
     * 获取按执行顺序排列的模块槽位。
     *
     * @return 不可修改的模块槽位列表
     */
    public List<ModuleSlot> getModuleSlots() {
        return moduleSlots;
    }

    /**
     * 获取计划阶段产生的告警。
     *
     * @return 不可修改的告警列表
     */
    public List<String> getWarnings() {
        return warnings;
    }
}
