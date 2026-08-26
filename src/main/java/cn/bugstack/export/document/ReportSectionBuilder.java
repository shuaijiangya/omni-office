package cn.bugstack.export.document;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 报告语义章节构建器。
 *
 * <p>该 Builder 不暴露 Aspose 或 {@code office.docx} 类型，模块可以稳定地表达报告内容，
 * 再由目标格式编译器转换为具体文档实现。</p>
 */
public final class ReportSectionBuilder {

    /** 正在构建的报告章节。 */
    private final ReportSection section;

    private ReportSectionBuilder(String title) {
        this.section = new ReportSection(title);
    }

    /**
     * 创建指定标题的章节构建器。
     *
     * @param title 章节标题
     * @return 章节构建器
     */
    public static ReportSectionBuilder section(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("report section title must not be blank");
        }
        return new ReportSectionBuilder(title.trim());
    }

    /**
     * 追加正文段落。
     *
     * @param text 段落文本
     * @return 当前构建器
     */
    public ReportSectionBuilder paragraph(String text) {
        return paragraph(null, text);
    }

    /**
     * 追加指定样式段落。
     *
     * @param styleName 段落样式名称
     * @param text 段落文本
     * @return 当前构建器
     */
    public ReportSectionBuilder paragraph(String styleName, String text) {
        return paragraph(styleName, text, null);
    }

    /**
     * 追加指定样式和字体颜色的段落。
     *
     * @param styleName 段落样式名称
     * @param text 段落文本
     * @param fontColor 字体颜色，格式为 {@code #RRGGBB}
     * @return 当前构建器
     */
    public ReportSectionBuilder paragraph(String styleName, String text, String fontColor) {
        ReportParagraph paragraph = new ReportParagraph(text);
        paragraph.setStyleName(styleName);
        paragraph.setFontColor(fontColor);
        return add(paragraph);
    }

    /**
     * 开始构建包含多个独立样式文本范围的段落。
     *
     * @return 富文本段落构建器
     */
    public ReportParagraphBuilder richParagraph() {
        return new ReportParagraphBuilder(this);
    }

    /**
     * 追加项目符号列表项。
     *
     * @param text 列表项文本
     * @return 当前构建器
     */
    public ReportSectionBuilder bullet(String text) {
        return bullet(text, null);
    }

    /**
     * 追加指定字体颜色的项目符号列表项。
     *
     * @param text 列表项文本
     * @param fontColor 字体颜色，格式为 {@code #RRGGBB}
     * @return 当前构建器
     */
    public ReportSectionBuilder bullet(String text, String fontColor) {
        ReportListItem item = new ReportListItem(ReportListType.BULLET, text);
        item.setFontColor(fontColor);
        return add(item);
    }

    /**
     * 追加编号列表项。
     *
     * @param text 列表项文本
     * @return 当前构建器
     */
    public ReportSectionBuilder numbered(String text) {
        return numbered(text, null);
    }

    /**
     * 追加指定字体颜色的编号列表项。
     *
     * @param text 列表项文本
     * @param fontColor 字体颜色，格式为 {@code #RRGGBB}
     * @return 当前构建器
     */
    public ReportSectionBuilder numbered(String text, String fontColor) {
        ReportListItem item = new ReportListItem(ReportListType.NUMBERED, text);
        item.setFontColor(fontColor);
        return add(item);
    }

    /**
     * 开始构建表格。
     *
     * @param headers 表头
     * @return 表格构建器
     */
    public ReportTableBuilder table(String... headers) {
        return new ReportTableBuilder(this, headers);
    }

    /**
     * 开始构建 Word 原生图表。
     *
     * @param type 图表类型
     * @return 图表构建器
     */
    public ReportChartBuilder chart(ReportChartType type) {
        if (type == null) throw new IllegalArgumentException("report chart type must not be null");
        return new ReportChartBuilder(this, type);
    }

    /**
     * 追加图片及可选图题。
     *
     * @param source 图片来源
     * @param width 宽度，单位由目标编译器解释
     * @param height 高度，单位由目标编译器解释
     * @param caption 图题文本
     * @return 当前构建器
     */
    public ReportSectionBuilder image(String source, Integer width, Integer height, String caption) {
        return image(source, width, height, caption, CaptionPosition.BELOW);
    }

    /**
     * 追加图片并设置图题位置。
     *
     * @param source 图片来源
     * @param width 宽度，单位由目标编译器解释
     * @param height 高度，单位由目标编译器解释
     * @param caption 图题文本
     * @param captionPosition 图题位于图片上方或下方
     * @return 当前构建器
     */
    public ReportSectionBuilder image(String source, Integer width, Integer height, String caption,
                                      CaptionPosition captionPosition) {
        ReportImage image = new ReportImage(source);
        image.setWidth(width);
        image.setHeight(height);
        if (caption != null && !caption.trim().isEmpty()) {
            ReportCaption reportCaption = new ReportCaption(CaptionTargetType.IMAGE, caption.trim());
            reportCaption.setPosition(captionPosition);
            image.setCaption(reportCaption);
        }
        return add(image);
    }

    /** 追加已解析的图形语义元素。 */
    public ReportSectionBuilder diagram(ReportDiagram diagram) {
        return add(diagram);
    }

    /**
     * 追加显式分页符。
     *
     * @return 当前构建器
     */
    public ReportSectionBuilder pageBreak() {
        return add(new ReportPageBreak());
    }

    /**
     * 追加类设计表格。
     *
     * @param title 类设计小节标题
     * @param sourceRoot 源码根目录
     * @param className 目标类全限定名
     * @return 当前构建器
     */
    public ReportSectionBuilder classDesignTable(String title, Path sourceRoot, String className) {
        return classDesignTable(title, sourceRoot, className, null);
    }

    /**
     * 追加可配置的类设计表格。
     *
     * @param title 类设计小节标题
     * @param sourceRoot 源码根目录
     * @param className 目标类全限定名
     * @param customizer 类设计表格配置回调；为空时使用默认配置
     * @return 当前构建器
     */
    public ReportSectionBuilder classDesignTable(String title, Path sourceRoot, String className,
                                                 Consumer<ReportClassDesignTable.Builder> customizer) {
        ReportClassDesignTable.Builder builder = ReportClassDesignTable.builder(title, sourceRoot, className);
        if (customizer != null) {
            customizer.accept(builder);
        }
        return add(builder.build());
    }

    /**
     * 递归追加子章节。
     *
     * @param title 子章节标题
     * @param customizer 子章节内容回调
     * @return 当前构建器
     */
    public ReportSectionBuilder section(String title, Consumer<ReportSectionBuilder> customizer) {
        ReportSectionBuilder child = section(title);
        customizer.accept(child);
        return add(child.build());
    }

    /**
     * 追加自定义语义块。
     *
     * @param element 报告元素
     * @return 当前构建器
     */
    public ReportSectionBuilder add(ReportElement element) {
        if (element == null) {
            throw new IllegalArgumentException("report element must not be null");
        }
        section.addElement(element);
        return this;
    }

    /**
     * 完成章节构建。
     *
     * @return 章节语义模型
     */
    public ReportSection build() {
        return section;
    }
}
