package cn.bugstack.export.core;

import cn.bugstack.export.api.ReportExportException;
import cn.bugstack.export.api.ReportExportStage;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.export.api.ReportRequest;
import cn.bugstack.export.api.ReportResult;
import cn.bugstack.export.definition.AbstractReportDefinition;
import cn.bugstack.export.definition.ModuleSlot;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.document.ReportDocument;
import cn.bugstack.export.document.ReportSectionBuilder;
import cn.bugstack.export.module.AbstractReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportConditionRegistry;
import cn.bugstack.export.module.ReportDataContext;
import cn.bugstack.export.module.ReportDataKey;
import cn.bugstack.export.module.ReportModuleContext;
import cn.bugstack.export.module.ReportModuleRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultReportExporterTest {

    @Test
    void exportsSemanticDocumentThroughStableLifecycle() {
        EchoModule module = new EchoModule();
        ReportModuleRegistry registry = new ReportModuleRegistry().register(module);
        RecordingRenderer renderer = new RecordingRenderer();
        DefaultReportExporter exporter = new DefaultReportExporter(registry,
                new ReportPlanner(registry, new ReportConditionRegistry()), new ReportDocumentValidator(), renderer);

        ReportResult result = exporter.export(ReportRequest.<String>builder()
                .definition(new EchoDefinition())
                .input("report-content")
                .outputFormat(ReportOutputFormat.DOCX)
                .build(), Path.of("target", "lifecycle.docx"));

        assertEquals("echo-report", result.getReportCode());
        assertEquals(ReportOutputFormat.DOCX, result.getOutputFormat());
        assertTrue(result.getStageDurationsMillis().containsKey(ReportExportStage.PLAN.name()));
        assertTrue(result.getStageDurationsMillis().containsKey(ReportExportStage.RENDER.name()));
        assertEquals("Echo Report", renderer.document.getTitle());
        assertEquals("report-content", ((cn.bugstack.export.document.ReportParagraph)
                renderer.document.getSections().get(0).getElements().get(0)).getText());
    }

    @Test
    void reportsModuleCodeWhenModuleCompositionFails() {
        BrokenModule module = new BrokenModule();
        ReportModuleRegistry registry = new ReportModuleRegistry().register(module);
        DefaultReportExporter exporter = new DefaultReportExporter(registry,
                new ReportPlanner(registry, new ReportConditionRegistry()), new ReportDocumentValidator(), new RecordingRenderer());

        ReportExportException exception = assertThrows(ReportExportException.class,
                () -> exporter.export(ReportRequest.<String>builder()
                        .definition(new BrokenDefinition())
                        .input("data")
                        .build(), Path.of("target", "broken.docx")));

        assertEquals(ReportExportStage.COMPOSE, exception.getStage());
        assertEquals("broken", exception.getModuleCode());
    }

    @Test
    void validatorRejectsPartiallyConfiguredImageSize() {
        ReportDocument document = new ReportDocument();
        document.setTitle("Report");
        ReportSectionBuilder section = ReportSectionBuilder.section("Section")
                .image("diagram.png", 120, null, null);
        document.getSections().add(section.build());

        assertFalse(new ReportDocumentValidator().validate(document).isEmpty());
    }

    @Test
    void exportsBytesThroughSameModuleLifecycle() {
        EchoModule module = new EchoModule();
        ReportModuleRegistry registry = new ReportModuleRegistry().register(module);
        RecordingRenderer renderer = new RecordingRenderer();
        DefaultReportExporter exporter = new DefaultReportExporter(registry,
                new ReportPlanner(registry, new ReportConditionRegistry()), new ReportDocumentValidator(), renderer);

        byte[] content = exporter.exportToBytes(ReportRequest.<String>builder()
                .definition(new EchoDefinition())
                .input("byte-content")
                .outputFormat(ReportOutputFormat.DOCX)
                .build());

        assertArrayEquals(new byte[]{1, 2, 3}, content);
        assertEquals("byte-content", ((cn.bugstack.export.document.ReportParagraph)
                renderer.document.getSections().get(0).getElements().get(0)).getText());
    }

    @Test
    void rejectsPlannerThatUsesDifferentModuleRegistry() {
        ReportModuleRegistry exporterRegistry = new ReportModuleRegistry().register(new EchoModule());
        ReportModuleRegistry plannerRegistry = new ReportModuleRegistry().register(new EchoModule());

        assertThrows(IllegalArgumentException.class, () -> new DefaultReportExporter(exporterRegistry,
                new ReportPlanner(plannerRegistry, new ReportConditionRegistry()),
                new ReportDocumentValidator(), new RecordingRenderer()));
    }

    private static final class EchoDefinition extends AbstractReportDefinition<String> {

        private EchoDefinition() {
            super("echo-report", "Echo Report", "1.0");
        }

        @Override
        protected void configure(ReportBlueprint.Builder builder, String input) {
            builder.module(ModuleSlot.builder(EchoModule.CODE).build());
        }

        @Override
        public void contributeData(ReportDataContext context, String input) {
            context.put(EchoModule.DATA_KEY, input);
        }
    }

    private static final class EchoModule extends AbstractReportModule<String> {

        private static final String CODE = "echo";
        private static final ReportDataKey<String> DATA_KEY = ReportDataKey.of("echo-data", String.class);
        private static final ModuleDescriptor<String> DESCRIPTOR = ModuleDescriptor.of(CODE, "Echo", DATA_KEY);

        @Override
        public ModuleDescriptor<String> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        protected void composeContent(ReportSectionBuilder section, String data, ReportModuleContext context) {
            section.paragraph(data);
        }
    }

    private static final class BrokenDefinition extends AbstractReportDefinition<String> {

        private BrokenDefinition() {
            super("broken-report", "Broken Report", "1.0");
        }

        @Override
        protected void configure(ReportBlueprint.Builder builder, String input) {
            builder.module(ModuleSlot.builder(BrokenModule.CODE).build());
        }

        @Override
        public void contributeData(ReportDataContext context, String input) {
            context.put(BrokenModule.DATA_KEY, input);
        }
    }

    private static final class BrokenModule extends AbstractReportModule<String> {

        private static final String CODE = "broken";
        private static final ReportDataKey<String> DATA_KEY = ReportDataKey.of("broken-data", String.class);
        private static final ModuleDescriptor<String> DESCRIPTOR = ModuleDescriptor.of(CODE, "Broken", DATA_KEY);

        @Override
        public ModuleDescriptor<String> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        protected void composeContent(ReportSectionBuilder section, String data, ReportModuleContext context) {
            throw new IllegalStateException("expected module failure");
        }
    }

    private static final class RecordingRenderer implements ReportDocumentRenderer {

        private ReportDocument document;

        @Override
        public void render(ReportDocument document, ReportBlueprint blueprint, ReportOutputFormat format, Path outputPath) {
            this.document = document;
        }

        @Override
        public byte[] renderToBytes(ReportDocument document, ReportBlueprint blueprint, ReportOutputFormat format) {
            this.document = document;
            return new byte[]{1, 2, 3};
        }
    }
}
