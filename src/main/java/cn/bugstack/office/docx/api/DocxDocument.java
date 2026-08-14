package cn.bugstack.office.docx.api;

import cn.bugstack.office.docx.builder.ApprovalPageBuilder;
import cn.bugstack.office.docx.builder.PageSetupBuilder;
import cn.bugstack.office.docx.builder.RevisionHistoryBuilder;
import cn.bugstack.office.docx.builder.SectionBuilder;
import cn.bugstack.office.docx.exception.DocxValidationException;
import cn.bugstack.office.docx.model.ApprovalPageNode;
import cn.bugstack.office.docx.model.CoverPageNode;
import cn.bugstack.office.docx.model.DocumentNode;
import cn.bugstack.office.docx.model.RevisionHistoryNode;
import cn.bugstack.office.docx.model.SectionNode;
import cn.bugstack.office.docx.model.TemplateCoverPageNode;
import cn.bugstack.office.docx.render.AsposeDocxRenderer;
import cn.bugstack.office.docx.render.DocxRenderer;
import cn.bugstack.office.docx.style.DefaultStyles;
import cn.bugstack.office.docx.style.ParagraphStyle;
import cn.bugstack.office.docx.style.StyleProfile;
import cn.bugstack.office.docx.style.StyleRegistry;
import cn.bugstack.office.docx.validate.DocxValidator;
import cn.bugstack.office.docx.validate.ValidationResult;

