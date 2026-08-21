# Omni Office 生产运维手册

## 观测入口

- `/health/live` 只表示进程可响应。
- `/health/ready` 返回 `dataDirectory`、`generationRepository`、`artifactStorage` 三项检查；任一失败时返回 503。
- `/metrics` 暴露低基数 Prometheus 指标，不包含租户、主体、任务 ID 或文档内容。
- `/v1/admin/operations/summary` 需要 `operations:read`，只返回调用身份所属租户的任务与 Webhook 汇总。

排障时优先使用 `X-Correlation-Id` 对齐调用方日志、Generation Job 和审计记录。不要把 API Key、Bearer
Token、Webhook Secret、模板数据或 DocumentSpec 正文复制到工单。

## 服务不可用或未就绪

1. 分别请求 live 和 ready，确认是进程故障还是依赖故障。
2. 按 ready 的失败项检查数据卷权限、PostgreSQL 连接/Flyway 版本或 S3 Endpoint/Bucket 权限。
3. 数据库恢复前不要切换到文件仓储继续接收生产流量，这会形成两个不一致的任务队列。
4. 依赖恢复后观察 queued/running 数量；Worker 会在原租约到期后接管，不要批量重置 RUNNING。

## HTTP 5xx 升高

1. 按 `route` 标签定位 API 面，比较 request count 与 duration sum/count。
2. 检查 ready、数据库池、对象存储和 Aspose License/字体环境。
3. 对同一幂等键安全重试提交；不要为规避错误不断创建新幂等键。

## 任务积压或租约恢复

1. 从租户 operations summary 确认 QUEUED/RUNNING/FAILED 分布。
2. 检查实例 CPU、内存、数据库锁等待和对象存储延迟。
3. Worker 租约为有限期并带心跳；实例崩溃后等待租约过期即可恢复。强行修改任务表可能导致重复生成。
4. 扩容前确认数据库连接池和 S3 限额能够承载新增实例。

## Webhook 投递失败

1. 查询 `/v1/webhook-deliveries`，区分 PENDING、RETRYING 与 DEAD。
2. 验证管理员预注册端点、TLS、接收方状态码和 HMAC Secret 是否同步。
3. 接收方必须按 `X-Omni-Event-Id` 去重；恢复期间可能发生至少一次的重复投递。
4. DEAD 事件当前保留用于审计，不应直接改库重放；确认接收方修复后通过受控运维流程重新提交业务任务。

## 工件清理失败

1. 查看 cleanup errors 增量以及 S3/本地卷权限和可用空间。
2. 不要直接删除租户前缀之外的对象。S3 键必须位于配置前缀的 `tenants/{tenantId}` 下。
3. 修复依赖后等待下一个小时清理周期；紧急释放空间时先导出待删清单并由双人复核。

## 发布前故障演练

每次生产大版本至少验证：停止一个 Worker 后租约接管；数据库短暂不可用时 ready 返回 503；S3 写失败时任务
进入可诊断失败态；Webhook 返回 500 后退避重试；同一幂等键并发提交只产生一个任务；配额并发准入不穿透；
租户 A 无法查询或下载租户 B 的任务与工件。演练记录应包含版本、时间、操作者、结果和恢复耗时。
