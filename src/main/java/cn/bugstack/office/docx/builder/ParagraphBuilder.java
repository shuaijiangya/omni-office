package cn.bugstack.office.docx.builder;

import cn.bugstack.office.docx.model.CaptionRefInline;
import cn.bugstack.office.docx.model.CaptionType;
import cn.bugstack.office.docx.model.ChartInline;
import cn.bugstack.office.docx.model.ChartNode;
import cn.bugstack.office.docx.model.ChartType;
import cn.bugstack.office.docx.model.ImageInline;
import cn.bugstack.office.docx.model.ParagraphListType;
import cn.bugstack.office.docx.model.ParagraphNode;
import cn.bugstack.office.docx.model.TextRunInline;
import cn.bugstack.office.docx.model.VisioInline;
import cn.bugstack.office.docx.style.RunStyle;

import java.util.function.Consumer;

/**
 * Paragraph 级 Builder，用于向段落中添加行内内容。
 *
 * <p>文本、图片和 Visio 预览图都属于 paragraph 的 child。泛型 {@code P}
 * 表示调用 {@link #end()} 后返回的父级 Builder 类型。</p>
 *
 * @param <P> 父级 Builder 类型
 */
public class ParagraphBuilder<P> {

    /** 段落所属的父构建器。 */
    private final P parent;
    /** 正在构建的段落节点。 */
    private final ParagraphNode paragraph;

    /**
     * 创建段落 Builder。
     *
     * @param parent 父级 Builder
     * @param paragraph 当前段落节点
     */
    public ParagraphBuilder(P parent, ParagraphNode paragraph) {
        this.parent = parent;
        this.paragraph = paragraph;
    }

    /**
     * 设置当前段落的样式名称。
     *
     * @param styleName 样式名称，例如 {@code BodyText}、{@code Heading1}
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> style(String styleName) {
        paragraph.setStyleName(styleName);
        return this;
    }

    /**
     * 将当前段落设置为项目符号列表段落。
     *
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> bullet() {
        paragraph.setListType(ParagraphListType.BULLET);
        return this;
    }

    /**
     * 将当前段落设置为编号列表段落。
     *
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> numbered() {
        paragraph.setListType(ParagraphListType.NUMBER);
        return this;
    }

    /**
     * 向段落追加文本 run。
     *
     * @param text 文本内容
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> text(String text) {
        paragraph.addInline(new TextRunInline(text));
        return this;
    }

    /**
     * 向段落追加指定字体颜色的文本 run。
     *
     * @param text 文本内容
     * @param fontColor 字体颜色，格式为 {@code #RRGGBB}；为空时继承段落样式
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> text(String text, String fontColor) {
        paragraph.addInline(new TextRunInline(text, fontColor));
        return this;
    }

    /**
     * 向段落追加具有独立样式的文本范围。
     *
     * @param text 文本内容
     * @param styleCustomizer 字体、字号、颜色及文字效果配置
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> text(String text, Consumer<RunStyle> styleCustomizer) {
        RunStyle style = new RunStyle();
        if (styleCustomizer != null) {
            styleCustomizer.accept(style);
        }
        paragraph.addInline(new TextRunInline(text, style));
        return this;
    }

    /**
     * 向段落追加预先构造好独立样式的文本范围。
     *
     * @param text 文本内容
     * @param style 文本范围样式；为空时继承段落样式
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> text(String text, RunStyle style) {
        paragraph.addInline(new TextRunInline(text, style));
        return this;
    }

    /**
     * 向段落追加题注引用。
     *
     * @param type 题注类型
     * @param captionId 题注业务标识
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> captionRef(CaptionType type, String captionId) {
        paragraph.addInline(new CaptionRefInline(type, captionId));
        return this;
    }

    /**
     * 向段落追加图片行内节点。
     *
     * @param source 图片路径或 Aspose 可识别的图片来源
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> image(String source) {
        paragraph.addInline(new ImageInline(source));
        return this;
    }

    /**
     * 向段落追加指定尺寸的图片行内节点。
     *
     * @param source 图片路径或 Aspose 可识别的图片来源
     * @param widthPoints 图片宽度，单位为 point
     * @param heightPoints 图片高度，单位为 point
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> image(String source, double widthPoints, double heightPoints) {
        paragraph.addInline(new ImageInline(source, widthPoints, heightPoints));
        return this;
    }

    /**
     * 向段落追加 Visio 预览图行内节点。
     *
     * <p>该方法只插入预览图片。需要在 Word 内双击编辑时，请使用
     * {@link #editableVisio(String, String, double, double)}。</p>
     *
     * @param previewSource Visio 预览图路径或 Aspose 可识别的图片来源
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> visio(String previewSource) {
        paragraph.addInline(new VisioInline(previewSource));
        return this;
    }

    /**
     * 向段落追加可编辑的 Visio OLE 行内节点。
     *
     * <p>VSDX 会被嵌入 docx 包中，PNG 仅作为 Word 页面展示的预览图。用户在支持
     * OLE Visio 对象的 Office 客户端中双击预览图即可打开嵌入的 VSDX。</p>
     *
     * @param vsdxSource 可编辑 VSDX 文件路径
     * @param previewSource Word 中展示的 VSDX 原生 PNG 预览图路径
     * @param widthPoints 最大显示宽度，单位为 point
     * @param heightPoints 最大显示高度，单位为 point
     * @return 当前段落 Builder
     */
    public ParagraphBuilder<P> editableVisio(String vsdxSource, String previewSource,
                                              double widthPoints, double heightPoints) {
        paragraph.addInline(VisioInline.embedded(vsdxSource, previewSource, widthPoints, heightPoints));
        return this;
    }

    /**
     * 向当前段落追加 Word 原生可编辑图表。
     *
     * <p>图表 Builder 的 {@code end()} 返回当前 Paragraph Builder，因此完整链式写法为
     * {@code section.paragraph().chart(type)...end().end()}。</p>
     *
     * @param type 柱状图、条形图、饼图、折线图或雷达图
     * @return 图表 Builder
     */
    public ChartBuilder<ParagraphBuilder<P>> chart(ChartType type) {
        if (type == null) throw new IllegalArgumentException("inline chart type must not be null");
        ChartNode chart = new ChartNode();
        chart.setChartType(type);
        paragraph.addInline(new ChartInline(chart));
        return new ChartBuilder<>(this, chart);
    }

    /**
     * 结束当前段落构建并返回父级 Builder。
     *
     * @return 父级 Builder
     */
    public P end() {
        return parent;
    }
}
