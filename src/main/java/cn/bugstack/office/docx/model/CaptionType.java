package cn.bugstack.office.docx.model;

/**
 * 题注类型。
 */
public enum CaptionType {

    /**
     * 图题。
     */
    FIGURE("图"),

    /**
     * 表题。
     */
    TABLE("表");

    /** 显示在题注编号前的标签。 */
    private final String label;

    CaptionType(String label) {
        this.label = label;
    }

    /**
     * 获取题注中文标签。
     *
     * @return 题注标签
     */
    public String getLabel() {
        return label;
    }
}
