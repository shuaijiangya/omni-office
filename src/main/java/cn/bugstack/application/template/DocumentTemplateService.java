package cn.bugstack.application.template;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;

/** templateId + 显式版本 + JSON 数据的独立文档模板入口。 */
public interface DocumentTemplateService {

    DocumentSpec renderSpec(String templateId, String version, JsonNode data);

    byte[] exportToBytes(String templateId, String version, JsonNode data, ReportOutputFormat format);

    void export(String templateId, String version, JsonNode data, ReportOutputFormat format, Path outputPath);

    JsonNode getDataSchema(String templateId, String version);

    List<DocumentTemplateDescriptor> listTemplates();
}
