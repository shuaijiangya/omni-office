package cn.bugstack.office.docx.render;

import cn.bugstack.office.docx.exception.DocxRenderException;
import cn.bugstack.office.docx.model.ApprovalPageNode;
import cn.bugstack.office.docx.model.CaptionNode;
import cn.bugstack.office.docx.model.CaptionRefInline;
import cn.bugstack.office.docx.model.CaptionType;
import cn.bugstack.office.docx.model.CoverPageNode;
import cn.bugstack.office.docx.model.DocxBlock;
import cn.bugstack.office.docx.model.DocxInline;
import cn.bugstack.office.docx.model.DocxPageOrientation;
import cn.bugstack.office.docx.model.DocxPageSetup;
import cn.bugstack.office.docx.model.DocxPaperSize;
import cn.bugstack.office.docx.model.DocumentNode;
import cn.bugstack.office.docx.model.ImageInline;
import cn.bugstack.office.docx.model.ParagraphListType;
import cn.bugstack.office.docx.model.ParagraphNode;
import cn.bugstack.office.docx.model.PageBreakNode;
import cn.bugstack.office.docx.model.RevisionHistoryNode;
import cn.bugstack.office.docx.model.SectionNode;
import cn.bugstack.office.docx.model.TableCellNode;
import cn.bugstack.office.docx.model.TableCellVerticalAlignment;
import cn.bugstack.office.docx.model.TableNode;
import cn.bugstack.office.docx.model.TableRowNode;
import cn.bugstack.office.docx.model.TableVerticalMerge;
import cn.bugstack.office.docx.model.TemplateCoverPageNode;
import cn.bugstack.office.docx.model.TextRunInline;
import cn.bugstack.office.docx.model.VisioInline;
import cn.bugstack.office.docx.style.ParagraphStyle;
import cn.bugstack.office.docx.style.StyleRegistry;
import cn.bugstack.office.docx.style.TableStyle;
import com.aspose.words.BreakType;
import com.aspose.words.CellMerge;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.Field;
import com.aspose.words.FieldType;
import com.aspose.words.HeaderFooterType;
import com.aspose.words.LineSpacingRule;
import com.aspose.words.LineStyle;
import com.aspose.words.List;
import com.aspose.words.ListLevel;
import com.aspose.words.ListLevelAlignment;
import com.aspose.words.ListTrailingCharacter;
import com.aspose.words.ListTemplate;
import com.aspose.words.Orientation;
import com.aspose.words.NumberStyle;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.OutlineLevel;
import com.aspose.words.PaperSize;
import com.aspose.words.ParagraphAlignment;
import com.aspose.words.Paragraph;
import com.aspose.words.PreferredWidth;
import com.aspose.words.Run;
import com.aspose.words.Row;
import com.aspose.words.Shape;
import com.aspose.words.StyleIdentifier;
import com.aspose.words.Style;
import com.aspose.words.Table;
import com.aspose.words.Underline;
import com.aspose.words.CellVerticalAlignment;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 基于 Aspose Words 的 docx 渲染器。
 *
 * <p>该类是封装层与 Aspose API 的主要适配边界，负责把内部 Composite
 * 节点树转换为 Aspose 文档、段落、表格和行内图片。</p>
 */
public class AsposeDocxRenderer implements DocxRenderer {

    /** 未指定样式时使用的正文段落样式名称。 */
    private static final String DEFAULT_PARAGRAPH_STYLE = "BodyText";

    /** 目录从二级开始每增加一级使用的左缩进，单位为 point。 */
    private static final double TOC_LEVEL_INDENT_POINTS = 24D;

    /** 渲染时解析段落、文本和表格样式的注册表。 */
    private final StyleRegistry styleRegistry;

    /**
     * 创建 Aspose 渲染器。
     *
     * @param styleRegistry 渲染时使用的样式注册表
     */
    public AsposeDocxRenderer(StyleRegistry styleRegistry) {
        this.styleRegistry = styleRegistry;
    }

    /**
     * 渲染文档节点树并保存为 docx。
     *
     * @param document 文档根节点
     * @param outputPath 输出 docx 文件路径
     * @throws DocxRenderException 渲染或保存失败时抛出
     */
    @Override
    public void render(DocumentNode document, Path outputPath) {
        try (OutputStream output = Files.newOutputStream(outputPath)) {
            render(document, output);
        } catch (DocxRenderException e) {
            throw e;
        } catch (IOException e) {
            throw new DocxRenderException("open docx output failed: " + outputPath, e);
        }
    }

    /**
     * 将内部文档节点树渲染到调用方提供的输出流。
     *
     * <p>该方法不会关闭输出流，流的生命周期仍由调用方负责。</p>
     *
     * @param document 文档根节点
     * @param output 输出流
     * @throws DocxRenderException 渲染或保存失败时抛出
     */
    public void render(DocumentNode document, OutputStream output) {
        if (output == null) {
            throw new IllegalArgumentException("docx output stream must not be null");
        }
        try {
            AsposeWordsLicenseLoader.applyConfiguredLicense();
            Document asposeDocument = new Document();
            clearHeadingTextEffects(asposeDocument);
            DocumentBuilder builder = new DocumentBuilder(asposeDocument);
            RenderContext context = new RenderContext(asposeDocument, builder, styleRegistry);
            context.setHeadingNumberingEnabled(document.isHeadingNumberingEnabled());
            if (document.isHeadingNumberingEnabled()) {
                context.setHeadingList(configureHeadingNumbering(asposeDocument));
            }
            applyDocumentMetadata(document, asposeDocument);
            applyPageSetup(document.getPageSetup(), asposeDocument);
            configureTableOfContentsStyles(asposeDocument);
            renderFrontMatter(document, context);
            renderTableOfContents(document, context);
            prepareModuleSection(document, context);
            for (int i = 0; i < document.getSections().size(); i++) {
                if (i > 0) {
                    builder.insertBreak(BreakType.SECTION_BREAK_NEW_PAGE);
                    configureModuleSection(document, builder, false);
                }
                renderSection(document.getSections().get(i), context);
            }
            asposeDocument.updateFields();
            normalizeTableOfContentsEntries(asposeDocument);
            markDynamicFieldsDirty(asposeDocument);
            asposeDocument.save(output, com.aspose.words.SaveFormat.DOCX);
        } catch (Exception e) {
            throw new DocxRenderException("render docx failed", e);
        }
    }

