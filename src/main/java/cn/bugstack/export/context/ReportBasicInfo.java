package cn.bugstack.export.context;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报告封面、页眉或前置信息中使用的通用数据。
 */
public class ReportBasicInfo {

    /** 报告编号。 */
    private String reportNo;

    /** 被评估对象名称。 */
    private String assessmentTarget;

    /** 报告编制人。 */
    private String preparedBy;

    /** 报告生成时间。 */
    private LocalDateTime generatedTime;

    /** 其他可扩展的基础属性。 */
    private Map<String, String> properties = new LinkedHashMap<>();

    /**
     * 获取报告编号。
     *
     * @return 报告编号
     */
    public String getReportNo() {
        return reportNo;
    }

    /**
     * 设置报告编号。
     *
     * @param reportNo 报告编号
     */
    public void setReportNo(String reportNo) {
        this.reportNo = reportNo;
    }

    /**
     * 获取被评估对象名称。
     *
     * @return 被评估对象名称
     */
    public String getAssessmentTarget() {
        return assessmentTarget;
    }

    /**
     * 设置被评估对象名称。
     *
     * @param assessmentTarget 被评估对象名称
     */
    public void setAssessmentTarget(String assessmentTarget) {
        this.assessmentTarget = assessmentTarget;
    }

    /**
     * 获取报告编制人。
     *
     * @return 报告编制人
     */
    public String getPreparedBy() {
        return preparedBy;
    }

    /**
     * 设置报告编制人。
     *
     * @param preparedBy 报告编制人
     */
    public void setPreparedBy(String preparedBy) {
        this.preparedBy = preparedBy;
    }

    /**
     * 获取报告生成时间。
     *
     * @return 报告生成时间
     */
    public LocalDateTime getGeneratedTime() {
        return generatedTime;
    }

    /**
     * 设置报告生成时间。
     *
     * @param generatedTime 报告生成时间
     */
    public void setGeneratedTime(LocalDateTime generatedTime) {
        this.generatedTime = generatedTime;
    }

    /**
     * 获取其他基础属性。
     *
     * @return 其他基础属性
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * 设置其他基础属性。
     *
     * @param properties 其他基础属性
     */
    public void setProperties(Map<String, String> properties) {
        this.properties = properties == null ? new LinkedHashMap<>() : properties;
    }
}
