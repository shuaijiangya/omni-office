package cn.bugstack.export.api;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次报告导出的结果和审计摘要。
 */
public final class ReportResult {

    /** 已导出报告的编码。 */
    private final String reportCode;
    /** 使用的报告蓝图版本。 */
    private final String blueprintVersion;
    /** 实际生成的输出格式。 */
    private final ReportOutputFormat outputFormat;
    /** 最终输出文件路径。 */
    private final Path outputPath;
    /** 导出过程产生的非阻断告警。 */
    private final List<String> warnings;
    /** 各导出阶段耗时，单位为毫秒。 */
    private final Map<String, Long> stageDurationsMillis;

    /**
     * 创建报告导出结果。
     *
     * @param reportCode 报告编码
     * @param blueprintVersion 蓝图版本
     * @param outputFormat 输出格式
     * @param outputPath 输出文件路径
     * @param warnings 非阻断告警
     * @param stageDurationsMillis 各阶段耗时，单位毫秒
     */
    public ReportResult(String reportCode, String blueprintVersion, ReportOutputFormat outputFormat,
                        Path outputPath, List<String> warnings, Map<String, Long> stageDurationsMillis) {
        this.reportCode = reportCode;
        this.blueprintVersion = blueprintVersion;
        this.outputFormat = outputFormat;
        this.outputPath = outputPath;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.stageDurationsMillis = Collections.unmodifiableMap(new LinkedHashMap<>(stageDurationsMillis));
    }

    /**
     * 获取报告编码。
     *
     * @return 报告编码
     */
    public String getReportCode() {
        return reportCode;
    }

    /**
     * 获取本次导出采用的蓝图版本。
     *
     * @return 蓝图版本
     */
    public String getBlueprintVersion() {
        return blueprintVersion;
    }

    /**
     * 获取实际生成的文件格式。
     *
     * @return 输出格式
     */
    public ReportOutputFormat getOutputFormat() {
        return outputFormat;
    }

    /**
     * 获取最终输出文件路径。
     *
     * @return 输出文件路径
     */
    public Path getOutputPath() {
        return outputPath;
    }

    /**
     * 获取导出过程中产生的非阻断告警。
     *
     * @return 不可修改的告警列表
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * 获取各导出阶段耗时。
     *
     * @return 阶段名称到毫秒耗时的不可修改映射
     */
    public Map<String, Long> getStageDurationsMillis() {
        return stageDurationsMillis;
    }
}
