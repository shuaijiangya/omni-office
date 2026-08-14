package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.definition.ReportLayout;

/**
 * 可组合评估报告的页码页脚显示格式。
 *
 * <p>该格式只控制页码周围的文字。目录仍使用大写罗马数字，业务模块仍使用
 * 阿拉伯数字。</p>
 */
public enum ComposablePageNumberFooterFormat {

    /** 仅显示页码，例如 {@code I} 或 {@code 1}。 */
    PAGE_ONLY(ReportLayout.PAGE_NUMBER_ONLY_FOOTER),

    /** 显示中文修饰文字，例如 {@code 第 I 页} 或 {@code 第 1 页}。 */
    CHINESE_DECORATED(ReportLayout.CHINESE_PAGE_NUMBER_FOOTER);

    /** 写入 Word 页脚的模板，其中 PAGE 会被转换为页码域。 */
    private final String template;

    ComposablePageNumberFooterFormat(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}
