package cn.bugstack.application.document;

import cn.bugstack.export.document.ReportImage;
import cn.bugstack.export.document.CaptionPosition;
import cn.bugstack.export.document.ReportListItem;
import cn.bugstack.export.document.ReportPageBreak;
import cn.bugstack.export.document.ReportParagraph;
import cn.bugstack.export.document.ReportSection;
import cn.bugstack.export.document.ReportTable;
import cn.bugstack.protocol.document.DocumentMetadataSpec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.BulletListBlockSpec;
import cn.bugstack.protocol.document.block.ImageBlockSpec;
import cn.bugstack.protocol.document.block.PageBreakBlockSpec;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DefaultDocumentSpecCompilerTest {

    @Test
    void compilesAllM1SemanticBlocksIntoReportDocument() {
        DocumentSpec spec = new DocumentSpec();
        spec.setMetadata(new DocumentMetadataSpec("Compiler test"));
        SectionSpec section = new SectionSpec("Section");
        ParagraphBlockSpec paragraph = new ParagraphBlockSpec();
        paragraph.setStyleName("BodyText");
        paragraph.setFontColor("#112233");
        TextRangeSpec plain = new TextRangeSpec("Plain ");
        TextRangeSpec emphasized = new TextRangeSpec("Emphasized");
        TextRangeStyleSpec emphasizedStyle = new TextRangeStyleSpec();
        emphasizedStyle.setBold(true);
        emphasizedStyle.setFontColor("#C00000");
        emphasized.setStyle(emphasizedStyle);
        paragraph.setTextRanges(Arrays.asList(plain, emphasized));
        section.addBlock(paragraph);

        BulletListBlockSpec bullets = new BulletListBlockSpec();
        bullets.setItems(Arrays.asList("One", "Two"));
        section.addBlock(bullets);

        TableBlockSpec table = new TableBlockSpec();
        table.setHeaders(Arrays.asList("Key", "Value"));
        table.setRows(Collections.singletonList(Arrays.asList("K", "V")));
        table.setCaption("Table caption");
        table.setCaptionPosition("ABOVE");
        table.setAlignment("CENTER");
        table.setFontColor("#445566");
        TextRangeStyleSpec headerStyle = new TextRangeStyleSpec();
        headerStyle.setAsciiFontFamily("Arial");
        headerStyle.setFarEastFontFamily("微软雅黑");
        table.setHeaderTextStyle(headerStyle);
        TextRangeStyleSpec bodyStyle = new TextRangeStyleSpec();
        bodyStyle.setAsciiFontFamily("Calibri");
        bodyStyle.setFarEastFontFamily("仿宋");
        table.setBodyTextStyle(bodyStyle);
        table.setMerges(Collections.singletonList(new TableMergeSpec(1, 0, 1, 2)));
        section.addBlock(table);

        ImageBlockSpec image = new ImageBlockSpec();
        image.setSource("trusted-image.png");
        image.setAlternativeText("Alternative");
        image.setCaption("Image caption");
        section.addBlock(image);

        SubsectionBlockSpec subsection = new SubsectionBlockSpec("Child");
        subsection.addBlock(new ParagraphBlockSpec("Child body"));
        section.addBlock(subsection);
        section.addBlock(new PageBreakBlockSpec());
        spec.addSection(section);

        cn.bugstack.export.document.ReportDocument result = new DefaultDocumentSpecCompiler().compile(spec);

        ReportSection compiled = result.getSections().get(0);
        assertEquals("Compiler test", result.getTitle());
        assertInstanceOf(ReportParagraph.class, compiled.getElements().get(0));
        assertInstanceOf(ReportListItem.class, compiled.getElements().get(1));
        assertInstanceOf(ReportListItem.class, compiled.getElements().get(2));
        assertInstanceOf(ReportTable.class, compiled.getElements().get(3));
        assertInstanceOf(ReportImage.class, compiled.getElements().get(4));
        assertInstanceOf(ReportSection.class, compiled.getElements().get(5));
        assertInstanceOf(ReportPageBreak.class, compiled.getElements().get(6));
        assertEquals("Table caption", ((ReportTable) compiled.getElements().get(3)).getCaption().getText());
        assertEquals("#112233", ((ReportParagraph) compiled.getElements().get(0)).getFontColor());
        assertEquals(2, ((ReportParagraph) compiled.getElements().get(0)).getTextRanges().size());
        assertEquals(Boolean.TRUE, ((ReportParagraph) compiled.getElements().get(0))
                .getTextRanges().get(1).getStyle().getBold());
        assertEquals("CENTER", ((ReportTable) compiled.getElements().get(3)).getAlignment().name());
        assertEquals(1, ((ReportTable) compiled.getElements().get(3)).getMerges().size());
        assertEquals("Arial", ((ReportTable) compiled.getElements().get(3))
                .getHeaderTextStyle().getAsciiFontFamily());
        assertEquals("微软雅黑", ((ReportTable) compiled.getElements().get(3))
                .getHeaderTextStyle().getFarEastFontFamily());
        assertEquals("Calibri", ((ReportTable) compiled.getElements().get(3))
                .getBodyTextStyle().getAsciiFontFamily());
        assertEquals("仿宋", ((ReportTable) compiled.getElements().get(3))
                .getBodyTextStyle().getFarEastFontFamily());
        assertEquals(CaptionPosition.ABOVE,
                ((ReportTable) compiled.getElements().get(3)).getCaption().getPosition());
        assertEquals("Alternative", ((ReportImage) compiled.getElements().get(4)).getAlternativeText());
    }
}
