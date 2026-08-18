package cn.bugstack.application.document;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentSpecJsonCodec;
import com.aspose.words.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDynamicDocumentExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsPublishedDocumentSpecToDocxAndPdfBytes() throws Exception {
        DocumentSpec spec = example();
        DefaultDynamicDocumentExporter exporter = new DefaultDynamicDocumentExporter();

        byte[] docx = exporter.exportToBytes(spec, ReportOutputFormat.DOCX);
        byte[] pdf = exporter.exportToBytes(spec, ReportOutputFormat.PDF);

        assertTrue(docx.length > 0);
        assertTrue(pdf.length > 4);
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        Document word = new Document(new ByteArrayInputStream(docx));
        assertTrue(word.getText().contains("DocumentSpec 完整能力示例"));
        assertTrue(word.getText().contains("结构化内容"));
        assertTrue(word.getText().contains("递归章节"));
    }

    @Test
    void exportsPublishedDocumentSpecToAtomicFiles() throws Exception {
        DefaultDynamicDocumentExporter exporter = new DefaultDynamicDocumentExporter();
        Path docx = tempDir.resolve("dynamic.docx");
        Path pdf = tempDir.resolve("dynamic.pdf");

        exporter.export(example(), ReportOutputFormat.DOCX, docx);
        exporter.export(example(), ReportOutputFormat.PDF, pdf);

        assertTrue(Files.size(docx) > 0);
        assertTrue(Files.size(pdf) > 0);
    }

    private DocumentSpec example() {
        InputStream input = getClass().getResourceAsStream("/document-spec/1.0/example-complete.json");
        assertNotNull(input);
        return new DocumentSpecJsonCodec().read(input);
    }
}
