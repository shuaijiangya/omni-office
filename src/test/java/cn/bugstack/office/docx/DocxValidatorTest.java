package cn.bugstack.office.docx;

import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.model.DocumentNode;
import cn.bugstack.office.docx.model.TableCellNode;
import cn.bugstack.office.docx.model.TableNode;
import cn.bugstack.office.docx.model.TableRowNode;
import cn.bugstack.office.docx.validate.DocxValidator;
import cn.bugstack.office.docx.validate.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxValidatorTest {

    @Test
    void emptyDocumentIsInvalid() {
        ValidationResult result = new DocxValidator().validate(new DocumentNode());

        assertFalse(result.isValid());
        assertTrue(result.getMessages().get(0).contains("section"));
    }

    @Test
    void sectionWithParagraphIsValid() {
        DocxDocument document = DocxDocument.create()
                .section()
                .paragraph()
                .text("正文")
                .end()
                .end();

        ValidationResult result = new DocxValidator().validate(document.getNode());

        assertTrue(result.isValid());
    }

    @Test
    void tableWithInconsistentCellCountsIsInvalid() {
        DocxDocument document = DocxDocument.create()
                .section()
                .table()
                .row("A", "B")
                .end()
                .end();

        TableNode table = (TableNode) document.getNode().getSections().get(0).getBlocks().get(0);
        TableRowNode inconsistent = new TableRowNode();
        inconsistent.addCell(new TableCellNode());
        table.addRow(inconsistent);

        ValidationResult result = new DocxValidator().validate(document.getNode());

        assertFalse(result.isValid());
        assertTrue(result.getMessages().get(0).contains("cell"));
    }
}
