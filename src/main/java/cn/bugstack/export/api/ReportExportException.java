package cn.bugstack.export.api;

/**
 * 报告导出过程中的上下文化异常。
 *
 * <p>异常携带报告编码、模块编码和执行阶段，调用方可以据此定位失败的业务模块，
 * 而无需从底层 Word 或文件异常中反推导出位置。</p>
 */
public class ReportExportException extends RuntimeException {

    /** 导出失败所在阶段。 */
    private final ReportExportStage stage;
    /** 失败报告的编码。 */
    private final String reportCode;
    /** 失败模块的编码。 */
    private final String moduleCode;

    /**
     * 创建报告导出异常。
     *
     * @param stage 失败阶段
     * @param reportCode 报告编码
     * @param moduleCode 模块编码；报告级失败时可以为 {@code null}
     * @param message 错误说明
     * @param cause 原始异常
     */
    public ReportExportException(ReportExportStage stage, String reportCode, String moduleCode,
                                 String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.reportCode = reportCode;
        this.moduleCode = moduleCode;
    }

    /**
     * 获取失败阶段。
     *
     * @return 失败阶段
     */
    public ReportExportStage getStage() {
        return stage;
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
     * 获取失败模块编码。
     *
     * @return 模块编码；报告级失败时为 {@code null}
     */
    public String getModuleCode() {
        return moduleCode;
    }
}
