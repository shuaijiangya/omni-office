# 上传 DOCX 的 AI 识别与精准补全

本能力用于处理用户已有的 Word 文档，无需预先定义“设计文档章节对象”：系统提取正文段落和表格
结构，LLM 根据用户描述选择待补位置，必要时通过白名单 Function Calling 获取业务事实，再由本地
校验器审核锚点、覆盖策略和行主键，最后写入一份新的 DOCX 供人工预览。

## 当前已落地的闭环

```text
上传 DOCX → REST 租户私有工件（或 Java SourceDocxUploadStore）
  → SourceDocxStructureExtractor
  → DocumentStructureSnapshot
  → StructuredAiCompletionFunctionOrchestrator（可选）
  → StructuredAiDocumentCompletionPlanner
  → DocumentCompletionPlan（ParagraphPatchPlan + RunPatchPlan + TablePatchPlan + CellPatchPlan）
  → DocumentCompletionPlanValidator
  → SourceDocxDocumentPatchExecutor
  → 预览 DOCX
```

当前版本具备：

- 只接受 `.docx`；Java 本地入口最大 100 MiB，当前 HTTP 上传入口最大 25 MiB。
- Aspose 加载前检查 OOXML ZIP 的必需部件、路径穿越、重复条目、解压规模及危险外部关系。
- 提取正文直接段落的 Section/位置/样式，以及表格题注、表头、数据行、Section 序号和表格 ID。
- 同一份计划可定位多个段落和多个表格。段落支持替换文本和在锚点后插入新段落。
- 多 Run 段落可按 `runIndex` 和原文前置条件精确替换一个文本片段，保留其他片段的颜色、加粗等样式。
- 为每一列生成 `column_0`、`column_1` 等稳定列 ID，避免 LLM 使用易漂移的自然语言列名。
- LLM 只能选择已提取的表格 ID、列 ID、行主键和有限覆盖策略。
- 支持 `BLANKS_ONLY` 和 `OVERWRITE_ALL`。
- 使用一个或多个行主键执行 UPSERT，避免按物理行号修改。
- 固定格式表格可使用 `CellPatchPlan` 的零起点物理行列位置填值，不要求行主键；源文件哈希防止位置漂移。
- 新增行克隆模板行，保留原有边框、底纹、段落和字体样式。
- 临时文件保存成功后再原子替换目标文件。
- 计划绑定源文件 SHA-256；源文档修改后拒绝写回。上传存储按 ownerId 隔离。
- 支持白名单 Function Calling；未注册函数不能执行。
- 函数参数按注册时的 JSON Schema 校验，失败时不会调用业务函数。
- 文档文本和函数结果均作为不可信数据，不能修改系统规则或扩大权限。

重复行 UPSERT 仍限定“首行表头 + 至少一行样本数据 + 等宽纯文本行”，遇到合并、嵌套或
复杂内容表格会拒绝。定点单元格补丁按物理格逐一检查：即使同一张表存在合并区域或多段落
内容，也可填写别处的普通空白格；合并续格及含对象的目标格不能被纯文本替换。正文中包含
对象的段落可作为 `INSERT_AFTER` 锚点，但不能执行 `REPLACE_TEXT`。未支持的位置应进入人工
确认，不允许模型绕过校验。

对于有多个独立 Run 的正文段落，整段 `REPLACE_TEXT` 会被拒绝；应使用 `runPatches`，传入
`paragraphId`、从 0 开始的 `runIndex`、精确的 `expectedText`、新文本和覆盖策略。该操作只修改
目标 Run，不重建整个段落。

## 对外 REST + MCP/Function Calling 闭环

服务配置 `OMNI_OFFICE_OLLAMA_MODEL`（例如 `qwen3.5:2b`）后，提供以下入口。未配置结构化 AI
客户端时，不公布这三个补全工具，HTTP 上传返回 `503`。

1. 已认证调用方使用 `POST /v1/source-documents` 上传原始 DOCX 字节；请求
   `Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document`，
   需要 `source-docx:upload` 权限。响应返回 `sourceArtifactId`、`resourceUri` 和 `sha256`。
   上传与输出工件按租户和主体隔离；原始文件不会被改动。
2. 调用 MCP 或 Function 工具 `omni_source_docx_analyze`，以 `sourceArtifactId` 获取段落、
   Run、表格及安全单元格的定位信息。大文档可附加零起点 `sectionIndex`，只分析该 Section；
   未指定时返回全篇结构，超过 1,000,000 字符则要求分 Section 查询。
3. 调用 `omni_source_docx_plan`，传入 `sourceArtifactId`、`instruction` 和可选的
   `businessFacts` JSON 对象；大文档同样可指定 `sectionIndex` 分批规划。返回 `READY`
   计划或 `NEEDS_INPUT` 问题，不写文件。Section 筛选不改变全局锚点和源 SHA-256。
4. 调用方展示计划中的目标位置、原内容、拟写内容与覆盖策略，取得人工确认后，再调用
   `omni_source_docx_apply`，传入原 `sourceArtifactId`、完整 `reviewedPlan` 及
   `approved: true`。服务重新分析原文件、比较 SHA-256、校验每个定位和覆盖策略，生成新
   DOCX 工件。工件通过 `GET /artifacts/{artifactId}` 下载，需 `artifacts:read` 权限。

