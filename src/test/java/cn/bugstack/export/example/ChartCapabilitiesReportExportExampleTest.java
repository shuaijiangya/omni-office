package cn.bugstack.export.example;

import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ChartCapabilitiesReportExportExample} 的原生图表结构回归测试。 */
class ChartCapabilitiesReportExportExampleTest {

    /**
     * 验证示例包含七个可编辑图表，并覆盖横向单指标单样本、空系列名称
     * 及无坐标轴标题场景。
     *
     * @throws Exception 示例生成或 Word 回读失败时抛出
     */
    @Test
    void exportsEditableNativeWordChartsWithOptionalNamesAndAxisTitles() throws Exception {
        ChartCapabilitiesReportExportExample.main(new String[0]);

        assertTrue(Files.exists(ChartCapabilitiesReportExportExample.OUTPUT));
        Document document = new Document(ChartCapabilitiesReportExportExample.OUTPUT.toString());
        NodeCollection shapes = document.getChildNodes(NodeType.SHAPE, true);
        List<Shape> charts = new ArrayList<>();
        for (int index = 0; index < shapes.getCount(); index++) {
            Shape shape = (Shape) shapes.get(index);
            if (shape.hasChart()) charts.add(shape);
        }

        assertEquals(7, charts.size());
        assertEquals(1, charts.get(0).getChart().getSeries().getCount());
        assertEquals(1, charts.get(1).getChart().getSeries().getCount());
        assertEquals(1, charts.get(2).getChart().getSeries().getCount());
        assertEquals(2, charts.get(3).getChart().getSeries().getCount());
        assertEquals(1, charts.get(4).getChart().getSeries().getCount());
        assertEquals(2, charts.get(5).getChart().getSeries().getCount());
        assertEquals(2, charts.get(6).getChart().getSeries().getCount());
        assertEquals(com.aspose.words.ChartType.BAR,
                charts.get(4).getChart().getSeries().get(0).getSeriesType());
        assertTrue(charts.get(0).getChart().getTitle().getText().contains("三级指标"));
        assertTrue(charts.get(4).getChart().getTitle().getText().contains("单项指标"));
    }
}
