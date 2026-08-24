package cn.bugstack.office.docx;

import cn.bugstack.office.docx.style.DefaultStyles;
import cn.bugstack.office.docx.style.DocxLineSpacingRule;
import cn.bugstack.office.docx.style.DocxParagraphAlignment;
import cn.bugstack.office.docx.style.ParagraphStyle;
import cn.bugstack.office.docx.style.StyleRegistry;
import cn.bugstack.office.docx.style.TableStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleRegistryTest {

    @Test
    void defaultRegistryContainsStandardDocxStyles() {
        StyleRegistry registry = DefaultStyles.createRegistry();

        assertTrue(registry.contains("Normal"));
        assertTrue(registry.contains("Title"));
        assertTrue(registry.contains("Subtitle"));
        assertTrue(registry.contains("Heading1"));
        assertTrue(registry.contains("Heading2"));
        assertTrue(registry.contains("Heading3"));
        assertTrue(registry.contains("Heading4"));
        assertTrue(registry.contains("Heading5"));
        assertTrue(registry.contains("Heading6"));
        assertTrue(registry.contains("Heading7"));
        assertTrue(registry.contains("Heading8"));
        assertTrue(registry.contains("Heading9"));
        assertTrue(registry.contains("BodyText"));
        assertTrue(registry.contains("Caption"));
        assertTrue(registry.contains("TableNormal"));
        assertTrue(registry.contains("TableHeader"));
        assertTrue(registry.contains("TableCell"));
        assertTrue(registry.contains("ImageCaption"));
        assertTrue(registry.contains("CodeBlock"));
    }

    @Test
    void paragraphStylesAreCopiedFromRegistry() {
        StyleRegistry registry = DefaultStyles.createRegistry();

        ParagraphStyle first = registry.getParagraphStyle("BodyText");
        ParagraphStyle second = registry.getParagraphStyle("BodyText");

        assertNotSame(first, second);
        first.setFontSize(18);
        assertEquals(10.5, second.getFontSize());
    }

    @Test
    void headingStyleDefinesAsciiAndFarEastFonts() {
        StyleRegistry registry = DefaultStyles.createRegistry();

        ParagraphStyle title = registry.getParagraphStyle("Title");
        ParagraphStyle subtitle = registry.getParagraphStyle("Subtitle");
        ParagraphStyle heading1 = registry.getParagraphStyle("Heading1");
        ParagraphStyle heading9 = registry.getParagraphStyle("Heading9");

        assertEquals(DocxParagraphAlignment.LEFT, title.getAlignment());
        assertEquals(DocxParagraphAlignment.LEFT, subtitle.getAlignment());
        assertEquals(DocxParagraphAlignment.LEFT, heading1.getAlignment());
        assertEquals(DocxParagraphAlignment.LEFT, heading9.getAlignment());
        assertEquals("Times New Roman", heading1.getAsciiFontFamily());
        assertEquals("黑体", heading1.getFarEastFontFamily());
        assertEquals(9, heading9.getOutlineLevel());
        assertEquals("Times New Roman", heading9.getAsciiFontFamily());
        assertEquals("黑体", heading9.getFarEastFontFamily());
    }

    @Test
    void bodyTextDefinesParagraphLayoutStandard() {
        StyleRegistry registry = DefaultStyles.createRegistry();

        ParagraphStyle bodyText = registry.getParagraphStyle("BodyText");

        assertEquals(DocxParagraphAlignment.JUSTIFY, bodyText.getAlignment());
        assertEquals(2.0, bodyText.getCharacterUnitFirstLineIndent());
        assertEquals(0.0, bodyText.getLeftIndentPoints());
        assertEquals(0.0, bodyText.getRightIndentPoints());
        assertEquals(DocxLineSpacingRule.MULTIPLE, bodyText.getLineSpacingRule());
        assertEquals(18.0, bodyText.getLineSpacing());
        assertEquals(0.0, bodyText.getSpaceBeforePoints());
        assertEquals(0.0, bodyText.getSpaceAfterPoints());
    }

    @Test
    void tableHeaderAndBodyHaveIndependentTextStyles() {
        StyleRegistry registry = DefaultStyles.createRegistry();

        TableStyle first = registry.getTableStyle("TableHeader");
        TableStyle second = registry.getTableStyle("TableHeader");

        assertEquals("宋体", first.getHeaderTextStyle().getFontFamily());
        assertEquals("宋体", first.getBodyTextStyle().getFontFamily());
        assertEquals("Times New Roman", first.getHeaderTextStyle().getAsciiFontFamily());
        assertEquals("黑体", first.getHeaderTextStyle().getFarEastFontFamily());
        assertEquals("Times New Roman", first.getBodyTextStyle().getAsciiFontFamily());
        assertEquals("宋体", first.getBodyTextStyle().getFarEastFontFamily());
        assertEquals("#000000", first.getHeaderTextStyle().getColor());
        assertEquals("#000000", first.getBodyTextStyle().getColor());
        assertFalse(first.getHeaderTextStyle().isBold());
        assertFalse(first.getBodyTextStyle().isBold());
        assertTrue(first.isRepeatHeaderRow());

        first.getHeaderTextStyle().setFontFamily("黑体");
        assertEquals("宋体", second.getHeaderTextStyle().getFontFamily());
    }
}
