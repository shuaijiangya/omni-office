package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.definition.ReportCoverTemplate;
import cn.bugstack.export.document.ReportElement;
import cn.bugstack.export.document.ReportParagraph;

import java.util.Arrays;
import java.util.List;

/**
 * 可组合评估报告的封面模型。
 *
 * <p>封面作为独立 Word Section 写在目录 Section 之前。</p>
 */
public final class ComposableReportCoverModel implements ReportCoverTemplate {

    /** 默认封面文档名称。 */
    public static final String DEFAULT_DOCUMENT_NAME = "评估分析报告";
    /** 默认封面项目名称。 */
    public static final String DEFAULT_PROJECT_NAME = "评估项目";
    /** 默认封面版本。 */
    public static final String DEFAULT_VERSION = "V1.0";

    /** 封面文档名称，同时作为报告主标题。 */
    private final String documentName;
    /** 封面项目名称或评估对象。 */
    private final String projectName;
    /** 封面文档版本。 */
    private final String version;

    public ComposableReportCoverModel(String documentName, String projectName, String version) {
        this.documentName = requiredText(documentName, "cover document name");
        this.projectName = requiredText(projectName, "cover project name");
        this.version = requiredText(version, "cover version");
    }

    /**
     * 创建标准默认封面。
     *
     * @return 默认封面模型
     */
    public static ComposableReportCoverModel defaultCover() {
        return new ComposableReportCoverModel(
                DEFAULT_DOCUMENT_NAME, DEFAULT_PROJECT_NAME, DEFAULT_VERSION);
    }

    public String getDocumentName() {
        return documentName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getVersion() {
        return version;
    }

    /**
     * 创建标准封面的语义元素。
     *
     * <p>可组合报告会优先使用原有标准封面渲染，以保持版式兼容；其他调用方直接把该
     * 对象作为动态封面模板使用时，也可以通过这些元素正常生成封面。</p>
     *
     * @return 标准封面元素
     */
    @Override
    public List<ReportElement> createElements() {
        ReportParagraph title = new ReportParagraph(documentName);
        title.setStyleName("Title");
        return Arrays.asList(title,
                new ReportParagraph("项目名称：" + projectName),
                new ReportParagraph("文档版本：" + version));
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
