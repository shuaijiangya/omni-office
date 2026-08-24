# Word 生成能力矩阵

三条业务入口最终共享 `ReportDocument -> DocxReportCompiler -> Word/PDF/HTML`，但公开协议刻意只暴露可安全、
可跨语言表达的能力。下表用于避免现有业务 Export、内部 AI 和外部 MCP/Function Calling 之间出现隐含边界。

| 能力 | 业务 Export | DocumentSpec/内部 AI | 外部 MCP/Function |
| --- | --- | --- | --- |
| 段落、九级标题、列表、分页 | 支持 | 支持 | 支持 |
| 字体颜色（`#RRGGBB`） | 支持 | 段落、列表、表格文本 | 段落、列表、表格文本 |
| 单段落多文本范围及独立样式 | 支持 | `textRanges` | `textRanges` |
| 表格自适应页面宽度、列宽比例、中文宋体、西文数字新罗马且居中 | 支持 | 支持 | 支持 |
| 表头与表内容独立动态文本样式（通用/ASCII/东亚字体等；未设置时继承默认值） | 支持 | 支持 | 支持 |
| 矩形合并单元格 | 支持 | 支持 `merges` | 支持 `merges` |
| 题注及上方/下方位置 | 支持 | 支持 `captionPosition` | 支持 `captionPosition` |
| 封面、修订记录、审批页 | 支持 | 支持 | 支持 |
| A3/A4/Letter、横竖版、页边距 | 支持 | 支持 | 支持 |
| 自动目录、标题编号、页眉页脚、页码 | 支持 | 支持 | 支持 |
| PNG/JPEG 图片 | 受信路径/业务对象 | 模板可使用受信来源 | 只能使用主体私有 `assetId` |
| 流程图、ER、系统 ER、用例等图 | 支持 | 内联 DiagramSpec 或工件 ID | 通过 `omni_diagram_generate` 或内联定义 |
| Word 内可编辑 Visio | 支持 | `EDITABLE_VISIO` | `EDITABLE_VISIO` |
| 单元格内复合块 | 支持 | 暂不支持 | 暂不支持 |
| 题注交叉引用、书签、超链接 | 支持/可扩展 | 暂不支持 | 暂不支持 |
| 自定义业务语义元素 | 支持扩展编译器 | 不开放 | 不开放 |

兼容原则：DocumentSpec `1.0` 的新增字段均为可选字段；既有 JSON 保持有效。未来只有发生不兼容语义变化时才发布
新的 Schema 版本。外部图片不得传服务器路径或 URL，必须先调用 `omni_asset_store` 获得同主体 `assetId`。

表格合并使用零基坐标；`startRow=0` 表示表头。`rowSpan` 与 `columnSpan` 至少一个大于 1，合并区域不能重叠
或越界。只有区域左上角可以包含文本，其余逻辑单元格必须传空字符串，避免合并时静默丢弃内容。
`columnWidths` 表示相对比例而非固定磅值；表格总宽度始终占满当前页面正文的可用宽度。

AI 的两类运行策略：

- `reviewPolicy=AUTO`：结构校验通过后直接进入确定性渲染。
- `reviewPolicy=REQUIRED`：停在 `PENDING_REVIEW`，由具备 `ai:review` 权限且不是创建者的主体批准后继续。

模板发布比普通数据校验更严格：审批请求必须携带代表性 `sampleData`，系统会执行数据 Schema、模板展开、
DocumentSpec 校验、真实 DOCX 渲染及 OOXML 回读，并在模板版本上保存样例与渲染 SHA-256 证据。
