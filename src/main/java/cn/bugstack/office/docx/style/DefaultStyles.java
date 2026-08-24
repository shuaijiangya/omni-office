package cn.bugstack.office.docx.style;

/**
 * 内置标准样式工厂。
 *
 * <p>该类集中定义封装层开箱即用的 Word 样式集合，作为
 * {@link cn.bugstack.office.docx.api.DocxDocument#useDefaultStyles()} 和
 * {@link DefaultStyleProfile} 的默认样式来源。样式会注册到 {@link StyleRegistry}，
 * 渲染器再根据样式名称把内部样式映射为 Aspose/Word 的真实样式。</p>
 *
 * <p>默认样式遵循中文技术文档常见排版约定：西文字体使用 Times New Roman，
 * 正文中文字体使用宋体，标题中文字体使用黑体。正文默认两字符首行缩进、两端对齐、
 * 1.5 倍行距；标题默认不缩进、左对齐，并与下段同页；题注默认居中。</p>
 *
 * <p>该类只负责创建样式定义，不保存文档状态，也不直接操作 Aspose 对象。
 * 如需定义另一套标准，应优先实现 {@link StyleProfile}，再复用或替换本类提供的注册逻辑。</p>
 */
public final class DefaultStyles {

    /**
     * 默认西文字体。
     */
    private static final String DEFAULT_ASCII_FONT = "Times New Roman";

    /**
     * 默认中文正文字体。
     */
    private static final String DEFAULT_FAR_EAST_FONT = "宋体";

    /**
     * 默认中文标题字体。
     */
    private static final String HEADING_FAR_EAST_FONT = "黑体";

    private DefaultStyles() {
    }

