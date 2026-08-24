package cn.bugstack.export.example.composable;

import com.aspose.words.Document;
import com.aspose.words.Field;
import com.aspose.words.FieldType;
import com.aspose.words.HeaderFooter;
import com.aspose.words.HeaderFooterType;
import com.aspose.words.NumberStyle;
import com.aspose.words.Paragraph;
import com.aspose.words.Section;
import cn.bugstack.export.example.composable.model.AssessmentCalculationAnalysisModuleData;
import cn.bugstack.export.example.composable.model.AssessmentScenarioConstructionModuleData;
import cn.bugstack.export.example.composable.model.CombatProcessAnalysisModuleData;
import cn.bugstack.export.example.composable.model.ComparisonAnalysisModuleData;
import cn.bugstack.export.example.composable.model.ComposableModuleData;
import cn.bugstack.export.example.composable.model.ComposablePageNumberFooterFormat;
import cn.bugstack.export.example.composable.model.ComposableReportCoverModel;
import cn.bugstack.export.example.composable.model.ComposableReportModuleModel;
import cn.bugstack.export.example.composable.model.ContributionRateAnalysisModuleData;
import cn.bugstack.export.example.composable.model.FunctionalOptimizationAnalysisModuleData;
import cn.bugstack.export.example.composable.model.ImpactAnalysisModuleData;
import cn.bugstack.export.example.composable.model.VulnerabilityAnalysisModuleData;
import cn.bugstack.export.example.style.CustomAssessmentStyleProfile;
import cn.bugstack.export.template.cover.DocumentModificationRecordCoverTemplate;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ComposableTextReportExporter} 的模块组合回归测试。
 */
class ComposableTextReportExporterTest {

    /** 验证业务样式画像可通过现有模块模型传入，不需要修改报告定义。 */
    @Test
    void acceptsBusinessDefinedStyleProfileFromModuleModel() {
        CustomAssessmentStyleProfile styleProfile = new CustomAssessmentStyleProfile();
        ComposableReportModuleModel modules = ComposableReportModuleModel.builder()
                .styleProfile(styleProfile)
                .module(new ImpactAnalysisModuleData("自定义样式正文"))
                .build();

        assertSame(styleProfile, new ComposableTextReportDefinition()
                .blueprint(reportInput("自定义样式报告", modules))
                .getLayout()
                .getStyleProfile());
    }

    /**
     * 验证只导出入参选择的模块，并保持调用方指定的顺序。
     *
     * @throws Exception Word 生成或读取失败时抛出
     */
    @Test
    void exportsOnlySelectedModulesInInputOrder() throws Exception {
        Path output = Path.of("target", "composable-selected-modules.docx");
        ComposableReportModuleModel modules = ComposableReportModuleModel.builder()
                .header("动态组合报告页眉")
                .module(new CombatProcessAnalysisModuleData("风险模块正文"))
                .module(new AssessmentScenarioConstructionModuleData("概述模块正文"))
                .module(new FunctionalOptimizationAnalysisModuleData("结论模块正文"))
                .build();
        ComposableReportInput input = reportInput("动态组合报告", modules);

        new ComposableTextReportExporter().export(input, output);

        assertTrue(Files.exists(output));
        Document document = new Document(output.toString());
        assertEquals(3, document.getSections().getCount());
        Section coverSection = document.getSections().get(0);
        Section tableOfContentsSection = document.getSections().get(1);
        Section moduleSection = document.getSections().get(2);
        List<String> paragraphs = paragraphTexts(moduleSection);
        assertFalse(document.getText().contains("ASSESSMENT-TEST-001"));
        assertFalse(document.getText().contains("报告编号"));
        assertFalse(document.getText().contains("生成时间"));
        assertFalse(document.getText().contains("任务阶段"));
        assertTrue(document.getText().contains("测试评估对象"));
        assertTrue(document.getText().contains("V2.0"));
        assertTrue(coverSection.getText().contains("动态组合报告"));
        assertTrue(tableOfContentsSection.getText().replace(" ", "").contains("目录"));
        assertNoBusinessHeaderFooter(coverSection, document);
        HeaderFooter tableOfContentsFooter = tableOfContentsSection.getHeadersFooters()
                .getByHeaderFooterType(HeaderFooterType.FOOTER_PRIMARY);
        HeaderFooter moduleFooter = moduleSection.getHeadersFooters()
                .getByHeaderFooterType(HeaderFooterType.FOOTER_PRIMARY);
        HeaderFooter moduleHeader = moduleSection.getHeadersFooters()
                .getByHeaderFooterType(HeaderFooterType.HEADER_PRIMARY);
        assertNotNull(tableOfContentsFooter);
        assertNotNull(moduleFooter);
        assertNotNull(moduleHeader);
        assertFalse(tableOfContentsFooter.getText().contains("目录"));
        assertFalse(tableOfContentsFooter.getText().contains("第"));
        assertFalse(moduleFooter.getText().contains("第"));
        assertTrue(moduleHeader.getText().contains("动态组合报告页眉"));
        assertFalse(tableOfContentsFooter.isLinkedToPrevious());
        assertFalse(moduleFooter.isLinkedToPrevious());
        assertTrue(hasPageNumberField(tableOfContentsFooter));
        assertTrue(hasPageNumberField(moduleFooter));
        assertTrue(tableOfContentsSection.getPageSetup().getRestartPageNumbering());
        assertEquals(1, tableOfContentsSection.getPageSetup().getPageStartingNumber());
        assertEquals(NumberStyle.UPPERCASE_ROMAN,
                tableOfContentsSection.getPageSetup().getPageNumberStyle());
        assertTrue(moduleSection.getPageSetup().getRestartPageNumbering());
        assertEquals(1, moduleSection.getPageSetup().getPageStartingNumber());
        assertEquals(NumberStyle.ARABIC, moduleSection.getPageSetup().getPageNumberStyle());
        assertInOrder(paragraphs,
                "作战流程分析", "风险模块正文",
                "评估场景构设", "概述模块正文",
                "功能优化分析", "结论模块正文");
        assertFalse(paragraphs.contains("评估计算分析"));
        assertFalse(paragraphs.contains("脆弱性分析"));
    }

