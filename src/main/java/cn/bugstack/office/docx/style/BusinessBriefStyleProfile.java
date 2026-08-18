package cn.bugstack.office.docx.style;

/**
 * 适合跨平台演示与正式技术简报的内置样式。
 * 使用可在 Linux 生产镜像中安装的 Noto Sans CJK SC 覆盖中英文，
 * A4 页面作为中文技术文档命名覆盖项保留；其他桌面系统可按字体回退规则替换。
 */
public final class BusinessBriefStyleProfile implements StyleProfile {

    private static final BusinessBriefStyleProfile INSTANCE = new BusinessBriefStyleProfile();
    private static final String FONT = "Noto Sans CJK SC";

    private BusinessBriefStyleProfile() { }

    public static BusinessBriefStyleProfile standard() { return INSTANCE; }

    @Override
    public StyleRegistry createRegistry() {
        StyleRegistry registry = DefaultStyles.createRegistry();
        configure(registry, "Normal", 11, false, 0, 6, false);
        configure(registry, "BodyText", 11, false, 0, 6, false);
        configure(registry, "CodeBlock", 9, false, 0, 6, false);
        configure(registry, "Title", 23, true, 0, 4, false);
        configure(registry, "Subtitle", 14, false, 0, 16, false);
        configure(registry, "Heading1", 16, true, 16, 8, true);
        configure(registry, "Heading2", 13, true, 12, 6, true);
        configure(registry, "Heading3", 12, true, 8, 4, true);
        for (int level = 4; level <= 9; level++) {
            configure(registry, "Heading" + level, 11, true, 8, 4, true);
        }
        configure(registry, "Caption", 9, false, 4, 4, false);
        configure(registry, "ImageCaption", 9, false, 4, 4, false);
        return registry;
    }

    private void configure(StyleRegistry registry, String name, double size, boolean bold,
                           double before, double after, boolean keepWithNext) {
        ParagraphStyle style = registry.getParagraphStyle(name);
        style.setFontFamily(FONT);
        style.setAsciiFontFamily(FONT);
        style.setFarEastFontFamily(FONT);
        style.setFontSize(size);
        style.setBold(bold);
        style.setItalic(false);
        style.setUnderline(false);
        style.setCharacterUnitFirstLineIndent(0);
        style.setAlignment("Caption".equals(name) || "ImageCaption".equals(name)
                ? DocxParagraphAlignment.CENTER : DocxParagraphAlignment.LEFT);
        style.setLineSpacingRule(DocxLineSpacingRule.MULTIPLE);
        style.setLineSpacing(size * 1.2);
        style.setSpaceBeforePoints(before);
        style.setSpaceAfterPoints(after);
        style.setKeepWithNext(keepWithNext);
        registry.registerParagraphStyle(style);
    }
}
