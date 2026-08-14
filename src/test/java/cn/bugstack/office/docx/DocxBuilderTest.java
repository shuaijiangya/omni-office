package cn.bugstack.office.docx;

import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.model.CaptionNode;
import cn.bugstack.office.docx.model.CaptionType;
import cn.bugstack.office.docx.model.CaptionRefInline;
import cn.bugstack.office.docx.model.CoverPageNode;
import cn.bugstack.office.docx.model.DocumentNode;
import cn.bugstack.office.docx.model.ImageInline;
import cn.bugstack.office.docx.model.ParagraphListType;
import cn.bugstack.office.docx.model.ParagraphNode;
import cn.bugstack.office.docx.model.RevisionHistoryNode;
import cn.bugstack.office.docx.model.SectionNode;
import cn.bugstack.office.docx.model.TableCellNode;
import cn.bugstack.office.docx.model.TableCellVerticalAlignment;
import cn.bugstack.office.docx.model.TableNode;
import cn.bugstack.office.docx.model.TableVerticalMerge;
import cn.bugstack.office.docx.model.VisioInline;
import cn.bugstack.office.docx.model.DocxPageOrientation;
import cn.bugstack.office.docx.model.DocxPaperSize;
import cn.bugstack.office.docx.style.Gjb438cStyleProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DocxBuilderTest {

    @Test
    void buildsDocumentTreeWithBlockAndInlineBoundaries() {
        DocxDocument document = DocxDocument.create()
                .useDefaultStyles()
                .section()
                .heading1("系统架构设计")
                .paragraph()
                .text("流程如下：")
                .image("architecture.png")
                .visio("process-preview.png")
                .end()
                .table()
                .headers("模块", "职责")
                .row("Document", "文档入口")
                .row(row -> row
                        .cell(cell -> cell.paragraph().text("架构图").image("module.png").end())
                        .cell(cell -> cell.paragraph().text("说明").end()))
                .end()
                .end();

        DocumentNode root = document.getNode();
        assertEquals(1, root.getSections().size());

        SectionNode section = root.getSections().get(0);
        assertEquals(3, section.getBlocks().size());
        assertInstanceOf(ParagraphNode.class, section.getBlocks().get(0));
        assertInstanceOf(ParagraphNode.class, section.getBlocks().get(1));
        assertInstanceOf(TableNode.class, section.getBlocks().get(2));

        ParagraphNode paragraph = (ParagraphNode) section.getBlocks().get(1);
        assertEquals(3, paragraph.getInlines().size());
        assertInstanceOf(ImageInline.class, paragraph.getInlines().get(1));
        assertInstanceOf(VisioInline.class, paragraph.getInlines().get(2));

        TableNode table = (TableNode) section.getBlocks().get(2);
        assertEquals(3, table.getRows().size());
        TableCellNode cell = table.getRows().get(2).getCells().get(0);
        assertEquals(1, cell.getBlocks().size());
        assertInstanceOf(ParagraphNode.class, cell.getBlocks().get(0));
    }

    @Test
    void buildsCaptionAsSectionBlock() {
        DocxDocument document = DocxDocument.create()
                .section()
                .figureCaption("系统架构图")
                .tableCaption("模块清单")
                .end();

        SectionNode section = document.getNode().getSections().get(0);

        assertEquals(2, section.getBlocks().size());
        CaptionNode figureCaption = (CaptionNode) section.getBlocks().get(0);
        CaptionNode tableCaption = (CaptionNode) section.getBlocks().get(1);

        assertEquals(CaptionType.FIGURE, figureCaption.getType());
        assertEquals("系统架构图", figureCaption.getText());
        assertEquals(CaptionType.TABLE, tableCaption.getType());
        assertEquals("模块清单", tableCaption.getText());
    }

    @Test
    void supportsHeadingLevelsOneToNine() {
        DocxDocument document = DocxDocument.create()
                .section()
                .heading1("一级")
                .heading2("二级")
                .heading3("三级")
                .heading4("四级")
                .heading5("五级")
                .heading6("六级")
                .heading7("七级")
                .heading8("八级")
                .heading9("九级")
                .end();

        SectionNode section = document.getNode().getSections().get(0);

        assertEquals(9, section.getBlocks().size());
        for (int i = 0; i < 9; i++) {
            ParagraphNode paragraph = (ParagraphNode) section.getBlocks().get(i);
            assertEquals("Heading" + (i + 1), paragraph.getStyleName());
        }
    }

    @Test
    void storesDocumentLevelOptionsForStandardDocuments() {
        DocxDocument document = DocxDocument.create()
                .useStyleProfile(Gjb438cStyleProfile.standard())
                .enableHeadingNumbering()
                .tableOfContents("目录", 3)
                .header("GJB 438C 软件开发文档")
                .footer("第 PAGE 页")
                .section()
                .heading1("范围")
                .end();

        DocumentNode root = document.getNode();

        assertEquals(true, root.isHeadingNumberingEnabled());
        assertEquals(true, root.isTableOfContentsEnabled());
        assertEquals("目录", root.getTableOfContentsTitle());
        assertEquals(3, root.getTableOfContentsDepth());
        assertEquals("GJB 438C 软件开发文档", root.getHeaderText());
        assertEquals("第 PAGE 页", root.getFooterText());
    }

    @Test
    void storesTableStyleAndImageSizeOnNodes() {
        DocxDocument document = DocxDocument.create()
                .section()
                .paragraph()
                .image("diagram.png", 240, 120)
                .end()
                .table()
                .style("TableHeader")
                .headers("模块", "职责")
                .end()
                .end();

        SectionNode section = document.getNode().getSections().get(0);
        ParagraphNode paragraph = (ParagraphNode) section.getBlocks().get(0);
        ImageInline image = (ImageInline) paragraph.getInlines().get(0);
        TableNode table = (TableNode) section.getBlocks().get(1);

        assertEquals(240.0, image.getWidthPoints());
        assertEquals(120.0, image.getHeightPoints());
        assertEquals("TableHeader", table.getStyleName());
    }

    @Test
    void storesGjb438cFrontMatterBlocks() {
        DocxDocument document = DocxDocument.create()
                .cover("软件设计说明", "omni-office", "V1.0")
                .revisionHistory(history -> history.revision("V1.0", "2026-07-07", "创建文档", "luojiang"))
                .approvalPage(approval -> approval.approval("编制", "张三", "2026-07-07"))
                .section()
                .heading1("范围")
                .end();

        DocumentNode root = document.getNode();
        CoverPageNode cover = (CoverPageNode) root.getFrontMatterBlocks().get(0);
        RevisionHistoryNode history = (RevisionHistoryNode) root.getFrontMatterBlocks().get(1);

        assertEquals(3, root.getFrontMatterBlocks().size());
        assertEquals("软件设计说明", cover.getDocumentName());
        assertEquals("omni-office", cover.getProjectName());
        assertEquals("V1.0", cover.getVersion());
        assertEquals("创建文档", history.getRecords().get(0).getDescription());
    }

    @Test
    void storesListAndCaptionReferenceInlineNodes() {
        DocxDocument document = DocxDocument.create()
                .section()
                .figureCaption("arch", "系统架构图")
                .bullet("支持封面、目录和修订记录")
                .numbered("按章节组织内容")
                .paragraph()
                .text("详见")
                .captionRef(CaptionType.FIGURE, "arch")
                .end()
                .end();

        SectionNode section = document.getNode().getSections().get(0);
        ParagraphNode bullet = (ParagraphNode) section.getBlocks().get(1);
        ParagraphNode numbered = (ParagraphNode) section.getBlocks().get(2);
        ParagraphNode reference = (ParagraphNode) section.getBlocks().get(3);

        assertEquals(ParagraphListType.BULLET, bullet.getListType());
        assertEquals(ParagraphListType.NUMBER, numbered.getListType());
        assertInstanceOf(CaptionRefInline.class, reference.getInlines().get(1));
    }

    @Test
    void storesTableColumnWidthsAndCellColspan() {
        DocxDocument document = DocxDocument.create()
                .section()
                .table()
                .widths(120, 240, 120)
                .row(row -> row
                        .cell(2, cell -> cell.paragraph().text("跨两列").end())
                        .cell(cell -> cell.paragraph().text("备注").end()))
                .end()
                .end();

        TableNode table = (TableNode) document.getNode().getSections().get(0).getBlocks().get(0);
        TableCellNode firstCell = table.getRows().get(0).getCells().get(0);

        assertEquals(120.0, table.getColumnWidths()[0]);
        assertEquals(240.0, table.getColumnWidths()[1]);
        assertEquals(2, firstCell.getColumnSpan());
    }

    @Test
    void storesPageSetupMetadataAndCellVerticalOptions() {
        DocxDocument document = DocxDocument.create()
                .metadata("软件设计说明", "luojiang", "omni-office")
                .pageSetup(setup -> setup
                        .paper(DocxPaperSize.A4)
                        .landscape()
                        .margins(72, 54, 72, 54))
                .section()
                .table()
                .row(row -> row
                        .cell(cell -> cell.verticalMerge(TableVerticalMerge.FIRST)
                                .verticalAlign(TableCellVerticalAlignment.CENTER)
                                .paragraph().text("合并开始").end())
                        .cell(cell -> cell.paragraph().text("第一行").end()))
                .row(row -> row
                        .cell(cell -> cell.verticalMerge(TableVerticalMerge.PREVIOUS))
                        .cell(cell -> cell.paragraph().text("第二行").end()))
                .end()
                .end();

        DocumentNode root = document.getNode();
        TableNode table = (TableNode) root.getSections().get(0).getBlocks().get(0);
        TableCellNode firstCell = table.getRows().get(0).getCells().get(0);
        TableCellNode secondRowFirstCell = table.getRows().get(1).getCells().get(0);

        assertEquals("软件设计说明", root.getMetadata().getTitle());
        assertEquals("luojiang", root.getMetadata().getAuthor());
        assertEquals("omni-office", root.getMetadata().getSubject());
        assertEquals(DocxPaperSize.A4, root.getPageSetup().getPaperSize());
        assertEquals(DocxPageOrientation.LANDSCAPE, root.getPageSetup().getOrientation());
        assertEquals(72.0, root.getPageSetup().getTopMarginPoints());
        assertEquals(TableVerticalMerge.FIRST, firstCell.getVerticalMerge());
        assertEquals(TableCellVerticalAlignment.CENTER, firstCell.getVerticalAlignment());
        assertEquals(TableVerticalMerge.PREVIOUS, secondRowFirstCell.getVerticalMerge());
    }
}
