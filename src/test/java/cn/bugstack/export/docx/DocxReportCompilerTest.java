package cn.bugstack.export.docx;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.definition.ReportLayout;
import cn.bugstack.export.definition.ReportStyleProfile;
import cn.bugstack.export.document.ReportDocument;
import cn.bugstack.export.document.ReportElement;
import cn.bugstack.export.document.ReportElementType;
import cn.bugstack.export.document.ReportSectionBuilder;
import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.model.DocxBlock;
import cn.bugstack.office.docx.model.ParagraphNode;
import cn.bugstack.office.docx.model.SectionNode;
import com.aspose.words.Document;
import com.aspose.words.Field;
import com.aspose.words.FieldType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxReportCompilerTest {

    @Test
    void compilesBuiltInSemanticBlocksToDocx() throws Exception {
        Path directory = Files.createTempDirectory("report-docx-compiler");
        Path image = directory.resolve("diagram.png");
        Path output = directory.resolve("report.docx");
        Files.write(image, Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9J3wAAAABJRU5ErkJggg=="));

        ReportDocument report = new ReportDocument();
        report.setTitle("评估报告");
        ReportSectionBuilder section = ReportSectionBuilder.section("评估结果")
                .paragraph("语义报告模块不直接依赖 Aspose。")
                .bullet("支持项目符号列表。")
                .numbered("支持编号列表。")
                .image(image.toString(), 36, 36, "架构示意")
                .pageBreak()
                .section("类设计", child -> child.classDesignTable("导出器类设计",
                        Path.of("src/main/java"), "cn.bugstack.export.core.DefaultReportExporter"));
        section.table("模块", "职责")
                .style("TableHeader")
                .widths(140, 360)
                .row("定义", "声明报告蓝图和模块槽位。")
                .row("编译器", "将语义树适配为 Word 文档。")
                .caption("导出框架职责", true)
                .end();
        report.getSections().add(section.build());

        ReportBlueprint blueprint = ReportBlueprint.builder("assessment", "评估报告", "1.0")
                .layout(ReportLayout.builder()
                        .styleProfile(ReportStyleProfile.GJB_438C)
                        .headingNumberingEnabled(true)
                        .build())
                .build();

        new DocxReportCompiler().render(report, blueprint, ReportOutputFormat.DOCX, output);

        Document rendered = new Document(output.toString());
        String text = rendered.getText();
        assertTrue(Files.isRegularFile(output));
        assertTrue(text.contains("评估报告"));
        assertTrue(text.contains("评估结果"));
        assertTrue(text.contains("导出框架职责"));
        assertTrue(text.contains("DefaultReportExporter"));
        assertTrue(hasPageNumberField(rendered));
    }

    @Test
    void compilesRegisteredCustomSemanticBlock() throws Exception {
        Path directory = Files.createTempDirectory("report-docx-custom-block");
        Path output = directory.resolve("custom.docx");
        ReportDocument report = new ReportDocument();
        report.setTitle("自定义块报告");
        report.getSections().add(ReportSectionBuilder.section("正文")
                .add(new NoticeBlock("需要人工复核。"))
                .build());
        ReportBlueprint blueprint = ReportBlueprint.builder("custom", "自定义块报告", "1.0").build();

        DocxReportCompiler compiler = new DocxReportCompiler().register(new NoticeBlockCompiler());
        compiler.render(report, blueprint, ReportOutputFormat.DOCX, output);

        assertEquals(true, new Document(output.toString()).getText().contains("需要人工复核。"));
    }

    @Test
    void assignsClassDesignHeadingsRelativeToParentSection() {
        ReportDocument report = new ReportDocument();
        report.setTitle("类设计报告");
        report.getSections().add(ReportSectionBuilder.section("类设计表格导出")
                .classDesignTable("PathImageSource 类设计", Path.of("src/main/java"),
                        "cn.bugstack.office.docx.source.PathImageSource")
                .build());
        ReportBlueprint blueprint = ReportBlueprint.builder("class-design", "类设计报告", "1.0").build();

        DocxDocument document = new DocxReportCompiler().compile(report, blueprint);
        SectionNode section = document.getNode().getSections().get(0);

        assertEquals("Heading1", paragraph(section.getBlocks().get(1)).getStyleName());
        assertEquals("Heading2", paragraph(section.getBlocks().get(2)).getStyleName());
        assertEquals("Heading3", paragraph(section.getBlocks().get(4)).getStyleName());
        assertEquals("Heading3", paragraph(section.getBlocks().get(6)).getStyleName());
    }

    @Test
    void appliesClassDesignTableOutputOptions() {
        ReportDocument report = new ReportDocument();
        report.setTitle("按需类设计报告");
        report.getSections().add(ReportSectionBuilder.section("类设计表格导出")
                .classDesignTable("PathImageSource 概要", Path.of("src/main/java"),
                        "cn.bugstack.office.docx.source.PathImageSource",
                        options -> options.includeFields(false).includeMethods(false))
                .build());
        ReportBlueprint blueprint = ReportBlueprint.builder("class-design-options", "按需类设计报告", "1.0").build();

        DocxDocument document = new DocxReportCompiler().compile(report, blueprint);
        SectionNode section = document.getNode().getSections().get(0);

        assertEquals(4, section.getBlocks().size());
        assertEquals("Heading2", paragraph(section.getBlocks().get(2)).getStyleName());
    }

    @Test
    void delegatesDocumentCreationToConfiguredFactory() {
        AtomicBoolean factoryInvoked = new AtomicBoolean();
        ReportDocument report = new ReportDocument();
        report.setTitle("自定义模板报告");
        report.getSections().add(ReportSectionBuilder.section("正文").paragraph("内容").build());
        ReportBlueprint blueprint = ReportBlueprint.builder("custom-factory", "自定义模板报告", "1.0").build();

        DocxReportCompiler compiler = new DocxReportCompiler(ignored -> {
            factoryInvoked.set(true);
            return DocxDocument.create().useDefaultStyles();
        });
        compiler.compile(report, blueprint);

        assertTrue(factoryInvoked.get());
    }

    @Test
    void retainsExistingOutputWhenCompilationFails() throws Exception {
        Path directory = Files.createTempDirectory("report-docx-atomic");
        Path output = directory.resolve("report.docx");
        Files.writeString(output, "last-known-good");
        ReportDocument report = new ReportDocument();
        report.setTitle("故障报告");
        report.getSections().add(ReportSectionBuilder.section("错误内容")
                .classDesignTable("不存在的类", directory.resolve("missing-source"), "example.Missing")
                .build());
        ReportBlueprint blueprint = ReportBlueprint.builder("atomic", "故障报告", "1.0").build();

        assertThrows(IllegalStateException.class,
                () -> new DocxReportCompiler().render(report, blueprint, ReportOutputFormat.DOCX, output));

        assertEquals("last-known-good", Files.readString(output));
    }

    @Test
    void rendersPdfFromSameSemanticReport() throws Exception {
        Path directory = Files.createTempDirectory("report-pdf-compiler");
        Path output = directory.resolve("report.pdf");
        ReportDocument report = new ReportDocument();
        report.setTitle("PDF 报告");
        report.getSections().add(ReportSectionBuilder.section("正文").paragraph("PDF 输出验证。").build());
        ReportBlueprint blueprint = ReportBlueprint.builder("pdf", "PDF 报告", "1.0").build();

        new DocxReportCompiler().render(report, blueprint, ReportOutputFormat.PDF, output);

        assertTrue(Files.size(output) > 4);
        assertEquals("%PDF", new String(Files.readAllBytes(output), 0, 4));
    }

    @Test
    void rendersDocxAndPdfToBytesWithoutTemporaryOutputFile() throws Exception {
        ReportDocument report = new ReportDocument();
        report.setTitle("字节流报告");
        report.getSections().add(ReportSectionBuilder.section("正文").paragraph("内存输出验证。").build());
        ReportBlueprint blueprint = ReportBlueprint.builder("bytes", "字节流报告", "1.0").build();
        DocxReportCompiler compiler = new DocxReportCompiler();

        byte[] docx = compiler.renderToBytes(report, blueprint, ReportOutputFormat.DOCX);
        byte[] pdf = compiler.renderToBytes(report, blueprint, ReportOutputFormat.PDF);

        assertEquals("PK", new String(docx, 0, 2));
        assertTrue(new Document(new ByteArrayInputStream(docx)).getText().contains("内存输出验证。"));
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    private static final class NoticeBlock implements ReportElement {

        private final String text;

        private NoticeBlock(String text) {
            this.text = text;
        }

        @Override
        public ReportElementType getElementType() {
            return ReportElementType.PARAGRAPH;
        }
    }

    private static final class NoticeBlockCompiler implements ReportElementCompiler<NoticeBlock> {

        @Override
        public Class<NoticeBlock> supportedType() {
            return NoticeBlock.class;
        }

        @Override
        public void compile(NoticeBlock element, DocxReportCompileContext context) {
            context.getSection().paragraph().style("BodyText").text(element.text).end();
        }
    }

    /**
     * 判断文档范围内是否存在页码域。
     *
     * @param document 待检查文档
     * @return 存在页码域时返回 {@code true}
     */
    private boolean hasPageNumberField(Document document) throws Exception {
        for (Field field : document.getRange().getFields()) {
            if (field.getType() == FieldType.FIELD_PAGE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将测试中的块级节点转换为段落节点。
     *
     * @param block 块级节点
     * @return 段落节点
     */
    private ParagraphNode paragraph(DocxBlock block) {
        return (ParagraphNode) block;
    }
}
