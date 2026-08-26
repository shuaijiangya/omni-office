package cn.bugstack.application.document;

import cn.bugstack.protocol.document.DocumentMetadataSpec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.DiagramBlockSpec;
import cn.bugstack.protocol.document.block.ChartBlockSpec;
import cn.bugstack.protocol.document.block.ChartSeriesSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import cn.bugstack.protocol.document.block.TableMergeSpec;
import cn.bugstack.protocol.document.block.TextRangeSpec;
import cn.bugstack.protocol.document.block.TextRangeStyleSpec;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentSpecValidatorTest {

    @Test
    void acceptsAValidDocumentSpec() {
        DocumentSpec spec = validSpec();

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertTrue(result.isValid(), () -> result.getViolations().toString());
    }

    @Test
    void reportsPrecisePathsForStructuralErrors() {
        DocumentSpec spec = validSpec();
        spec.getMetadata().setTitle(" ");
        TableBlockSpec table = new TableBlockSpec();
        table.setHeaders(Arrays.asList("A", "B"));
        table.setRows(Collections.singletonList(Collections.singletonList("only one cell")));
        spec.getSections().get(0).addBlock(table);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> "/metadata/title".equals(v.getPath()) && "REQUIRED".equals(v.getCode())));
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> "/sections/0/blocks/1/rows/0".equals(v.getPath())
                        && "COLUMN_MISMATCH".equals(v.getCode())));
    }

    @Test
    void enforcesRecursiveSectionDepthLimit() {
        DocumentSpec spec = validSpec();
        SubsectionBlockSpec current = new SubsectionBlockSpec("level-2");
        spec.getSections().get(0).addBlock(current);
        for (int depth = 3; depth <= 10; depth++) {
            SubsectionBlockSpec child = new SubsectionBlockSpec("level-" + depth);
            current.addBlock(child);
            current = child;
        }

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> "LIMIT_EXCEEDED".equals(v.getCode())
                        && v.getMessage().contains("section depth")));
    }

    @Test
    void reservesDiagramBlockUntilM2CapabilityIsInstalled() {
        DocumentSpec spec = validSpec();
        DiagramBlockSpec diagram = new DiagramBlockSpec();
        diagram.setDiagramArtifactId("diagram-001");
        spec.getSections().get(0).addBlock(diagram);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertFalse(result.isValid());
        assertEquals("CAPABILITY_NOT_AVAILABLE", result.getViolations().stream()
                .filter(v -> v.getPath().endsWith("/blocks/1"))
                .findFirst().orElseThrow().getCode());
    }

    @Test
    void acceptsRectangularMergeWithEmptyFollowerCells() {
        DocumentSpec spec = validSpec();
        TableBlockSpec table = new TableBlockSpec();
        table.setHeaders(Arrays.asList("分组", "名称", "状态"));
        table.setRows(Arrays.asList(
                Arrays.asList("A", "服务一", "正常"),
                Arrays.asList("", "服务二", "正常")));
        table.setMerges(Collections.singletonList(new TableMergeSpec(1, 0, 2, 1)));
        table.setAlignment("CENTER");
        table.setFontColor("#1F4E79");
        table.setCaptionPosition("ABOVE");
        spec.getSections().get(0).addBlock(table);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertTrue(result.isValid(), () -> result.getViolations().toString());
    }

    @Test
    void rejectsOverlappingMergesNonEmptyFollowersAndInvalidColor() {
        DocumentSpec spec = validSpec();
        TableBlockSpec table = new TableBlockSpec();
        table.setHeaders(Arrays.asList("A", "B", "C"));
        table.setRows(Collections.singletonList(Arrays.asList("one", "must be empty", "three")));
        table.setMerges(Arrays.asList(
                new TableMergeSpec(1, 0, 1, 2),
                new TableMergeSpec(1, 1, 1, 2)));
        table.setFontColor("red");
        spec.getSections().get(0).addBlock(table);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream().anyMatch(v -> "INVALID_FORMAT".equals(v.getCode())));
        assertTrue(result.getViolations().stream().anyMatch(v -> "OVERLAPPING_MERGE".equals(v.getCode())));
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> "MERGED_CELL_MUST_BE_EMPTY".equals(v.getCode())));
    }

    @Test
    void acceptsMultipleIndependentlyStyledTextRanges() {
        DocumentSpec spec = validSpec();
        ParagraphBlockSpec paragraph = new ParagraphBlockSpec();
        TextRangeSpec plain = new TextRangeSpec("普通文本");
        TextRangeSpec emphasized = new TextRangeSpec("强调文本");
        TextRangeStyleSpec style = new TextRangeStyleSpec();
        style.setFontFamily("Arial");
        style.setFontSize(14D);
        style.setFontColor("#C00000");
        style.setBold(true);
        style.setItalic(false);
        style.setUnderline(true);
        emphasized.setStyle(style);
        paragraph.setTextRanges(Arrays.asList(plain, emphasized));
        spec.getSections().get(0).addBlock(paragraph);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertTrue(result.isValid(), () -> result.getViolations().toString());
    }

    @Test
    void rejectsParagraphWhenTextAndTextRangesAreBothConfigured() {
        DocumentSpec spec = validSpec();
        ParagraphBlockSpec paragraph = new ParagraphBlockSpec("legacy text");
        paragraph.setTextRanges(Collections.singletonList(new TextRangeSpec("range text")));
        spec.getSections().get(0).addBlock(paragraph);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> "ONE_OF_REQUIRED".equals(v.getCode())
                        && v.getPath().endsWith("/blocks/1")));
    }

    @Test
    void acceptsMultiSeriesComparisonChart() {
        DocumentSpec spec = validSpec();
        ChartBlockSpec chart = new ChartBlockSpec();
        chart.setChartType("COLUMN");
        chart.setCategories(Arrays.asList("第一季度", "第二季度"));
        ChartSeriesSpec previous = new ChartSeriesSpec();
        previous.setName(null);
        previous.setValues(Arrays.asList(10D, 12D));
        ChartSeriesSpec current = new ChartSeriesSpec();
        current.setName("");
        current.setValues(Arrays.asList(14D, 18D));
        chart.setSeries(Arrays.asList(previous, current));
        chart.setCategoryAxisTitle(null);
        chart.setValueAxisTitle("");
        spec.getSections().get(0).addBlock(chart);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertTrue(result.isValid(), () -> result.getViolations().toString());
    }

    @Test
    void acceptsHorizontalSingleMetricSingleSampleComparisonChart() {
        DocumentSpec spec = validSpec();
        ChartBlockSpec chart = new ChartBlockSpec();
        chart.setChartType("BAR");
        chart.setCategories(Collections.singletonList("任务完成率（%）"));
        ChartSeriesSpec sample = new ChartSeriesSpec();
        sample.setName("");
        sample.setValues(Collections.singletonList(92D));
        chart.setSeries(Collections.singletonList(sample));
        chart.setLegendVisible(false);
        chart.setShowValues(true);
        spec.getSections().get(0).addBlock(chart);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertTrue(result.isValid(), () -> result.getViolations().toString());
    }

    @Test
    void rejectsInvalidPieAndMismatchedChartSeries() {
        DocumentSpec spec = validSpec();
        ChartBlockSpec chart = new ChartBlockSpec();
        chart.setChartType("PIE");
        chart.setCategories(Arrays.asList("A", "B"));
        ChartSeriesSpec first = new ChartSeriesSpec();
        first.setName("占比");
        first.setValues(Collections.singletonList(-1D));
        ChartSeriesSpec second = new ChartSeriesSpec();
        second.setName("不允许的第二系列");
        second.setValues(Arrays.asList(1D, 2D));
        chart.setSeries(Arrays.asList(first, second));
        spec.getSections().get(0).addBlock(chart);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream().anyMatch(v -> "COLUMN_MISMATCH".equals(v.getCode())));
        assertTrue(result.getViolations().stream().anyMatch(v -> "INVALID_CHART_DATA".equals(v.getCode())));
    }

    private DocumentSpec validSpec() {
        DocumentSpec spec = new DocumentSpec();
        spec.setMetadata(new DocumentMetadataSpec("Valid document"));
        spec.addSection(new SectionSpec("Overview").addBlock(new ParagraphBlockSpec("Body text")));
        return spec;
    }
}
