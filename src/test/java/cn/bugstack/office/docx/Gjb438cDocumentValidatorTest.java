package cn.bugstack.office.docx;

import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.validate.Gjb438cDocumentValidator;
import cn.bugstack.office.docx.validate.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gjb438cDocumentValidatorTest {

    @Test
    void rejectsDocumentWithoutRequiredStandardBlocks() {
        DocxDocument document = DocxDocument.create()
                .section()
                .paragraph()
                .text("只有正文")
                .end()
                .end();

        ValidationResult result = new Gjb438cDocumentValidator().validate(document.getNode());

        assertFalse(result.isValid());
        assertTrue(result.getMessages().contains("GJB 438C document must contain a cover page"));
        assertTrue(result.getMessages().contains("GJB 438C document must enable table of contents"));
    }

    @Test
    void acceptsDocumentWithRequiredStandardBlocks() {
        DocxDocument document = DocxDocument.create()
                .cover("软件设计说明", "omni-office", "V1.0")
                .revisionHistory(history -> history.revision("V1.0", "2026-07-07", "创建文档", "luojiang"))
                .approvalPage(approval -> approval.approval("编制", "张三", "2026-07-07"))
                .tableOfContents("目录", 3)
                .section()
                .heading1("范围")
                .end();

        ValidationResult result = new Gjb438cDocumentValidator().validate(document.getNode());

        assertTrue(result.isValid());
    }

    @Test
    void acceptsDynamicTemplateCoverAsCoverPage() {
        DocxDocument document = DocxDocument.create()
                .templateCover()
                .table()
                .headers("序号", "修改人", "修改时间")
                .row("1", "张三", "2026-08-14")
                .end()
                .end()
                .revisionHistory(history -> history.revision(
                        "V1.0", "2026-08-14", "创建文档", "张三"))
                .approvalPage(approval -> approval.approval(
                        "编制", "张三", "2026-08-14"))
                .tableOfContents("目录", 3)
                .section()
                .heading1("范围")
                .end();

        ValidationResult result = new Gjb438cDocumentValidator().validate(document.getNode());

        assertTrue(result.isValid());
    }
}
