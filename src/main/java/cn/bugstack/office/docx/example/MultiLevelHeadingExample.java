package cn.bugstack.office.docx.example;

import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Word 原生九级标题自动编号示例。
 *
 * <p>该示例覆盖从一级逐层进入九级标题，以及回退到上级标题后的编号重置行为。生成的
 * 标题编号由 Word 多级列表提供，而非写入标题正文的普通文本。</p>
 *
 * @author luojiang
 */
public final class MultiLevelHeadingExample {

    /** 多级标题示例文档的输出路径。 */
    private static final Path OUTPUT_PATH = Path.of("target", "multi-level-heading-example.docx");

    /**
     * 私有构造方法，避免实例化示例类。
     */
    private MultiLevelHeadingExample() {
    }

    /**
     * 生成包含 Word 原生一至九级自动编号标题的示例文档。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 当输出目录或 Word 文档无法创建时抛出
     */
    public static void main(String[] args) throws Exception {
        AsposeWordsLicenseLoader.applyConfiguredLicense();
        Files.createDirectories(OUTPUT_PATH.getParent());

        DocxDocument.create()
                .useDefaultStyles()
                .enableHeadingNumbering()
                .metadata("Word 原生多级标题编号示例", "luojiang", "omni-office")
                .tableOfContents("目录", 9)
                .header("多级标题自动编号示例")
                .footer("第 PAGE 页")
                .section()
                .title("Word 原生多级标题编号示例")
                .heading1("项目概述")
                .paragraph().text("本节验证一级标题从 1 开始编号。 ").end()
                .heading2("适用范围")
                .paragraph().text("二级标题继承一级标题编号，显示为 1.1。 ").end()
                .heading3("业务范围")
                .heading4("风险识别")
                .heading5("风险来源")
                .heading6("数据采集")
                .heading7("接口校验")
                .heading8("字段约束")
                .heading9("异常处理规则")
                .paragraph().text("该标题应显示为 1.1.1.1.1.1.1.1.1。 ").end()
                .heading3("技术范围")
                .paragraph().text("回退到三级标题后，应显示为 1.1.2。 ").end()
                .heading2("交付范围")
                .heading3("文档交付物")
                .paragraph().text("回退到二级标题后，三级标题应显示为 1.2.1。 ").end()
                .heading1("实施要求")
                .heading2("质量要求")
                .paragraph().text("新一级标题应从 2 开始，二级标题应显示为 2.1。 ").end()
                .end()
                .save(OUTPUT_PATH);

        System.out.println("Generated DOCX: " + OUTPUT_PATH.toAbsolutePath());
    }
}
