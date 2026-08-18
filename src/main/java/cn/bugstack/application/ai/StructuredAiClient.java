package cn.bugstack.application.ai;

/**
 * 内部 AI 服务适配 SPI。
 *
 * <p>实现方负责调用具体模型服务，并返回仅包含 JSON 的响应文本。</p>
 */
public interface StructuredAiClient {

    String generateJson(StructuredAiRequest request);
}
