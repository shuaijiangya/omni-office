package cn.bugstack.application.document;

import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.AbstractListBlockSpec;
import cn.bugstack.protocol.document.block.BlockSpec;
import cn.bugstack.protocol.document.block.ChartBlockSpec;
import cn.bugstack.protocol.document.block.DiagramBlockSpec;
import cn.bugstack.protocol.document.block.ImageBlockSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;

/** 以字符、表格和媒体规模估算文档渲染成本，不依赖具体 Word 引擎。 */
public final class DocumentCostEstimator {

    public DocumentCostEstimate estimate(DocumentSpec document) {
        Counter counter = new Counter();
        if (document != null && document.getMetadata() != null) {
            counter.text += length(document.getMetadata().getTitle());
            counter.text += length(document.getMetadata().getAuthor());
            counter.text += length(document.getMetadata().getSubject());
        }
        if (document != null && document.getCover() != null) {
            counter.text += length(document.getCover().getDocumentName());
            counter.text += length(document.getCover().getProjectName());
            counter.text += length(document.getCover().getVersion());
        }
        if (document != null && document.getRevisionHistory() != null) {
            document.getRevisionHistory().forEach(item -> {
                if (item == null) return;
                counter.text += length(item.getVersion()) + length(item.getDate())
                        + length(item.getDescription()) + length(item.getAuthor());
                counter.tableCells += 4;
            });
        }
        if (document != null && document.getApprovals() != null) {
            document.getApprovals().forEach(item -> {
                if (item == null) return;
                counter.text += length(item.getRole()) + length(item.getPerson()) + length(item.getDate());
                counter.tableCells += 3;
            });
        }
        if (document != null && document.getSections() != null) {
            document.getSections().forEach(section -> visit(section, counter));
        }
        long weighted = counter.text + counter.tableCells * 12L + counter.media * 1800L;
        int pages = (int) Math.max(1L, (weighted + 1799L) / 1800L);
        return new DocumentCostEstimate(counter.sections, counter.blocks, counter.text,
                counter.tableCells, counter.media, pages);
    }

    private void visit(SectionSpec section, Counter counter) {
        if (section == null) return;
        counter.sections++;
        counter.text += length(section.getTitle());
        if (section.getBlocks() == null) return;
        for (BlockSpec block : section.getBlocks()) {
            counter.blocks++;
            if (block instanceof ParagraphBlockSpec) {
                ParagraphBlockSpec paragraph = (ParagraphBlockSpec) block;
                counter.text += length(paragraph.getText());
                if (paragraph.getTextRanges() != null) {
                    paragraph.getTextRanges().forEach(range -> {
                        if (range != null) counter.text += length(range.getText());
                    });
                }
            } else if (block instanceof AbstractListBlockSpec) {
                AbstractListBlockSpec list = (AbstractListBlockSpec) block;
                if (list.getItems() != null) list.getItems().forEach(item -> counter.text += length(item));
            } else if (block instanceof TableBlockSpec) {
                TableBlockSpec table = (TableBlockSpec) block;
                int columns = table.getHeaders() == null ? 0 : table.getHeaders().size();
                int rows = table.getRows() == null ? 0 : table.getRows().size();
                counter.tableCells += columns * (long) (rows + 1);
                if (table.getHeaders() != null) table.getHeaders().forEach(item -> counter.text += length(item));
                if (table.getRows() != null) table.getRows().forEach(row -> {
                    if (row != null) row.forEach(item -> counter.text += length(item));
                });
            } else if (block instanceof ChartBlockSpec) {
                ChartBlockSpec chart = (ChartBlockSpec) block;
                counter.media++;
                counter.text += length(chart.getTitle()) + length(chart.getCaption())
                        + length(chart.getCategoryAxisTitle()) + length(chart.getValueAxisTitle());
                if (chart.getCategories() != null) {
                    chart.getCategories().forEach(item -> counter.text += length(item));
                }
                if (chart.getSeries() != null) {
                    chart.getSeries().forEach(item -> {
                        if (item != null) counter.text += length(item.getName());
                    });
                }
            } else if (block instanceof ImageBlockSpec || block instanceof DiagramBlockSpec) {
                counter.media++;
            } else if (block instanceof SubsectionBlockSpec) {
                SubsectionBlockSpec child = (SubsectionBlockSpec) block;
                SectionSpec nested = new SectionSpec(child.getTitle());
                nested.setBlocks(child.getBlocks());
                visit(nested, counter);
            }
        }
    }

    private int length(String value) { return value == null ? 0 : value.length(); }

    private static final class Counter {
        private int sections;
        private int blocks;
        private long text;
        private long tableCells;
        private int media;
    }
}
