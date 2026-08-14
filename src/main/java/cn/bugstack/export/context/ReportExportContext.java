package cn.bugstack.export.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次报告导出的完整上下文。
 *
 * <p>模板仅决定模块选择和排序；各模块的动态标题、内容、表格、题注和图片均从
 * {@link #contentData} 中按模块编码取得。</p>
 */
public class ReportExportContext {

    /** 最终报告主标题，可覆盖模板名称。 */
    private String reportTitle;

    /** 已按模板顺序排序的模块编码。 */
    private List<String> contentOptions = new ArrayList<>();

    /** 报告的通用信息。 */
    private ReportBasicInfo basicInfo;

    /** 以模块编码为键保存的运行时模块数据。 */
    private Map<String, Object> contentData = new LinkedHashMap<>();

    /** 供标题、正文等占位替换使用的全局变量。 */
    private Map<String, Object> variables = new LinkedHashMap<>();

    /**
     * 按模块编码放入运行时模块数据。
     *
     * @param optionCode 模板内容选项编码
     * @param data 模块运行时数据
     */
    public void putContentData(String optionCode, Object data) {
        if (!hasText(optionCode)) {
            throw new IllegalArgumentException("模块编码不能为空");
        }
        contentData.put(optionCode.trim(), data);
    }

    /**
     * 获取指定模块的运行时数据。
     *
     * @param optionCode 模板内容选项编码
     * @param dataType 预期的数据类型
     * @param <T> 数据类型
     * @return 指定模块的运行时数据
     */
    public <T> T getContentData(String optionCode, Class<T> dataType) {
        Object data = contentData.get(optionCode);
        if (data == null) {
            throw new IllegalArgumentException("未找到模块数据：" + optionCode);
        }
        if (!dataType.isInstance(data)) {
            throw new IllegalArgumentException("模块数据类型不匹配：" + optionCode);
        }
        return dataType.cast(data);
    }

    /**
     * 按变量名称放入全局变量。
     *
     * @param name 变量名称
     * @param value 变量值
     */
    public void putVariable(String name, Object value) {
        if (!hasText(name)) {
            throw new IllegalArgumentException("变量名称不能为空");
        }
        variables.put(name.trim(), value);
    }

    /**
     * 判断字符串是否包含有效文本。
     *
     * @param value 待判断字符串
     * @return {@code true} 表示包含有效文本
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 获取最终报告主标题。
     *
     * @return 最终报告主标题
     */
    public String getReportTitle() {
        return reportTitle;
    }

    /**
     * 设置最终报告主标题。
     *
     * @param reportTitle 最终报告主标题
     */
    public void setReportTitle(String reportTitle) {
        this.reportTitle = reportTitle;
    }

    /**
     * 获取已排序的模块编码。
     *
     * @return 已排序的模块编码
     */
    public List<String> getContentOptions() {
        return contentOptions;
    }

    /**
     * 设置已排序的模块编码。
     *
     * @param contentOptions 已排序的模块编码
     */
    public void setContentOptions(List<String> contentOptions) {
        this.contentOptions = contentOptions == null ? new ArrayList<>() : contentOptions;
    }

    /**
     * 获取报告通用信息。
     *
     * @return 报告通用信息
     */
    public ReportBasicInfo getBasicInfo() {
        return basicInfo;
    }

    /**
     * 设置报告通用信息。
     *
     * @param basicInfo 报告通用信息
     */
    public void setBasicInfo(ReportBasicInfo basicInfo) {
        this.basicInfo = basicInfo;
    }

    /**
     * 获取以模块编码为键保存的运行时模块数据。
     *
     * @return 运行时模块数据
     */
    public Map<String, Object> getContentData() {
        return contentData;
    }

    /**
     * 设置以模块编码为键保存的运行时模块数据。
     *
     * @param contentData 运行时模块数据
     */
    public void setContentData(Map<String, Object> contentData) {
        this.contentData = contentData == null ? new LinkedHashMap<>() : contentData;
    }

    /**
     * 获取全局变量。
     *
     * @return 全局变量
     */
    public Map<String, Object> getVariables() {
        return variables;
    }

    /**
     * 设置全局变量。
     *
     * @param variables 全局变量
     */
    public void setVariables(Map<String, Object> variables) {
        this.variables = variables == null ? new LinkedHashMap<>() : variables;
    }
}