    /**
     * 创建一套默认样式注册表。
     *
     * <p>注册表包含段落样式、表格样式和单元格基础样式。段落样式覆盖
     * {@code Normal}、{@code Title}、{@code Subtitle}、{@code Heading1} 到
     * {@code Heading9}、{@code BodyText}、{@code Caption}、{@code ImageCaption}
     * 和 {@code CodeBlock}。表格样式覆盖普通边框表格，以及表头、表内容可独立配置的表格。</p>
     *
     * @return 包含常用段落、表格和图片样式的注册表
     */
    public static StyleRegistry createRegistry() {
        StyleRegistry registry = new StyleRegistry();

        registry.registerParagraphStyle(body("Normal", 10.5, false, 0));
        registry.registerParagraphStyle(heading("Title", 22, true, 0, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(heading("Subtitle", 14, false, 0, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(heading("Heading1", 16, true, 1, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(heading("Heading2", 14, true, 2, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(heading("Heading3", 12, true, 3, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(heading("Heading4", 12, true, 4, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(heading("Heading5", 10.5, true, 5, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(heading("Heading6", 10.5, true, 6, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(heading("Heading7", 10.5, true, 7, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(heading("Heading8", 10.5, true, 8, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(heading("Heading9", 10.5, true, 9, DocxParagraphAlignment.LEFT));
        registry.registerParagraphStyle(body("BodyText", 10.5, false, 0));
        registry.registerParagraphStyle(caption("Caption", 9, false, 0));
        registry.registerParagraphStyle(caption("ImageCaption", 9, false, 0));
        registry.registerParagraphStyle(body("CodeBlock", 9, false, 0));

        TableStyle tableNormal = new TableStyle("TableNormal");
        tableNormal.setBordered(true);
        registry.registerTableStyle(tableNormal);

        TableStyle tableHeader = new TableStyle("TableHeader");
        tableHeader.setBordered(true);
        tableHeader.setRepeatHeaderRow(true);
        registry.registerTableStyle(tableHeader);

        registry.registerTableStyle(new TableStyle("TableCell"));

        return registry;
    }

    /**
     * 创建正文类段落样式。
     *
     * <p>正文类样式默认使用宋体作为东亚字体、Times New Roman 作为西文字体，
     * 两端对齐，首行缩进两个字符，1.5 倍行距，段前段后均为 0。</p>
     *
     * @param name 样式名称
     * @param fontSize 字号，单位为 point
     * @param bold 是否加粗
     * @param outlineLevel 大纲级别，正文通常为 0
     * @return 正文类段落样式
     */
    private static ParagraphStyle body(String name, double fontSize, boolean bold, int outlineLevel) {
        ParagraphStyle style = paragraph(name, fontSize, bold, outlineLevel, DEFAULT_FAR_EAST_FONT);
        style.setAlignment(DocxParagraphAlignment.JUSTIFY);
        style.setCharacterUnitFirstLineIndent(2.0);
        style.setLineSpacingRule(DocxLineSpacingRule.MULTIPLE);
        style.setLineSpacing(18.0);
        style.setSpaceBeforePoints(0.0);
        style.setSpaceAfterPoints(0.0);
        return style;
    }

    /**
     * 创建标题类段落样式。
     *
     * <p>标题类样式默认使用黑体作为东亚字体、Times New Roman 作为西文字体，
     * 强制关闭斜体和下划线，不设置首行缩进，1.5 倍行距，段前段后均为 0。一级到九级标题会设置
     * {@code keepWithNext}，避免标题与紧随内容分页分离。</p>
     *
     * @param name 样式名称
     * @param fontSize 字号，单位为 point
     * @param bold 是否加粗
     * @param outlineLevel 大纲级别，标题为 1 到 9，文档标题可为 0
     * @param alignment 段落对齐方式
     * @return 标题类段落样式
     */
    private static ParagraphStyle heading(String name, double fontSize, boolean bold, int outlineLevel,
                                          DocxParagraphAlignment alignment) {
        ParagraphStyle style = paragraph(name, fontSize, bold, outlineLevel, HEADING_FAR_EAST_FONT);
        style.setItalic(false);
        style.setUnderline(false);
        style.setAlignment(alignment);
        style.setCharacterUnitFirstLineIndent(0.0);
        style.setLineSpacingRule(DocxLineSpacingRule.MULTIPLE);
        style.setLineSpacing(18.0);
        style.setSpaceBeforePoints(0.0);
        style.setSpaceAfterPoints(0.0);
        style.setKeepWithNext(outlineLevel > 0);
        return style;
    }

    /**
     * 创建题注类段落样式。
     *
     * <p>题注类样式默认居中、不缩进、1.5 倍行距，用于图片、表格等对象的说明文本。
     * 题注编号由渲染上下文负责生成，样式本身只表达版式。</p>
     *
     * @param name 样式名称
     * @param fontSize 字号，单位为 point
     * @param bold 是否加粗
     * @param outlineLevel 大纲级别，题注通常为 0
     * @return 题注类段落样式
     */
    private static ParagraphStyle caption(String name, double fontSize, boolean bold, int outlineLevel) {
        ParagraphStyle style = paragraph(name, fontSize, bold, outlineLevel, DEFAULT_FAR_EAST_FONT);
        style.setAlignment(DocxParagraphAlignment.CENTER);
        style.setCharacterUnitFirstLineIndent(0.0);
        style.setLineSpacingRule(DocxLineSpacingRule.MULTIPLE);
        style.setLineSpacing(18.0);
        style.setSpaceBeforePoints(0.0);
        style.setSpaceAfterPoints(0.0);
        return style;
    }

    /**
     * 创建段落样式基础对象。
     *
     * <p>该方法只填充所有段落样式共享的基础属性：样式名称、西文字体、东亚字体、
     * 字号、加粗和大纲级别。对齐、缩进、行距、段前段后等布局属性由具体工厂方法补充。</p>
     *
     * @param name 样式名称
     * @param fontSize 字号，单位为 point
     * @param bold 是否加粗
     * @param outlineLevel 大纲级别，普通正文为 0，标题为 1 到 9
     * @param farEastFontFamily 东亚字体族
     * @return 段落样式基础对象
     */
    private static ParagraphStyle paragraph(String name, double fontSize, boolean bold, int outlineLevel,
                                            String farEastFontFamily) {
        ParagraphStyle style = new ParagraphStyle(name, farEastFontFamily, fontSize);
        style.setAsciiFontFamily(DEFAULT_ASCII_FONT);
        style.setFarEastFontFamily(farEastFontFamily);
        style.setBold(bold);
        style.setOutlineLevel(outlineLevel);
        return style;
    }
}
