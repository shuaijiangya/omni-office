# 上传 DOCX 补全功能验证记录

验证日期：2026-10-14。验证对象为当前工作区的源码，输出位于 `target/completion-preview/` 和
`target/completion-complex-sample/`；
`target/` 为构建目录，不随 Git 提交。此次验证使用可预测的测试模型输出，不把真实 LLM 的事实正确率
等同于程序正确率。

以下记录首先使用可预测的测试模型输出验证框架；随后已启动本机 Ollama 并执行
`qwen3.5:2b` 的[多样本在线规划实测](source-docx-ollama-live-verification.md)。两类结果应分开看：
框架测试通过不等于小模型能正确完成每个自由描述样本。

## 已执行的检查

```bash
/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -q \
  -Dtest=SourceDocxCompletionApplicationTest,SourceDocxDocumentCompletionTest,McpHttpServerTest \
  -Dcompletion.preview.dir=target/completion-preview test

/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -q test
```

两条命令均退出码为 0。当前工作区已生成复杂源样本时，完整测试集共 236 项，
其中 1 项真实 Ollama 测试因未启用 `-Dollama.live=true` 而跳过，失败及错误均为 0。
单独的补全测试覆盖：

| 场景 | 验证点 |
| --- | --- |
| 单表补全 | 行主键 UPSERT、仅填空白单元格、克隆样本行、保留人工内容 |
| 多位置补全 | 两个 Section、两个表格、插入正文段落、替换占位段落、一次保存 |
| 固定格式表格 | 无行主键时按零起点物理行列填充两个空白单元格 |
| 混合复杂表格 | 保留纵向合并区域和相邻多段落内容，仅填充安全目标格；拒绝合并续格 |
| 结构化段落锚点 | 在包含书签的段落后插入说明，保留书签节点 |
| 多样式段落 | 仅替换一个 Run，保留旁边 Run 的斜体及目标 Run 的颜色和加粗；拒绝危险的整段替换 |
| 源文件变化 | 规划后改动源 DOCX，执行器拒绝写入且不创建输出文件 |
| 白名单 Function Calling | 仅允许注册函数、参数先按 JSON Schema 验证、结果进入规划上下文 |
| 上传隔离 | 错误文件拒绝上传、不同 ownerId 无法解析同一文档 ID |
| HTTP 上传 | `POST /v1/source-documents` 返回主体私有工件；错误媒体类型和非法 DOCX 被拒绝，其他主体不能下载 |
| MCP/Function 工具门面 | 配置 AI 后发布分析、规划和写回工具；同一主体闭环成功，跨主体调用失败 |
| HTTP MCP 协议闭环 | REST 上传取得 ID 后，经 MCP 会话调用分析、规划和确认写回；输出按主体下载，响应不含临时路径 |
| 跨请求审核计划 | 规划 JSON 可序列化传递；未确认或篡改源 SHA-256 的写回被拒绝 |
| 大文档分 Section | `sectionIndex` 只发送选定 Section 的结构给模型，同时保持全局定位与原文件 SHA-256 |
| 上传包安全 | OOXML 加载前拒绝 ZIP 路径穿越，限制解压规模及危险外部关系 |
| 复杂表格保护 | 多段落单元格被识别并拒绝纯文本覆盖，避免丢失原始内容 |
| 复杂源文档完整写回 | 47 处待填位置、1 条新增接口行、合并标题及多样式 Run 保留；标题与开篇说明转为完成态 |

示例输出 [design-preview.docx](../target/completion-preview/design-preview.docx) 来自
`SourceDocxDocumentCompletionTest`。它包含跨两个 Section 的接口表和风险表，最终保留人工原文，
填充两个空白单元格、追加一个接口行、插入一段接口描述并替换风险占位段落。
另有 [rich-preview.docx](../target/completion-preview/rich-preview.docx)，用于验证含合并与多段落内容
的表格只修改指定的安全格。
[styled-runs-preview.docx](../target/completion-preview/styled-runs-preview.docx) 用于验证红色加粗
片段和黑色斜体片段在替换后仍保持独立样式。
[external-reviewed-preview.docx](../target/completion-preview/external-reviewed-preview.docx) 来自对外工具门面
上传、规划、显式确认和工件发布的完整链路。该测试同时确认源 DOCX 未被修改，并且输出只暴露
资源 URI 和修改计数，不泄露服务器临时路径。

## 页面检查

已使用文档渲染脚本将示例 DOCX 渲染为两页 PNG，并逐页检查页数、表格边界和分页。
该渲染环境缺少可用的中文字体映射，PNG 中的中文不可见，因此不能把其中文显示效果当作
最终 Word 字体结论。随后使用 macOS Quick Look 缩略图核对中文字符、表头、表格内容和段落，
均可见且未发现裁剪或重叠。若要验收实际排版，应在目标系统的 Microsoft Word 或 WPS 中打开
示例 DOCX，再按业务模板的字体与页面规格确认。混合复杂表格的 Quick Look 预览中，合并
区域、定点填充值和原有第二段均可见，未发现单元格内容重叠或裁剪。
对 `external-reviewed-preview.docx` 的两页 PNG 及 macOS Quick Look 缩略图也进行了检查：
Section 分页和表格边界正常，中文风险替换内容可见；没有可见的裁剪或表格错位。

[复杂样本的完整写回示例](complex-source-docx-sample.md)生成
`target/completion-complex-sample/complex-completed.docx` 和相应的审核计划 JSON。
该测试不调用 LLM；它验证已确认事实经过审核计划写回后的可重复结果，不能替代
Ollama 自主规划准确率的测试。复杂输出已渲染为 3 页；打包 LibreOffice 对其中的中文
仍无法正确显示，Quick Look 首页可见中文和已填内容，因此尚不能据此宣称逐页中文排版已完成验收。

## 当前边界

- 只处理 DOCX；不解析 PDF、DOC、扫描件或图片。复杂文件应由调用方先转换并人工核对。
- 模型只给计划；生成事实应来自用户明确描述、调用方 JSON 或白名单业务函数。当前没有自动的事实溯源校验，
  需要调用方审核结果。
- 复杂表格暂不支持重复行 UPSERT；定点填充只允许目标单元格本身为普通文本且不是合并续格，
  复杂邻格保持原样。
- 已有 REST 上传和 MCP/Function 分析、规划、写回工具，但尚无服务内持久化的审核记录。
  `approved: true` 由调用方代表其真实审核流程声明，调用方须保存审批证据。
- HTTP 上传限 25 MiB；Java 本地处理限 100 MiB。超大文档可用 `sectionIndex` 分 Section
  规划，但同一次申请无法自动跨 Section 拼装并审批全部补丁。
- 本机 Ollama 后续已成功启动；真实模型 8 个样本中 4 个成功写回、1 个要求补充信息、
  3 个被安全校验拦截。详情见[在线实测](source-docx-ollama-live-verification.md)。
