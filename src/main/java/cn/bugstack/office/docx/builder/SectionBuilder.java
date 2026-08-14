package cn.bugstack.office.docx.builder;

import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.design.ClassDesignTableBuilder;
import cn.bugstack.office.docx.design.ClassDesignTableWriter;
import cn.bugstack.office.docx.design.model.ClassDesignDoc;
import cn.bugstack.office.docx.design.parser.ClassMetadataParser;
import cn.bugstack.office.docx.design.parser.SourceClassMetadataParser;
import cn.bugstack.office.docx.model.CaptionNode;
import cn.bugstack.office.docx.model.CaptionType;
import cn.bugstack.office.docx.model.ParagraphNode;
import cn.bugstack.office.docx.model.PageBreakNode;
import cn.bugstack.office.docx.model.SectionNode;
import cn.bugstack.office.docx.model.TableNode;

import java.util.function.Consumer;

/**
 * Section 级 Builder，用于向章节中添加块级内容。
 *
 * <p>Section 只能承载段落、表格等 block 节点。图片、Visio、文本等 inline
 * 节点必须通过 {@link ParagraphBuilder} 添加到段落中。</p>
 */
public class SectionBuilder {

    /** 所属 DOCX 文档。 */
    private final DocxDocument document;
    /** 正在构建的章节节点。 */
    private final SectionNode section;

    /**
     * 创建章节 Builder。
     *
     * @param document 所属文档门面
     * @param section 当前章节节点
     */
    public SectionBuilder(DocxDocument document, SectionNode section) {
        this.document = document;
        this.section = section;
    }

    /**
     * 在当前章节中追加一个普通段落。
     *
     * @return 段落 Builder
     */
    public ParagraphBuilder<SectionBuilder> paragraph() {
        ParagraphNode paragraph = new ParagraphNode();
        section.addBlock(paragraph);
        return new ParagraphBuilder<>(this, paragraph);
    }

    /**
     * 在当前章节中追加一级标题段落。
     *
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder heading1(String text) {
        return heading(1, text);
    }

    /**
     * 在当前章节中追加二级标题段落。
     *
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder heading2(String text) {
        return heading(2, text);
    }

    /**
     * 在当前章节中追加三级标题段落。
     *
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder heading3(String text) {
        return heading(3, text);
    }

    /**
     * 在当前章节中追加四级标题段落。
     *
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder heading4(String text) {
        return heading(4, text);
    }

    /**
     * 在当前章节中追加五级标题段落。
     *
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder heading5(String text) {
        return heading(5, text);
    }

    /**
     * 在当前章节中追加六级标题段落。
     *
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder heading6(String text) {
        return heading(6, text);
    }

    /**
     * 在当前章节中追加七级标题段落。
     *
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder heading7(String text) {
        return heading(7, text);
    }

    /**
     * 在当前章节中追加八级标题段落。
     *
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder heading8(String text) {
        return heading(8, text);
    }

    /**
     * 在当前章节中追加九级标题段落。
     *
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder heading9(String text) {
        return heading(9, text);
    }

    /**
     * 在当前章节中追加指定级别标题段落。
     *
     * @param level 标题级别，范围为 1 到 9
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder heading(int level, String text) {
        if (level < 1 || level > 9) {
            throw new IllegalArgumentException("heading level must be between 1 and 9: " + level);
        }
        paragraph().style("Heading" + level).text(text).end();
        return this;
    }

    /**
     * 在当前章节中追加文档标题段落。
     *
     * @param text 标题文本
     * @return 当前章节 Builder
     */
    public SectionBuilder title(String text) {
        paragraph().style("Title").text(text).end();
        return this;
    }

    /**
     * 在当前章节中追加项目符号列表段落。
     *
     * @param text 列表项文本
     * @return 当前章节 Builder
     */
    public SectionBuilder bullet(String text) {
        paragraph().bullet().text(text).end();
        return this;
    }

    /**
     * 在当前章节中追加编号列表段落。
     *
     * @param text 列表项文本
     * @return 当前章节 Builder
     */
    public SectionBuilder numbered(String text) {
        paragraph().numbered().text(text).end();
        return this;
    }

    /**
     * 在当前章节中追加显式分页符。
     *
     * @return 当前章节 Builder
     */
    public SectionBuilder pageBreak() {
        section.addBlock(new PageBreakNode());
        return this;
    }

    /**
     * 在当前章节中追加一个表格。
     *
     * @return 表格 Builder
     */
    public TableBuilder<SectionBuilder> table() {
        TableNode table = new TableNode();
        section.addBlock(table);
        return new TableBuilder<>(this, table);
    }

    /**
     * 在当前章节中追加指定类的设计表格。
     *
     * <p>该方法会读取配置中的源码类，解析类、字段、方法及 Javadoc 注释，
     * 并将结果写入一个标准五列表格。表格本身仍是普通 {@link TableNode}，
     * 因此会继续沿用现有表格样式和 Aspose 渲染能力。</p>
     *
     * @param customizer 类设计表格配置回调
     * @return 当前章节 Builder
     */
    public SectionBuilder classDesignTable(Consumer<ClassDesignTableBuilder> customizer) {
        return writeClassDesignTable(3, customizer);
    }

    /**
     * 在当前章节中追加标题和指定类的设计表格。
     *
     * <p>该快捷方法按标准文档层级使用二级标题表示类设计，属性说明和方法说明
     * 自动使用其下的三级标题。若类设计表格位于已有子章节中，请使用
     * {@link #classDesignTable(int, String, Consumer)} 显式指定标题层级。</p>
     *
     * @param title 类设计表格标题
     * @param customizer 类设计表格配置回调
     * @return 当前章节 Builder
     */
    public SectionBuilder classDesignTable(String title, Consumer<ClassDesignTableBuilder> customizer) {
        return classDesignTable(2, title, customizer);
    }

