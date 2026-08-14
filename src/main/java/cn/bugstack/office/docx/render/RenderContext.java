package cn.bugstack.office.docx.render;

import cn.bugstack.office.docx.model.ParagraphListType;
import cn.bugstack.office.docx.style.StyleRegistry;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.List;

import java.util.HashMap;
import java.util.Map;

/**
 * Aspose 渲染过程上下文。
 */
public class RenderContext {

    /** 正在写入的 Aspose 文档对象。 */
    private final Document document;
    /** Aspose 内容写入器。 */
    private final DocumentBuilder builder;
    /** 当前渲染使用的样式注册表。 */
    private final StyleRegistry styleRegistry;
    /** 当前图片题注序号。 */
    private int figureCaptionNumber;
    /** 当前表格题注序号。 */
    private int tableCaptionNumber;
    /** 是否为 Heading1 至 Heading9 应用 Word 原生多级编号。 */
    private boolean headingNumberingEnabled;
    /** Word 原生九级标题多级列表；未启用标题编号时为 {@code null}。 */
    private List headingList;
    /** 当前连续列表的类型；普通内容会将其重置为 {@link ParagraphListType#NONE}。 */
    private ParagraphListType activeListType = ParagraphListType.NONE;
    /** 当前连续列表对应的 Aspose 列表对象。 */
    private List activeList;
    /** 已写入文档的题注编号映射。 */
    private final Map<String, Integer> captionNumbers = new HashMap<>();

    /**
     * 创建渲染上下文。
     *
     * @param document Aspose 文档对象
     * @param builder Aspose 文档构建器
     * @param styleRegistry 样式注册表
     */
    public RenderContext(Document document, DocumentBuilder builder, StyleRegistry styleRegistry) {
        this.document = document;
        this.builder = builder;
        this.styleRegistry = styleRegistry;
    }

    /**
     * 获取 Aspose 文档对象。
     *
     * @return Aspose 文档对象
     */
    public Document getDocument() {
        return document;
    }

    /**
     * 获取 Aspose 文档构建器。
     *
     * @return Aspose 文档构建器
     */
    public DocumentBuilder getBuilder() {
        return builder;
    }

    /**
     * 获取样式注册表。
     *
     * @return 样式注册表
     */
    public StyleRegistry getStyleRegistry() {
        return styleRegistry;
    }

    /**
     * 获取下一个图题编号。
     *
     * @return 图题编号
     */
    public int nextFigureCaptionNumber() {
        return ++figureCaptionNumber;
    }

    /**
     * 获取下一个表题编号。
     *
     * @return 表题编号
     */
    public int nextTableCaptionNumber() {
        return ++tableCaptionNumber;
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
     * 设置是否启用 Word 原生标题多级编号。
     *
     * @param headingNumberingEnabled 是否启用标题自动编号
     */
    public void setHeadingNumberingEnabled(boolean headingNumberingEnabled) {
        this.headingNumberingEnabled = headingNumberingEnabled;
    }

    /**
     * 获取 Heading1 至 Heading9 共用的 Word 原生多级列表。
     *
     * @return 标题多级列表；未启用时返回 {@code null}
     */
    public List getHeadingList() {
        return headingList;
    }

    /**
     * 设置 Heading1 至 Heading9 共用的 Word 原生多级列表。
     *
     * @param headingList 标题多级列表；传入 {@code null} 表示未配置
     */
    public void setHeadingList(List headingList) {
        this.headingList = headingList;
    }

    /**
     * 获取当前连续列表的类型。
     *
     * @return 当前列表类型
     */
    public ParagraphListType getActiveListType() {
        return activeListType;
    }

    /**
     * 设置当前连续列表的类型。
     *
     * @param activeListType 当前列表类型
     */
    public void setActiveListType(ParagraphListType activeListType) {
        this.activeListType = activeListType;
    }

    /**
     * 获取当前连续列表使用的 Aspose 列表对象。
     *
     * @return 当前列表对象；不存在时返回 {@code null}
     */
    public List getActiveList() {
        return activeList;
    }

    /**
     * 设置当前连续列表使用的 Aspose 列表对象。
     *
     * @param activeList 当前列表对象；传入 {@code null} 表示没有活动列表
     */
    public void setActiveList(List activeList) {
        this.activeList = activeList;
    }

    /**
     * 记录题注编号。
     *
     * @param key 题注唯一键
     * @param number 题注编号
     */
    public void rememberCaptionNumber(String key, int number) {
        captionNumbers.put(key, number);
    }

    /**
     * 查找题注编号。
     *
     * @param key 题注唯一键
     * @return 题注编号；不存在时返回 {@code null}
     */
    public Integer findCaptionNumber(String key) {
        return captionNumbers.get(key);
    }
}
