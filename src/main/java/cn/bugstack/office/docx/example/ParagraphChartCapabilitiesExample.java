package cn.bugstack.office.docx.example;

import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.builder.SectionBuilder;
import cn.bugstack.office.docx.model.ChartLegendPosition;
import cn.bugstack.office.docx.model.ChartType;
import cn.bugstack.office.docx.model.DocxPageOrientation;
import cn.bugstack.office.docx.model.DocxPaperSize;
import cn.bugstack.office.docx.style.BusinessBriefStyleProfile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 使用 {@code section.paragraph().chart(...)} 构建五种 Word 原生图表的示例。
 *
 * <p>该入口适合直接使用 word-core Builder 的调用方。图表是 Paragraph 的 inline child，
 * 可以与图片、Visio 使用相同的段落构建方式；每个图表仍是 Word 内可编辑的原生对象。</p>
 */
public final class ParagraphChartCapabilitiesExample {

    /** 示例文档固定输出位置。 */
    public static final Path OUTPUT = Path.of("target", "paragraph-chart-capabilities-example.docx");

    private ParagraphChartCapabilitiesExample() { }

    /**
     * 生成 paragraph 图表 Builder 示例文档。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 创建输出目录或保存 Word 失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT.getParent());
        DocxDocument document = DocxDocument.create()
                .useStyleProfile(BusinessBriefStyleProfile.standard())
                .metadata("Paragraph 原生图表示例", "omni-office", "paragraph chart builder")
                .pageSetup(setup -> setup
                        .paper(DocxPaperSize.A4)
                        .portrait()
                        .margins(54D, 54D, 54D, 54D))
                .enableHeadingNumbering()
                .header("omni-office · paragraph 原生图表")
                .footer("第 PAGE 页");

        SectionBuilder section = document.section()
                .heading1("Word 原生图表")
                .paragraph().text("以下图表均通过 section.paragraph().chart(...) 构建。 ").end();

        section.paragraph().style("Heading2").text("柱状图").end()
                .paragraph()
                .chart(ChartType.COLUMN)
                .title("2026 年季度收入")
                .categories("第一季度", "第二季度", "第三季度", "第四季度")
                .series("收入（万元）", 128D, 156D, 184D, 213D)
                .axisTitles("季度", "万元")
                .legend(false, ChartLegendPosition.BOTTOM)
                .showValues(true)
                .end()
                .end()
                .figureCaption("季度收入柱状图")
                .pageBreak();

        section.paragraph().style("Heading2").text("饼图").end()
                .paragraph()
                .chart(ChartType.PIE)
                .title("产品收入结构")
                .categories("文档生成", "图形生成", "模板服务", "外部工具")
                .series("收入占比", 42D, 24D, 21D, 13D)
                .legend(true, ChartLegendPosition.RIGHT)
                .showPercentages(true)
                .end()
                .end()
                .figureCaption("产品收入结构饼图")
                .pageBreak();

        section.paragraph().style("Heading2").text("对比图").end()
                .paragraph()
                .chart(ChartType.COLUMN)
                .title("年度业务指标对比")
                .categories("新增客户", "续约客户", "交付项目", "客户满意度")
                .series("2025 年", 68D, 74D, 81D, 86D)
                .series("2026 年", 85D, 88D, 96D, 93D)
                .axisTitles("指标", "完成值")
                .legend(true, ChartLegendPosition.BOTTOM)
                .showValues(true)
                .end()
                .end()
                .figureCaption("两年度业务指标对比图")
                .pageBreak();

        section.paragraph().style("Heading2").text("折线图").end()
                .paragraph()
                .chart(ChartType.LINE)
                .title("月度活跃用户趋势")
                .categories("1月", "2月", "3月", "4月", "5月", "6月")
                .series("2025 年", 42D, 48D, 53D, 61D, 67D, 75D)
                .series("2026 年", 55D, 63D, 72D, 84D, 96D, 112D)
                .axisTitles("月份", "用户数（千）")
                .legend(true, ChartLegendPosition.BOTTOM)
                .end()
                .end()
                .figureCaption("月度活跃用户趋势折线图")
                .pageBreak();

        section.paragraph().style("Heading2").text("雷达图").end()
                .paragraph()
                .chart(ChartType.RADAR)
                .title("产品能力综合评估")
                .categories("易用性", "稳定性", "性能", "扩展性", "安全性")
                .series("当前版本", 82D, 88D, 84D, 91D, 86D)
                .series("目标版本", 92D, 94D, 92D, 96D, 95D)
                .legend(true, ChartLegendPosition.BOTTOM)
                .end()
                .end()
                .figureCaption("产品能力雷达图")
                .end()
                .save(OUTPUT);

        System.out.println("Paragraph chart example generated: " + OUTPUT.toAbsolutePath());
    }
}