    /** 将封装层元数据同步到 Aspose 文档属性。 */
    private void applyDocumentMetadata(DocumentNode document, Document asposeDocument) {
        if (document.getMetadata().getTitle() != null) {
            asposeDocument.getBuiltInDocumentProperties().setTitle(document.getMetadata().getTitle());
        }
        if (document.getMetadata().getAuthor() != null) {
            asposeDocument.getBuiltInDocumentProperties().setAuthor(document.getMetadata().getAuthor());
        }
        if (document.getMetadata().getSubject() != null) {
            asposeDocument.getBuiltInDocumentProperties().setSubject(document.getMetadata().getSubject());
        }
    }

    /** 将页面设置应用到 Aspose 文档的首个章节。 */
    private void applyPageSetup(DocxPageSetup pageSetup, Document asposeDocument) throws Exception {
        com.aspose.words.PageSetup asposePageSetup = asposeDocument.getFirstSection().getPageSetup();
        asposePageSetup.setPaperSize(toAsposePaperSize(pageSetup.getPaperSize()));
        asposePageSetup.setOrientation(toAsposeOrientation(pageSetup.getOrientation()));
        asposePageSetup.setTopMargin(pageSetup.getTopMarginPoints());
        asposePageSetup.setRightMargin(pageSetup.getRightMarginPoints());
        asposePageSetup.setBottomMargin(pageSetup.getBottomMarginPoints());
        asposePageSetup.setLeftMargin(pageSetup.getLeftMarginPoints());
    }

    /** 渲染封面、修订记录和签署页等前置内容。 */
    private void renderFrontMatter(DocumentNode document, RenderContext context) throws Exception {
        for (int index = 0; index < document.getFrontMatterBlocks().size(); index++) {
            DocxBlock block = document.getFrontMatterBlocks().get(index);
            if (block instanceof CoverPageNode) {
                renderCoverPage((CoverPageNode) block, context);
            } else if (block instanceof TemplateCoverPageNode) {
                renderSection((TemplateCoverPageNode) block, context);
            } else if (block instanceof RevisionHistoryNode) {
                renderRevisionHistory((RevisionHistoryNode) block, context);
            } else if (block instanceof ApprovalPageNode) {
                renderApprovalPage((ApprovalPageNode) block, context);
            }
            if (index < document.getFrontMatterBlocks().size() - 1) {
                context.getBuilder().insertBreak(BreakType.PAGE_BREAK);
            }
        }
        if (!document.getFrontMatterBlocks().isEmpty()
                && (document.isTableOfContentsEnabled() || !document.getSections().isEmpty())) {
            context.getBuilder().insertBreak(BreakType.SECTION_BREAK_NEW_PAGE);
            clearHeaderFooter(context.getBuilder().getCurrentSection());
        }
    }

    /** 渲染文档封面。 */
    private void renderCoverPage(CoverPageNode cover, RenderContext context) throws Exception {
        DocumentBuilder builder = context.getBuilder();
        builder.getParagraphFormat().setAlignment(ParagraphAlignment.CENTER);
        builder.getFont().setNameFarEast("黑体");
        builder.getFont().setNameAscii("Times New Roman");
        builder.getFont().setSize(22);
        builder.getFont().setBold(true);
        builder.writeln(cover.getDocumentName());
        builder.writeln();
        builder.getFont().setSize(14);
        builder.getFont().setBold(false);
        builder.writeln("项目名称：" + cover.getProjectName());
        builder.writeln("文档版本：" + cover.getVersion());
        builder.getParagraphFormat().clearFormatting();
        builder.getFont().clearFormatting();
    }

    /** 渲染修订记录表格。 */
    private void renderRevisionHistory(RevisionHistoryNode history, RenderContext context) throws Exception {
        renderSimpleHeading("修订记录", context);
        DocumentBuilder builder = context.getBuilder();
        builder.startTable();
        writeTableTextRow(builder, "版本", "日期", "说明", "修订人");
        for (RevisionHistoryNode.RevisionRecord record : history.getRecords()) {
            writeTableTextRow(builder, record.getVersion(), record.getDate(), record.getDescription(), record.getAuthor());
        }
        builder.endTable();
        builder.writeln();
    }

    /** 渲染审批或签署页面。 */
    private void renderApprovalPage(ApprovalPageNode approvalPage, RenderContext context) throws Exception {
        renderSimpleHeading("签署页", context);
        DocumentBuilder builder = context.getBuilder();
        builder.startTable();
        writeTableTextRow(builder, "角色", "签署人", "日期");
        for (ApprovalPageNode.ApprovalRecord record : approvalPage.getRecords()) {
            writeTableTextRow(builder, record.getRole(), record.getPerson(), record.getDate());
        }
        builder.endTable();
        builder.writeln();
    }

    /** 使用一级标题样式写入前置页面的简单标题。 */
    private void renderSimpleHeading(String text, RenderContext context) throws Exception {
        ParagraphNode heading = new ParagraphNode();
        heading.setStyleName("Heading1");
        applyParagraphStyle(heading, context.getBuilder());
        // 前置页标题用于文档导航，不参与正文 Heading1 至 Heading9 的章节编号。
        context.getBuilder().getListFormat().removeNumbers();
        context.getBuilder().writeln(text);
        context.getBuilder().getParagraphFormat().clearFormatting();
        context.getBuilder().getFont().clearFormatting();
    }

    /** 向当前表格写入一行文本单元格。 */
    private void writeTableTextRow(DocumentBuilder builder, String... values) throws Exception {
        for (String value : values) {
            builder.insertCell();
            builder.getCellFormat().getBorders().setLineStyle(LineStyle.SINGLE);
            builder.write(value);
        }
        builder.endRow();
    }

