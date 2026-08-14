package cn.bugstack.export.module;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报告模块策略注册表。
 */
public final class ReportModuleRegistry {

    private final Map<String, ReportModule<?>> modules = new LinkedHashMap<>();

    /**
     * 创建空模块注册表。
     */
    public ReportModuleRegistry() {
    }

    /**
     * 使用已有模块集合创建注册表。
     *
     * @param modules 模块集合
     */
    public ReportModuleRegistry(Iterable<? extends ReportModule<?>> modules) {
        if (modules != null) {
            for (ReportModule<?> module : modules) {
                register(module);
            }
        }
    }

    /**
     * 注册模块。
     *
     * @param module 报告模块
     * @return 当前注册表
     */
    public ReportModuleRegistry register(ReportModule<?> module) {
        if (module == null || module.descriptor() == null) {
            throw new IllegalArgumentException("report module and descriptor must not be null");
        }
        String code = module.descriptor().getCode();
        if (modules.containsKey(code)) {
            throw new IllegalStateException("duplicate report module: " + code);
        }
        modules.put(code, module);
        return this;
    }

    /**
     * 获取指定编码模块。
     *
     * @param code 模块编码
     * @return 模块策略
     */
    public ReportModule<?> require(String code) {
        ReportModule<?> module = modules.get(code);
        if (module == null) {
            throw new IllegalArgumentException("report module is not registered: " + code);
        }
        return module;
    }

    /**
     * 判断模块是否已注册。
     *
     * @param code 模块编码
     * @return 已注册时返回 {@code true}
     */
    public boolean contains(String code) {
        return modules.containsKey(code);
    }
}
