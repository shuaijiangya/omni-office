# API 版本与兼容策略

## 稳定边界

`DocumentSpec 1.0`、`DiagramSpec 1.0`、`DocumentTemplate` 显式版本、`/v1` REST 和当前 MCP 工具 Schema
分别独立演进。内部 AI、Function Calling、MCP 与 REST 只是入口，不得改变统一 export 的既有业务模板语义。

在同一主版本内允许新增可选字段、响应字段、枚举外的独立新端点和可选能力；不得删除或重命名字段、收紧已发布
请求约束、改变字段含义、把可选字段改为必填，或改变幂等键、租户隔离和终态定义。客户端必须忽略未知响应字段，
但服务端继续拒绝请求中的未知字段，以防模型幻觉静默进入生成链路。

模板版本由调用方显式指定。发布新模板前必须比较 data Schema；不兼容变更使用新的语义主版本和显式迁移，
不会自动选择最新版或回退到其他模板。已 PUBLISHED/RETIRED 的历史版本内容不可覆盖。

## 弃用流程

计划移除的能力至少跨一个小版本保留，并依次完成：在 OpenAPI/capabilities 标记 deprecated、README 和发布说明
给出替代方案、记录调用量、通知调用方、在约定日期后才进入下一主版本删除。安全漏洞修复可以加速，但必须发布
影响范围和迁移步骤。

每次发布运行 `scripts/release-check.sh`。契约一致性测试会检查关键路径、唯一 operationId、核心 Schema 和生产能力
声明，避免代码、OpenAPI、capabilities 与文档分叉。