工具调用使用现有的 `mcp:invoke` 权限。`approved: true` 是集成方对“已经完成审核”的声明，
不是服务内持久化的审批记录；调用方仍须保存审核人、时间和计划版本等审计证据。模型不能
自行代表用户确认。若模型返回 `NEEDS_INPUT`、表格结构不安全或事实不足，不调用写回工具。
`businessFacts` 应由可信业务系统或外部 Function Calling 获取，上传的文档文本和函数返回
内容都不能作为工具授权指令。
对于较慢的本地模型，MCP 调用 `omni_source_docx_plan` 时建议使用现有异步 Tasks 协议并轮询
结果；同步 HTTP MCP 请求受 `OMNI_OFFICE_REQUEST_TIMEOUT_SECONDS` 限制。

示例工具参数：

```json
{"sourceArtifactId":"<上传响应中的 UUID>","instruction":"根据已确认的接口清单补全空白表格，不覆盖人工内容","businessFacts":{"apis":[{"name":"创建订单","method":"POST"}]}}
```

```json
{"sourceArtifactId":"<同一 UUID>","reviewedPlan":{ "status":"READY", "sourceSha256":"<规划响应中的 SHA-256>", "paragraphPatches":[], "tablePatches":[], "cellPatches":[], "runPatches":[], "confidence":0.9, "reason":"...", "questions":[] },"approved":true}
```

第二段 JSON 仅展示字段形状；零操作计划不可执行。实际调用必须原样传入并审核规划工具返回的
非空补丁计划。Function Calling Java 适配器应使用带 `principalId` 的 `invoke(...)` 和
`readResource(...)` 重载，将外部身份传入工件权限校验。

## Java 本地上传与多位置补全

`SourceDocxUploadStore` 是可选的本地存储组件，适用于嵌入式 Java 使用；它与上述租户 HTTP
工件库是两种入口，ID 不能混用。服务层应先完成用户认证，再传入可信的 `ownerId`。
本地源文件限制为 `.docx` 且不超过 100 MiB；压缩后体积符合限制但解压超过
300 MiB、或包含非普通超链接外部关系的文件也会被拒绝。

```java
SourceDocxUploadStore uploads = new SourceDocxUploadStore(uploadRoot);
String documentId = uploads.upload(ownerId, "方案.docx", incomingStream);
Path source = uploads.resolve(ownerId, documentId);

SourceDocxCompletionApplication completion =
        new SourceDocxCompletionApplication(structuredAiClient);
DocumentStructureSnapshot snapshot = completion.analyze(source);
DocumentCompletionPlan plan = completion.planDocument(
        source,
        "根据已确认的接口事实补齐接口说明和风险清单；保留人工填写的内容。",
        businessFacts);

if (!plan.getQuestions().isEmpty()) {
    // 将问题展示给用户补充；不要执行写回。
} else {
    // 先展示段落锚点、表格 ID、行主键、拟写内容和覆盖策略，让用户审核。
    DocumentCompletionResult preview = completion.applyDocument(source, previewPath, plan);
}
```

`businessFacts` 必须是 JSON 对象。模型可以使用用户描述中明确给出的事实及 `businessFacts`，
但不能凭空补项目细节。生成的 `confidence` 和 `reason` 只用于提示人工审核，不是可信的事实证明。
对于项目数据，业务服务应提交可核对的来源或先使用白名单函数获取事实。

同一张表不能在一次计划中混用“行主键 UPSERT”和“物理单元格补丁”。固定格式表格即使首行不是
真正表头，也可以通过 `cellPatches` 的 `tableId`、`rowIndex`、`columnIndex` 精确填空；位置从 0
开始。`BLANKS_ONLY` 遇到原单元格非空会拒绝计划，`OVERWRITE_ALL` 必须由调用方审核后执行。

## 仅补单个表格的兼容入口

调用方已经准备好业务事实时，可以直接把它们放入 `businessContext`：

```java
StructuredAiClient model = new OllamaStructuredAiClient("qwen3.5:2b");
SourceDocxCompletionApplication completion = new SourceDocxCompletionApplication(model);

DocumentStructureSnapshot snapshot = completion.analyze(sourceDocx);

ObjectNode context = new ObjectMapper().createObjectNode();
context.put("projectId", "order-center");
context.set("apis", apiData);

TableCompletionPlanningResult plan = completion.plan(
        sourceDocx,
        "补充接口清单，只填写空白项，并追加缺失接口。",
        context);

if (plan.getStatus() == TableCompletionStatus.READY) {
    TablePatchResult result = completion.apply(sourceDocx, previewDocx, plan);
}
```

`plan(...)` 不修改文件。调用方应先展示目标表格、补丁行、覆盖策略、置信度和原因，确认后再调用
`apply(...)`。

## 使用 Function Calling

业务系统显式注册允许模型调用的函数：

