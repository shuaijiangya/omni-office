package cn.bugstack.application.ai.observability;

import java.util.List;

/** AI 轨迹存储 SPI，可替换为 OpenTelemetry、数据库或日志平台适配器。 */
public interface AiTraceStore {

    void append(AiCallTrace trace);
    List<AiCallTrace> readAll();
}
