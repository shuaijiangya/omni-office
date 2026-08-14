package cn.bugstack.export.module;

/**
 * 报告模块数据的类型化键。
 *
 * @param <T> 数据类型
 */
public final class ReportDataKey<T> {

    /** 数据键名称。 */
    private final String name;
    /** 数据值的运行时类型。 */
    private final Class<T> type;

    private ReportDataKey(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    /**
     * 创建类型化数据键。
     *
     * @param name 键名称
     * @param type 数据类型
     * @param <T> 数据类型
     * @return 数据键
     */
    public static <T> ReportDataKey<T> of(String name, Class<T> type) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("report data key name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("report data key type must not be null");
        }
        return new ReportDataKey<>(name.trim(), type);
    }

    /**
     * 获取数据键名称。
     *
     * @return 数据键名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取数据值的运行时类型。
     *
     * @return 数据值类型
     */
    public Class<T> getType() {
        return type;
    }
}
