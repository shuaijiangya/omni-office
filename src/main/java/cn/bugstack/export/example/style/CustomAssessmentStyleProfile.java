package cn.bugstack.export.example.style;

import cn.bugstack.office.docx.style.DefaultStyles;
import cn.bugstack.office.docx.style.DocxLineSpacingRule;
import cn.bugstack.office.docx.style.DocxParagraphAlignment;
import cn.bugstack.office.docx.style.ParagraphStyle;
import cn.bugstack.office.docx.style.StyleProfile;
import cn.bugstack.office.docx.style.StyleRegistry;
import cn.bugstack.office.docx.style.TableStyle;

/**
 * 业务侧自定义的评估报告样式画像。
 *
 * <p>该类位于示例业务包，只依赖框架公开的 {@link StyleProfile} SPI。实现方可以先继承
 * 默认样式集合，再用同名样式覆盖需要调整的部分，不需要修改框架的样式枚举、编译器或
 * 渲染器。</p>
 */
public final class CustomAssessmentStyleProfile implements StyleProfile {

    /**
     * 创建本次导出使用的独立样式注册表。
     *
     * @return 包含业务自定义规则的样式注册表
     */
    @Override
    public StyleRegistry createRegistry() {
        StyleRegistry registry = DefaultStyles.createRegistry();

        ParagraphStyle title = requiredParagraph(registry, "Title");
        title.setFontFamily("微软雅黑");
        title.setFarEastFontFamily("微软雅黑");
        title.setAsciiFontFamily("Arial");
        title.setFontSize(24.0);
        title.setBold(true);
        title.setAlignment(DocxParagraphAlignment.CENTER);
        title.setSpaceAfterPoints(18.0);
        registry.registerParagraphStyle(title);

        ParagraphStyle heading1 = requiredParagraph(registry, "Heading1");
        heading1.setFarEastFontFamily("黑体");
        heading1.setFontSize(16.0);
        heading1.setBold(true);
        heading1.setSpaceBeforePoints(12.0);
        heading1.setSpaceAfterPoints(6.0);
        registry.registerParagraphStyle(heading1);

        ParagraphStyle body = customizeBody(requiredParagraph(registry, "BodyText"));
        registry.registerParagraphStyle(body);

        ParagraphStyle normal = customizeBody(requiredParagraph(registry, "Normal"));
        registry.registerParagraphStyle(normal);

        TableStyle table = new TableStyle("TableHeader");
        table.setBordered(true);
        table.setHeaderBold(true);
        registry.registerTableStyle(table);
        return registry;
    }

    private static ParagraphStyle requiredParagraph(StyleRegistry registry, String name) {
        ParagraphStyle style = registry.getParagraphStyle(name);
        if (style == null) {
            throw new IllegalStateException("missing base paragraph style: " + name);
        }
        return style;
    }

    private static ParagraphStyle customizeBody(ParagraphStyle style) {
        style.setFontFamily("宋体");
        style.setFarEastFontFamily("宋体");
        style.setAsciiFontFamily("Times New Roman");
        style.setFontSize(12.0);
        style.setAlignment(DocxParagraphAlignment.JUSTIFY);
        style.setCharacterUnitFirstLineIndent(2.0);
        style.setLineSpacingRule(DocxLineSpacingRule.MULTIPLE);
        style.setLineSpacing(18.0);
        return style;
    }
}