    /**
     * 验证八个模块都可参与同一份报告，且每个模块只写入一段正文。
     *
     * @throws Exception Word 字节读取失败时抛出
     */
    @Test
    void supportsAllEightPlainTextModules() throws Exception {
        ComposableReportModuleModel.Builder builder = ComposableReportModuleModel.builder();
        for (ComposableReportModule module : ComposableReportModule.values()) {
            builder.module(moduleData(module, module.getCode() + "-正文"));
        }

        byte[] content = new ComposableTextReportExporter().exportToBytes(
                reportInput("全模块报告", builder.build()));
        Document document = new Document(new java.io.ByteArrayInputStream(content));
        List<String> paragraphs = paragraphTexts(document.getSections().get(2));

        assertTrue(content.length > 0);
        for (ComposableReportModule module : ComposableReportModule.values()) {
            assertEquals(1, frequency(paragraphs, module.getTitle()));
            assertEquals(1, frequency(paragraphs, module.getCode() + "-正文"));
        }
    }

    /** 验证空组合和重复模块会在构建入参时被拒绝。 */
    @Test
    void rejectsInvalidModuleSelection() {
        assertThrows(IllegalArgumentException.class,
                () -> ComposableReportModuleModel.builder().build());
        assertThrows(IllegalArgumentException.class,
                () -> ComposableReportInput.builder().build());
        assertThrows(IllegalArgumentException.class,
                () -> ComposableReportModuleModel.builder().styleProfile(null));
        assertThrows(IllegalArgumentException.class,
                () -> ComposableReportModuleModel.builder()
                        .module(new AssessmentScenarioConstructionModuleData("第一次"))
                        .module(new AssessmentScenarioConstructionModuleData("第二次")));
    }

    /** 验证模块模型不设置页眉时，三个 Section 都不会产生页眉。 */
    @Test
    void supportsModulePagesWithoutHeader() throws Exception {
        ComposableReportModuleModel modules = ComposableReportModuleModel.builder()
                .module(new ImpactAnalysisModuleData("无页眉正文"))
                .build();

        byte[] content = new ComposableTextReportExporter().exportToBytes(
                reportInput("无页眉报告", modules));
        Document document = new Document(new java.io.ByteArrayInputStream(content));

        assertEquals(3, document.getSections().getCount());
        for (Section section : document.getSections()) {
            assertNoBusinessPrimaryHeader(section, document);
        }
        assertTrue(document.getSections().get(2).getText().contains("无页眉正文"));
    }

