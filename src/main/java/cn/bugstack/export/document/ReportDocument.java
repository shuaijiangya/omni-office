package cn.bugstack.export.document;

import cn.bugstack.export.context.ReportBasicInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 框架无关的报告文档根节点。
 */
public class ReportDocument {

    /** 报告主标题。 */
    private String title;

    /** 报告通用信息。 */
    private ReportBasicInfo basicInfo;

    /** 报告的一级章节。 */
    private List<ReportSection> sections = new ArrayList<>();

    /**
     * 创建报告文档构建器。
     *
     * @return 报告文档构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取报告主标题。
     *
     * @return 报告主标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置报告主标题。
     *
     * @param title 报告主标题
     */
    public void setTitle(String title) {
        this.title = title;
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
     * 获取一级章节。
     *
     * @return 一级章节
     */
    public List<ReportSection> getSections() {
        return sections;
    }

    /**
     * 设置一级章节。
     *
     * @param sections 一级章节
     */
    public void setSections(List<ReportSection> sections) {
        this.sections = sections == null ? new ArrayList<>() : sections;
    }

    /**
     * 报告文档构建器。
     */
    public static class Builder {

        /** 当前正在构建的报告文档。 */
        private final ReportDocument document = new ReportDocument();

        /**
         * 设置报告主标题。
         *
         * @param title 报告主标题
         * @return 当前构建器
         */
        public Builder title(String title) {
            document.setTitle(title);
            return this;
        }

        /**
         * 设置报告通用信息。
         *
         * @param basicInfo 报告通用信息
         * @return 当前构建器
         */
        public Builder basicInfo(ReportBasicInfo basicInfo) {
            document.setBasicInfo(basicInfo);
            return this;
        }

        /**
         * 追加一级章节。
         *
         * @param section 一级章节
         * @return 当前构建器
         */
        public Builder addSection(ReportSection section) {
            if (section != null) {
                document.getSections().add(section);
            }
            return this;
        }

        /**
         * 完成报告文档构建。
         *
         * @return 报告文档
         */
        public ReportDocument build() {
            return document;
        }
    }
}
