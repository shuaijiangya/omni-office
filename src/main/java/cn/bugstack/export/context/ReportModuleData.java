package cn.bugstack.export.context;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 contentOption 对应的运行时模块数据。
 */
public class ReportModuleData {

    /** 模块的动态一级标题，为空时由模块默认标题补充。 */
    private String title;

    /** 模块下的直接段落数据。 */
    private List<ReportParagraphData> paragraphs = new ArrayList<>();

    /** 模块下的直接表格数据。 */
    private List<ReportTableData> tables = new ArrayList<>();

    /** 模块下的直接图片数据。 */
    private List<ReportImageData> images = new ArrayList<>();

    /** 模块下的动态子章节数据。 */
    private List<ReportSectionData> sections = new ArrayList<>();

    /**
     * 获取模块动态一级标题。
     *
     * @return 模块动态一级标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置模块动态一级标题。
     *
     * @param title 模块动态一级标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取模块下的直接段落数据。
     *
     * @return 模块下的直接段落数据
     */
    public List<ReportParagraphData> getParagraphs() {
        return paragraphs;
    }

    /**
     * 设置模块下的直接段落数据。
     *
     * @param paragraphs 模块下的直接段落数据
     */
    public void setParagraphs(List<ReportParagraphData> paragraphs) {
        this.paragraphs = paragraphs == null ? new ArrayList<>() : paragraphs;
    }

    /**
     * 获取模块下的直接表格数据。
     *
     * @return 模块下的直接表格数据
     */
    public List<ReportTableData> getTables() {
        return tables;
    }

    /**
     * 设置模块下的直接表格数据。
     *
     * @param tables 模块下的直接表格数据
     */
    public void setTables(List<ReportTableData> tables) {
        this.tables = tables == null ? new ArrayList<>() : tables;
    }

    /**
     * 获取模块下的直接图片数据。
     *
     * @return 模块下的直接图片数据
     */
    public List<ReportImageData> getImages() {
        return images;
    }

    /**
     * 设置模块下的直接图片数据。
     *
     * @param images 模块下的直接图片数据
     */
    public void setImages(List<ReportImageData> images) {
        this.images = images == null ? new ArrayList<>() : images;
    }

    /**
     * 获取模块下的动态子章节数据。
     *
     * @return 模块下的动态子章节数据
     */
    public List<ReportSectionData> getSections() {
        return sections;
    }

    /**
     * 设置模块下的动态子章节数据。
     *
     * @param sections 模块下的动态子章节数据
     */
    public void setSections(List<ReportSectionData> sections) {
        this.sections = sections == null ? new ArrayList<>() : sections;
    }
}
