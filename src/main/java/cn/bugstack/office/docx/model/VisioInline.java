package cn.bugstack.office.docx.model;

/**
 * Visio 行内节点。
 *
 * <p>Visio 对象始终是段落内的 inline。仅传预览图时按普通图片插入；通过
 * {@link #embedded(String, String, Double, Double)} 创建时，渲染器会将 VSDX
 * 作为 OLE 对象嵌入 Word，并使用 VSDX 原生导出的 PNG 预览图展示页面效果。</p>
 */
public class VisioInline implements DocxInline {

    /** Visio 图形的 PNG 预览图片数据源。 */
    private final String previewSource;
    /** 可编辑 VSDX 文件来源；为空时仅插入预览图片。 */
    private final String vsdxSource;
    /** OLE 对象在 Word 中的最大宽度，单位为磅；为空时不限制宽度。 */
    private final Double widthPoints;
    /** OLE 对象在 Word 中的最大高度，单位为磅；为空时不限制高度。 */
    private final Double heightPoints;

    /**
     * 创建 Visio 预览图行内节点。
     *
     * @param previewSource 预览图路径或 Aspose 可识别的图片来源
     */
    public VisioInline(String previewSource) {
        this(previewSource, null, null, null);
    }

    /**
     * 创建可编辑 Visio OLE 行内节点。
     *
     * @param vsdxSource 可编辑 VSDX 文件路径
     * @param previewSource Word 页面显示的 PNG 预览图路径
     * @param widthPoints 最大宽度，单位为 point；为空时不限制宽度
     * @param heightPoints 最大高度，单位为 point；为空时不限制高度
     * @return 可编辑 Visio 行内节点
     */
    public static VisioInline embedded(String vsdxSource, String previewSource, Double widthPoints,
                                       Double heightPoints) {
        return new VisioInline(previewSource, vsdxSource, widthPoints, heightPoints);
    }

    /**
     * 创建 Visio 行内节点。
     *
     * @param previewSource 预览图路径或 Aspose 可识别的图片来源
     * @param vsdxSource 可编辑 VSDX 文件路径；为空时仅插入预览图片
     * @param widthPoints 最大宽度，单位为 point；为空时不限制宽度
     * @param heightPoints 最大高度，单位为 point；为空时不限制高度
     */
    private VisioInline(String previewSource, String vsdxSource, Double widthPoints, Double heightPoints) {
        this.previewSource = previewSource;
        this.vsdxSource = vsdxSource;
        this.widthPoints = widthPoints;
        this.heightPoints = heightPoints;
    }

    /**
     * 获取 Visio 预览图来源。
     *
     * @return 预览图路径或来源
     */
    public String getPreviewSource() {
        return previewSource;
    }

    /**
     * 获取可编辑 VSDX 文件来源。
     *
     * @return VSDX 文件路径；仅预览模式时返回 {@code null}
     */
    public String getVsdxSource() {
        return vsdxSource;
    }

    /**
     * 获取 Word 中的最大显示宽度。
     *
     * @return 最大宽度，单位为 point；未设置时返回 {@code null}
     */
    public Double getWidthPoints() {
        return widthPoints;
    }

    /**
     * 获取 Word 中的最大显示高度。
     *
     * @return 最大高度，单位为 point；未设置时返回 {@code null}
     */
    public Double getHeightPoints() {
        return heightPoints;
    }

    /**
     * 判断当前节点是否应作为可编辑 OLE Visio 对象插入。
     *
     * @return 存在 VSDX 文件来源时返回 {@code true}
     */
    public boolean isEmbedded() {
        return vsdxSource != null && !vsdxSource.trim().isEmpty();
    }
}
