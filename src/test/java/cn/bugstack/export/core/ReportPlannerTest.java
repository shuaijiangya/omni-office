package cn.bugstack.export.core;

import cn.bugstack.export.definition.ModuleSlot;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportConditionRegistry;
import cn.bugstack.export.module.ReportDataContext;
import cn.bugstack.export.module.ReportDataKey;
import cn.bugstack.export.module.ReportModule;
import cn.bugstack.export.module.ReportModuleContext;
import cn.bugstack.export.module.ReportModuleRegistry;
import cn.bugstack.export.document.ReportSection;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportPlannerTest {

    @Test
    void plansModulesInDependencyOrder() {
        ReportDataKey<String> alphaKey = ReportDataKey.of("alpha-data", String.class);
        ReportDataKey<String> betaKey = ReportDataKey.of("beta-data", String.class);
        ReportModuleRegistry registry = new ReportModuleRegistry(Arrays.asList(
                module("alpha", alphaKey), module("beta", betaKey)));
        ReportBlueprint blueprint = ReportBlueprint.builder("demo", "Demo", "1")
                .module(ModuleSlot.builder("beta").dependsOn("alpha").build())
                .module(ModuleSlot.builder("alpha").build())
                .build();
        ReportDataContext data = new ReportDataContext();
        data.put(alphaKey, "A");
        data.put(betaKey, "B");

        ReportPlan plan = new ReportPlanner(registry, new ReportConditionRegistry()).plan(blueprint, data);

        assertEquals(Arrays.asList("alpha", "beta"), plan.getModuleSlots().stream()
                .map(ModuleSlot::getModuleCode).collect(Collectors.toList()));
    }

    @Test
    void rejectsDuplicateSlotEvenWhenFirstSlotIsConditionallyDisabled() {
        ReportDataKey<String> dataKey = ReportDataKey.of("data", String.class);
        ReportModuleRegistry registry = new ReportModuleRegistry(Arrays.asList(module("overview", dataKey)));
        ReportConditionRegistry conditions = new ReportConditionRegistry().register(new cn.bugstack.export.module.ReportCondition() {
            @Override
            public String key() {
                return "never";
            }

            @Override
            public boolean matches(ReportDataContext context) {
                return false;
            }
        });
        ReportBlueprint blueprint = ReportBlueprint.builder("demo", "Demo", "1")
                .module(ModuleSlot.builder("overview").condition("never").build())
                .module(ModuleSlot.builder("overview").build())
                .build();
        ReportDataContext data = new ReportDataContext();
        data.put(dataKey, "value");

        assertThrows(IllegalArgumentException.class,
                () -> new ReportPlanner(registry, conditions).plan(blueprint, data));
    }

    @Test
    void rejectsCyclicDependenciesBeforeComposition() {
        ReportDataKey<String> firstKey = ReportDataKey.of("first-data", String.class);
        ReportDataKey<String> secondKey = ReportDataKey.of("second-data", String.class);
        ReportModuleRegistry registry = new ReportModuleRegistry(Arrays.asList(
                module("first", firstKey), module("second", secondKey)));
        ReportBlueprint blueprint = ReportBlueprint.builder("demo", "Demo", "1")
                .module(ModuleSlot.builder("first").dependsOn("second").build())
                .module(ModuleSlot.builder("second").dependsOn("first").build())
                .build();
        ReportDataContext data = new ReportDataContext();
        data.put(firstKey, "first");
        data.put(secondKey, "second");

        assertThrows(IllegalArgumentException.class,
                () -> new ReportPlanner(registry, new ReportConditionRegistry()).plan(blueprint, data));
    }

    @Test
    void preservesFirstModuleWhenDuplicateRegistrationIsRejected() {
        ReportDataKey<String> dataKey = ReportDataKey.of("data", String.class);
        ReportModule<String> first = module("overview", dataKey);
        ReportModule<String> duplicate = module("overview", dataKey);
        ReportModuleRegistry registry = new ReportModuleRegistry().register(first);

        assertThrows(IllegalStateException.class, () -> registry.register(duplicate));
        assertEquals(first, registry.require("overview"));
    }

    /**
     * 创建用于计划器测试的最小报告模块。
     *
     * @param code 模块编码
     * @param dataKey 模块数据键
     * @return 测试报告模块
     */
    private static ReportModule<String> module(String code, ReportDataKey<String> dataKey) {
        return new ReportModule<String>() {
            private final ModuleDescriptor<String> descriptor = ModuleDescriptor.of(code, code, dataKey);

            @Override
            public ModuleDescriptor<String> descriptor() {
                return descriptor;
            }

            @Override
            public ReportSection compose(ReportModuleContext context, String data) {
                return new ReportSection(code);
            }
        };
    }
}
