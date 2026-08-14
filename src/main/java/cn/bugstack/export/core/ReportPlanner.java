package cn.bugstack.export.core;

import cn.bugstack.export.definition.ModuleSlot;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.module.ReportCondition;
import cn.bugstack.export.module.ReportConditionRegistry;
import cn.bugstack.export.module.ReportDataContext;
import cn.bugstack.export.module.ReportModule;
import cn.bugstack.export.module.ReportModuleRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报告执行计划器。
 *
 * <p>计划器在内容写入前完成模块存在性、条件、必填数据和依赖图校验，避免导出到一半
 * 才因为缺少模块或循环依赖留下不完整文件。</p>
 */
public final class ReportPlanner {

    /** 可解析报告模块的注册表。 */
    private final ReportModuleRegistry moduleRegistry;
    /** 可解析模块参与条件的注册表。 */
    private final ReportConditionRegistry conditionRegistry;

    /**
     * 创建报告执行计划器。
     *
     * @param moduleRegistry 报告模块注册表
     * @param conditionRegistry 命名条件注册表；为 {@code null} 时使用空注册表
     */
    public ReportPlanner(ReportModuleRegistry moduleRegistry, ReportConditionRegistry conditionRegistry) {
        if (moduleRegistry == null) {
            throw new IllegalArgumentException("report module registry must not be null");
        }
        this.moduleRegistry = moduleRegistry;
        this.conditionRegistry = conditionRegistry == null ? new ReportConditionRegistry() : conditionRegistry;
    }

    /**
     * 生成本次导出的稳定模块计划。
     *
     * @param blueprint 报告蓝图
     * @param dataContext 报告数据上下文
     * @return 已排序的执行计划
     */
    public ReportPlan plan(ReportBlueprint blueprint, ReportDataContext dataContext) {
        if (blueprint == null) {
            throw new IllegalArgumentException("report blueprint must not be null");
        }
        if (dataContext == null) {
            throw new IllegalArgumentException("report data context must not be null");
        }

        Map<String, ModuleSlot> enabled = new LinkedHashMap<>();
        Set<String> declaredCodes = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        for (ModuleSlot slot : blueprint.getModuleSlots()) {
            if (!declaredCodes.add(slot.getModuleCode())) {
                throw new IllegalArgumentException("duplicate report module slot: " + slot.getModuleCode());
            }
            if (!isEnabled(slot, dataContext)) {
                warnings.add("module skipped by condition: " + slot.getModuleCode());
                continue;
            }
            ReportModule<?> module = moduleRegistry.require(slot.getModuleCode());
            if (!dataContext.contains(module.descriptor().getDataKey())) {
                if (slot.isRequired()) {
                    throw new IllegalArgumentException("missing required module data: " + slot.getModuleCode()
                            + ", key=" + module.descriptor().getDataKey().getName());
                }
                warnings.add("optional module skipped because data is missing: " + slot.getModuleCode());
                continue;
            }
            enabled.put(slot.getModuleCode(), slot);
        }
        return new ReportPlan(sortByDependencies(enabled), warnings);
    }

    /**
     * 判断计划器是否使用指定模块注册表。
     *
     * <p>该方法用于保证计划阶段和组装阶段解析的是同一组模块，避免同一模块编码
     * 在两个注册表中映射为不同数据键或实现时产生不可预期的导出结果。</p>
     *
     * @param registry 待校验的模块注册表
     * @return 使用同一实例时返回 {@code true}
     */
    boolean usesRegistry(ReportModuleRegistry registry) {
        return moduleRegistry == registry;
    }

    /**
     * 根据槽位条件判断模块是否参与本次导出。
     *
     * @param slot 待判断模块槽位
     * @param dataContext 报告数据上下文
     * @return 模块应参与导出时返回 {@code true}
     */
    private boolean isEnabled(ModuleSlot slot, ReportDataContext dataContext) {
        String conditionKey = slot.getConditionKey();
        if (conditionKey == null || conditionKey.trim().isEmpty()) {
            return true;
        }
        ReportCondition condition = conditionRegistry.find(conditionKey);
        if (condition == null) {
            throw new IllegalArgumentException("report condition is not registered: " + conditionKey);
        }
        return condition.matches(dataContext);
    }

    /**
     * 按模块依赖关系对已启用槽位进行拓扑排序。
     *
     * @param enabled 已启用模块槽位
     * @return 有序模块槽位列表
     */
    private List<ModuleSlot> sortByDependencies(Map<String, ModuleSlot> enabled) {
        List<ModuleSlot> sorted = new ArrayList<>();
        Map<String, VisitState> states = new HashMap<>();
        for (String code : enabled.keySet()) {
            visit(code, enabled, states, sorted, new LinkedHashSet<>());
        }
        return sorted;
    }

    /**
     * 深度优先访问模块依赖，并检测循环依赖与缺失依赖。
     *
     * @param code 当前模块编码
     * @param enabled 已启用模块槽位
     * @param states 模块访问状态
     * @param ordered 拓扑排序结果
     */
    private void visit(String code, Map<String, ModuleSlot> enabled, Map<String, VisitState> states,
                       List<ModuleSlot> sorted, Set<String> path) {
        VisitState state = states.get(code);
        if (state == VisitState.VISITED) {
            return;
        }
        if (state == VisitState.VISITING) {
            path.add(code);
            throw new IllegalArgumentException("cyclic report module dependency: " + String.join(" -> ", path));
        }

        ModuleSlot slot = enabled.get(code);
        if (slot == null) {
            throw new IllegalArgumentException("report module dependency is not enabled: " + code);
        }
        states.put(code, VisitState.VISITING);
        path.add(code);
        for (String dependencyCode : slot.getDependsOn()) {
            if (!enabled.containsKey(dependencyCode)) {
                throw new IllegalArgumentException("module dependency is not enabled: " + code
                        + " depends on " + dependencyCode);
            }
            visit(dependencyCode, enabled, states, sorted, path);
        }
        path.remove(code);
        states.put(code, VisitState.VISITED);
        sorted.add(slot);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
