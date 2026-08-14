package cn.bugstack.export.definition;

/**
 * 使用文档名称、项目名称和版本号描述的标准封面。
 *
 * <p>实现该接口的封面会沿用框架标准封面版式；只实现
 * {@link ReportCoverTemplate} 的封面仍按动态语义元素渲染。</p>
 */
public interface StandardReportCoverTemplate extends ReportCoverTemplate {

    /** 获取项目名称或报告对象。 */
    String getProjectName();

    /** 获取文档版本。 */
    String getVersion();
}
