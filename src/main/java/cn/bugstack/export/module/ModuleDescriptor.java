package cn.bugstack.export.module;

/**
 * 报告模块的静态元数据。
 *
 * @param <T> 模块输入数据类型
 */
public final class ModuleDescriptor<T> {

    /** 模块唯一编码。 */
    private final String code;
    /** 模块默认标题。 */
    private final String defaultTitle;
    /** 模块所需输入数据的键。 */
    private final ReportDataKey<T> dataKey;

    private ModuleDescriptor(String code, String defaultTitle, ReportDataKey<T> dataKey) {
        this.code = code;
        this.defaultTitle = defaultTitle;
        this.dataKey = dataKey;
    }

    /**
     * 创建模块描述符。
     *
     * @param code 模块编码
     * @param defaultTitle 默认标题
     * @param dataKey 模块数据键
     * @param <T> 模块输入数据类型
     * @return 模块描述符
     */
    public static <T> ModuleDescriptor<T> of(String code, String defaultTitle, ReportDataKey<T> dataKey) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("report module code must not be blank");
        }
        if (defaultTitle == null || defaultTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("report module default title must not be blank");
        }
        if (dataKey == null) {
            throw new IllegalArgumentException("report module data key must not be null");
        }
        return new ModuleDescriptor<>(code.trim(), defaultTitle.trim(), dataKey);
    }

    /**
     * 获取模块编码。
     *
     * @return 模块编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取模块默认章节标题。
     *
     * @return 默认章节标题
     */
    public String getDefaultTitle() {
        return defaultTitle;
    }

    /**
     * 获取模块输入数据键。
     *
     * @return 类型化输入数据键
     */
    public ReportDataKey<T> getDataKey() {
        return dataKey;
    }
}
