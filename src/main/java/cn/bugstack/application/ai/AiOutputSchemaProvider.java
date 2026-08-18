package cn.bugstack.application.ai;

import com.fasterxml.jackson.databind.JsonNode;

/** 提供给结构化模型的自包含输出 Schema。 */
public interface AiOutputSchemaProvider {

    JsonNode documentSpecSchema();
}
