# 复杂源 DOCX 补全测试样本

此样本用于验证“上传已有 DOCX → 解析结构 → AI 规划 → 人工审核 → 确定性写回”。数据为虚构的订单服务设计，不对应实际生产系统。当前解析器读到 7 个章节、6 张表、40 个正文段落，以及 35 个空白单元格、10 个占位段落和 2 个多样式 Run，占 47 个待填位置；同时保留部分已写内容，便于检查误覆盖。

## 生成样本

使用工作区随附的 Python 运行时：

```bash
/Users/luojiang/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 \
  scripts/generate-complex-source-docx.py
```

默认输出为 `target/completion-complex-sample/complex-source.docx`。事实数据在 [complex-source-docx-facts.json](samples/complex-source-docx-facts.json)，可作为 `businessFacts` 传入规划接口；不要把事实 JSON 直接复制到源 DOCX。

## 查看完整补全效果

先执行上述样本生成命令，再运行[完整写回示例测试](../src/test/java/cn/bugstack/application/completion/ComplexSourceDocxCompletedExampleTest.java)：

```bash
/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -q \
  -Dtest=ComplexSourceDocxCompletedExampleTest test
```

测试读取事实 JSON，分析源 DOCX，构造带源文件 SHA-256 的审核计划，再通过
`SourceDocxCompletionApplication.applyReviewedPlan(...)` 写入
`target/completion-complex-sample/complex-completed.docx`。同时输出
`target/completion-complex-sample/complex-reviewed-plan.json`，便于逐项查看实际写入位置。
这些 `target/` 文件是本地构建产物，不随 Git 提交；重跑测试会覆盖同名输出。

此示例是**已确认事实 → 显式审核计划 → 确定性写回**，没有调用 Ollama，也不能作为
`qwen3.5:2b` 自主规划整份复杂文档成功的证据。源样本的 47 处待填位置全部补齐：
10 个占位段落、35 个空白表格单元格和 2 个独立样式 Run；另追加一条 `GET /orders` 接口行，
并把标题和开篇说明改为完成态。测试断言源表格仍为 6 张、风险表合并标题保留、
接口表新增行正确、评审结论的红色加粗样式保留，且输出中不再含“待补充”或“待填写”。

本机打包的 LibreOffice 渲染器缺少可用的中文字体映射，逐页 PNG 不适合验收中文字形；
macOS Quick Look 可显示中文首页。正式视觉验收仍应使用目标环境的 Word/WPS 打开完成态 DOCX。

## 测试覆盖

| 区域 | 待补内容 | 推荐的补丁类型 |
| --- | --- | --- |
| 文档基本信息 | 负责人、评审人、实施日期 | `cellPatches` 或有稳定键时的 `tablePatches` |
| 系统目标与范围 | 两个占位段落、一个红色加粗 Run | `paragraphPatches`、`runPatches` |
| 逻辑架构与部署 | 两个占位段落、组件表中的多个空白单元格 | `paragraphPatches`、`cellPatches`；有稳定键时也可使用 `tablePatches` |
| 接口设计 | 三条已有接口的缺失值、一条新增接口、幂等段落 | `tablePatches`、`paragraphPatches` |
| 数据模型与状态 | 字段说明、状态流转段落 | `cellPatches`、`paragraphPatches`；有稳定键时也可使用 `tablePatches` |
| 异常与风险 | 合并标题下的 6 个空白单元格、一个红色加粗 Run | `cellPatches`、`runPatches`；不得对该表使用 `tablePatches` |
| 非功能需求与验收 | 4 项指标的目标值和验收方式 | `cellPatches`；有稳定键时也可使用 `tablePatches` |
| 上线与回滚 | 发布、触发条件、回滚操作、实施日期 | `paragraphPatches` |

必须保留源文档的既有内容，包括“支付平台独立”“API 网关负责统一鉴权”“订单服务负责人李工”“查询订单详情”“订单保存时间”和风险项名称。风险表首行横向合并，不能因为补全而拆开。红色加粗的“待填写”应仅替换文本，保留 Run 样式。
接口表同时包含 `POST /orders` 和新增的 `GET /orders`，因此行主键必须由“接口 + 方法”组成，不能只用接口路径。

## 推荐执行方式

先调用 `analyze_source_docx` 检查 `tableId`、`paragraphId` 和物理行列，再按 Section 分批规划及审核。当前小模型在自由描述和合并表格上的规划并不稳定，因此不要把整份复杂文档一次交给模型后自动写回。每批写回会产生一个新 DOCX；若需连续处理多个 Section，下一批应以该新文件为源重新分析和规划。

示例指令（配合完整 `businessFacts`）：

> 只补全“系统目标与范围”章节中带有“待补充”或“待填写”的位置。建设目标和不包含范围分别来自 businessFacts.scope.goal、businessFacts.scope.excluded；上线窗口来自 businessFacts.scope.releaseWindow。保持既有范围说明原文不变；红色加粗片段只改对应 Run。

对风险表的指令应明确要求 `cellPatches`，并根据分析结果提供物理 `rowIndex`、`columnIndex`。所有计划必须先展示给审核人确认，不能仅依据模型的 `confidence` 自动通过。
这份风险表的合并标题位于物理行 0，列名位于物理行 1；三条风险数据位于物理行 2 至 4，待填的“影响”和“应对措施”分别是物理列 1 和 2。
