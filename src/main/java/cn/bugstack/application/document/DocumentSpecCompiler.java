package cn.bugstack.application.document;

import cn.bugstack.export.document.ReportDocument;
import cn.bugstack.protocol.document.DocumentSpec;

/**
 * 将公开的 DocumentSpec 协议编译为内部报告语义树。
 */
public interface DocumentSpecCompiler {

    ReportDocument compile(DocumentSpec spec);
}
