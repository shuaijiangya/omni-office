package cn.bugstack.office.docx.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文档根节点，组合多个 Section。
 */
public class DocumentNode implements DocxNode {

    private final List<DocxBlock> frontMatterBlocks = new ArrayList<>();
    private final List<SectionNode> sections = new ArrayList<>();
    /** 是否为标题自动生成编号。 */
    private boolean headingNumberingEnabled;
    /** 是否生成目录域。 */
    private boolean tableOfContentsEnabled;
    /** 目录显示标题。 */
    private String tableOfContentsTitle = "目录";
    /** 目录收录的最大标题层级。 */
    private int tableOfContentsDepth = 3;
    /** 文档页眉文本。 */
    private String headerText;
    /** 文档页脚文本。 */
    private String footerText;
    /** 目录独立 Section 使用的页脚文本。 */
    private String tableOfContentsFooterText;
    /** 模块正文 Section 的起始页码。 */
    private int modulePageNumberStart = 1;
    private final DocxMetadata metadata = new DocxMetadata();
    private final DocxPageSetup pageSetup = new DocxPageSetup();

    /**
     * 创建空的文档根节点。
     */
    public DocumentNode() {
    }

    /**
     * 追加一个章节节点。
     *
     * @param section 章节节点
     */
    public void addSection(SectionNode section) {
        sections.add(section);
    }

    /**
     * 追加文档前置页块。
     *
     * @param block 前置页块
     */
    public void addFrontMatterBlock(DocxBlock block) {
        frontMatterBlocks.add(block);
    }

    /**
     * 获取文档前置页块列表。
     *
     * @return 不可修改的前置页块列表
     */
    public List<DocxBlock> getFrontMatterBlocks() {
        return Collections.unmodifiableList(frontMatterBlocks);
    }

    /**
     * 获取文档中的章节列表。
     *
     * @return 不可修改的章节列表
     */
    public List<SectionNode> getSections() {
        return Collections.unmodifiableList(sections);
    }

    /**
     * 判断是否启用标题自动编号。
     *
     * @return 启用返回 {@code true}
     */
    public boolean isHeadingNumberingEnabled() {
        return headingNumberingEnabled;
    }

    /**
     * 设置是否启用标题自动编号。
     *
     * @param headingNumberingEnabled 是否启用标题自动编号
     */
    public void setHeadingNumberingEnabled(boolean headingNumberingEnabled) {
        this.headingNumberingEnabled = headingNumberingEnabled;
    }

    /**
     * 判断是否插入目录。
     *
     * @return 插入目录返回 {@code true}
     */
    public boolean isTableOfContentsEnabled() {
        return tableOfContentsEnabled;
    }

    /**
     * 设置是否插入目录。
     *
     * @param tableOfContentsEnabled 是否插入目录
     */
    public void setTableOfContentsEnabled(boolean tableOfContentsEnabled) {
        this.tableOfContentsEnabled = tableOfContentsEnabled;
    }

    /**
     * 获取目录标题。
     *
     * @return 目录标题
     */
    public String getTableOfContentsTitle() {
        return tableOfContentsTitle;
    }

    /**
     * 设置目录标题。
     *
     * @param tableOfContentsTitle 目录标题
     */
    public void setTableOfContentsTitle(String tableOfContentsTitle) {
        this.tableOfContentsTitle = tableOfContentsTitle;
    }

    /**
     * 获取目录收录标题深度。
     *
     * @return 标题深度，范围为 1 到 9
     */
    public int getTableOfContentsDepth() {
        return tableOfContentsDepth;
    }

    /**
     * 设置目录收录标题深度。
     *
     * @param tableOfContentsDepth 标题深度，范围为 1 到 9
     */
    public void setTableOfContentsDepth(int tableOfContentsDepth) {
        this.tableOfContentsDepth = tableOfContentsDepth;
    }

    /**
     * 获取页眉文本。
     *
     * @return 页眉文本；未设置时返回 {@code null}
     */
    public String getHeaderText() {
        return headerText;
    }

    /**
     * 设置页眉文本。
     *
     * @param headerText 页眉文本
     */
    public void setHeaderText(String headerText) {
        this.headerText = headerText;
    }

    /**
     * 获取页脚文本。
     *
     * @return 页脚文本；未设置时返回 {@code null}
     */
    public String getFooterText() {
        return footerText;
    }

    /**
     * 设置页脚文本。
     *
     * @param footerText 页脚文本
     */
    public void setFooterText(String footerText) {
        this.footerText = footerText;
    }

    /**
     * 获取目录独立 Section 的页脚文本。
     *
     * @return 目录页脚；未设置时返回 {@code null}
     */
    public String getTableOfContentsFooterText() {
        return tableOfContentsFooterText;
    }

    /**
     * 设置目录独立 Section 的页脚文本。
     *
     * @param tableOfContentsFooterText 目录页脚文本
     */
    public void setTableOfContentsFooterText(String tableOfContentsFooterText) {
        this.tableOfContentsFooterText = tableOfContentsFooterText;
    }

    /**
     * 获取模块正文 Section 的起始页码。
     *
     * @return 起始页码
     */
    public int getModulePageNumberStart() {
        return modulePageNumberStart;
    }

    /**
     * 设置模块正文 Section 的起始页码。
     *
     * @param modulePageNumberStart 起始页码，必须大于 0
     */
    public void setModulePageNumberStart(int modulePageNumberStart) {
        if (modulePageNumberStart < 1) {
            throw new IllegalArgumentException("module page number start must be greater than 0");
        }
        this.modulePageNumberStart = modulePageNumberStart;
    }

    /**
     * 获取文档元数据。
     *
     * @return 文档元数据
     */
    public DocxMetadata getMetadata() {
        return metadata;
    }

    /**
     * 获取页面设置。
     *
     * @return 页面设置
     */
    public DocxPageSetup getPageSetup() {
        return pageSetup;
    }
}