import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Docx 文档门面对象，也是创建文档的 fluent API 入口。
 *
 * <p>该类对外隐藏 Aspose 的 {@code DocumentBuilder}、节点游标和格式细节，
 * 对内维护一棵 {@link DocumentNode} 组件树。调用 {@link #save(Path)} 时会先校验
 * 内部结构，再委托渲染器输出 docx 文件。</p>
 */
public class DocxDocument {

    private final DocumentNode node = new DocumentNode();
    private StyleRegistry styleRegistry = DefaultStyles.createRegistry();

    private DocxDocument() {
    }

    /**
     * 创建一个空的 docx 文档构建入口。
     *
     * @return 新的文档门面对象
     */
    public static DocxDocument create() {
        return new DocxDocument();
    }

    /**
     * 启用内置标准样式。
     *
     * <p>当前会重置为 {@link DefaultStyles#createRegistry()} 提供的默认样式集合。</p>
     *
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument useDefaultStyles() {
        this.styleRegistry = DefaultStyles.createRegistry();
        return this;
    }

    /**
     * 使用指定的文档样式画像。
     *
     * <p>样式画像用于封装一整套文档标准。调用该方法会整体替换当前样式注册表。</p>
     *
     * @param profile 文档样式画像
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument useStyleProfile(StyleProfile profile) {
        this.styleRegistry = profile.createRegistry();
        return this;
    }

    /**
     * 启用 Word 原生标题自动编号。
     *
     * <p>渲染时会创建一个关联 Heading1 至 Heading9 的九级 Word 多级列表，编号不会
     * 以普通文本写入段落。因此在 Word 中插入、删除或调整标题层级后，编号可自动更新。</p>
     *
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument enableHeadingNumbering() {
        node.setHeadingNumberingEnabled(true);
        return this;
    }

    /**
     * 在文档开头插入目录。
     *
     * @param title 目录标题
     * @param depth 收录标题深度，范围为 1 到 9
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument tableOfContents(String title, int depth) {
        if (depth < 1 || depth > 9) {
            throw new IllegalArgumentException("toc depth must be between 1 and 9: " + depth);
        }
        node.setTableOfContentsEnabled(true);
        node.setTableOfContentsTitle(title);
        node.setTableOfContentsDepth(depth);
        return this;
    }

    /**
     * 设置文档页眉文本。
     *
     * @param text 页眉文本
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument header(String text) {
        node.setHeaderText(text);
        return this;
    }

    /**
     * 设置文档页脚文本。
     *
     * @param text 页脚文本；包含 {@code PAGE} 时渲染为当前页码域
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument footer(String text) {
        node.setFooterText(text);
        return this;
    }

    /**
     * 设置目录独立 Section 的页脚文本。
     *
     * @param text 目录页脚；包含 {@code PAGE} 时渲染为当前页码域
     * @return 当前文档对象
     */
    public DocxDocument tableOfContentsFooter(String text) {
        node.setTableOfContentsFooterText(text);
        return this;
    }

    /**
     * 设置模块正文 Section 的起始页码。
     *
     * @param pageNumber 起始页码，必须大于 0
     * @return 当前文档对象
     */
    public DocxDocument modulePageNumberStart(int pageNumber) {
        node.setModulePageNumberStart(pageNumber);
        return this;
    }

    /**
     * 设置文档元数据。
     *
     * @param title 文档标题
     * @param author 作者
     * @param subject 主题
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument metadata(String title, String author, String subject) {
        node.getMetadata().setTitle(title);
        node.getMetadata().setAuthor(author);
        node.getMetadata().setSubject(subject);
        return this;
    }

    /**
     * 配置页面设置。
     *
     * @param customizer 页面设置构建回调
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument pageSetup(Consumer<PageSetupBuilder> customizer) {
        customizer.accept(new PageSetupBuilder(node.getPageSetup()));
        return this;
    }

    /**
     * 添加 GJB 438C 文档封面。
     *
     * @param documentName 文档名称
     * @param projectName 项目名称
     * @param version 文档版本
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument cover(String documentName, String projectName, String version) {
        node.addFrontMatterBlock(new CoverPageNode(documentName, projectName, version));
        return this;
    }

    /**
     * 创建可动态写入段落、表格等内容的独立封面页。
     *
     * @return 动态封面页构建器
     */
    public SectionBuilder templateCover() {
        TemplateCoverPageNode cover = new TemplateCoverPageNode();
        node.addFrontMatterBlock(cover);
        return new SectionBuilder(this, cover);
    }

    /**
     * 添加修订记录页。
     *
     * @param customizer 修订记录构建回调
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument revisionHistory(Consumer<RevisionHistoryBuilder> customizer) {
        RevisionHistoryNode history = new RevisionHistoryNode();
        customizer.accept(new RevisionHistoryBuilder(history));
        node.addFrontMatterBlock(history);
        return this;
    }

    /**
     * 添加签署页。
     *
     * @param customizer 签署页构建回调
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument approvalPage(Consumer<ApprovalPageBuilder> customizer) {
        ApprovalPageNode approvalPage = new ApprovalPageNode();
        customizer.accept(new ApprovalPageBuilder(approvalPage));
        node.addFrontMatterBlock(approvalPage);
        return this;
    }

    /**
     * 新增一个 Section，并返回章节 Builder。
     *
     * @return 用于继续添加段落、表格等块级内容的章节 Builder
     */
    public SectionBuilder section() {
        SectionNode section = new SectionNode();
        node.addSection(section);
        return new SectionBuilder(this, section);
    }

    /**
     * 获取内部文档节点树。
     *
     * <p>主要供渲染器、校验器和测试使用；业务侧通常不需要直接操作该对象。</p>
     *
     * @return 文档根节点
     */
    public DocumentNode getNode() {
        return node;
    }

    /**
     * 获取当前文档使用的样式注册表。
     *
     * @return 样式注册表
     */
    public StyleRegistry getStyleRegistry() {
        return styleRegistry;
    }

    /**
     * 注册自定义段落样式。
     *
     * <p>注册后可以通过 {@code paragraph().style("样式名")} 使用该样式。</p>
     *
     * @param style 自定义段落样式
     * @return 当前文档对象，便于链式调用
     */
    public DocxDocument registerParagraphStyle(ParagraphStyle style) {
        styleRegistry.registerParagraphStyle(style);
        return this;
    }

    /**
     * 使用默认 Aspose 渲染器保存文档。
     *
     * @param outputPath 输出 docx 文件路径
     */
    public void save(String outputPath) {
        save(Path.of(outputPath), new AsposeDocxRenderer(styleRegistry));
    }

    /**
     * 使用默认 Aspose 渲染器保存文档。
     *
     * @param outputPath 输出 docx 文件路径
     */
    public void save(Path outputPath) {
        save(outputPath, new AsposeDocxRenderer(styleRegistry));
    }

    /**
     * 使用默认 Aspose 渲染器将文档输出为内存字节。
     *
     * <p>该方法适合 Web 下载、消息附件等调用场景。对于超大文档，优先使用
     * {@link #save(Path)} 以避免一次性占用大量堆内存。</p>
     *
     * @return 完整 docx 文件字节
     */
    public byte[] toByteArray() {
        ValidationResult result = new DocxValidator().validate(node);
        if (!result.isValid()) {
            String message = result.getMessages().stream().collect(Collectors.joining("; "));
            throw new DocxValidationException(message);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new AsposeDocxRenderer(styleRegistry).render(node, output);
        return output.toByteArray();
    }

    /**
     * 使用指定渲染器保存文档。
     *
     * <p>该方法会先执行 {@link DocxValidator} 校验。校验失败时不会调用渲染器，
     * 而是抛出 {@link DocxValidationException}。</p>
     *
     * @param outputPath 输出 docx 文件路径
     * @param renderer   文档渲染器
     * @throws DocxValidationException 当文档结构不合法时抛出
     */
    public void save(Path outputPath, DocxRenderer renderer) {
        ValidationResult result = new DocxValidator().validate(node);
        if (!result.isValid()) {
            String message = result.getMessages().stream().collect(Collectors.joining("; "));
            throw new DocxValidationException(message);
        }
        renderer.render(node, outputPath);
    }
}
