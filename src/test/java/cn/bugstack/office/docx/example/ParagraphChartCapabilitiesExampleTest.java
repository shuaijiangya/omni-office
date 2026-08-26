package cn.bugstack.office.docx.example;

import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ParagraphChartCapabilitiesExample} 的 paragraph 原生图表回归测试。 */
class ParagraphChartCapabilitiesExampleTest {

    /**
     * 验证 paragraph Builder 输出五个 Word 原生图表。
     *
     * @throws Exception 示例生成或 Word 回读失败时抛出
     */
    @Test
    void exportsFiveNativeChartsFromParagraphBuilder() throws Exception {
        ParagraphChartCapabilitiesExample.main(new String[0]);

        assertTrue(Files.exists(ParagraphChartCapabilitiesExample.OUTPUT));
        Document document = new Document(ParagraphChartCapabilitiesExample.OUTPUT.toString());
        NodeCollection shapes = document.getChildNodes(NodeType.SHAPE, true);
        int chartCount = 0;
        int comparisonSeriesCount = 0;
        for (int index = 0; index < shapes.getCount(); index++) {
            Shape shape = (Shape) shapes.get(index);
            if (!shape.hasChart()) continue;
            chartCount++;
            if (shape.getChart().getTitle().getText().contains("年度业务指标对比")) {
                comparisonSeriesCount = shape.getChart().getSeries().getCount();
            }
        }

        assertEquals(5, chartCount);
        assertEquals(2, comparisonSeriesCount);
    }
}
