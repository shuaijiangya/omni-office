package cn.bugstack.office.docx.style;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档样式注册表。
 *
 * <p>注册时保存样式副本，读取时也返回副本，避免调用方修改某个节点样式时污染全局默认样式。</p>
 */
public class StyleRegistry {

    private final Map<String, ParagraphStyle> paragraphStyles = new LinkedHashMap<>();
    private final Map<String, TableStyle> tableStyles = new LinkedHashMap<>();
    private final Map<String, ImageStyle> imageStyles = new LinkedHashMap<>();

    /**
     * 创建空的样式注册表。
     */
    public StyleRegistry() {
    }

    /**
     * 注册段落样式。
     *
     * @param style 段落样式
     */
    public void registerParagraphStyle(ParagraphStyle style) {
        paragraphStyles.put(style.getName(), style.copy());
    }

    /**
     * 注册表格样式。
     *
     * @param style 表格样式
     */
    public void registerTableStyle(TableStyle style) {
        tableStyles.put(style.getName(), style.copy());
    }

    /**
     * 注册图片样式。
     *
     * @param style 图片样式
     */
    public void registerImageStyle(ImageStyle style) {
        imageStyles.put(style.getName(), style.copy());
    }

    /**
     * 判断任意类型的样式是否存在。
     *
     * @param name 样式名称
     * @return 样式存在返回 {@code true}
     */
    public boolean contains(String name) {
        return paragraphStyles.containsKey(name)
                || tableStyles.containsKey(name)
                || imageStyles.containsKey(name);
    }

    /**
     * 获取段落样式副本。
     *
     * @param name 样式名称
     * @return 段落样式副本，不存在时返回 {@code null}
     */
    public ParagraphStyle getParagraphStyle(String name) {
        ParagraphStyle style = paragraphStyles.get(name);
        return style == null ? null : style.copy();
    }

    /**
     * 获取表格样式副本。
     *
     * @param name 样式名称
     * @return 表格样式副本，不存在时返回 {@code null}
     */
    public TableStyle getTableStyle(String name) {
        TableStyle style = tableStyles.get(name);
        return style == null ? null : style.copy();
    }

    /**
     * 获取图片样式副本。
     *
     * @param name 样式名称
     * @return 图片样式副本，不存在时返回 {@code null}
     */
    public ImageStyle getImageStyle(String name) {
        ImageStyle style = imageStyles.get(name);
        return style == null ? null : style.copy();
    }
}
