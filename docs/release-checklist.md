# 发布与安全验收清单

## 构建与契约

- 使用 JDK 17+ 和 Maven 3.6.3+，设置 `MAVEN_BIN` 后执行 `scripts/release-check.sh`。
- 完整测试必须为 0 failure、0 error、0 skipped，并包含真实 PostgreSQL、HTTP、OIDC/JWKS 和 S3 协议测试。
- OpenAPI、capabilities、README、环境变量示例和数据库迁移必须与发布版本一致。
- 对已有 `/v1`、MCP 工具 Schema 和模板 Schema 执行兼容性审查，遵守 `docs/api-versioning.md`。

## 身份与秘密

- 生产使用企业 OIDC/JWKS、短期令牌和最小 scope；HS256/API Key 只用于受控场景并定期轮换。
- 数据库密码、S3 凭证、Webhook Secret、Aspose License 不进入镜像、Git、日志、指标或工件。
- 确认 `templates:review` 与创建者分离，`operations:read` 不向其他租户泄漏汇总。
- 公网 URL 使用 HTTPS；只允许文档化的回环/Compose 内部 HTTP 例外。

## 数据与恢复

- PostgreSQL 执行一致性备份并验证 Flyway history；S3 开启版本控制/生命周期，备份模板目录和外部配置。
- 恢复演练顺序为数据库、模板/配置、对象存储；恢复后检查工件摘要、任务终态和 Outbox 去重键。
- 不把文件任务仓储作为 PostgreSQL 故障时的生产降级队列。
- 确认工件保留期、审计保留期和删除流程符合业务及合规要求。

## 发布与回滚

- 先部署无流量实例，通过 live/ready 与 smoke generation，再逐步放量。
- 数据库迁移必须向前兼容当前和上一应用版本；破坏性清理延后到旧版本完全退出之后。
- 回滚只回滚应用，不回滚已成功执行的 Flyway 迁移；需要数据修复时创建新的前向迁移。
- 发布后观察 5xx、路由耗时、QUEUED/RUNNING、Webhook DEAD 和清理错误至少一个业务高峰。
- 完成 `docs/operations-runbook.md` 中的故障演练并归档证据。