    /**
     * 为模块正文创建独立 Section，并把该 Section 的页码重启为配置值。
     */
    private void prepareModuleSection(DocumentNode document, RenderContext context) throws Exception {
        if (document.getSections().isEmpty()) {
            return;
        }
        DocumentBuilder builder = context.getBuilder();
        if (document.isTableOfContentsEnabled()) {
            builder.insertBreak(BreakType.SECTION_BREAK_NEW_PAGE);
        }
        configureModuleSection(document, builder, true);
        builder.getParagraphFormat().clearFormatting();
        builder.getFont().clearFormatting();
    }

    /** 为当前模块 Section 配置可选页眉、正文页脚和页码规则。 */
    private void configureModuleSection(DocumentNode document, DocumentBuilder builder,
                                        boolean restartPageNumbering) throws Exception {
        clearHeaderFooter(builder.getCurrentSection());
        builder.getPageSetup().setPageNumberStyle(NumberStyle.ARABIC);
        builder.getPageSetup().setRestartPageNumbering(restartPageNumbering);
        if (restartPageNumbering) {
            builder.getPageSetup().setPageStartingNumber(document.getModulePageNumberStart());
        }
        writeHeaderFooter(builder, document.getHeaderText(), document.getFooterText());
    }

    /** 删除 Section 继承的页眉页脚关系，确保各分节互不影响。 */
    private void clearHeaderFooter(com.aspose.words.Section section) throws Exception {
        section.getHeadersFooters().linkToPrevious(false);
        section.clearHeadersFooters();
    }

    /** 根据文本向当前 Section 写入页眉与页脚。 */
    private void writeHeaderFooter(DocumentBuilder builder, String headerText, String footerText) throws Exception {
        com.aspose.words.Section currentSection = builder.getCurrentSection();
        if (headerText != null && !headerText.trim().isEmpty()) {
            builder.moveToHeaderFooter(HeaderFooterType.HEADER_PRIMARY);
            builder.getParagraphFormat().setAlignment(ParagraphAlignment.CENTER);
            builder.write(headerText);
        }
        if (footerText != null && !footerText.trim().isEmpty()) {
            builder.moveTo(currentSection.getBody().getLastParagraph());
            builder.moveToHeaderFooter(HeaderFooterType.FOOTER_PRIMARY);
            builder.getParagraphFormat().setAlignment(ParagraphAlignment.CENTER);
            writeTextWithPageField(footerText, builder);
        }
        builder.moveTo(currentSection.getBody().getLastParagraph());
    }

    /** 写入普通文本，并将 PAGE 标记替换为页码域。 */
    private void writeTextWithPageField(String text, DocumentBuilder builder) throws Exception {
        String marker = "PAGE";
        int start = text.indexOf(marker);
        if (start < 0) {
            builder.write(text);
            return;
        }
        builder.write(text.substring(0, start));
        builder.insertField(marker, "");
        builder.write(text.substring(start + marker.length()));
    }

    /**
     * 标记目录、目录页码引用和页脚页码域需要由文档阅读器重新计算。
     *
     * <p>分页结果会受阅读器实际字体度量影响，目录和页脚中的缓存页码可能与最终分页不同。
     * 将这些动态域标记为 dirty 后，Word/WPS 可在打开文档时依据实际分页重新计算，同时
     * 避免在未授权 Aspose 环境中强制分页布局可能造成的评估版内容截断。</p>
     *
     * @param document Aspose 文档对象
     */
    private void markDynamicFieldsDirty(Document document) throws Exception {
        for (Field field : document.getRange().getFields()) {
            if (field.getType() == FieldType.FIELD_PAGE
                    || field.getType() == FieldType.FIELD_PAGE_REF
                    || field.getType() == FieldType.FIELD_TOC) {
                field.isDirty(true);
            }
        }
    }

    /** 在独立 Section 中插入目录域并设置目录专属页脚。 */
    private void renderTableOfContents(DocumentNode document, RenderContext context) throws Exception {
        if (!document.isTableOfContentsEnabled()) {
            return;
        }
        DocumentBuilder builder = context.getBuilder();
        clearHeaderFooter(builder.getCurrentSection());
        builder.getPageSetup().setRestartPageNumbering(true);
        builder.getPageSetup().setPageStartingNumber(1);
        builder.getPageSetup().setPageNumberStyle(NumberStyle.UPPERCASE_ROMAN);
        writeHeaderFooter(builder, null, document.getTableOfContentsFooterText());
        applyTableOfContentsTitleStyle(builder);
        builder.write(document.getTableOfContentsTitle());
        builder.writeln();
        prepareTableOfContentsField(builder);
        builder.insertTableOfContents("\\o \"1-" + document.getTableOfContentsDepth() + "\" \\h \\z \\u");
        builder.writeln();
        builder.getParagraphFormat().clearFormatting();
        builder.getFont().clearFormatting();
    }

    /**
     * 按标准层级配置目录标题和一至九级目录条目。
     *
     * <p>目录标题居中显示；一级条目顶格，二级至九级条目每级递进两个汉字宽度。目录
     * 页码使用的右对齐制表位与点引导线由目录域单独维护，不受此处段落缩进设置影响。</p>
     *
     * @param document 正在生成的 Aspose 文档
     */
    private void configureTableOfContentsStyles(Document document) {
        ParagraphStyle entryStyle = styleRegistry.getParagraphStyle(DEFAULT_PARAGRAPH_STYLE);
        for (int levelIndex = 0; levelIndex < 9; levelIndex++) {
            Style tableOfContentsStyle = document.getStyles().getByStyleIdentifier(
                    StyleIdentifier.TOC_1 + levelIndex);
            if (tableOfContentsStyle == null) {
                continue;
            }
            tableOfContentsStyle.getParagraphFormat().setAlignment(ParagraphAlignment.LEFT);
            tableOfContentsStyle.getParagraphFormat().setLeftIndent(levelIndex * TOC_LEVEL_INDENT_POINTS);
            tableOfContentsStyle.getParagraphFormat().setRightIndent(0);
            tableOfContentsStyle.getParagraphFormat().setFirstLineIndent(0);
            tableOfContentsStyle.getParagraphFormat().setCharacterUnitFirstLineIndent(0);
            if (entryStyle != null) {
                tableOfContentsStyle.getFont().setNameAscii(entryStyle.getAsciiFontFamily());
                tableOfContentsStyle.getFont().setNameFarEast(entryStyle.getFarEastFontFamily());
                tableOfContentsStyle.getFont().setSize(entryStyle.getFontSize());
                tableOfContentsStyle.getFont().setBold(false);
            }
        }
    }