    /**
     * 在当前章节中追加指定层级标题和类设计表格。
     *
     * <p>类基本信息表紧随该标题输出，属性说明和方法说明使用 {@code titleLevel + 1}
     * 的标题层级。因此类设计标题最多支持第八级，以保留一个下级标题层级。</p>
     *
     * @param titleLevel 类设计标题层级，范围为 1 到 8
     * @param title 类设计表格标题
     * @param customizer 类设计表格配置回调
     * @return 当前章节 Builder
     */
    public SectionBuilder classDesignTable(int titleLevel, String title,
                                           Consumer<ClassDesignTableBuilder> customizer) {
        if (titleLevel < 1 || titleLevel >= 9) {
            throw new IllegalArgumentException("class design table title level must be between 1 and 8: " + titleLevel);
        }
        heading(titleLevel, title);
        return writeClassDesignTable(titleLevel + 1, customizer);
    }

    /**
     * 解析指定类并以给定层级写入属性、方法等明细标题。
     *
     * @param detailHeadingLevel 类设计明细标题层级
     * @param customizer 类设计表格配置回调
     * @return 当前章节 Builder
     */
    private SectionBuilder writeClassDesignTable(int detailHeadingLevel,
                                                 Consumer<ClassDesignTableBuilder> customizer) {
        ClassDesignTableBuilder builder = new ClassDesignTableBuilder();
        customizer.accept(builder);
        ClassMetadataParser parser = new SourceClassMetadataParser();
        ClassDesignDoc classDesignDoc = parser.parse(builder.getOptions());
        new ClassDesignTableWriter().write(this, classDesignDoc, detailHeadingLevel, builder.getOptions());
        return this;
    }

    /**
     * 在当前章节中追加图题。
     *
     * @param text 图题文本，不包含自动编号前缀
     * @return 当前章节 Builder
     */
    public SectionBuilder figureCaption(String text) {
        return caption(CaptionType.FIGURE, text);
    }

    /**
     * 在当前章节中追加带业务标识的图题。
     *
     * @param id 图题业务标识
     * @param text 图题文本，不包含自动编号前缀
     * @return 当前章节 Builder
     */
    public SectionBuilder figureCaption(String id, String text) {
        return caption(CaptionType.FIGURE, id, text);
    }

    /**
     * 在当前章节中追加可编辑 Visio OLE 图及其自动编号图题。
     *
     * <p>VSDX 作为段落中的 OLE 行内对象写入，题注作为紧随其后的章节块写入，从而保持
     * 图题自动编号、题注样式和交叉引用能力。</p>
     *
     * @param vsdxSource 可编辑 VSDX 文件路径
     * @param previewSource Word 中展示的 PNG 预览图路径
     * @param widthPoints OLE 图最大显示宽度，单位为 point
     * @param heightPoints OLE 图最大显示高度，单位为 point
     * @param captionText 图题文本，不包含自动编号前缀
     * @return 当前章节 Builder
     */
    public SectionBuilder editableVisio(String vsdxSource, String previewSource, double widthPoints,
                                        double heightPoints, String captionText) {
        return editableVisio(null, vsdxSource, previewSource, widthPoints, heightPoints, captionText);
    }

    /**
     * 在当前章节中追加可编辑 Visio OLE 图及带业务标识的自动编号图题。
     *
     * @param captionId 图题业务标识，可用于 {@code captionRef(CaptionType.FIGURE, captionId)}
     * @param vsdxSource 可编辑 VSDX 文件路径
     * @param previewSource Word 中展示的 PNG 预览图路径
     * @param widthPoints OLE 图最大显示宽度，单位为 point
     * @param heightPoints OLE 图最大显示高度，单位为 point
     * @param captionText 图题文本，不包含自动编号前缀
     * @return 当前章节 Builder
     */
    public SectionBuilder editableVisio(String captionId, String vsdxSource, String previewSource,
                                        double widthPoints, double heightPoints, String captionText) {
        paragraph().editableVisio(vsdxSource, previewSource, widthPoints, heightPoints).end();
        if (captionId == null || captionId.trim().isEmpty()) {
            return figureCaption(captionText);
        }
        return figureCaption(captionId, captionText);
    }

    /**
     * 在当前章节中追加表题。
     *
     * @param text 表题文本，不包含自动编号前缀
     * @return 当前章节 Builder
     */
    public SectionBuilder tableCaption(String text) {
        return caption(CaptionType.TABLE, text);
    }

    /**
     * 在当前章节中追加带业务标识的表题。
     *
     * @param id 表题业务标识
     * @param text 表题文本，不包含自动编号前缀
     * @return 当前章节 Builder
     */
    public SectionBuilder tableCaption(String id, String text) {
        return caption(CaptionType.TABLE, id, text);
    }

    /**
     * 在当前章节中追加指定类型题注。
     *
     * @param type 题注类型
     * @param text 题注文本，不包含自动编号前缀
     * @return 当前章节 Builder
     */
    public SectionBuilder caption(CaptionType type, String text) {
        section.addBlock(new CaptionNode(type, text));
        return this;
    }

    /**
     * 在当前章节中追加带业务标识的题注。
     *
     * @param type 题注类型
     * @param id 题注业务标识
     * @param text 题注文本，不包含自动编号前缀
     * @return 当前章节 Builder
     */
    public SectionBuilder caption(CaptionType type, String id, String text) {
        section.addBlock(new CaptionNode(type, id, text));
        return this;
    }

    /**
     * 结束当前章节构建并返回文档门面。
     *
     * @return 所属文档
     */
    public DocxDocument end() {
        return document;
    }
}
