package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import cn.bugstack.office.diagram.model.DiagramType;
import com.aspose.diagram.Diagram;
import com.aspose.diagram.Shape;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定“库管理”流程图的 JSON 到 Visio 与 PNG 测试。
 *
 * <p>与数据流图复用相同业务 JSON，仅图模板不同。输出保存在
 * {@code target/library-flow-template-example} 以便直接查看。</p>
 */
class ReferenceLibraryFlowRendererTest {

    /** 功能3及数据1至数据3的共享输入。 */
    private static final Path INPUT = Path.of(
            "src/test/resources/diagram/data-flow-function3-three-inputs.json");
    /** 可查看的固定流程图输出目录。 */
    private static final Path OUTPUT = Path.of("target/library-flow-template-example/功能3-库管理流程图.vsdx");

    /**
     * 验证菜单栏、功能3、三份数据、增删改查及结束按参考图生成可编辑 Visio。
     *
     * @throws Exception JSON、VSDX 或 PNG 处理失败时抛出
     */
    @Test
    void rendersFixedLibraryFlowFromTheSameThreeInputJson() throws Exception {
        VisioDiagramArtifact artifact = new ReferenceLibraryFlowRenderer().render(INPUT, OUTPUT);
        assertEquals(DiagramType.FLOW, artifact.getType());
        assertTrue(Files.size(artifact.getVsdxPath()) > 0L);
        assertTrue(Files.size(artifact.getPreviewPngPath()) > 0L);

        BufferedImage preview = ImageIO.read(artifact.getPreviewPngPath().toFile());
        assertEquals(1200, preview.getWidth());
        assertEquals(1150, preview.getHeight());
        assertEquals(Color.WHITE.getRGB(), preview.getRGB(20, 20));
        assertEquals(Color.BLACK.getRGB(), preview.getRGB(600, 365));

        Diagram loaded = new Diagram(artifact.getVsdxPath().toString());
        try {
            Set<String> labels = new HashSet<>();
            boolean hasData1ToOperations = false;
            boolean hasData3ToOperations = false;
            int operationDividers = 0;
            int sharedArrowSegments = 0;
            for (int index = 0; index < loaded.getPages().getPage(0).getShapes().getCount(); index++) {
                Shape shape = loaded.getPages().getPage(0).getShapes().get(index);
                labels.add(shape.getPureText());
                String name = shape.getNameU();
                hasData1ToOperations |= name.startsWith("LibraryLine_Data1ToOperations_");
                hasData3ToOperations |= name.startsWith("LibraryLine_Data3ToOperations_");
                if (name.startsWith("LibraryLine_OperationDivider")) operationDividers++;
                if (name.startsWith("LibraryLine_CollectorToOperationsArrow_")) sharedArrowSegments++;
            }
            for (String label : new String[]{"菜单栏", "功能3", "数据1", "数据2", "数据3",
                    "增", "删", "改", "查", "结束"}) {
                assertTrue(labels.contains(label), "missing label: " + label);
            }
            assertTrue(hasData1ToOperations);
            assertTrue(hasData3ToOperations);
            assertEquals(3, operationDividers);
            assertEquals(2, sharedArrowSegments);
        } finally {
            loaded.dispose();
        }
    }
}
