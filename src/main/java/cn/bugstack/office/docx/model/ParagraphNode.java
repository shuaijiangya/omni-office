package cn.bugstack.office.docx.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 段落节点，承载文本、图片、Visio 等行内节点。
 */
public class ParagraphNode implements DocxBlock {

    /** 段落样式名称。 */
    private String styleName;
    /** 段落列表类型。 */
    private ParagraphListType listType = ParagraphListType.NONE;
    private final List<DocxInline> inlines = new ArrayList<>();

    /**
     * 创建空的段落节点。
     */
    public ParagraphNode() {
    }

    /**
     * 追加行内节点。
     *
     * @param inline 行内节点
     */
    public void addInline(DocxInline inline) {
        inlines.add(inline);
    }

    /**
     * 获取段落内的行内节点。
     *
     * @return 不可修改的行内节点列表
     */
    public List<DocxInline> getInlines() {
        return Collections.unmodifiableList(inlines);
    }

    /**
     * 获取段落样式名称。
     *
     * @return 样式名称，未设置时为 {@code null}
     */
    public String getStyleName() {
        return styleName;
    }

    /**
     * 设置段落样式名称。
     *
     * @param styleName 样式名称
     */
    public void setStyleName(String styleName) {
        this.styleName = styleName;
    }

    /**
     * 获取段落列表类型。
     *
     * @return 段落列表类型
     */
    public ParagraphListType getListType() {
        return listType;
    }

    /**
     * 设置段落列表类型。
     *
     * @param listType 段落列表类型
     */
    public void setListType(ParagraphListType listType) {
        this.listType = listType;
    }
}
