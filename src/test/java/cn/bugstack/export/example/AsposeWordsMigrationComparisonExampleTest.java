package cn.bugstack.export.example;

import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import com.aspose.words.Table;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link AsposeWordsMigrationComparisonExample} 的双路径结构回归测试。 */
class AsposeWordsMigrationComparisonExampleTest {

    /**
     * 验证 Aspose 原生写法与框架写法均生成表格、原生图表和关键业务内容。
     *
     * @throws Exception 示例生成或 Word 回读失败时抛出
     */
    @Test
    void generatesSemanticallyEquivalentAsposeAndFrameworkDocuments() throws Exception {
        AsposeWordsMigrationComparisonExample.main(new String[0]);

        assertTrue(Files.exists(AsposeWordsMigrationComparisonExample.ASPOSE_OUTPUT));
        assertTrue(Files.exists(AsposeWordsMigrationComparisonExample.FRAMEWORK_OUTPUT));
        Document aspose = new Document(AsposeWordsMigrationComparisonExample.ASPOSE_OUTPUT.toString());
        Document framework = new Document(AsposeWordsMigrationComparisonExample.FRAMEWORK_OUTPUT.toString());

        assertDocumentContract(aspose);
        assertDocumentContract(framework);
        assertEquals(aspose.getBuiltInDocumentProperties().getTitle(),
                framework.getBuiltInDocumentProperties().getTitle());
    }

    /** 校验两种实现必须共同满足的 Word 结构契约。 */
    private void assertDocumentContract(Document document) throws Exception {
        String text = document.getText();
        assertTrue(text.contains("Aspose.Words to omni-office Migration Comparison"));
        assertTrue(text.contains("business modules no longer manipulate the Word cursor"));
        assertTrue(text.contains("ReportDefinition + ReportModule"));

        NodeCollection tables = document.getChildNodes(NodeType.TABLE, true);
        assertEquals(1, tables.getCount());
        assertEquals(3, ((Table) tables.get(0)).getRows().getCount());

        List<Shape> charts = new ArrayList<>();
        NodeCollection shapes = document.getChildNodes(NodeType.SHAPE, true);
        for (int index = 0; index < shapes.getCount(); index++) {
            Shape shape = (Shape) shapes.get(index);
            if (shape.hasChart()) charts.add(shape);
        }
        assertEquals(1, charts.size());
        assertEquals(2, charts.get(0).getChart().getSeries().getCount());
        assertTrue(charts.get(0).getChart().getTitle().getText().contains("Annual business metric"));
    }
}
