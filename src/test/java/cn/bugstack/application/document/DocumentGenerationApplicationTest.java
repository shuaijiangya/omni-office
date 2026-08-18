package cn.bugstack.application.document;

import cn.bugstack.application.artifact.DiagramArtifactReference;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.diagram.DiagramEdgeSpec;
import cn.bugstack.protocol.diagram.DiagramNodeSpec;
import cn.bugstack.protocol.diagram.DiagramNodeTypeSpec;
import cn.bugstack.protocol.diagram.DiagramSpec;
import cn.bugstack.protocol.diagram.DiagramTypeSpec;
import cn.bugstack.protocol.document.DocumentMetadataSpec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.DiagramBlockSpec;
import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentGenerationApplicationTest {

    @TempDir
    Path tempDir;

    @Test
    void reusesArtifactAndEmbedsEditableVisioInWord() throws Exception {
        DocumentGenerationApplication application = application();
        DiagramArtifactReference artifact = application.generateDiagram(flow());
        DiagramBlockSpec block = new DiagramBlockSpec();
        block.setDiagramArtifactId(artifact.getDiagramArtifactId());
        block.setEmbedMode("EDITABLE_VISIO");
        block.setCaption("自动生成流程图");
        block.setMaxWidthPoints(400D);
        block.setMaxHeightPoints(240D);

        byte[] docx = application.exportToBytes(document(block), ReportOutputFormat.DOCX);
        Document word = new Document(new ByteArrayInputStream(docx));
        Shape embedded = firstOleShape(word.getChildNodes(NodeType.SHAPE, true));

        assertTrue(docx.length > 0);
        assertNotNull(embedded);
        assertTrue(embedded.getOleFormat().getRawData().length > 0);
        assertTrue(word.getText().contains("图 1 自动生成流程图"));
    }

    @Test
    void materializesInlineDefinitionAndExportsPdfPreview() {
        DocumentGenerationApplication application = application();
        DiagramBlockSpec block = new DiagramBlockSpec();
        block.setDefinition(flow());
        block.setEmbedMode("PREVIEW_IMAGE");
        block.setCaption("内联流程图");

        byte[] pdf = application.exportToBytes(document(block), ReportOutputFormat.PDF);

        assertTrue(pdf.length > 4);
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    private DocumentGenerationApplication application() {
        return new DocumentGenerationApplication(tempDir.resolve("artifacts"));
    }

    private DocumentSpec document(DiagramBlockSpec block) {
        DocumentSpec document = new DocumentSpec();
        document.setMetadata(new DocumentMetadataSpec("M2 图形文档"));
        document.addSection(new SectionSpec("图形能力").addBlock(block));
        return document;
    }

    private DiagramSpec flow() {
        DiagramSpec spec = new DiagramSpec();
        spec.setType(DiagramTypeSpec.FLOW);
        spec.setTitle("自动生成流程图");
        spec.getNodes().add(new DiagramNodeSpec("start", "开始", DiagramNodeTypeSpec.START));
        spec.getNodes().add(new DiagramNodeSpec("build", "生成文档", DiagramNodeTypeSpec.PROCESS));
        spec.getNodes().add(new DiagramNodeSpec("end", "结束", DiagramNodeTypeSpec.END));
        spec.getEdges().add(new DiagramEdgeSpec("start", "build", null));
        spec.getEdges().add(new DiagramEdgeSpec("build", "end", null));
        return spec;
    }

    private Shape firstOleShape(NodeCollection shapes) {
        for (int i = 0; i < shapes.getCount(); i++) {
            Shape shape = (Shape) shapes.get(i);
            if (shape.getOleFormat() != null) {
                return shape;
            }
        }
        return null;
    }
}
