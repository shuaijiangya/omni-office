package cn.bugstack.export.module;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 命名条件注册表。
 */
public final class ReportConditionRegistry {

    private final Map<String, ReportCondition> conditions = new LinkedHashMap<>();

    /**
     * 创建空条件注册表。
     */
    public ReportConditionRegistry() {
    }

    /**
     * 注册条件。
     *
     * @param condition 条件实例
     * @return 当前注册表
     */
    public ReportConditionRegistry register(ReportCondition condition) {
        if (condition == null || condition.key() == null || condition.key().trim().isEmpty()) {
            throw new IllegalArgumentException("report condition and key must not be null");
        }
        String key = condition.key().trim();
        if (conditions.containsKey(key)) {
            throw new IllegalStateException("duplicate report condition: " + condition.key());
        }
        conditions.put(key, condition);
        return this;
    }

    /**
     * 按编码获取条件。
     *
     * @param key 条件编码
     * @return 条件；未注册时返回 {@code null}
     */
    public ReportCondition find(String key) {
        return key == null ? null : conditions.get(key);
    }
}
