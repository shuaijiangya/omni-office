package cn.bugstack.application.document;

import cn.bugstack.export.document.CaptionTargetType;
import cn.bugstack.export.document.CaptionPosition;
import cn.bugstack.export.document.ReportCaption;
import cn.bugstack.export.document.ReportDocument;
import cn.bugstack.export.document.ReportElement;
import cn.bugstack.export.document.ReportImage;
import cn.bugstack.export.document.ReportListItem;
import cn.bugstack.export.document.ReportListType;
import cn.bugstack.export.document.ReportPageBreak;
import cn.bugstack.export.document.ReportParagraph;
import cn.bugstack.export.document.ReportSection;
import cn.bugstack.export.document.ReportTable;
import cn.bugstack.export.document.ReportTableAlignment;
import cn.bugstack.export.document.ReportTableMerge;
import cn.bugstack.export.document.ReportTextRange;
import cn.bugstack.export.document.ReportTextRangeStyle;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.AbstractListBlockSpec;
import cn.bugstack.protocol.document.block.BlockSpec;
import cn.bugstack.protocol.document.block.BulletListBlockSpec;
import cn.bugstack.protocol.document.block.DiagramBlockSpec;
import cn.bugstack.protocol.document.block.ImageBlockSpec;
import cn.bugstack.protocol.document.block.PageBreakBlockSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import cn.bugstack.protocol.document.block.TableMergeSpec;
import cn.bugstack.protocol.document.block.TextRangeSpec;
import cn.bugstack.protocol.document.block.TextRangeStyleSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * DocumentSpec v1 到现有 ReportDocument 模型的默认编译器。
 */
public final class DefaultDocumentSpecCompiler implements DocumentSpecCompiler {

    private final DiagramBlockResolver diagramBlockResolver;

    /** 创建 M1 兼容编译器；遇到图块时明确报告能力未配置。 */
    public DefaultDocumentSpecCompiler() {
        this(null);
    }

    /** 创建启用 M2 图工件能力的编译器。 */
    public DefaultDocumentSpecCompiler(DiagramBlockResolver diagramBlockResolver) {
        this.diagramBlockResolver = diagramBlockResolver;
    }

    @Override
    public ReportDocument compile(DocumentSpec spec) {
        if (spec == null || spec.getMetadata() == null) {
            throw new IllegalArgumentException("document spec and metadata must not be null");
        }
        ReportDocument document = new ReportDocument();
        document.setTitle(spec.getMetadata().getTitle());
        for (SectionSpec section : spec.getSections()) {
            document.getSections().add(compileSection(section));
        }
        return document;
    }

    private ReportSection compileSection(SectionSpec source) {
        ReportSection target = new ReportSection(source.getTitle());
        for (BlockSpec block : source.getBlocks()) {
            if (block instanceof AbstractListBlockSpec) {
                compileList((AbstractListBlockSpec) block, target,
                        block instanceof BulletListBlockSpec ? ReportListType.BULLET : ReportListType.NUMBERED);
            } else {
                target.addElement(compileBlock(block));
            }
        }
        return target;
    }

    private ReportElement compileBlock(BlockSpec block) {
        if (block instanceof ParagraphBlockSpec) {
            ParagraphBlockSpec source = (ParagraphBlockSpec) block;
            ReportParagraph target = new ReportParagraph(source.getText());
            target.setStyleName(source.getStyleName());
            target.setFontColor(source.getFontColor());
            for (TextRangeSpec range : source.getTextRanges()) {
                ReportTextRange targetRange = new ReportTextRange(range.getText());
                targetRange.setStyle(compileTextRangeStyle(range.getStyle()));
                target.getTextRanges().add(targetRange);
            }
            return target;
        }
        if (block instanceof TableBlockSpec) {
            return compileTable((TableBlockSpec) block);
        }
        if (block instanceof ImageBlockSpec) {
            return compileImage((ImageBlockSpec) block);
        }
        if (block instanceof SubsectionBlockSpec) {
            SubsectionBlockSpec source = (SubsectionBlockSpec) block;
            SectionSpec section = new SectionSpec(source.getTitle());
            section.setBlocks(source.getBlocks());
            return compileSection(section);
        }
        if (block instanceof PageBreakBlockSpec) {
            return new ReportPageBreak();
        }
        if (block instanceof DiagramBlockSpec) {
            if (diagramBlockResolver == null) {
                throw new UnsupportedOperationException("diagram artifact capability is not configured");
            }
            return diagramBlockResolver.resolve((DiagramBlockSpec) block);
        }
        throw new IllegalArgumentException("unsupported document block: " + block.getClass().getName());
    }

