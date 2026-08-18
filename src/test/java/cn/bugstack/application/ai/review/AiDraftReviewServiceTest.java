package cn.bugstack.application.ai.review;

import cn.bugstack.application.ai.AiDocumentResult;
import cn.bugstack.application.ai.AiGenerationMode;
import cn.bugstack.application.document.DefaultDynamicDocumentExporter;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentSpecJsonCodec;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDraftReviewServiceTest {

    @Test
    void requiresIndependentApprovalBeforeExport() throws Exception {
        FileAiDraftReviewService review = new FileAiDraftReviewService(
                Files.createTempDirectory("ai-review"), new DefaultDynamicDocumentExporter());
        DocumentSpec spec;
        try (InputStream input = getClass().getResourceAsStream("/document-spec/1.0/example-simple.json")) {
            spec = new DocumentSpecJsonCodec().read(input);
        }
        AiDraftRecord draft = review.submit(new AiDocumentResult(AiGenerationMode.FREEFORM_DOCUMENT,
                spec, null, null, null, 1), "author");
        assertEquals(AiDraftStatus.PENDING_REVIEW, draft.getStatus());
        assertThrows(IllegalStateException.class,
                () -> review.exportApproved(draft.getDraftId(), ReportOutputFormat.DOCX));
        assertThrows(IllegalArgumentException.class,
                () -> review.approve(draft.getDraftId(), "author", "self approval"));
        assertEquals(AiDraftStatus.APPROVED,
                review.approve(draft.getDraftId(), "reviewer", "looks good").getStatus());
        byte[] docx = review.exportApproved(draft.getDraftId(), ReportOutputFormat.DOCX);
        assertTrue(docx.length > 2 && docx[0] == 'P' && docx[1] == 'K');
        assertThrows(IllegalStateException.class,
                () -> review.reject(draft.getDraftId(), "reviewer", "late rejection"));
    }
}
