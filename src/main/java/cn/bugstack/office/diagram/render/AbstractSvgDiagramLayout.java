package cn.bugstack.office.diagram.render;

import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.SvgDiagramOptions;

/**
 * SVG 布局策略的公共绘制工具。
 */
abstract class AbstractSvgDiagramLayout implements SvgDiagramLayout {

    /** SVG 图形的默认内边距。 */
    protected static final int PADDING = 48;

    /**
     * 创建 SVG 文档头、背景和箭头标记定义。
     *
     * @param definition 图语义定义
     * @param height 实际画布高度
     * @return SVG 文档头文本
     */
    protected String begin(DiagramDefinition definition, int height) {
        SvgDiagramOptions options = definition.getOptions();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + options.getWidth()
                + "\" height=\"" + height + "\" viewBox=\"0 0 " + options.getWidth() + " " + height + "\">\n"
                + "<defs><marker id=\"arrow\" markerWidth=\"10\" markerHeight=\"7\" refX=\"9\" refY=\"3.5\" orient=\"auto\">"
                + "<polygon points=\"0 0, 10 3.5, 0 7\" fill=\"" + options.getStrokeColor() + "\"/></marker></defs>\n"
                + "<rect width=\"100%\" height=\"100%\" fill=\"" + options.getBackgroundColor() + "\"/>\n"
                + text(options.getWidth() / 2, 30, definition.getTitle(), "middle", 20, "700", options)
                + "<g stroke=\"" + options.getStrokeColor() + "\" fill=\"" + options.getFillColor()
                + "\" stroke-width=\"1.5\">\n";
    }

    /**
     * 结束 SVG 文档。
     *
     * @return SVG 文档尾文本
     */
    protected String end() {
        return "</g>\n</svg>\n";
    }

    /**
     * 生成带箭头的关系线及可选标签。
     *
     * @param from 起始坐标
     * @param to 目标坐标
     * @param label 关系标签
     * @param options SVG 配置
     * @return SVG 关系线文本
     */
    protected String edge(Point from, Point to, String label, SvgDiagramOptions options) {
        StringBuilder svg = new StringBuilder();
        svg.append("<line x1=\"").append(from.x).append("\" y1=\"").append(from.y)
                .append("\" x2=\"").append(to.x).append("\" y2=\"").append(to.y)
                .append("\" stroke=\"").append(options.getStrokeColor())
                .append("\" fill=\"none\" marker-end=\"url(#arrow)\"/>\n");
        if (label != null && !label.trim().isEmpty()) {
            svg.append(text((from.x + to.x) / 2, (from.y + to.y) / 2 - 6, label, "middle", 12, "400", options));
        }
        return svg.toString();
    }

    /**
     * 生成由多个拐点组成的带箭头关系线及可选标签。
     *
     * @param points 关系线依次经过的坐标，至少需要两个坐标
     * @param label 关系标签
     * @param options SVG 配置
     * @return SVG 折线文本
     * @throws IllegalArgumentException 当坐标数量少于两个时抛出
     */
    protected String polyline(Point[] points, String label, SvgDiagramOptions options) {
        if (points == null || points.length < 2) {
            throw new IllegalArgumentException("polyline requires at least two points");
        }
        StringBuilder svg = new StringBuilder("<polyline points=\"");
        for (Point point : points) {
            svg.append(point.x).append(",").append(point.y).append(" ");
        }
        svg.append("\" stroke=\"").append(options.getStrokeColor())
                .append("\" fill=\"none\" marker-end=\"url(#arrow)\"/>\n");
        if (label != null && !label.trim().isEmpty()) {
            Point labelPoint = points[points.length / 2];
            svg.append(text(labelPoint.x + 8, labelPoint.y - 6, label, "start", 12, "400", options));
        }
        return svg.toString();
    }

    /**
     * 生成 SVG 文本节点，并将换行文本展开为多个 tspan。
     *
     * @param x 文本基线横坐标
     * @param y 文本基线纵坐标
     * @param value 文本内容
     * @param anchor 文本锚点
     * @param fontSize 字号
     * @param weight 字重
     * @param options SVG 配置
     * @return SVG 文本节点
     */
    protected String text(int x, int y, String value, String anchor, int fontSize, String weight,
                          SvgDiagramOptions options) {
        String[] lines = escape(value).split("\\n", -1);
        StringBuilder svg = new StringBuilder("<text x=\"").append(x).append("\" y=\"").append(y)
                .append("\" text-anchor=\"").append(anchor).append("\" font-family=\"")
                .append(escape(options.getFontFamily())).append("\" font-size=\"").append(fontSize)
                .append("\" font-weight=\"").append(weight).append("\" fill=\"")
                .append(options.getStrokeColor()).append("\" stroke=\"none\">");
        for (int index = 0; index < lines.length; index++) {
            if (index == 0) {
                svg.append("<tspan x=\"").append(x).append("\">").append(lines[index]).append("</tspan>");
            } else {
                svg.append("<tspan x=\"").append(x).append("\" dy=\"1.25em\">")
                        .append(lines[index]).append("</tspan>");
            }
        }
        return svg.append("</text>\n").toString();
    }

    /**
     * 转义 SVG XML 文本。
     *
     * @param value 原始文本
     * @return 已转义文本
     */
    protected String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * 图形布局中使用的二维坐标。
     */
    protected static final class Point {

        /** 横坐标。 */
        protected final int x;
        /** 纵坐标。 */
        protected final int y;

        /**
         * 创建二维坐标。
         *
         * @param x 横坐标
         * @param y 纵坐标
         */
        protected Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
