package cn.bugstack.export.document;

import java.util.ArrayList;
import java.util.List;

/**
 * 报告中的章节节点。
 *
 * <p>章节允许嵌套，渲染器通过所在树的深度生成对应的 Word 标题级别。</p>
 */
public class ReportSection implements ReportElement {

    /** 章节动态标题。 */
    private String title;

    /** 章节内的段落、表格、图片或子章节。 */
    private List<ReportElement> elements = new ArrayList<>();

    /**
     * 创建空章节，供序列化框架使用。
     */
    public ReportSection() {
    }

    /**
     * 创建指定标题的章节。
     *
     * @param title 动态章节标题
     */
    public ReportSection(String title) {
        this.title = title;
    }

    /**
     * 获取当前元素的类型。
     *
     * @return 章节元素类型
     */
    @Override
    public ReportElementType getElementType() {
        return ReportElementType.SECTION;
    }

    /**
     * 向当前章节追加元素。
     *
     * @param element 报告元素
     */
    public void addElement(ReportElement element) {
        if (element != null) {
            elements.add(element);
        }
    }

    /**
     * 获取章节动态标题。
     *
     * @return 章节动态标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置章节动态标题。
     *
     * @param title 章节动态标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取章节内元素。
     *
     * @return 章节内元素
     */
    public List<ReportElement> getElements() {
        return elements;
    }

    /**
     * 设置章节内元素。
     *
     * @param elements 章节内元素
     */
    public void setElements(List<ReportElement> elements) {
        this.elements = elements == null ? new ArrayList<>() : elements;
    }
}
