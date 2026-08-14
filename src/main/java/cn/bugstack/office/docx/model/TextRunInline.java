package cn.bugstack.office.docx.model;

/**
 * 文本 run 行内节点。
 */
public class TextRunInline implements DocxInline {

    /** 文本片段内容。 */
    private final String text;

    /**
     * 创建文本行内节点。
     *
     * @param text 文本内容
     */
    public TextRunInline(String text) {
        this.text = text;
    }

    /**
     * 获取文本内容。
     *
     * @return 文本内容
     */
    public String getText() {
        return text;
    }
}
