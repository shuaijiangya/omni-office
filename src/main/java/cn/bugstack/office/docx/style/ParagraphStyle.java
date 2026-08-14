package cn.bugstack.office.docx.style;

/**
 * 段落样式定义。
 */
public class ParagraphStyle {

    /** 样式唯一名称。 */
    private final String name;
    /** 兼容场景下使用的统一字体名称。 */
    private String fontFamily;
    /** 西文和数字字体名称。 */
    private String asciiFontFamily;
    /** 中文等东亚文字字体名称。 */
    private String farEastFontFamily;
    /** 字号，单位为磅。 */
    private double fontSize;
    /** 是否加粗。 */
    private boolean bold;
    /** 是否倾斜。 */
    private boolean italic;
    /** 是否显示下划线。 */
    private boolean underline;
    /** Word 大纲层级。 */
    private int outlineLevel;
    /** 段落水平对齐方式。 */
    private DocxParagraphAlignment alignment = DocxParagraphAlignment.LEFT;
    /** 左缩进，单位为磅。 */
    private double leftIndentPoints;
    /** 右缩进，单位为磅。 */
    private double rightIndentPoints;
    /** 首行缩进，单位为字符。 */
    private double characterUnitFirstLineIndent;
    /** 行距规则。 */
    private DocxLineSpacingRule lineSpacingRule = DocxLineSpacingRule.MULTIPLE;
    /** 行距值，单位由行距规则决定。 */
    private double lineSpacing = 12.0;
    /** 段前间距，单位为磅。 */
    private double spaceBeforePoints;
    /** 段后间距，单位为磅。 */
    private double spaceAfterPoints;
    /** 是否与下段保持同页。 */
    private boolean keepWithNext;
    /** 是否保持段落内容不跨页。 */
    private boolean keepTogether;

    /**
     * 创建段落样式。
     *
     * @param name 样式名称
     * @param fontFamily 字体族
     * @param fontSize 字号，单位为 point
     */
    public ParagraphStyle(String name, String fontFamily, double fontSize) {
        this.name = name;
        this.fontFamily = fontFamily;
        this.asciiFontFamily = fontFamily;
        this.farEastFontFamily = fontFamily;
        this.fontSize = fontSize;
    }

    /**
     * 创建当前样式的副本。
     *
     * @return 样式副本
     */
    public ParagraphStyle copy() {
        ParagraphStyle copy = new ParagraphStyle(name, fontFamily, fontSize);
        copy.asciiFontFamily = asciiFontFamily;
        copy.farEastFontFamily = farEastFontFamily;
        copy.bold = bold;
        copy.italic = italic;
        copy.underline = underline;
        copy.outlineLevel = outlineLevel;
        copy.alignment = alignment;
        copy.leftIndentPoints = leftIndentPoints;
        copy.rightIndentPoints = rightIndentPoints;
        copy.characterUnitFirstLineIndent = characterUnitFirstLineIndent;
        copy.lineSpacingRule = lineSpacingRule;
        copy.lineSpacing = lineSpacing;
        copy.spaceBeforePoints = spaceBeforePoints;
        copy.spaceAfterPoints = spaceAfterPoints;
        copy.keepWithNext = keepWithNext;
        copy.keepTogether = keepTogether;
        return copy;
    }

    /**
     * 获取样式名称。
     *
     * @return 样式名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取字体族。
     *
     * @return 字体族
     */
    public String getFontFamily() {
        return fontFamily;
    }

    /**
     * 设置字体族。
     *
     * @param fontFamily 字体族
     */
    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    /**
     * 获取西文字体族。
     *
     * @return 西文字体族
     */
    public String getAsciiFontFamily() {
        return asciiFontFamily;
    }

    /**
     * 设置西文字体族。
     *
     * @param asciiFontFamily 西文字体族
     */
    public void setAsciiFontFamily(String asciiFontFamily) {
        this.asciiFontFamily = asciiFontFamily;
    }

    /**
     * 获取中文等东亚文字体族。
     *
     * @return 中文等东亚文字体族
     */
    public String getFarEastFontFamily() {
        return farEastFontFamily;
    }

    /**
     * 设置中文等东亚文字体族。
     *
     * @param farEastFontFamily 中文等东亚文字体族
     */
    public void setFarEastFontFamily(String farEastFontFamily) {
        this.farEastFontFamily = farEastFontFamily;
    }

    /**
     * 获取字号。
     *
     * @return 字号，单位为 point
     */
    public double getFontSize() {
        return fontSize;
    }

    /**
     * 设置字号。
     *
     * @param fontSize 字号，单位为 point
     */
    public void setFontSize(double fontSize) {
        this.fontSize = fontSize;
    }

    /**
     * 判断是否加粗。
     *
     * @return 加粗返回 {@code true}
     */
    public boolean isBold() {
        return bold;
    }

    /**
     * 设置是否加粗。
     *
     * @param bold 是否加粗
     */
    public void setBold(boolean bold) {
        this.bold = bold;
    }

    /**
     * 判断文本是否使用斜体。
     *
     * @return 使用斜体时返回 {@code true}
     */
    public boolean isItalic() {
        return italic;
    }

