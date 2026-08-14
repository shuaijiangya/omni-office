package cn.bugstack.office.docx;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramNodeType;
import cn.bugstack.office.diagram.model.DiagramType;
import cn.bugstack.office.diagram.visio.VsdxDiagramRenderer;
import cn.bugstack.office.docx.api.DocxDocument;
import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可编辑 Visio 行内节点的 Word OLE 集成测试。
 *
 * @author luojiang
 */
class EditableVisioInlineTest {

    /** 可写入 VSDX 与 DOCX 测试文件的临时目录。 */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证 VSDX 可作为 OLE 对象嵌入 docx，并生成可交叉引用的自动编号图题。
     *
     * @throws Exception 当生成或读取 Office 文件失败时抛出
     */
    @Test
    void shouldEmbedEditableVsdxAsOleObject() throws Exception {
        DiagramDefinition definition = createDefinition();
        VisioDiagramArtifact artifact = new VsdxDiagramRenderer().render(definition,
                temporaryDirectory.resolve("architecture.vsdx"));
        Path output = temporaryDirectory.resolve("editable-visio.docx");

        DocxDocument.create()
                .useDefaultStyles()
                .section()
                .heading1("可编辑架构图")
                .editableVisio("authentication", artifact.getVsdxPath().toString(),
                        artifact.getPreviewPngPath().toString(), 420D, 240D, "认证用例图")
                .paragraph()
                .text("详见")
                .captionRef(cn.bugstack.office.docx.model.CaptionType.FIGURE, "authentication")
                .end()
                .end()
                .save(output);

        Document document = new Document(output.toString());
        NodeCollection shapes = document.getChildNodes(NodeType.SHAPE, true);
        Shape oleShape = (Shape) shapes.get(0);

        assertTrue(Files.exists(output));
        assertTrue(shapes.getCount() > 0);
        assertNotNull(oleShape.getOleFormat());
        assertFalse(oleShape.getOleFormat().isLink());
        assertTrue(oleShape.getOleFormat().getRawData().length > 0);
        BufferedImage previewImage = ImageIO.read(artifact.getPreviewPngPath().toFile());
        double sourceAspectRatio = (double) previewImage.getWidth() / previewImage.getHeight();
        double oleAspectRatio = oleShape.getWidth() / oleShape.getHeight();
        assertTrue(oleShape.getAspectRatioLocked());
        assertTrue(oleShape.getWidth() <= 420D);
        assertTrue(oleShape.getHeight() <= 240D);
        assertEquals(sourceAspectRatio, oleAspectRatio, 0.01D);
        assertTrue(document.getText().contains("图 1 认证用例图"));
        assertTrue(document.getText().contains("详见图 1"));
    }

    /**
     * 创建用于 OLE 嵌入测试的最小用例图。
     *
     * @return 用例图定义
     */
    private DiagramDefinition createDefinition() {
        return DiagramDefinition.builder(DiagramType.USE_CASE, "认证用例图")
                .node(new DiagramNode("user", "用户", DiagramNodeType.ACTOR))
                .node(new DiagramNode("login", "登录", DiagramNodeType.USE_CASE))
                .edge(new DiagramEdge("user", "login"))
                .build();
    }
}
