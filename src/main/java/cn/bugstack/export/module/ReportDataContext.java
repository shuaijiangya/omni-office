package cn.bugstack.export.module;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 模块间共享的类型化数据和全局变量上下文。
 *
 * <p>核心层不执行动态表达式。条件、标题替换和业务模块都通过命名变量或类型化数据键
 * 显式读取数据，避免模板配置拥有任意代码执行能力。</p>
 */
public class ReportDataContext {

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Object> variables = new LinkedHashMap<>();

    /**
     * 存入模块数据。
     *
     * @param key 数据键
     * @param value 数据值
     * @param <T> 数据类型
     */
    public <T> void put(ReportDataKey<T> key, T value) {
        validateValue(key, value);
        values.put(key.getName(), value);
    }

    /**
     * 获取必填模块数据。
     *
     * @param key 数据键
     * @param <T> 数据类型
     * @return 数据值
     */
    public <T> T require(ReportDataKey<T> key) {
        return find(key).orElseThrow(() -> new IllegalArgumentException("missing report data: " + key.getName()));
    }

    /**
     * 查询模块数据。
     *
     * @param key 数据键
     * @param <T> 数据类型
     * @return 可选数据值
     */
    public <T> Optional<T> find(ReportDataKey<T> key) {
        if (key == null) {
            throw new IllegalArgumentException("report data key must not be null");
        }
        Object value = values.get(key.getName());
        if (value == null) {
            return Optional.empty();
        }
        if (!key.getType().isInstance(value)) {
            throw new IllegalStateException("report data type mismatch for key: " + key.getName());
        }
        return Optional.of(key.getType().cast(value));
    }

    /**
     * 判断模块数据是否存在。
     *
     * @param key 数据键
     * @return 存在时返回 {@code true}
     */
    public boolean contains(ReportDataKey<?> key) {
        return key != null && values.containsKey(key.getName());
    }

    /**
     * 设置报告级变量。
     *
     * @param name 变量名
     * @param value 变量值
     */
    public void putVariable(String name, Object value) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("report variable name must not be blank");
        }
        variables.put(name.trim(), value);
    }

    /**
     * 获取报告级变量。
     *
     * @param name 变量名
     * @return 变量值；不存在时为 {@code null}
     */
    public Object getVariable(String name) {
        return variables.get(name);
    }

    /**
     * 校验数据值是否与数据键声明的类型兼容。
     *
     * @param key 数据键
     * @param value 待写入数据值
     * @param <T> 数据类型
     */
    private <T> void validateValue(ReportDataKey<T> key, T value) {
        if (key == null) {
            throw new IllegalArgumentException("report data key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("report data value must not be null: " + key.getName());
        }
        if (!key.getType().isInstance(value)) {
            throw new IllegalArgumentException("report data value type mismatch: " + key.getName());
        }
    }
}
