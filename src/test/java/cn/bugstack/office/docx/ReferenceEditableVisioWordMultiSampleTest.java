package cn.bugstack.office.docx;

import cn.bugstack.office.docx.example.ReferenceEditableVisioWordExample;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.SaveFileFormat;
import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多样本验证两种参考 VSDX 写入 Word 后仍为可编辑 OLE 图形。
 *
 * <p>覆盖最少输入、奇偶输入和超出原固定数量的宽图；同时回读 VSDX、DOCX 及
 * Word 内嵌对象，避免只检查 PNG 预览而漏掉实际绘图文件。</p>
 */
class ReferenceEditableVisioWordMultiSampleTest {

    /** 可直接检查每组 VSDX、预览图和 Word 的测试输出目录。 */
    private static final Path OUTPUT = Path.of("target/reference-editable-visio-multi-sample");

    /**
     * 生成五组样本并逐一验证 Word 的 VSDX 数据和版面尺寸。
     *
     * @throws Exception 当生成、封装或回读任一文件失败时抛出
     */
    @Test
    void embedsOneTwoThreeFourAndNineInputDiagramsWithoutLosingVisioData() throws Exception {
        for (int count : new int[]{1, 2, 3, 4, 9}) {
            Path directory = OUTPUT.resolve(count + "-inputs");
            Files.createDirectories(directory);
            Path input = createInput(directory, count);
            ReferenceEditableVisioWordExample.main(new String[]{input.toString(), directory.toString()});
            verifySample(directory, count);
        }
    }

    /** 生成与真实调用方相同的简化业务 JSON。 */
    private Path createInput(Path directory, int count) throws Exception {
        ObjectNode root = new ObjectMapper().createObjectNode();
        root.put("function", "库管理");
        ArrayNode inputs = root.putArray("inputs");
        for (int index = 1; index <= count; index++) inputs.add("数据" + index);
        Path input = directory.resolve("input.json");
        new ObjectMapper().writeValue(input.toFile(), root);
        return input;
    }

    /** 回读绘图、Word 和压缩包，验证实际嵌入对象与预览显示尺寸。 */
    private void verifySample(Path directory, int count) throws Exception {
        Path[] diagrams = {directory.resolve("数据流图.vsdx"), directory.resolve("库管理流程图.vsdx")};
        Path[] previews = {directory.resolve("数据流图.preview.png"),
                directory.resolve("库管理流程图.preview.png")};
        for (Path diagramPath : diagrams) {
            assertValidZip(diagramPath);
            Diagram diagram = new Diagram(diagramPath.toString());
            try {
                assertEquals(1, diagram.getPages().getCount());
                Path rendered = diagramPath.resolveSibling(diagramPath.getFileName() + ".visio.png");
                diagram.save(rendered.toString(), SaveFileFormat.PNG);
                BufferedImage renderedImage = ImageIO.read(rendered.toFile());
                assertNotNull(renderedImage, "VSDX must render after being read back");
                assertTrue(renderedImage.getWidth() > 0 && renderedImage.getHeight() > 0);
                for (int index = 1; index <= count; index++) {
                    String expected = "数据" + index;
                    boolean present = false;
                    for (int shapeIndex = 0; shapeIndex < diagram.getPages().getPage(0)
                            .getShapes().getCount(); shapeIndex++) {
                        present |= expected.equals(diagram.getPages().getPage(0)
                                .getShapes().get(shapeIndex).getPureText());
                    }
                    assertTrue(present, diagramPath + " misses " + expected);
                }
            } finally {
                diagram.dispose();
            }
        }

        Path word = directory.resolve("可编辑Visio示例.docx");
        assertValidZip(word);
        Document document = new Document(word.toString());
        assertEquals(2, document.getPageCount(), "both diagrams must remain on their own Word pages");
        NodeCollection shapes = document.getChildNodes(NodeType.SHAPE, true);
        List<Shape> embedded = new ArrayList<>();
        for (int index = 0; index < shapes.getCount(); index++) {
            Shape shape = (Shape) shapes.get(index);
            if (shape.getOleFormat() != null) embedded.add(shape);
        }
        assertEquals(2, embedded.size(), "each sample must embed both editable Visio diagrams");
        for (int index = 0; index < embedded.size(); index++) {
            Shape shape = embedded.get(index);
            assertFalse(shape.getOleFormat().isLink());
            assertTrue(shape.getOleFormat().getProgId().startsWith("Visio.Drawing"));
            assertTrue(containsBytes(shape.getOleFormat().getRawData(),
                    Files.readAllBytes(diagrams[index])),
                    "Word OLE container must retain the complete original VSDX bytes");
            assertNotNull(shape.getImageData());
            assertTrue(shape.getImageData().hasImage());
            BufferedImage preview = ImageIO.read(previews[index].toFile());
            assertNotNull(preview);
            assertTrue(shape.getWidth() <= 460.01D && shape.getHeight() <= 460.01D,
                    "diagram preview must fit within the Word page's configured bounds");
            assertEquals((double) preview.getWidth() / preview.getHeight(),
                    shape.getWidth() / shape.getHeight(), 0.01D,
                    "Word must preserve the Visio preview's aspect ratio");
        }
        try (ZipFile packageFile = new ZipFile(word.toFile())) {
            assertEquals(2, packageFile.stream()
                    .filter(entry -> entry.getName().startsWith("word/embeddings/"))
                    .count());
            assertEquals(2, packageFile.stream()
                    .filter(entry -> entry.getName().startsWith("word/media/"))
                    .count());
        }
    }

    /** 读取每个压缩条目以验证 VSDX 和 DOCX 包的 CRC，而非只检查文件扩展名。 */
    private void assertValidZip(Path path) throws Exception {
        assertTrue(Files.size(path) > 0L);
        try (ZipFile zip = new ZipFile(path.toFile())) {
            assertTrue(zip.size() > 0);
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                if (!entry.isDirectory()) {
                    try (InputStream content = zip.getInputStream(entry)) {
                        CRC32 crc = new CRC32();
                        crc.update(content.readAllBytes());
                        assertEquals(entry.getCrc(), crc.getValue(), path + ": " + entry.getName());
                    }
                }
            }
        }
    }

    /** Word 以 OLE 容器包装原文件，需查找其中完整保留的 VSDX 字节段。 */
    private boolean containsBytes(byte[] container, byte[] source) {
        for (int offset = 0; offset <= container.length - source.length; offset++) {
            int index = 0;
            while (index < source.length && container[offset + index] == source[index]) index++;
            if (index == source.length) return true;
        }
        return false;
    }
}
