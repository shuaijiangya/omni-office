package cn.bugstack.application.template.governance;

import cn.bugstack.protocol.template.DocumentTemplateSpec;
import cn.bugstack.protocol.template.DocumentTemplateSpecJsonCodec;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDocumentTemplateCatalogTest {

    @Test
    void persistsReviewWorkflowAndOnlyExposesPublishedVersions() throws Exception {
        java.nio.file.Path root = Files.createTempDirectory("template-governance");
        FileDocumentTemplateCatalog catalog = new FileDocumentTemplateCatalog(root);
        DocumentTemplateSpec template;
        try (InputStream input = getClass().getResourceAsStream(
                "/document-template/1.0/example-assessment-template.json")) {
            template = new DocumentTemplateSpecJsonCodec().read(input);
        }
        assertEquals(TemplateLifecycleStatus.DRAFT,
                catalog.createDraft(template, "author").getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> catalog.require(template.getTemplateId(), template.getVersion()));
        assertEquals(TemplateLifecycleStatus.IN_REVIEW,
                catalog.submit(template.getTemplateId(), template.getVersion(), "author").getStatus());
        assertEquals(TemplateLifecycleStatus.PUBLISHED,
                catalog.approve(template.getTemplateId(), template.getVersion(), "reviewer", "approved")
                        .getStatus());
        assertEquals(1, catalog.list().size());
        assertEquals(template.getName(), catalog.require(template.getTemplateId(), template.getVersion()).getName());
        assertThrows(IllegalStateException.class, () -> catalog.createDraft(template, "author"));

        FileDocumentTemplateCatalog restarted = new FileDocumentTemplateCatalog(root);
        assertEquals(1, restarted.list().size());
    }
}
