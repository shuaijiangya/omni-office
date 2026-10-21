package cn.bugstack.office.docx;

import cn.bugstack.office.docx.example.ReferenceEditableVisioWordExample;
import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证两张参考图在 Word 中是可编辑 OLE 对象，而不只是 PNG 图片。
 */
class ReferenceEditableVisioWordExampleTest {

    /** 供用户查看的固定输出目录。 */
    private static final Path OUTPUT = Path.of("target/reference-editable-visio-word-example");

    /**
     * 生成示例并检查 DOCX 内的两个嵌入 Visio 对象。
     *
     * @throws Exception 文件生成或检查失败时抛出
     */
    @Test
    void embedsBothReferenceDiagramsAsEditableOle() throws Exception {
        ReferenceEditableVisioWordExample.main(new String[0]);
        Path docx = OUTPUT.resolve("可编辑Visio示例.docx");
        assertTwoEditableVisioObjects(docx);
    }

    /**
     * 以用户截图中的双输入结构生成另一份可编辑 Word 示例。
     *
     * @throws Exception 文件生成或检查失败时抛出
     */
    @Test
    void embedsTwoInputFlowAsEditableVisio() throws Exception {
        Path directory = Path.of("target/reference-editable-visio-word-example-two-inputs");
        ReferenceEditableVisioWordExample.main(new String[]{
                "src/test/resources/diagram/data-flow-function3.json", directory.toString()});
        assertTwoEditableVisioObjects(directory.resolve("可编辑Visio示例.docx"));
    }

    /** 检查 Word 中确实保存了两张可编辑的 Visio 对象。 */
    private void assertTwoEditableVisioObjects(Path docx) throws Exception {
        assertTrue(Files.size(docx) > 0);

        Document document = new Document(docx.toString());
        NodeCollection shapes = document.getChildNodes(NodeType.SHAPE, true);
        int embeddedCount = 0;
        for (int index = 0; index < shapes.getCount(); index++) {
            Shape shape = (Shape) shapes.get(index);
            if (shape.getOleFormat() == null) {
                continue;
            }
            embeddedCount++;
            assertFalse(shape.getOleFormat().isLink());
            assertTrue(shape.getOleFormat().getProgId().startsWith("Visio.Drawing"),
                    "OLE 对象必须被 Word 标识为 Visio 绘图");
            assertTrue(shape.getOleFormat().getRawData().length > 0);
            assertNotNull(shape.getImageData());
            assertTrue(shape.getWidth() > 0);
            assertTrue(shape.getHeight() > 0);
        }
        assertEquals(2, embeddedCount, "必须是两张可编辑 Visio OLE 图，而不是普通 PNG");

        try (ZipFile packageFile = new ZipFile(docx.toFile())) {
            long embeddings = packageFile.stream()
                    .filter(entry -> entry.getName().startsWith("word/embeddings/"))
                    .count();
            assertEquals(2, embeddings, "DOCX 包内应有两个嵌入对象");
        }
    }
}