    /**
     * 设置文本是否使用斜体。
     *
     * @param italic 是否使用斜体
     */
    public void setItalic(boolean italic) {
        this.italic = italic;
    }

    /**
     * 判断文本是否使用单下划线。
     *
     * @return 使用下划线时返回 {@code true}
     */
    public boolean isUnderline() {
        return underline;
    }

    /**
     * 设置文本是否使用单下划线。
     *
     * @param underline 是否使用下划线
     */
    public void setUnderline(boolean underline) {
        this.underline = underline;
    }

    /**
     * 获取标题大纲级别。
     *
     * @return 大纲级别，普通正文为 0
     */
    public int getOutlineLevel() {
        return outlineLevel;
    }

    /**
     * 设置标题大纲级别。
     *
     * @param outlineLevel 大纲级别，普通正文为 0
     */
    public void setOutlineLevel(int outlineLevel) {
        this.outlineLevel = outlineLevel;
    }

    /**
     * 获取段落对齐方式。
     *
     * @return 段落对齐方式
     */
    public DocxParagraphAlignment getAlignment() {
        return alignment;
    }

    /**
     * 设置段落对齐方式。
     *
     * @param alignment 段落对齐方式
     */
    public void setAlignment(DocxParagraphAlignment alignment) {
        this.alignment = alignment;
    }

    /**
     * 获取左缩进。
     *
     * @return 左缩进，单位为 point
     */
    public double getLeftIndentPoints() {
        return leftIndentPoints;
    }

    /**
     * 设置左缩进。
     *
     * @param leftIndentPoints 左缩进，单位为 point
     */
    public void setLeftIndentPoints(double leftIndentPoints) {
        this.leftIndentPoints = leftIndentPoints;
    }

    /**
     * 获取右缩进。
     *
     * @return 右缩进，单位为 point
     */
    public double getRightIndentPoints() {
        return rightIndentPoints;
    }

    /**
     * 设置右缩进。
     *
     * @param rightIndentPoints 右缩进，单位为 point
     */
    public void setRightIndentPoints(double rightIndentPoints) {
        this.rightIndentPoints = rightIndentPoints;
    }

    /**
     * 获取按字符计算的首行缩进。
     *
     * @return 首行缩进字符数
     */
    public double getCharacterUnitFirstLineIndent() {
        return characterUnitFirstLineIndent;
    }

    /**
     * 设置按字符计算的首行缩进。
     *
     * @param characterUnitFirstLineIndent 首行缩进字符数
     */
    public void setCharacterUnitFirstLineIndent(double characterUnitFirstLineIndent) {
        this.characterUnitFirstLineIndent = characterUnitFirstLineIndent;
    }

    /**
     * 获取行距规则。
     *
     * @return 行距规则
     */
    public DocxLineSpacingRule getLineSpacingRule() {
        return lineSpacingRule;
    }

    /**
     * 设置行距规则。
     *
     * @param lineSpacingRule 行距规则
     */
    public void setLineSpacingRule(DocxLineSpacingRule lineSpacingRule) {
        this.lineSpacingRule = lineSpacingRule;
    }

    /**
     * 获取行距值。
     *
     * @return 行距值；多倍行距中 18 表示 1.5 倍
     */
    public double getLineSpacing() {
        return lineSpacing;
    }

    /**
     * 设置行距值。
     *
     * @param lineSpacing 行距值；多倍行距中 18 表示 1.5 倍
     */
    public void setLineSpacing(double lineSpacing) {
        this.lineSpacing = lineSpacing;
    }

    /**
     * 获取段前间距。
     *
     * @return 段前间距，单位为 point
     */
    public double getSpaceBeforePoints() {
        return spaceBeforePoints;
    }

    /**
     * 设置段前间距。
     *
     * @param spaceBeforePoints 段前间距，单位为 point
     */
    public void setSpaceBeforePoints(double spaceBeforePoints) {
        this.spaceBeforePoints = spaceBeforePoints;
    }

    /**
     * 获取段后间距。
     *
     * @return 段后间距，单位为 point
     */
    public double getSpaceAfterPoints() {
        return spaceAfterPoints;
    }

    /**
     * 设置段后间距。
     *
     * @param spaceAfterPoints 段后间距，单位为 point
     */
    public void setSpaceAfterPoints(double spaceAfterPoints) {
        this.spaceAfterPoints = spaceAfterPoints;
    }

    /**
     * 判断是否与下段同页。
     *
     * @return 与下段同页返回 {@code true}
     */
    public boolean isKeepWithNext() {
        return keepWithNext;
    }

    /**
     * 设置是否与下段同页。
     *
     * @param keepWithNext 是否与下段同页
     */
    public void setKeepWithNext(boolean keepWithNext) {
        this.keepWithNext = keepWithNext;
    }

    /**
     * 判断段中是否不分页。
     *
     * @return 段中不分页返回 {@code true}
     */
    public boolean isKeepTogether() {
        return keepTogether;
    }

    /**
     * 设置段中是否不分页。
     *
     * @param keepTogether 是否段中不分页
     */
    public void setKeepTogether(boolean keepTogether) {
        this.keepTogether = keepTogether;
    }
}
