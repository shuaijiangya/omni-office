package cn.bugstack.application.completion;

import com.fasterxml.jackson.databind.JsonNode;

/** 供文档补全 LLM 按白名单调用的业务数据函数。 */
public interface DocumentCompletionFunction {

    /** @return 稳定函数名 */
    String name();

    /** @return 面向模型的简短用途说明 */
    String description();

    /** @return 函数参数 JSON Schema */
    JsonNode inputSchema();

    /**
     * 执行函数并返回结构化事实数据。
     *
     * @param arguments 已由函数实现自行校验的参数
     * @return JSON 结果
     */
    JsonNode invoke(JsonNode arguments);
}
