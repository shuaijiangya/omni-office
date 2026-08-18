package cn.bugstack.application.ai;

import cn.bugstack.export.api.ReportOutputFormat;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;

/** 内部 AI 的自由文档和模板数据双入口。 */
public interface InternalAiDocumentService {

    AiDocumentResult generateFreeform(String instruction, JsonNode context);

    AiDocumentResult generateFromTemplate(String templateId, String version,
                                          String instruction, JsonNode context);

    byte[] exportToBytes(AiDocumentResult result, ReportOutputFormat format);

    void export(AiDocumentResult result, ReportOutputFormat format, Path outputPath);
}