    /** 验证未设置封面时使用默认封面。 */
    @Test
    void usesDefaultCoverWhenCustomCoverIsNotConfigured() throws Exception {
        ComposableReportModuleModel modules = ComposableReportModuleModel.builder()
                .module(new ImpactAnalysisModuleData("默认封面正文"))
                .build();

        ComposableReportInput input = ComposableReportInput.builder(modules).build();
        Document document = new Document(new java.io.ByteArrayInputStream(
                new ComposableTextReportExporter().exportToBytes(input)));

        assertEquals(3, document.getSections().getCount());
        assertTrue(document.getSections().get(0).getText().contains(
                ComposableReportCoverModel.DEFAULT_DOCUMENT_NAME));
        assertTrue(document.getSections().get(0).getText().contains(
                ComposableReportCoverModel.DEFAULT_PROJECT_NAME));
        assertTrue(document.getSections().get(0).getText().contains(
                ComposableReportCoverModel.DEFAULT_VERSION));
    }

    /** 验证调用方可以保留“第 N 页”页脚外观。 */
    @Test
    void supportsChineseDecoratedPageNumberFooter() throws Exception {
        ComposableReportModuleModel modules = ComposableReportModuleModel.builder()
                .pageNumberFooterFormat(ComposablePageNumberFooterFormat.CHINESE_DECORATED)
                .module(new ImpactAnalysisModuleData("中文页脚正文"))
                .build();

        Document document = new Document(new java.io.ByteArrayInputStream(
                new ComposableTextReportExporter().exportToBytes(
                        reportInput("中文页脚报告", modules))));

        HeaderFooter tocFooter = document.getSections().get(1).getHeadersFooters()
                .getByHeaderFooterType(HeaderFooterType.FOOTER_PRIMARY);
        HeaderFooter moduleFooter = document.getSections().get(2).getHeadersFooters()
                .getByHeaderFooterType(HeaderFooterType.FOOTER_PRIMARY);
        assertTrue(tocFooter.getText().contains("第"));
        assertTrue(tocFooter.getText().contains("页"));
        assertTrue(moduleFooter.getText().contains("第"));
        assertTrue(moduleFooter.getText().contains("页"));
        assertEquals(NumberStyle.UPPERCASE_ROMAN,
                document.getSections().get(1).getPageSetup().getPageNumberStyle());
        assertEquals(NumberStyle.ARABIC,
                document.getSections().get(2).getPageSetup().getPageNumberStyle());
    }

    /** 验证用户可用正式模板替换固定三字段封面，并动态写入修改记录表格。 */
    @Test
    void supportsDynamicDocumentModificationRecordCoverTemplate() throws Exception {
        DocumentModificationRecordCoverTemplate cover =
                DocumentModificationRecordCoverTemplate.builder()
                        .documentName("评估分析报告")
                        .record("张三", "2026-08-13 10:30")
                        .record("李四", "2026-08-14 09:00")
                        .build();
        ComposableReportModuleModel modules = ComposableReportModuleModel.builder()
                .module(new ImpactAnalysisModuleData("动态封面正文"))
                .build();

        Path output = Path.of("target", "document-modification-record-cover-template.docx");
        new ComposableTextReportExporter().export(
                ComposableReportInput.builder(cover, modules).build(), output);
        Document document = new Document(output.toString());

        Section coverSection = document.getSections().get(0);
        assertTrue(Files.isRegularFile(output));
        assertEquals(3, document.getSections().getCount());
        assertEquals(1, coverSection.getBody().getTables().getCount());
        assertEquals(3, coverSection.getBody().getTables().get(0).getRows().getCount());
        assertEquals(3, coverSection.getBody().getTables().get(0).getFirstRow().getCells().getCount());
        assertTrue(coverSection.getText().contains("序号"));
        assertTrue(coverSection.getText().contains("修改人"));
        assertTrue(coverSection.getText().contains("修改时间"));
        assertTrue(coverSection.getText().contains("张三"));
        assertTrue(coverSection.getText().contains("李四"));
        assertFalse(coverSection.getText().contains("项目名称"));
        assertFalse(document.getText().contains("动态封面报告"));
        assertFalse(document.getText().contains("评估分析报告"));
        assertTrue(document.getSections().get(2).getText().contains("动态封面正文"));
    }

    /** 创建包含独立封面模型、模块模型和文档元数据的测试入参。 */
    private ComposableReportInput reportInput(String title, ComposableReportModuleModel modules) {
        ComposableReportCoverModel cover = new ComposableReportCoverModel(
                title, "测试评估对象", "V2.0");
        return ComposableReportInput.builder(cover, modules)
                .preparedBy("测试评估组")
                .build();
    }

