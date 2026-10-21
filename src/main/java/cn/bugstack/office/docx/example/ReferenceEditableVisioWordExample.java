package cn.bugstack.office.docx.example;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.visio.ReferenceDataFlowRenderer;
import cn.bugstack.office.diagram.visio.ReferenceLibraryFlowRenderer;
import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 将参考数据流图和库管理流程图作为可编辑 Visio OLE 对象嵌入 Word。
 *
 * <p>PNG 仅是 OLE 对象的页面预览；DOCX 中实际保存 VSDX，不能以普通图片代替。</p>
 */
public final class ReferenceEditableVisioWordExample {

    /** 默认业务输入。 */
    private static final Path DEFAULT_INPUT = Path.of(
            "src/test/resources/diagram/data-flow-function3-four-inputs.json");
    /** 默认输出目录。 */
    private static final Path DEFAULT_OUTPUT = Path.of("target/reference-editable-visio-word-example");

    /** 防止实例化示例类。 */
    private ReferenceEditableVisioWordExample() {
    }

    /**
     * 从同一 JSON 生成两张 VSDX，并嵌入 DOCX。
     *
     * @param args 可选参数：输入 JSON 路径、输出目录
     * @throws Exception 图形或文档生成失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Path input = args.length > 0 ? Path.of(args[0]) : DEFAULT_INPUT;
        Path outputDirectory = args.length > 1 ? Path.of(args[1]) : DEFAULT_OUTPUT;
        Files.createDirectories(outputDirectory);
        AsposeWordsLicenseLoader.applyConfiguredLicense();

        VisioDiagramArtifact dataFlow = new ReferenceDataFlowRenderer().render(input,
                outputDirectory.resolve("数据流图.vsdx"));
        VisioDiagramArtifact libraryFlow = new ReferenceLibraryFlowRenderer().render(input,
                outputDirectory.resolve("库管理流程图.vsdx"));
        Path document = outputDirectory.resolve("可编辑Visio示例.docx");

        DocxDocument.create()
                .useDefaultStyles()
                .metadata("可编辑 Visio 示例", "omni-office", "数据流图和库管理流程图")
                .section()
                .heading1("Data Flow Diagram")
                .editableVisio("data-flow", dataFlow.getVsdxPath().toString(),
                        dataFlow.getPreviewPngPath().toString(), 460D, 460D, "Data Flow Diagram")
                .pageBreak()
                .heading1("Library Management Flow")
                .editableVisio("library-flow", libraryFlow.getVsdxPath().toString(),
                        libraryFlow.getPreviewPngPath().toString(), 460D, 460D, "Library Management Flow")
                .end()
                .save(document);

        System.out.println("Editable Word: " + document.toAbsolutePath());
        System.out.println("Data-flow Visio: " + dataFlow.getVsdxPath());
        System.out.println("Library-flow Visio: " + libraryFlow.getVsdxPath());
    }
}
