package cn.bugstack.application.schema;

import com.fasterxml.jackson.databind.JsonNode;

/** 两个相邻协议版本之间的确定性 JSON 迁移。 */
public interface SchemaMigration {

    String protocol();
    String fromVersion();
    String toVersion();
    JsonNode migrate(JsonNode input);
}