    /** 根据模块类型创建其独立的强类型数据对象。 */
    private ComposableModuleData moduleData(ComposableReportModule module, String text) {
        switch (module) {
            case ASSESSMENT_SCENARIO_CONSTRUCTION:
                return new AssessmentScenarioConstructionModuleData(text);
            case ASSESSMENT_CALCULATION_ANALYSIS:
                return new AssessmentCalculationAnalysisModuleData(text);
            case CONTRIBUTION_RATE_ANALYSIS:
                return new ContributionRateAnalysisModuleData(text);
            case IMPACT_ANALYSIS:
                return new ImpactAnalysisModuleData(text);
            case COMPARISON_ANALYSIS:
                return new ComparisonAnalysisModuleData(text);
            case COMBAT_PROCESS_ANALYSIS:
                return new CombatProcessAnalysisModuleData(text);
            case VULNERABILITY_ANALYSIS:
                return new VulnerabilityAnalysisModuleData(text);
            case FUNCTIONAL_OPTIMIZATION_ANALYSIS:
                return new FunctionalOptimizationAnalysisModuleData(text);
            default:
                throw new IllegalArgumentException("unsupported module: " + module);
        }
    }

    /**
     * 提取 Word 中所有非空段落文本。
     *
     * @param document Word 文档
     * @return 非空段落文本
     */
    private List<String> paragraphTexts(Section section) {
        List<String> texts = new ArrayList<>();
        for (Paragraph paragraph : section.getBody().getParagraphs()) {
            String text = paragraph.getText().trim();
            if (!text.isEmpty()) {
                texts.add(text);
            }
        }
        return texts;
    }

    /** 判断指定页眉页脚中是否包含 Word PAGE 域。 */
    private boolean hasPageNumberField(HeaderFooter headerFooter) throws Exception {
        for (Field field : headerFooter.getRange().getFields()) {
            if (field.getType() == FieldType.FIELD_PAGE) {
                return true;
            }
        }
        return false;
    }

    /** 验证 Section 不含业务页眉页脚，同时兼容 Aspose.Words 评估版自动水印节点。 */
    private void assertNoBusinessHeaderFooter(Section section, Document document) throws Exception {
        if (!hasAsposeEvaluationMarker(document)) {
            assertEquals(0, section.getHeadersFooters().getCount());
            return;
        }
        for (HeaderFooter value : section.getHeadersFooters()) {
            assertTrue(withoutEvaluationMarker(value.getText()).isBlank(),
                    "unexpected header/footer content: " + value.getText());
        }
    }

    /** 验证未配置业务页眉；评估版创建的空白水印页眉不视为业务页眉。 */
    private void assertNoBusinessPrimaryHeader(Section section, Document document) throws Exception {
        HeaderFooter header = section.getHeadersFooters()
                .getByHeaderFooterType(HeaderFooterType.HEADER_PRIMARY);
        if (header == null) return;
        assertTrue(hasAsposeEvaluationMarker(document), "unexpected primary header node");
        assertTrue(withoutEvaluationMarker(header.getText()).isBlank(),
                "unexpected primary header content: " + header.getText());
    }

    /** 判断文档是否包含 Aspose.Words 评估版自动生成的明确标识。 */
    private boolean hasAsposeEvaluationMarker(Document document) throws Exception {
        for (Section section : document.getSections()) {
            for (HeaderFooter value : section.getHeadersFooters()) {
                if (value.getText().contains("Evaluation Only. Created with Aspose.Words.")) return true;
            }
        }
        return false;
    }

    /** 去除唯一允许忽略的 Aspose.Words 评估版标识及 Word 控制字符。 */
    private String withoutEvaluationMarker(String value) {
        return value.replaceAll("Evaluation Only\\. Created with Aspose\\.Words\\. "
                        + "Copyright 2003-\\d{4} Aspose Pty Ltd\\.", "")
                .replaceAll("[\\x00-\\x1F]", "")
                .trim();
    }

    /**
     * 验证若干文本按给定顺序出现，允许其间存在报告标题等其他段落。
     *
     * @param actual 实际段落
     * @param expectedInOrder 预期有序文本
     */
    private void assertInOrder(List<String> actual, String... expectedInOrder) {
        int position = -1;
        for (String expected : Arrays.asList(expectedInOrder)) {
            int relativePosition = actual.subList(position + 1, actual.size()).indexOf(expected);
            assertTrue(relativePosition >= 0,
                    "paragraph not found in order: " + expected + ", actual=" + actual);
            position += relativePosition + 1;
        }
    }

    /**
     * 统计指定段落文本出现次数。
     *
     * @param paragraphs 段落列表
     * @param expected 待统计文本
     * @return 出现次数
     */
    private long frequency(List<String> paragraphs, String expected) {
        return paragraphs.stream().filter(expected::equals).count();
    }
}
