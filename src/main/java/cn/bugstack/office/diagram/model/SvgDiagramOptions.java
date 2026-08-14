package cn.bugstack.office.diagram.model;

/**
 * SVG 图的通用显示配置。
 */
public final class SvgDiagramOptions {

    /** 默认画布宽度。 */
    public static final int DEFAULT_WIDTH = 960;
    /** 默认画布高度。 */
    public static final int DEFAULT_HEIGHT = 540;

    /** SVG 画布宽度。 */
    private final int width;
    /** SVG 画布高度。 */
    private final int height;
    /** 图标题字体。 */
    private final String fontFamily;
    /** SVG 背景色。 */
    private final String backgroundColor;
    /** 节点边框颜色。 */
    private final String strokeColor;
    /** 节点填充颜色。 */
    private final String fillColor;

    /**
     * 创建默认 SVG 图配置。
     */
    public SvgDiagramOptions() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT, "Microsoft YaHei, SimSun, sans-serif", "#FFFFFF", "#1F2937", "#F8FAFC");
    }

    /**
     * 创建指定 SVG 图配置。
     *
     * @param width 画布宽度
     * @param height 画布高度
     * @param fontFamily 字体族
     * @param backgroundColor 背景色
     * @param strokeColor 边框颜色
     * @param fillColor 节点填充颜色
     */
    public SvgDiagramOptions(int width, int height, String fontFamily, String backgroundColor,
                             String strokeColor, String fillColor) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("svg diagram width and height must be greater than zero");
        }
        this.width = width;
        this.height = height;
        this.fontFamily = requiredText(fontFamily, "svg diagram font family");
        this.backgroundColor = requiredText(backgroundColor, "svg diagram background color");
        this.strokeColor = requiredText(strokeColor, "svg diagram stroke color");
        this.fillColor = requiredText(fillColor, "svg diagram fill color");
    }

    /**
     * 获取画布宽度。
     *
     * @return 画布宽度
     */
    public int getWidth() {
        return width;
    }

    /**
     * 获取画布高度。
     *
     * @return 画布高度
     */
    public int getHeight() {
        return height;
    }

    /**
     * 获取字体族。
     *
     * @return 字体族
     */
    public String getFontFamily() {
        return fontFamily;
    }

    /**
     * 获取背景色。
     *
     * @return 背景色
     */
    public String getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * 获取节点边框颜色。
     *
     * @return 节点边框颜色
     */
    public String getStrokeColor() {
        return strokeColor;
    }

    /**
     * 获取节点填充颜色。
     *
     * @return 节点填充颜色
     */
    public String getFillColor() {
        return fillColor;
    }

    /**
     * 校验必填样式文本。
     *
     * @param value 原始文本
     * @param name 参数名称
     * @return 去除首尾空白后的文本
     */
    private static String requiredText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