    private void compileList(AbstractListBlockSpec source, ReportSection target, ReportListType listType) {
        for (String item : source.getItems()) {
            ReportListItem listItem = new ReportListItem(listType, item);
            listItem.setStyleName(source.getStyleName());
            listItem.setFontColor(source.getFontColor());
            target.addElement(listItem);
        }
    }

    private ReportTable compileTable(TableBlockSpec source) {
        ReportTable target = new ReportTable();
        target.setHeaders(new ArrayList<>(source.getHeaders()));
        List<List<String>> rows = new ArrayList<>();
        for (List<String> row : source.getRows()) {
            rows.add(new ArrayList<>(row));
        }
        target.setRows(rows);
        target.setStyleName(source.getStyleName());
        target.setFontColor(source.getFontColor());
        target.setHeaderTextStyle(compileTextRangeStyle(source.getHeaderTextStyle()));
        target.setBodyTextStyle(compileTextRangeStyle(source.getBodyTextStyle()));
        target.setAlignment(ReportTableAlignment.valueOf(source.getAlignment()));
        List<ReportTableMerge> merges = new ArrayList<>();
        for (TableMergeSpec merge : source.getMerges()) {
            merges.add(new ReportTableMerge(merge.getStartRow(), merge.getStartColumn(),
                    merge.getRowSpan(), merge.getColumnSpan()));
        }
        target.setMerges(merges);
        double[] widths = new double[source.getColumnWidths().size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = source.getColumnWidths().get(i);
        }
        target.setColumnWidths(widths);
        if (hasText(source.getCaption())) {
            ReportCaption caption = new ReportCaption(CaptionTargetType.TABLE, source.getCaption());
            caption.setAutoNumbered(source.isCaptionAutoNumbered());
            caption.setPosition(CaptionPosition.valueOf(source.getCaptionPosition()));
            target.setCaption(caption);
        }
        return target;
    }

    private ReportImage compileImage(ImageBlockSpec source) {
        ReportImage target = new ReportImage(source.getSource());
        target.setAlternativeText(source.getAlternativeText());
        target.setWidth(source.getWidth());
        target.setHeight(source.getHeight());
        if (hasText(source.getCaption())) {
            ReportCaption caption = new ReportCaption(CaptionTargetType.IMAGE, source.getCaption());
            caption.setPosition(CaptionPosition.valueOf(source.getCaptionPosition()));
            target.setCaption(caption);
        }
        return target;
    }

    /** 将协议文本范围样式转换为 Report 层样式。 */
    private ReportTextRangeStyle compileTextRangeStyle(TextRangeStyleSpec source) {
        if (source == null) return null;
        ReportTextRangeStyle target = new ReportTextRangeStyle();
        target.setFontFamily(source.getFontFamily());
        target.setAsciiFontFamily(source.getAsciiFontFamily());
        target.setFarEastFontFamily(source.getFarEastFontFamily());
        target.setFontSize(source.getFontSize());
        target.setFontColor(source.getFontColor());
        target.setBold(source.getBold());
        target.setItalic(source.getItalic());
        target.setUnderline(source.getUnderline());
        return target;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