    /**
     * 为目录页标题应用居中且无缩进的专用样式。
     *
     * @param builder 当前 Aspose 文档构建器
     * @throws Exception 当目录标题样式无法应用时抛出
     */
    private void applyTableOfContentsTitleStyle(DocumentBuilder builder) throws Exception {
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.TITLE);
        builder.getParagraphFormat().setAlignment(ParagraphAlignment.CENTER);
        builder.getParagraphFormat().setLeftIndent(0);
        builder.getParagraphFormat().setRightIndent(0);
        builder.getParagraphFormat().setCharacterUnitFirstLineIndent(0);
        builder.getParagraphFormat().setOutlineLevel(OutlineLevel.BODY_TEXT);
        builder.getListFormat().removeNumbers();
        ParagraphStyle titleStyle = styleRegistry.getParagraphStyle("Title");
        if (titleStyle != null) {
            builder.getFont().setNameAscii(titleStyle.getAsciiFontFamily());
            builder.getFont().setNameFarEast(titleStyle.getFarEastFontFamily());
            builder.getFont().setSize(Math.min(titleStyle.getFontSize(), 16));
            builder.getFont().setBold(false);
        }
    }

    /**
     * 将目录域插入位置恢复为普通正文段落。
     *
     * <p>目录标题使用居中显示的 {@code Title} 样式；若不在插入目录域前清除该格式，
     * 目录域的结束段落可能继承标题格式并被目录更新逻辑识别为大纲条目。</p>
     *
     * @param builder 当前 Aspose 文档构建器
     * @throws Exception 当普通段落样式无法应用时抛出
     */
    private void prepareTableOfContentsField(DocumentBuilder builder) throws Exception {
        builder.getParagraphFormat().clearFormatting();
        builder.getFont().clearFormatting();
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.getParagraphFormat().setOutlineLevel(OutlineLevel.BODY_TEXT);
        builder.getListFormat().removeNumbers();
    }

    /**
     * 将目录域生成的条目规范为统一的常规字体和标准缩进。
     *
     * <p>更新目录域时，Word/Aspose 可能复制源标题的加粗等直接格式。此处仅处理
     * TOC1 至 TOC9 段落，使目录条目统一采用当前样式画像的正文字体；超链接、点引导线和
     * 右侧页码域仍由原目录域保留。</p>
     *
     * @param document 已更新目录域的 Aspose 文档
     */
    private void normalizeTableOfContentsEntries(Document document) {
        ParagraphStyle entryStyle = styleRegistry.getParagraphStyle(DEFAULT_PARAGRAPH_STYLE);
        NodeCollection paragraphs = document.getChildNodes(NodeType.PARAGRAPH, true);
        for (int index = 0; index < paragraphs.getCount(); index++) {
            Paragraph paragraph = (Paragraph) paragraphs.get(index);
            int levelIndex = toTableOfContentsLevelIndex(paragraph.getParagraphFormat().getStyleIdentifier());
            if (levelIndex < 0) {
                continue;
            }
            paragraph.getParagraphFormat().setAlignment(ParagraphAlignment.LEFT);
            paragraph.getParagraphFormat().setLeftIndent(levelIndex * TOC_LEVEL_INDENT_POINTS);
            paragraph.getParagraphFormat().setRightIndent(0);
            paragraph.getParagraphFormat().setFirstLineIndent(0);
            paragraph.getParagraphFormat().setCharacterUnitFirstLineIndent(0);
            for (Run run : paragraph.getRuns()) {
                if (entryStyle != null) {
                    run.getFont().setNameAscii(entryStyle.getAsciiFontFamily());
                    run.getFont().setNameFarEast(entryStyle.getFarEastFontFamily());
                    run.getFont().setSize(entryStyle.getFontSize());
                }
                run.getFont().setBold(false);
                run.getFont().setItalic(false);
                run.getFont().setUnderline(Underline.NONE);
            }
        }
    }

    /**
     * 将 Aspose 目录样式标识转换为从零开始的目录层级索引。
     *
     * @param styleIdentifier 当前段落的 Aspose 样式标识
     * @return 目录层级索引；不是 TOC1 至 TOC9 时返回 {@code -1}
     */
    private int toTableOfContentsLevelIndex(int styleIdentifier) {
        int levelIndex = styleIdentifier - StyleIdentifier.TOC_1;
        return levelIndex >= 0 && levelIndex < 9 ? levelIndex : -1;
    }

    /** 按块级节点顺序渲染章节内容。 */
    private void renderSection(SectionNode section, RenderContext context) throws Exception {
        for (DocxBlock block : section.getBlocks()) {
            if (block instanceof ParagraphNode) {
                renderParagraph((ParagraphNode) block, context);
            } else if (block instanceof CaptionNode) {
                endActiveList(context);
                renderCaption((CaptionNode) block, context);
            } else if (block instanceof TableNode) {
                endActiveList(context);
                renderTable((TableNode) block, context);
            } else if (block instanceof PageBreakNode) {
                endActiveList(context);
                context.getBuilder().insertBreak(BreakType.PAGE_BREAK);
            }
        }
        endActiveList(context);
    }

    /** 渲染段落样式、列表格式和行内节点。 */
    private void renderParagraph(ParagraphNode paragraph, RenderContext context) throws Exception {
        DocumentBuilder builder = context.getBuilder();
        String styleName = resolveStyleName(paragraph);
        if (paragraph.getListType() == ParagraphListType.NONE) {
            endActiveList(context);
        }
        applyParagraphStyle(paragraph, builder);
        applyListFormat(paragraph, context);
        applyHeadingNumberingIfNeeded(styleName, context);
        for (DocxInline inline : paragraph.getInlines()) {
            renderInline(inline, context);
        }
        builder.writeln();
        builder.getParagraphFormat().clearFormatting();
        builder.getFont().clearFormatting();
    }

    /** 为段落绑定或切换连续列表对象。 */
    private void applyListFormat(ParagraphNode paragraph, RenderContext context) {
        DocumentBuilder builder = context.getBuilder();
        ParagraphListType listType = paragraph.getListType();
        if (listType == ParagraphListType.NONE) {
            return;
        }
        if (listType == context.getActiveListType() && context.getActiveList() != null) {
            builder.getListFormat().setList(context.getActiveList());
            return;
        }
        endActiveList(context);
        if (listType == ParagraphListType.BULLET) {
            builder.getListFormat().applyBulletDefault();
        } else {
            builder.getListFormat().applyNumberDefault();
        }
        context.setActiveListType(listType);
        context.setActiveList(builder.getListFormat().getList());
    }

    /**
     * 结束当前连续列表，避免后续普通块意外延续其编号或项目符号。
     *
     * @param context 当前渲染上下文
     */
    private void endActiveList(RenderContext context) {
        if (context.getActiveListType() == ParagraphListType.NONE) {
            return;
        }
        context.getBuilder().getListFormat().removeNumbers();
        context.setActiveListType(ParagraphListType.NONE);
        context.setActiveList(null);
    }

    /**
     * 将当前标题段落绑定到 Word 原生九级多级列表。
     *
     * <p>编号只作为列表属性写入 docx，不会以普通文本写入段落。Word 因此可以在用户
     * 调整标题层级、插入标题或删除标题后自动重算编号。</p>
     *
     * @param styleName 当前段落样式名称
     * @param context 当前渲染上下文
     */
    private void applyHeadingNumberingIfNeeded(String styleName, RenderContext context) {
        int level = toHeadingLevel(styleName);
        if (level == 0 || !isHeadingNumbered(styleName, context)) {
            return;
        }
        context.getBuilder().getListFormat().setList(context.getHeadingList());
        context.getBuilder().getListFormat().setListLevelNumber(level - 1);
    }

    /**
     * 判断指定样式是否需要写入 Word 原生标题编号。
     *
     * @param styleName 当前段落样式名称
     * @param context 当前渲染上下文
     * @return 标题编号已启用且样式为 Heading1 至 Heading9 时返回 {@code true}
     */
    private boolean isHeadingNumbered(String styleName, RenderContext context) {
        return context.isHeadingNumberingEnabled()
                && context.getHeadingList() != null
                && toHeadingLevel(styleName) > 0;
    }

    /**
     * 创建并配置 Heading1 至 Heading9 共用的 Word 原生多级编号。
     *
     * <p>九个级别均使用阿拉伯数字，第 {@code n} 级引用前 {@code n} 个编号占位符，
     * 因而分别显示为 {@code 1}、{@code 1.1} 直到九级编号。列表同时关联到内置标题
     * 样式，保证用户在 Word 中新建对应标题时可继承同一编号体系。</p>
     *
     * @param document 正在生成的 Aspose 文档
     * @return 已关联标题样式的九级多级列表
     */
    private List configureHeadingNumbering(Document document) {
        List headingList = document.getLists().add(ListTemplate.OUTLINE_HEADINGS_NUMBERS);
        for (int levelIndex = 0; levelIndex < 9; levelIndex++) {
            ListLevel listLevel = headingList.getListLevels().get(levelIndex);
            listLevel.setStartAt(1);
            listLevel.setNumberStyle(NumberStyle.ARABIC);
            listLevel.setNumberFormat(createHeadingNumberFormat(levelIndex));
            listLevel.setRestartAfterLevel(levelIndex - 1);
            configureHeadingListLevelLayout(listLevel);

            Style headingStyle = document.getStyles().getByStyleIdentifier(
                    StyleIdentifier.HEADING_1 + levelIndex);
            if (headingStyle != null) {
                headingStyle.getListFormat().setList(headingList);
                headingStyle.getListFormat().setListLevelNumber(levelIndex);
                listLevel.setLinkedStyle(headingStyle);
            }
        }
        return headingList;
    }

    /**
     * 统一标题多级列表的编号和正文左边界。
     *
     * <p>Aspose 的内置大纲模板会随级别增加左缩进。标题层级表达由编号本身承担，
     * 因此此处让九级标题均从页边距开始，并使用空格分隔编号与标题正文。</p>
     *
     * @param listLevel 待配置的标题列表级别
     */
    private void configureHeadingListLevelLayout(ListLevel listLevel) {
        listLevel.setAlignment(ListLevelAlignment.LEFT);
        listLevel.setNumberPosition(0);
        listLevel.setTextPosition(0);
        listLevel.setTabPosition(0);
        listLevel.setTrailingCharacter(ListTrailingCharacter.SPACE);
    }

    /**
     * 创建一个引用当前级及所有父级的 Word 列表编号格式。
     *
     * @param levelIndex 从 {@code 0} 开始的标题级别索引
     * @return Word 列表编号格式，例如 {@code \u0000.\u0001}
     */
    private String createHeadingNumberFormat(int levelIndex) {
        StringBuilder numberFormat = new StringBuilder();
        for (int index = 0; index <= levelIndex; index++) {
            if (index > 0) {
                numberFormat.append('.');
            }
            numberFormat.append((char) index);
        }
        return numberFormat.toString();
    }

    /** 渲染题注并记录可交叉引用的题注编号。 */
    private void renderCaption(CaptionNode caption, RenderContext context) throws Exception {
        DocumentBuilder builder = context.getBuilder();
        ParagraphNode styleCarrier = new ParagraphNode();
        styleCarrier.setStyleName(caption.getStyleName());
        applyParagraphStyle(styleCarrier, builder);
        int number = nextCaptionNumber(caption, context);
        if (caption.getId() != null && !caption.getId().trim().isEmpty()) {
            context.rememberCaptionNumber(captionKey(caption.getType(), caption.getId()), number);
        }
        builder.write(formatCaptionText(caption, number));
        builder.writeln();
        builder.getParagraphFormat().clearFormatting();
        builder.getFont().clearFormatting();
    }

    /** 根据题注类型获取下一个顺序编号。 */
    private int nextCaptionNumber(CaptionNode caption, RenderContext context) {
        if (caption.getType() == CaptionType.TABLE) {
            return context.nextTableCaptionNumber();
        }
        return context.nextFigureCaptionNumber();
    }

    /** 组装包含标签、序号和正文的题注文本。 */
    private String formatCaptionText(CaptionNode caption, int number) {
        return caption.getType().getLabel() + " " + number + " " + caption.getText();
    }

    /** 生成题注交叉引用使用的内部键。 */
    private String captionKey(CaptionType type, String captionId) {
        return type.name() + ":" + captionId;
    }

    /** 将注册的段落样式映射到当前 Aspose 写入器。 */
    private void applyParagraphStyle(ParagraphNode paragraph, DocumentBuilder builder) throws Exception {
        String styleName = resolveStyleName(paragraph);
        Integer styleIdentifier = toStyleIdentifier(styleName);
        if (styleIdentifier != null) {
            builder.getParagraphFormat().setStyleIdentifier(styleIdentifier);
        }
        if ("Title".equals(styleName) || "Subtitle".equals(styleName)) {
            // 文档题名不是章节标题，不应被按大纲级别生成的目录域当作条目收录。
            builder.getParagraphFormat().setOutlineLevel(OutlineLevel.BODY_TEXT);
        }
        ParagraphStyle style = styleRegistry.getParagraphStyle(styleName);
        if (style != null) {
            builder.getFont().setNameAscii(style.getAsciiFontFamily());
            builder.getFont().setNameFarEast(style.getFarEastFontFamily());
            builder.getFont().setSize(style.getFontSize());
            builder.getFont().setBold(style.isBold());
            builder.getFont().setItalic(style.isItalic());
            builder.getFont().setUnderline(style.isUnderline() ? Underline.SINGLE : Underline.NONE);
            builder.getParagraphFormat().setAlignment(toAsposeAlignment(style.getAlignment()));
            builder.getParagraphFormat().setLeftIndent(style.getLeftIndentPoints());
            builder.getParagraphFormat().setRightIndent(style.getRightIndentPoints());
            builder.getParagraphFormat().setCharacterUnitFirstLineIndent(style.getCharacterUnitFirstLineIndent());
            builder.getParagraphFormat().setLineSpacingRule(toAsposeLineSpacingRule(style.getLineSpacingRule()));
            builder.getParagraphFormat().setLineSpacing(style.getLineSpacing());
            builder.getParagraphFormat().setSpaceBefore(style.getSpaceBeforePoints());
            builder.getParagraphFormat().setSpaceAfter(style.getSpaceAfterPoints());
            builder.getParagraphFormat().setKeepWithNext(style.isKeepWithNext());
            builder.getParagraphFormat().setKeepTogether(style.isKeepTogether());
        }
    }

    /**
     * 清除 Word 内置标题样式可能继承的斜体和下划线。
     *
     * <p>即使调用方没有显式设置文字效果，Word 的内置样式或宿主模板仍可能提供这两项
     * 格式。此处同时规范 Title、Subtitle 和 Heading1 至 Heading9，保证所有标准标题
     * 在样式层面保持非斜体、无下划线。</p>
     *
     * @param document Aspose 文档
     */
    private void clearHeadingTextEffects(Document document) {
        int[] styleIdentifiers = {
                StyleIdentifier.TITLE,
                StyleIdentifier.SUBTITLE,
                StyleIdentifier.HEADING_1,
                StyleIdentifier.HEADING_2,
                StyleIdentifier.HEADING_3,
                StyleIdentifier.HEADING_4,
                StyleIdentifier.HEADING_5,
                StyleIdentifier.HEADING_6,
                StyleIdentifier.HEADING_7,
                StyleIdentifier.HEADING_8,
                StyleIdentifier.HEADING_9
        };
        for (int styleIdentifier : styleIdentifiers) {
            Style style = document.getStyles().getByStyleIdentifier(styleIdentifier);
            if (style != null) {
                style.getFont().setItalic(false);
                style.getFont().setUnderline(Underline.NONE);
            }
        }
    }

    /** 解析段落最终使用的样式名称。 */
    private String resolveStyleName(ParagraphNode paragraph) {
        if (paragraph.getStyleName() == null || paragraph.getStyleName().trim().isEmpty()) {
            return DEFAULT_PARAGRAPH_STYLE;
        }
        return paragraph.getStyleName();
    }

    /** 将封装层段落对齐枚举转换为 Aspose 常量。 */
    private int toAsposeAlignment(cn.bugstack.office.docx.style.DocxParagraphAlignment alignment) {
        if (alignment == null) {
            return ParagraphAlignment.LEFT;
        }
        switch (alignment) {
            case CENTER:
                return ParagraphAlignment.CENTER;
            case RIGHT:
                return ParagraphAlignment.RIGHT;
            case JUSTIFY:
                return ParagraphAlignment.JUSTIFY;
            case LEFT:
            default:
                return ParagraphAlignment.LEFT;
        }
    }

    /** 将封装层行距规则转换为 Aspose 常量。 */
    private int toAsposeLineSpacingRule(cn.bugstack.office.docx.style.DocxLineSpacingRule rule) {
        if (rule == null) {
            return LineSpacingRule.MULTIPLE;
        }
        switch (rule) {
            case AT_LEAST:
                return LineSpacingRule.AT_LEAST;
            case EXACTLY:
                return LineSpacingRule.EXACTLY;
            case MULTIPLE:
            default:
                return LineSpacingRule.MULTIPLE;
        }
    }

    /** 将内置标题样式名称转换为 Aspose 样式标识。 */
    private Integer toStyleIdentifier(String styleName) {
        if ("Normal".equals(styleName)) {
            return StyleIdentifier.NORMAL;
        }
        if ("Title".equals(styleName)) {
            return StyleIdentifier.TITLE;
        }
        if ("Subtitle".equals(styleName)) {
            return StyleIdentifier.SUBTITLE;
        }
        if ("Heading1".equals(styleName)) {
            return StyleIdentifier.HEADING_1;
        }
        if ("Heading2".equals(styleName)) {
            return StyleIdentifier.HEADING_2;
        }
        if ("Heading3".equals(styleName)) {
            return StyleIdentifier.HEADING_3;
        }
        if ("Heading4".equals(styleName)) {
            return StyleIdentifier.HEADING_4;
        }
        if ("Heading5".equals(styleName)) {
            return StyleIdentifier.HEADING_5;
        }
        if ("Heading6".equals(styleName)) {
            return StyleIdentifier.HEADING_6;
        }
        if ("Heading7".equals(styleName)) {
            return StyleIdentifier.HEADING_7;
        }
        if ("Heading8".equals(styleName)) {
            return StyleIdentifier.HEADING_8;
        }
        if ("Heading9".equals(styleName)) {
            return StyleIdentifier.HEADING_9;
        }
        if ("BodyText".equals(styleName)) {
            return StyleIdentifier.BODY_TEXT;
        }
        if ("Caption".equals(styleName) || "ImageCaption".equals(styleName)) {
            return StyleIdentifier.CAPTION;
        }
        return null;
    }

    /** 从样式名称解析一至九级标题层级。 */
    private int toHeadingLevel(String styleName) {
        if (styleName != null && styleName.startsWith("Heading")) {
            try {
                int level = Integer.parseInt(styleName.substring("Heading".length()));
                return level >= 1 && level <= 9 ? level : 0;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** 按行内节点类型渲染文本、图片、Visio 或题注引用。 */
    private void renderInline(DocxInline inline, RenderContext context) throws Exception {
        DocumentBuilder builder = context.getBuilder();
        if (inline instanceof TextRunInline) {
            builder.write(((TextRunInline) inline).getText());
        } else if (inline instanceof CaptionRefInline) {
            renderCaptionReference((CaptionRefInline) inline, context);
        } else if (inline instanceof ImageInline) {
            insertImage((ImageInline) inline, builder);
        } else if (inline instanceof VisioInline) {
            insertVisio((VisioInline) inline, builder);
        }
    }

    /**
     * 按预览图片或可编辑 OLE 对象模式插入 Visio。
     *
     * @param visio Visio 行内节点
     * @param builder Aspose Word 构建器
     * @throws Exception 当预览图或 OLE 对象无法写入文档时抛出
     */
    private void insertVisio(VisioInline visio, DocumentBuilder builder) throws Exception {
        builder.getParagraphFormat().setAlignment(ParagraphAlignment.CENTER);
        if (!visio.isEmbedded()) {
            builder.insertImage(visio.getPreviewSource());
            return;
        }
        try (InputStream preview = Files.newInputStream(Path.of(visio.getPreviewSource()))) {
            Shape shape = builder.insertOleObject(visio.getVsdxSource(), false, false, preview);
            double[] displaySize = calculateAspectFitSize(Path.of(visio.getPreviewSource()),
                    visio.getWidthPoints(), visio.getHeightPoints());
            if (displaySize != null) {
                shape.setAspectRatioLocked(true);
                shape.setWidth(displaySize[0]);
                shape.setHeight(displaySize[1]);
            }
        }
    }

    /**
     * 按预览图原始纵横比计算落入目标边界框的最大显示尺寸。
     *
     * <p>宽度和高度同时存在时作为最大边界，而不是强制拉伸尺寸；只设置一个方向时，
     * 另一个方向按原始比例自动计算。这样可避免 OLE 预览图在 Word 中被压扁或拉长。</p>
     *
     * @param previewPath PNG 预览图路径
     * @param maximumWidthPoints 最大显示宽度，单位为 point；为空时不限制宽度
     * @param maximumHeightPoints 最大显示高度，单位为 point；为空时不限制高度
     * @return 等比例显示宽高，依次为宽度和高度；未指定边界时返回 {@code null}
     * @throws IOException 当预览图无法读取或格式不受支持时抛出
     */
    private double[] calculateAspectFitSize(Path previewPath, Double maximumWidthPoints,
                                            Double maximumHeightPoints) throws IOException {
        if (maximumWidthPoints == null && maximumHeightPoints == null) {
            return null;
        }
        BufferedImage previewImage = ImageIO.read(previewPath.toFile());
        if (previewImage == null || previewImage.getWidth() <= 0 || previewImage.getHeight() <= 0) {
            throw new IOException("Unsupported Visio preview image: " + previewPath);
        }
        double sourceWidth = previewImage.getWidth();
        double sourceHeight = previewImage.getHeight();
        double scaleByWidth = maximumWidthPoints == null
                ? Double.POSITIVE_INFINITY : maximumWidthPoints / sourceWidth;
        double scaleByHeight = maximumHeightPoints == null
                ? Double.POSITIVE_INFINITY : maximumHeightPoints / sourceHeight;
        double scale = Math.min(scaleByWidth, scaleByHeight);
        return new double[]{sourceWidth * scale, sourceHeight * scale};
    }

    /** 写入已记录题注的交叉引用文本。 */
    private void renderCaptionReference(CaptionRefInline reference, RenderContext context) throws Exception {
        Integer number = context.findCaptionNumber(captionKey(reference.getType(), reference.getCaptionId()));
        context.getBuilder().write(reference.getType().getLabel() + " " + (number == null ? "?" : number));
    }

    /** 按可选目标尺寸插入图片。 */
    private void insertImage(ImageInline image, DocumentBuilder builder) throws Exception {
        if (image.getWidthPoints() != null && image.getHeightPoints() != null) {
            builder.insertImage(image.getSource(), image.getWidthPoints(), image.getHeightPoints());
            return;
        }
        Shape shape = builder.insertImage(image.getSource());
        if (image.getWidthPoints() != null) {
            shape.setWidth(image.getWidthPoints());
        }
        if (image.getHeightPoints() != null) {
            shape.setHeight(image.getHeightPoints());
        }
    }

    /** 渲染表格行、单元格、边框与列宽。 */
    private void renderTable(TableNode table, RenderContext context) throws Exception {
        DocumentBuilder builder = context.getBuilder();
        TableStyle tableStyle = styleRegistry.getTableStyle(table.getStyleName());
        builder.startTable();
        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            TableRowNode row = table.getRows().get(rowIndex);
            int columnIndex = 0;
            for (TableCellNode cell : row.getCells()) {
                builder.insertCell();
                applyTableCellStyle(tableStyle, table, rowIndex, columnIndex, cell.getColumnSpan(), builder);
                builder.getCellFormat().setHorizontalMerge(cell.getColumnSpan() > 1 ? CellMerge.FIRST : CellMerge.NONE);
                builder.getCellFormat().setVerticalMerge(toAsposeCellMerge(cell.getVerticalMerge()));
                builder.getCellFormat().setVerticalAlignment(toAsposeCellVerticalAlignment(cell.getVerticalAlignment()));
                renderCell(cell, context);
                columnIndex += cell.getColumnSpan();
                for (int merged = 1; merged < cell.getColumnSpan(); merged++) {
                    builder.insertCell();
                    applyMergedFollowerCellStyle(tableStyle, table, rowIndex, columnIndex - cell.getColumnSpan() + merged, builder);
                }
            }
            builder.endRow();
        }
        Table renderedTable = builder.endTable();
        if (tableStyle != null && tableStyle.isHeaderBold() && renderedTable.getFirstRow() != null) {
            renderedTable.getFirstRow().getRowFormat().setHeadingFormat(true);
        }
        for (Row row : renderedTable.getRows()) {
            row.getRowFormat().setAllowBreakAcrossPages(false);
        }
        if (table.getColumnWidths().length > 0) {
            renderedTable.setAllowAutoFit(false);
        }
        builder.writeln();
        builder.getCellFormat().clearFormatting();
        builder.getFont().clearFormatting();
    }

    /** 应用普通表格单元格的边框、对齐与表头样式。 */
    private void applyTableCellStyle(TableStyle tableStyle, TableNode table, int rowIndex, int columnIndex,
                                     int columnSpan, DocumentBuilder builder) throws Exception {
        builder.getCellFormat().clearFormatting();
        if (tableStyle == null) {
            builder.getCellFormat().setHorizontalMerge(CellMerge.NONE);
        } else {
            if (tableStyle.isBordered()) {
                builder.getCellFormat().getBorders().setLineStyle(LineStyle.SINGLE);
                builder.getCellFormat().getBorders().setLineWidth(0.5);
            }
            builder.getFont().setBold(tableStyle.isHeaderBold() && rowIndex == 0);
        }
        double width = sumColumnWidths(table, columnIndex, columnSpan);
        if (width > 0) {
            builder.getCellFormat().setWidth(width);
            builder.getCellFormat().setPreferredWidth(PreferredWidth.fromPoints(width));
        }
    }

    /** 应用纵向合并后续单元格的边框与尺寸样式。 */
    private void applyMergedFollowerCellStyle(TableStyle tableStyle, TableNode table, int rowIndex, int columnIndex,
                                              DocumentBuilder builder) throws Exception {
        builder.getCellFormat().clearFormatting();
        if (tableStyle != null && tableStyle.isBordered()) {
            builder.getCellFormat().getBorders().setLineStyle(LineStyle.SINGLE);
            builder.getCellFormat().getBorders().setLineWidth(0.5);
        }
        builder.getFont().setBold(tableStyle != null && tableStyle.isHeaderBold() && rowIndex == 0);
        builder.getCellFormat().setHorizontalMerge(CellMerge.PREVIOUS);
        builder.getCellFormat().setVerticalMerge(CellMerge.NONE);
        double width = sumColumnWidths(table, columnIndex, 1);
        if (width > 0) {
            builder.getCellFormat().setWidth(width);
            builder.getCellFormat().setPreferredWidth(PreferredWidth.fromPoints(width));
        }
    }

    /** 计算横向合并单元格覆盖列的总宽度。 */
    private double sumColumnWidths(TableNode table, int columnIndex, int columnSpan) {
        double[] widths = table.getColumnWidths();
        if (widths.length == 0 || columnIndex >= widths.length) {
            return 0;
        }
        double width = 0;
        for (int i = columnIndex; i < columnIndex + columnSpan && i < widths.length; i++) {
            width += widths[i];
        }
        return width;
    }

    /** 将封装层纸张规格转换为 Aspose 常量。 */
    private int toAsposePaperSize(DocxPaperSize paperSize) {
        if (paperSize == DocxPaperSize.A3) {
            return PaperSize.A3;
        }
        if (paperSize == DocxPaperSize.LETTER) {
            return PaperSize.LETTER;
        }
        return PaperSize.A4;
    }

    /** 将封装层页面方向转换为 Aspose 常量。 */
    private int toAsposeOrientation(DocxPageOrientation orientation) {
        if (orientation == DocxPageOrientation.LANDSCAPE) {
            return Orientation.LANDSCAPE;
        }
        return Orientation.PORTRAIT;
    }

    /** 将封装层纵向合并状态转换为 Aspose 常量。 */
    private int toAsposeCellMerge(TableVerticalMerge verticalMerge) {
        if (verticalMerge == TableVerticalMerge.FIRST) {
            return CellMerge.FIRST;
        }
        if (verticalMerge == TableVerticalMerge.PREVIOUS) {
            return CellMerge.PREVIOUS;
        }
        return CellMerge.NONE;
    }

    /** 将封装层单元格垂直对齐方式转换为 Aspose 常量。 */
    private int toAsposeCellVerticalAlignment(TableCellVerticalAlignment alignment) {
        if (alignment == TableCellVerticalAlignment.CENTER) {
            return CellVerticalAlignment.CENTER;
        }
        if (alignment == TableCellVerticalAlignment.BOTTOM) {
            return CellVerticalAlignment.BOTTOM;
        }
        return CellVerticalAlignment.TOP;
    }

    /** 渲染单元格内的段落及其他块级内容。 */
    private void renderCell(TableCellNode cell, RenderContext context) throws Exception {
        for (DocxBlock block : cell.getBlocks()) {
            if (block instanceof ParagraphNode) {
                ParagraphNode paragraph = (ParagraphNode) block;
                for (DocxInline inline : paragraph.getInlines()) {
                    renderInline(inline, context);
                }
            } else if (block instanceof CaptionNode) {
                renderCaption((CaptionNode) block, context);
            } else if (block instanceof TableNode) {
                renderTable((TableNode) block, context);
            }
        }
    }
}
