package cn.bugstack.office.docx.example;

import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.design.ClassDesignTableBuilder;
import cn.bugstack.office.docx.model.CaptionType;
import cn.bugstack.office.docx.model.DocxPaperSize;
import cn.bugstack.office.docx.model.TableCellVerticalAlignment;
import cn.bugstack.office.docx.model.TableVerticalMerge;
import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import cn.bugstack.office.docx.style.DocxParagraphAlignment;
import cn.bugstack.office.docx.style.Gjb438cStyleProfile;
import cn.bugstack.office.docx.style.ParagraphStyle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Docx wrapper 使用示例。
 */
public class DocxWrapperExample {

    /**
     * 创建 Docx wrapper 示例对象。
     */
    public DocxWrapperExample() {
    }

    /**
     * 生成包含标题、段落、图片、Visio 预览和表格的示例文档。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 示例生成失败时抛出
     */
    public static void main(String[] args) throws Exception {
        AsposeWordsLicenseLoader.applyConfiguredLicense();
        Path targetDir = Path.of("target");
        Files.createDirectories(targetDir);
        Path preview = targetDir.resolve("docx-wrapper-preview.png");
        Path output = targetDir.resolve("docx-wrapper-example.docx");
        writePreviewImage(preview);

        ParagraphStyle emphasisNote = new ParagraphStyle("EmphasisNote", "仿宋", 12);
        emphasisNote.setAsciiFontFamily("Times New Roman");
        emphasisNote.setFarEastFontFamily("仿宋");
        emphasisNote.setAlignment(DocxParagraphAlignment.CENTER);
        emphasisNote.setBold(true);

        DocxDocument.create()
                .useStyleProfile(Gjb438cStyleProfile.standard())
                .metadata("软件设计说明", "luojiang", "omni-office")
                .pageSetup(setup -> setup
                        .paper(DocxPaperSize.A4)
                        .landscape()
                        .margins(72, 54, 72, 54))
                .cover("软件设计说明", "omni-office", "V1.0")
                .revisionHistory(history -> history
                        .revision("V1.0", "2026-07-07", "创建二次封装设计示例", "luojiang"))
                .approvalPage(approval -> approval
                        .approval("编制", "张三", "2026-07-07")
                        .approval("审核", "李四", "2026-07-07"))
                .enableHeadingNumbering()
                .tableOfContents("目  录", 3)
                .header("GJB 438C 软件开发文档")
                .footer("第 PAGE 页")
                .registerParagraphStyle(emphasisNote)
                .section()
                .title("Aspose Docx 二次封装示例")
                .heading1("设计目标")
                .heading2("API 目标")
                .paragraph()
                .text("通过 Builder API 创建文档，同时保持内部 document、section、paragraph、table、image、visio、style 的边界清晰。")
                .end()
                .paragraph()
                .style("EmphasisNote")
                .text("这是一段自定义样式文本。")
                .end()
                .bullet("支持 GJB 438C 前置页、目录、页眉页脚和结构校验。")
                .numbered("支持题注引用、列表、表格列宽和跨列单元格。")
                .heading1("图文混排")
                .heading2("行内元素")
                .paragraph()
                .text("图片和 Visio 预览都是 paragraph 的 child：")
                .image(preview.toString(), 144, 72)
                .text(" Visio 当前阶段按预览图插入：")
                .visio(preview.toString())
                .end()
                .figureCaption("mix", "图文混排示意")
                .paragraph()
                .text("正文中可以引用前文题注：")
                .captionRef(CaptionType.FIGURE, "mix")
                .text("。")
                .end()
                .heading1("模块拆分")
                .heading2("九级标题示例")
                .tableCaption("封装模块职责说明")
                .table()
                .style("TableHeader")
                .widths(120, 260, 140)
                .headers("模块", "职责", "设计模式")
                .row(row -> row
                        .cell(2, cell -> cell.paragraph().text("基础能力").end())
                        .cell(cell -> cell.paragraph().text("第二阶段").end()))
                .row("Document", "文档入口、保存与导出", "Facade + Builder")
                .row("Section", "章节与块级内容容器", "Composite")
                .row("Paragraph", "文本、图片、Visio 等行内元素容器", "Composite")
                .row("Renderer", "将组件树渲染为 Aspose 文档", "Adapter + Renderer")
                .row(row -> row
                        .cell(cell -> cell.verticalMerge(TableVerticalMerge.FIRST)
                                .verticalAlign(TableCellVerticalAlignment.CENTER)
                                .paragraph().text("版式控制").end())
                        .cell(cell -> cell.paragraph().text("页面设置、元数据、纵向合并").end())
                        .cell(cell -> cell.paragraph().text("Builder + Adapter").end()))
                .row(row -> row
                        .cell(cell -> cell.verticalMerge(TableVerticalMerge.PREVIOUS))
                        .cell(cell -> cell.paragraph().text("适合标准文档模板化输出").end())
                        .cell(cell -> cell.paragraph().text("Composite").end()))
                .end()
                .heading1("类设计表格导出")
                .paragraph()
                .text("以下表格由源码 Javadoc 自动解析生成，用于展示指定类的设计说明导出能力。")
                .end()
                .paragraph()
                .text("示例数据覆盖图片来源、类设计配置、章节 Builder、默认样式工厂和源码解析器，"
                        + "便于检查属性、方法、私有成员、静态工厂和复杂源码解析场景。")
                .end()
                .classDesignTable("PathImageSource 类设计", config -> config
                        .sourceRoot(Path.of("src/main/java"))
                        .className("cn.bugstack.office.docx.source.PathImageSource")
                        .includePrivate(true)
                        .includeGetterSetter(true))
                .classDesignTable("ClassDesignTableOptions 类设计", classDesignExample(
                        "cn.bugstack.office.docx.design.ClassDesignTableOptions"))
                .classDesignTable("SectionBuilder 类设计", classDesignExample(
                        "cn.bugstack.office.docx.builder.SectionBuilder"))
                .classDesignTable("DefaultStyles 类设计", classDesignExample(
                        "cn.bugstack.office.docx.style.DefaultStyles"))
                .classDesignTable("SourceClassMetadataParser 类设计", classDesignExample(
                        "cn.bugstack.office.docx.design.parser.SourceClassMetadataParser"))
                .end()
                .save(output);

        System.out.println("Generated: " + output.toAbsolutePath());
    }

    /**
     * 写入供图片和 Visio 预览示例使用的最小 PNG 文件。
     *
     * @param path 预览图片路径
     * @throws Exception 写入图片失败时抛出
     */
    private static void writePreviewImage(Path path) throws Exception {
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4XmNgYPgPAAEDAQD1FzX"
                + "NAAAAAElFTkSuQmCC";
        Files.write(path, Base64.getDecoder().decode(png));
    }

    /**
     * 创建类设计表格示例配置。
     *
     * <p>示例文档默认包含私有成员和 getter/setter，便于在导出的 docx 中完整观察
     * 属性与方法的解析效果。</p>
     *
     * @param className 需要生成设计表格的类全限定名
     * @return 类设计表格配置回调
     */
    private static Consumer<ClassDesignTableBuilder> classDesignExample(String className) {
        return config -> config
                .sourceRoot(Path.of("src/main/java"))
                .className(className)
                .includePrivate(true)
                .includeGetterSetter(true);
    }

}
