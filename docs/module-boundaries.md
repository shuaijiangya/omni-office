# 模块边界与拆分顺序

当前版本继续保持单 Maven 工程，以免在 API、存储和运行配置仍快速演进时增加发布复杂度；代码依赖方向已由
`ModuleBoundaryTest` 固化，为后续物理拆分提供安全网。

建议按以下顺序拆分，且每一步保持现有包名和公开 API：

| 目标模块 | 当前源码边界 | 允许的内部依赖 |
| --- | --- | --- |
| `omni-office-protocol` | `cn.bugstack.protocol` 与 JSON Schema 资源 | 无 |
| `omni-office-word-core` | `cn.bugstack.office` | 无 |
| `omni-office-export` | `cn.bugstack.export` | `word-core` |
| `omni-office-application` | `cn.bugstack.application.document/template/diagram/ai` | protocol、export、word-core |
| `omni-office-integrations` | `cn.bugstack.application.external/generation` | application |
| `omni-office-service` | HTTP/stdio main、部署资源 | integrations |

物理拆分前必须先消除 `systemPath` 依赖：在私有 Maven 仓库发布 Aspose JAR，CI 通过仓库凭证解析，许可证仍只在
运行时挂载。拆分过程不移动 `cn.bugstack.export` 现有业务模板，也不改变 Function Calling/MCP/OpenAPI 契约。

线程治理基线：HTTP 与 MCP 操作使用共享有界队列，MCP Tasks 和每租户 Generation Worker 也使用有界队列；
队列满时返回明确过载结果或让持久化租约恢复，不允许悄悄扩展无界内存。后续拆分 service 模块时，应把
Generation Worker 改为全服务共享池，并将 worker 数、队列容量及拒绝次数暴露为配置与指标。