```java
DocumentCompletionFunctionRegistry functions = new DocumentCompletionFunctionRegistry()
        .register(new DocumentCompletionFunction() {
            @Override
            public String name() {
                return "list_api_definitions";
            }

            @Override
            public String description() {
                return "读取指定项目的接口定义";
            }

            @Override
            public JsonNode inputSchema() {
                return apiFunctionSchema;
            }

            @Override
            public JsonNode invoke(JsonNode arguments) {
                return apiService.listDefinitions(arguments.path("projectId").asText());
            }
        });

TableCompletionPlanningResult plan = completion.planWithFunctions(
        sourceDocx,
        "根据项目源码补充接口清单，只填写缺失项。",
        baseContext,
        functions);
```

也可以调用 `planDocumentWithFunctions(...)` 让函数结果参与段落与多个表格的统一规划。
Function Calling 分成两步：

1. 模型从白名单选择最少的函数和参数。
2. 本地执行函数，将结果放入 `businessFacts.functionResults`（兼容入口为
   `businessContext.functionResults`），再让模型生成补丁。

安全限制：

- 单次最多 8 个函数调用。
- 拒绝相同函数与参数的重复调用。
- 函数结果总 JSON 字符数最多 200,000。
- 注册表校验输入 JSON Schema；函数实现仍须自行验证业务权限。
- 模型不能注册函数，也不能直接访问网络、文件系统或 Aspose。

## 精准更新规则

假设接口表列绑定为：

| 列 ID | 表头 |
| --- | --- |
| `column_0` | 接口名称 |
| `column_1` | 请求方式 |
| `column_2` | 请求路径 |
| `column_3` | 说明 |

LLM 选择：

```json
{
  "tableId": "table-xxxxxxxxxxxxxxxx",
  "overwritePolicy": "BLANKS_ONLY",
  "keyColumnIds": ["column_1", "column_2"],
  "rows": [
    {
      "column_0": "创建订单",
      "column_1": "POST",
      "column_2": "/api/orders",
      "column_3": "创建一笔新订单"
    }
  ]
}
```

执行器使用“请求方式 + 请求路径”匹配已有行：

- 已存在时，仅补充空白单元格。
- 不存在时，克隆模板行并追加。
- 计划中行主键缺失或重复时拒绝执行。
- 源文档存在重复行主键时拒绝执行。
- 表头或结构指纹变化时要求重新分析文档。

## 关键类型

| 类型 | 职责 |
| --- | --- |
| `DocumentStructureSnapshot` | 上传文档的安全结构摘要 |
| `SourceTableSnapshot` | 单个表格的题注、表头、数据和指纹 |
| `TableBindingSpec` | 表格、列和行主键的稳定绑定 |
| `TablePatchPlan` | 等待验证的 UPSERT 数据 |
| `CellPatchPlan` | 固定格式表格中的单元格定点填充 |
| `RunPatchPlan` | 保留段落其他局部样式的文本片段补全 |
| `TablePatchValidator` | 防止越界、漂移、重复和不安全覆盖 |
| `DocumentCompletionPlanValidator` | 跨段落、表格、单元格的统一校验 |
| `SourceDocxTablePatchExecutor` | 确定性修改 Word 并原子保存 |
| `DocumentCompletionFunctionRegistry` | Function Calling 白名单 |
| `StructuredAiTableCompletionPlanner` | 自然语言到受限补丁计划 |
| `StructuredAiDocumentCompletionPlanner` | 自然语言到多位置文档计划 |
| `SourceDocxCompletionApplication` | 分析、规划和应用的统一门面 |

## 尚未开放的范围

以下工作不属于当前可写回范围，不能因为模型输出了位置或数据就直接执行：

1. 合并单元格、嵌套表格、跨页复杂表头的重复行逻辑网格 UPSERT；当前仅允许对其中安全的
   物理单元格定点填充。
2. 图片、图表、Diagram、内容控件、页眉页脚、脚注等对象级补全。
3. 服务内持久化的审核/发布工作流及版本治理；当前对外 `approved` 由已认证调用方声明，
   调用方负责展示计划、取得确认并留存审批证据。

可预测模型的功能验证见[验证记录](source-docx-completion-verification.md)；本地
`qwen3.5:2b` 的真实模型多样本结果、生成文档和复现命令见
[Ollama 实测记录](source-docx-ollama-live-verification.md)。要查看一份包含 6 张表、合并单元格、
多样式 Run 和新增接口行的完成态文档，可按[复杂源 DOCX 样本](complex-source-docx-sample.md)
生成并运行审核计划写回示例。该示例使用已确认业务事实，不调用 Ollama。

回归测试位于：

- [`SourceDocxCompletionApplicationTest`](../src/test/java/cn/bugstack/application/completion/SourceDocxCompletionApplicationTest.java)
- [`SourceDocxDocumentCompletionTest`](../src/test/java/cn/bugstack/application/completion/SourceDocxDocumentCompletionTest.java)
- [`ComplexSourceDocxCompletedExampleTest`](../src/test/java/cn/bugstack/application/completion/ComplexSourceDocxCompletedExampleTest.java)
