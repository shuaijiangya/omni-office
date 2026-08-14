package cn.bugstack.export.api;

/**
 * 报告导出执行阶段。
 */
public enum ReportExportStage {

    /** 报告蓝图和模块计划校验阶段。 */
    PLAN,

    /** 模块内容组装阶段。 */
    COMPOSE,

    /** 报告语义文档校验阶段。 */
    VALIDATE,

    /** docx 编译和文件输出阶段。 */
    RENDER
}
