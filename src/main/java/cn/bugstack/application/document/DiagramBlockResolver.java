package cn.bugstack.application.document;

import cn.bugstack.export.document.ReportDiagram;
import cn.bugstack.protocol.document.block.DiagramBlockSpec;

/** 将 DocumentSpec 图块物化为报告语义元素。 */
public interface DiagramBlockResolver {

    ReportDiagram resolve(DiagramBlockSpec block);
}
