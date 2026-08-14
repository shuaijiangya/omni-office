package cn.bugstack.export.context;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行时产生的动态章节数据。
 *
 * <p>标题取自业务数据，可递归嵌套，因此不受模板中固定小标题的限制。</p>
 */
public class ReportSectionData {

    /** 当前章节的动态标题。 */
    private String title;

    /** 当前章节中的段落数据。 */
    private List<ReportParagraphData> paragraphs = new ArrayList<>();

    /** 当前章节中的表格数据。 */
    private List<ReportTableData> tables = new ArrayList<>();

    /** 当前章节中的图片数据。 */
    private List<ReportImageData> images = new ArrayList<>();

    /** 当前章节的子章节数据。 */
    private List<ReportSectionData> children = new ArrayList<>();

    /**
     * 创建空章节数据，供序列化框架使用。
     */
    public ReportSectionData() {
    }

    /**
     * 创建动态章节数据。
     *
     * @param title 当前章节的动态标题
     */
    public ReportSectionData(String title) {
        this.title = title;
    }

    /**
     * 获取当前章节的动态标题。
     *
     * @return 当前章节的动态标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置当前章节的动态标题。
     *
     * @param title 当前章节的动态标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取当前章节中的段落数据。
     *
     * @return 当前章节中的段落数据
     */
    public List<ReportParagraphData> getParagraphs() {
        return paragraphs;
    }

    /**
     * 设置当前章节中的段落数据。
     *
     * @param paragraphs 当前章节中的段落数据
     */
    public void setParagraphs(List<ReportParagraphData> paragraphs) {
        this.paragraphs = paragraphs == null ? new ArrayList<>() : paragraphs;
    }

    /**
     * 获取当前章节中的表格数据。
     *
     * @return 当前章节中的表格数据
     */
    public List<ReportTableData> getTables() {
        return tables;
    }

    /**
     * 设置当前章节中的表格数据。
     *
     * @param tables 当前章节中的表格数据
     */
    public void setTables(List<ReportTableData> tables) {
        this.tables = tables == null ? new ArrayList<>() : tables;
    }

    /**
     * 获取当前章节中的图片数据。
     *
     * @return 当前章节中的图片数据
     */
    public List<ReportImageData> getImages() {
        return images;
    }

    /**
     * 设置当前章节中的图片数据。
     *
     * @param images 当前章节中的图片数据
     */
    public void setImages(List<ReportImageData> images) {
        this.images = images == null ? new ArrayList<>() : images;
    }

    /**
     * 获取当前章节的子章节数据。
     *
     * @return 当前章节的子章节数据
     */
    public List<ReportSectionData> getChildren() {
        return children;
    }

    /**
     * 设置当前章节的子章节数据。
     *
     * @param children 当前章节的子章节数据
     */
    public void setChildren(List<ReportSectionData> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }
}
