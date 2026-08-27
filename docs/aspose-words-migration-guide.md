# Aspose.Words 到 omni-office 迁移对照指南

本指南面向长期直接使用 Aspose.Words `Document`、`DocumentBuilder` 和 Word 节点 API 的
Java 开发者。目标不是隐藏 Aspose 所有能力，而是将稳定的业务语义从游标、样式状态和
OOXML 细节中抽离，使同一份内容可复用到 DOCX、PDF、HTML、内部 AI 和外部 MCP/Function Calling。

## 1. 可运行对照示例

[`AsposeWordsMigrationComparisonExample`](../src/main/java/cn/bugstack/export/example/AsposeWordsMigrationComparisonExample.java)
使用同一个 `MigrationInput` 生成两份语义一致的 Word：

- `generateWithAspose(...)`：保留 Aspose `DocumentBuilder` 游标、样式清理、表格宽度计算、
  `CellMerge` 和 `ChartSeries` 组装。
- `generateWithFramework(...)`：使用 `ReportDefinition` 描述版式，使用 `ReportModule` 读取强类型数据，
  使用 `ReportSectionBuilder` 组合段落、表格和图表。

运行：

```bash
mvn -q -DskipTests package
java -cp "target/classes:lib/*" \
  cn.bugstack.export.example.AsposeWordsMigrationComparisonExample
```

输出：

- `target/aspose-words-direct-migration-example.docx`
- `target/omni-office-migration-example.docx`

## 2. 先选择正确的迁移层

omni-office 不只有一层 API。存量 Aspose 代码应根据职责迁移，避免把底层排版细节重新带入
业务模块。

| Aspose 存量代码 | 推荐目标 | 适用场景 |
| --- | --- | --- |
| 通用 Word 封装、自定义 Inline/节点、精细排版 | `office.docx` / `DocxDocument` | 仍需要接近 Word 结构，但希望去掉游标状态管理 |
| 具体业务报告、按模块组合的评估文档 | `export` / `ReportDefinition` + `ReportModule` | 业务开发的默认选择 |
| AI 自由生成文档结构 | `DocumentSpec` | 内部 AI 按安全 Schema 生成段落、表格、图表和图形 |
| 外部模型或第三方服务调用 | Function Calling / MCP | 对外只暴露受校验的 DocumentSpec，不暴露服务器路径和 Aspose 对象 |
| Aspose 特殊能力暂无标准语义 | `ReportElementCompiler<T>` | 作为受控扩展点保留，不向所有业务泄漏 Aspose |

## 3. Aspose.Words 与 `office.docx` 对照表

`office.docx` 是最接近 Aspose.Words 的迁移层。它仍然表达 Document、Section、Paragraph、Table
和 Inline，但先构建组件树，最后统一校验和渲染，不再让业务代码持有 Aspose 游标。

| Word 功能 | Aspose.Words 直接写法 | `office.docx` 写法 | 迁移后的变化 |
| --- | --- | --- | --- |
| 文档入口 | `new Document()` + `new DocumentBuilder(document)` | `DocxDocument.create()` | 不向调用方暴露 `DocumentBuilder` |
| 文档元数据 | `getBuiltInDocumentProperties()` | `.metadata(title, author, subject)` | 元数据与正文组装分离 |
| 纸张、方向、页边距 | `Section.getPageSetup()` 逐项设置 | `.pageSetup(setup -> setup.paper(...).landscape().margins(...))` | 页面配置集中在文档根节点 |
| 样式集 | 手工修改 Word `Style` 或每次设置 `Font` | `.useStyleProfile(...)` / `.useDefaultStyles()` | 一次替换整套标题、正文、题注和表格样式 |
| 自定义段落样式 | 向 `Document.getStyles()` 注册并维护继承关系 | `.registerParagraphStyle(style)` | 业务使用稳定样式名，渲染器负责映射 |
| 封面 | 手工分节、换页和排版 | `.cover(...)` 或 `.templateCover()` | 标准封面与可组合封面都是前置页节点 |
| 修订记录 | 手工建表和分页 | `.revisionHistory(...)` | 修订数据与表格排版解耦 |
| 审批签署页 | 手工建表和分页 | `.approvalPage(...)` | 签署页变成标准前置节点 |
| 页眉 | `moveToHeaderFooter(HEADER_PRIMARY)` | `.header(text)` | 无需移动游标再回到正文 |
| 页脚和页码 | `moveToHeaderFooter` + `insertField("PAGE")` | `.footer("第 PAGE 页")` | `PAGE` 由渲染器写入 Word 域 |
| 分节页码 | 手工设置 restart/startAt | `.tableOfContentsFooter(...)` + `.modulePageNumberStart(...)` | 目录与正文页码规则显式化 |
| 目录 | `insertTableOfContents(...)` 并处理分节 | `.tableOfContents(title, depth)` | 目录、前置页和正文边界统一处理 |
| 一至九级标题编号 | 手工创建 `List`/`ListLevel` 并绑定 Heading | `.enableHeadingNumbering()` + `.heading1(...)` 至 `.heading9(...)` | 继续使用 Word 原生多级编号，不是文本前缀 |
| Section | `builder.moveToSection(...)` | `.section() ... .end()` | 段落、表格等块归属关系在树中可见 |
| 普通段落 | `write/writeln` 并管理当前段落格式 | `.paragraph().style(...).text(...).end()` | 段落样式不会意外泄漏到下一段 |
| 同段多文本样式 | 反复更改 `builder.getFont()` 并手工恢复 | `.text(text, runStyle -> ...)` 可连续调用 | 每个 TextRange 独立携带字体、字号、颜色和文字效果 |
| 中英文字体 | `Font.setNameAscii/setNameFarEast` | `RunStyle.setAsciiFontFamily/setFarEastFontFamily` | 宋体/新罗马只是默认值，可用样式画像动态替换 |
| 项目符号/编号 | `applyBulletDefault/applyNumberDefault/removeNumbers` | `.bullet(text)` / `.numbered(text)` | 列表状态由渲染上下文管理 |
| 显式分页 | `insertBreak(BreakType.PAGE_BREAK)` | `.pageBreak()` | 分页符是独立块节点 |
| 图片 | `insertImage(...)` | `paragraph().image(source, width, height)` | 图片作为 Paragraph 的 Inline child |
| Visio 预览 | 手工插入预览图 | `paragraph().visio(preview)` | 只展示预览图 |
| Word 内可编辑 Visio | 手工组装 OLE 包、预览和关系 | `.editableVisio(vsdx, preview, width, height)` | VSDX 嵌入 DOCX，PNG 用于页面展示 |
| 图题/表题 | 手工维护编号和样式 | `.figureCaption(...)` / `.tableCaption(...)` | 编号由渲染上下文统一管理 |
| 题注交叉引用 | 手工书签、`REF` 域和更新 | `.captionRef(type, captionId)` | 业务用稳定 ID 引用，不关心当前序号 |
| 表格 | `startTable/insertCell/endRow/endTable` | `.table().headers(...).row(...).end()` | 不再依赖单元格游标顺序 |
| 单元格复合内容 | 移动到 Cell 后写入多段内容 | `.row(row -> row.cell(cell -> ...))` | 单元格中可组合段落、图片等块 |
| 表格页面宽度自适应 | 手工扣减页边距并计算 point | `.widths(2, 5, 2)` | 参数是列宽比例，表格总宽度由页面决定 |
| 表格对齐 | `Table.setAlignment(...)` | `.alignment(...)` | 表格位置与单元格文本对齐是两个独立概念 |
| 表头/表内容样式 | 每行或每单元格修改 `Font` | `.headerTextStyle(...)` / `.bodyTextStyle(...)` | 默认表头与内容已分区，且可单表覆盖 |
| 横向/纵向合并 | `CellMerge.FIRST/PREVIOUS` 分布在物理单元格上 | `columnSpan(...)` / `verticalMerge(...)` | 组件树负责把逻辑合并转成 Aspose 物理单元格 |
| Word 原生图表 | `insertChart` + `series.clear/add` + 图例/标签/轴配置 | `.chart(type).categories(...).series(...).end()` | 支持柱状、条形、饼图、折线和雷达图，结果仍可编辑 |
| Paragraph 内图表 | 需管理 Shape 插入时的段落位置 | `.paragraph().chart(type)...end().end()` | 图表可作为 Paragraph Inline child |
| 类设计表格 | 反射/解析源码后手工生成多张表 | `.classDesignTable(...)` | 内置源码和 Javadoc 解析链路 |
| 输出文件 | `document.save(...)` | `.save(path)` | 保存前先执行 `DocxValidator` |
| 输出字节 | `ByteArrayOutputStream` + `document.save(stream, ...)` | `.toByteArray()` | 适合 Web 下载或附件；大文档仍建议输出文件 |

### 3.1 适合使用 `office.docx` 的情况

- 原有代码本质上是通用 Word 组件，而不是某份具体业务报告。
- 需要复合单元格、题注引用、自定义 Inline 或贴近 Word 的精细结构。
- 希望消除 `DocumentBuilder` 游标和格式状态泄漏，但暂时不拆分业务模块。

## 4. Aspose.Words 与业务 `export` 对照表

`export` 不是 Aspose 的另一套方法名，而是将“一份 Word 怎么画”提升为“一份业务报告由哪些
模块组成”。业务代码默认应迁到这一层。

| 业务能力 | Aspose 项目中常见实现 | `export` 对应入口 | 职责边界 |
| --- | --- | --- | --- |
| 定义一类报告 | 一个超长 `generateXxxReport` 方法 | `AbstractReportDefinition<T>` | 只定义报告编码、版本、版式和模块顺序 |
| 报告全局版式 | 在生成方法开头设置 PageSetup/页眉/页脚 | `ReportLayout.builder()` | 样式画像、目录、标题编号和页码统一管理 |
| 业务输入 | Map、DTO 与 `DocumentBuilder` 交叉使用 | `ReportRequest<T>.input(input)` | 保留强类型对象，不强制全部转成 JSON |
| 数据分发 | 生成方法自行传参和强转 | `ReportDataContext` + `ReportDataKey<T>` | 模块只能读取已声明的类型数据 |
| 报告模块 | 按顺序调用多个 `writeXxx(builder)` | `AbstractReportModule<T>` | 每个模块拥有稳定编码、标题和数据类型 |
| 模块顺序 | 依赖 Java 调用顺序 | `ModuleSlot` 的声明顺序 | 调用方选择的顺序就是文档顺序 |
| 模块依赖 | 以注释或隐式约定维护 | `ModuleSlot.dependsOn(...)` + `ReportPlanner` | 缺失依赖或顺序冲突在生成前失败 |
| 条件模块 | `if` 散落在写 Word 代码中 | `ReportConditionRegistry` | 是否生成与如何排版分离 |
| 章节 | 切换 Heading 样式后写文本 | `ReportSectionBuilder.section(...)` | 子章节是语义树，不是游标位置 |
| 普通段落 | `builder.writeln(...)` | `section.paragraph(...)` | 默认继承报告样式画像 |
| 同段多 TextRange | 切换 `Font` 后连续 `write` | `section.richParagraph().text(...).text(...).end()` | 每个范围独立样式，无需手工恢复 |
| 列表 | 管理 `ListFormat` 开始和结束 | `section.bullet(...)` / `.numbered(...)` | 每个列表项是明确语义元素 |
| 表格 | 业务循环中穿插 CellFormat 设置 | `section.table(headers).widths(...).row(...).end()` | 业务传数据和比例列宽，渲染器处理页面尺寸 |
| 表头/内容动态样式 | 分支判断当前行是否表头 | `.headerTextStyle(...)` / `.bodyTextStyle(...)` | 未配置属性继承 `StyleProfile` 默认值 |
| 表格合并 | 循环计算 `CellMerge.FIRST/PREVIOUS` | `.merge(row, column, rowSpan, columnSpan)` | 表头是第 0 行，合并区越界/重叠会校验失败 |
| 表格题注 | 手工在表格前后写题注 | `.caption(text, autoNumbered, ABOVE/BELOW)` | 题注位置是显式属性 |
| 图片 | 业务方法直接传路径给 Aspose | `section.image(source, width, height, caption, position)` | 业务 Export 可用受信路径；外部入口必须改用 `assetId` |
| Diagram/Visio | 生成 VSDX/PNG 后手工插入 | `section.diagram(ReportDiagram)` | 流程图数据、图工件和 Word 嵌入分层处理 |
| Word 原生图表 | 业务方法组装 `ChartSeries` | `section.chart(type)...end()` | 图表语义可复用到 DocumentSpec 与外部工具 |
| 分页 | `insertBreak(...)` | `section.pageBreak()` | 分页作为报告语义元素 |
| 类设计表 | 每份报告自行解析源码 | `section.classDesignTable(...)` | 业务侧只提供源码根和类名 |
| 自定义业务语义 | 直接向 Aspose 文档插入特殊内容 | 自定义 `ReportElement` + `ReportElementCompiler<T>` | 特殊 Aspose 代码集中在编译边界，不泄漏到模块 |
| 结构校验 | 生成失败后才发现数据问题 | `ReportDocumentValidator` | 空内容、表格列不一致、图表矩阵错误等在渲染前拒绝 |
| 导出格式 | 各分支分别调用 `SaveFormat` | `ReportOutputFormat.DOCX/PDF/HTML` | 同一语义文档输出三种格式 |
| 安全保存 | 直接覆盖目标文件 | `DocxReportCompiler` 临时文件成功后再替换 | 生成失败不破坏已有报告 |

### 4.1 最小 `export` 调用链

```text
ReportRequest<T>
    -> ReportDefinition<T> 创建 ReportBlueprint
    -> ReportPlanner 解析 ModuleSlot/依赖/条件
    -> ReportModule<T> 生成 ReportDocument
    -> ReportDocumentValidator
    -> DocxReportCompiler
    -> DOCX / PDF / HTML
```

### 4.2 适合使用 `export` 的情况

- 文档是评估报告、项目报告、设计文档等明确业务产品。
- 同一报告需要按入参动态选择、排序或隐藏模块。
- 需要在不修改业务组装代码的情况下切换 DOCX、PDF 或 HTML。

## 5. `export`、内部 AI、Function Calling 与 MCP 使用对照

### 5.1 入口选择表

| 入口 | 主要调用者 | 输入是什么 | 谁决定章节结构 | 最终输出 | 优先使用场景 |
| --- | --- | --- | --- | --- | --- |
| 业务 `export` | 同一 Java 应用内的业务代码 | 强类型业务对象 `T` | `ReportDefinition` + `ReportModule` | 文件或字节，DOCX/PDF/HTML | 结构稳定、业务规则复杂、需要可编译期重构 |
| `office.docx` | Word 底层组件、特殊排版开发者 | `DocxDocument` 组件树 | Java Builder 调用方 | DOCX 文件或字节 | 贴近 Word 结构、需要复合 Cell/Inline/题注引用 |
| 内部 AI 自由模式 | 应用内受信的 AI 能力 | 指令 + 上下文 JSON | AI 生成完整 `DocumentSpec` | `AiDocumentResult` 审查后导出 | 结构不固定的临时报告、总结、初稿 |
| 内部 AI 模板模式 | 应用内受信的 AI 能力 | 模板 ID/版本 + 指令 + 上下文 | 已发布 `DocumentTemplate` | `AiDocumentResult` 审查后导出 | 章节稳定、只需 AI 填写数据；小模型首选 |
| Function Calling | 已经集成某家模型 SDK 的业务网关 | JSON Function Schema + JSON 参数 | 模板或外部 AI 生成的 `DocumentSpec` | 结构化工具结果 + `resourceUri` | 已有 tool-call 循环，不需要完整 MCP 会话 |
| MCP stdio | 本地 IDE、Agent 或 MCP Host | MCP `tools/call` | 模板或 MCP Host 中的 AI | `resource_link` / `resources/read` | 本地工具化、子进程托管 |
| MCP Streamable HTTP | 远程 Agent、多租户平台 | MCP 会话 + `tools/call` | 模板或远程 AI | 受控资源 URI，支持异步 Tasks | 认证、会话、限流、并发和跨租户隔离 |

所有智能入口最终都收敛到 `DocumentSpec/DocumentTemplate -> ReportDocument -> DocxReportCompiler`。
它们不会替换已有业务 `export` 模板，也不允许绕过语义校验直接操作 Aspose。

### 5.2 内部 AI 两种模式对照

| 对比项 | 自由文档 `generateFreeform` | 模板填充 `generateFromTemplate` |
| --- | --- | --- |
| AI 输出 | 完整 `DocumentSpec@1.0` | 只输出模板数据 Schema 允许的字段 |
| 章节控制权 | AI | 已发布模板 |
| 业务稳定性 | 中等，适合可变结构 | 高，适合正式业务文档 |
| 小模型适配 | 需更强结构生成能力 | 推荐，例如本地 `qwen3.5:2b` |
| 图片来源 | 禁止 AI 伪造路径、URL 和已有 artifactId | 由模板和受控数据决定 |
| Diagram | 使用带工件目录的应用时可生成内联 `DiagramSpec` | 由模板定义图结构或图数据 |
| 失败处理 | JSON/Schema/安全校验失败后有界校正，不回退到模板模式 | 数据/模板/DocumentSpec 校验失败后有界校正，不回退到自由模式 |
| 审查点 | 先检查 `AiDocumentResult.getDocumentSpec()` 再导出 | 先检查填充数据和编译后 DocumentSpec 再导出 |
| 导出 | `exportToBytes(result, format)` 或 `export(result, format, path)` | 同左 |

### 5.3 Function Calling 与 MCP 对照

| 对比项 | Function Calling | MCP |
| --- | --- | --- |
| 业务门面 | `ExternalDocumentToolApplication` | 同一个 `ExternalDocumentToolApplication` |
| 协议适配 | `FunctionCallingDocumentAdapter` | `McpJsonRpcServer` / `McpHttpServer` |
| 工具定义 | `type=function` + `function.parameters` JSON Schema | `tools/list` 中的 `inputSchema` |
| 调用 | `functions.invoke(name, argumentsJson)` | `tools/call` |
| 会话 | tool call ID 等由模型网关管理 | 显式 `initialize` 和 `MCP-Session-Id` |
| 传输 | 由接入的模型 SDK/网关决定 | 换行分隔 stdio 或 Streamable HTTP JSON 响应模式 |
| 异步长任务 | 由外部网关自行编排 | MCP `2025-11-25` 可使用 `tasks/get/result/cancel` |
| 文件返回 | 结果中返回 `resourceUri`，网关可调用 `readResource` | 结果含 `resource_link`，再调用 `resources/read` 或受控下载 |
| 认证/多租户 | 由承载 Function Calling 的业务网关绑定主体 | HTTP MCP 内置 API Key/JWT/OIDC、Origin、会话和租户隔离 |
| 适用场景 | 项目已有模型 SDK 和 tool-call 循环 | 希望让多种 Agent/IDE/Host 使用标准工具协议 |

### 5.4 Function Calling 和 MCP 共用工具表

| 工具 | 何时调用 | 主要输入 | 主要输出 | 后续用法 |
| --- | --- | --- | --- | --- |
| `omni_templates_list` | AI 先选择已发布模板 | 无 | 模板 ID、精确版本和描述 | 选定后调用 `omni_template_schema` |
| `omni_template_schema` | 获取模板允许的业务数据形状 | `templateId`、`version` | JSON Schema | AI 按 Schema 填数，不自定义章节 |
| `omni_template_export` | 使用已发布模板生成文档 | 模板 ID/版本、`data`、`outputFormat` | DOCX/PDF/HTML 工件引用 | 使用 `resourceUri` 下载 |
| `omni_document_export` | 外部 AI 自己生成完整文档结构 | `DocumentSpec@1.0` + `outputFormat` | DOCX/PDF/HTML 工件引用 | 适合自由文档；仍必须通过 Schema 和语义校验 |
| `omni_diagram_generate` | 需要流程图、ER、用例图或可编辑 Visio | `DiagramSpec@1.0` | `diagramArtifactId`、VSDX/PNG 资源 | 将 `diagramArtifactId` 写入 DocumentSpec `diagram` block |
| `omni_asset_store` | 外部图片需要嵌入 Word | Base64 PNG/JPEG | 当前主体私有 `assetId` | 将 `assetId` 写入 DocumentSpec `image` block |
| `omni_asset_get` | 嵌入前确认资产媒体类型和元数据 | `assetId` | 受管图片元数据 | 只能读取当前主体的资产 |
| `omni_asset_delete` | 图片不再使用时主动清理 | `assetId` | 删除结果 | 属于破坏性操作，调用方应明确授权 |

### 5.5 同一 Word 功能在四种入口中如何传递

| Word 功能 | 业务 `export` | 内部 AI | Function Calling / MCP | 安全边界 |
| --- | --- | --- | --- | --- |
| 段落和 TextRange | `paragraph/richParagraph` | AI 生成 `paragraph.textRanges` 或模板映射 | `omni_document_export` 的 paragraph block | 字体、字号、颜色和文字效果受 Schema/范围校验 |
| 表格 | `table/row/widths/merge` | AI 生成 table block 或只填充模板数组 | table block | 列宽为比例；合并区不能重叠或越界 |
| 图表 | `chart(ReportChartType)` | chart block | chart block，不需要额外图表工具 | categories 与 series values 必须是合法矩阵 |
| 业务模块 | `ReportModule<T>` | 模板模式通过固定结构间接表达 | 外部不暴露自定义业务模块 | 外部协议不能调用任意 Java 模块 |
| 图片 | 受信业务路径/对象 | 自由模式不允许伪造路径或 URL | 先 `omni_asset_store`，再传 `assetId` | 不暴露服务器文件路径 |
| Diagram/Visio | `ReportDiagram` | 开启工件能力时可内联 DiagramSpec | `omni_diagram_generate` 或内联 DiagramSpec | 图工件归属当前主体，VSDX/PNG 路径不对外暴露 |
| 输出 | `ReportOutputFormat` | `exportToBytes/export` | `outputFormat` + `resourceUri` | 外部调用者不能指定服务器输出路径 |

### 5.6 最小调用链选择

```text
固定正式业务报告       -> 业务 export
存量 Aspose 底层组件     -> office.docx
内部临时文档             -> AI generateFreeform
内部正式模板 + AI 填数    -> AI generateFromTemplate
已有模型 SDK/tool-call 网关  -> Function Calling
需要通用 Agent/IDE 工具协议 -> MCP
```

## 6. 同段多样式对照

Aspose 写法需要认真管理当前 `Font` 状态：

```java
builder.write("当前状态：");
builder.getFont().setColor(Color.decode("#548235"));
builder.getFont().setBold(true);
builder.write(input.status);
builder.getFont().setColor(Color.BLACK);
builder.getFont().setBold(false);
```

框架将样式绑定在对应的文本范围，不会污染后续文本：

```java
section.richParagraph()
        .text("当前状态：")
        .text(input.status, style -> {
            style.setFontColor("#548235");
            style.setBold(true);
        })
        .end();
```

## 7. 表格对照

Aspose 写法需要同时处理页面可用宽度、每列宽度、边框、对齐、字体槽位和合并状态。
框架侧保留业务上有意义的数据：

```java
section.table("阶段", "实现路径", "状态")
        .style("TableHeader")
        .widths(2, 5, 2)
        .row("业务组装", "ReportDefinition + ReportModule", "可迁移")
        .row("", "ReportDocument + DocxReportCompiler", "可输出")
        .merge(1, 0, 2, 1)
        .caption("组件迁移状态", true, CaptionPosition.ABOVE)
        .end();
```

`widths` 是比例，不是固定磅值。框架会根据当前 Section 的纸张、方向和页边距计算表格总宽度。
合并区域内被覆盖的单元格既可以留空，也可以重复左上角内容；若填写不同内容，校验器会拒绝生成，
避免合并时静默丢失业务数据。

## 8. 原生图表对照

```java
section.chart(ReportChartType.COLUMN)
        .title("年度业务指标对比")
        .categories(input.metrics)
        .series("2025 年", input.metric2025)
        .series("2026 年", input.metric2026)
        .axisTitles("指标", "完成值")
        .legend(true, ReportChartLegendPosition.BOTTOM)
        .showValues(true)
        .caption("年度业务指标对比图", CaptionPosition.BELOW)
        .end();
```

生成结果仍是 Word 原生可编辑图表，并非将图表转换为截图。`COLUMN`、`BAR`、`PIE`、`LINE`
和 `RADAR` 共享同一数据结构。

## 9. 渐进迁移步骤

1. 先提取业务输入对象，让旧 Aspose 方法与新框架方法共享同一输入。
2. 为旧文档建立结构契约：标题、表格行列数、合并区、图表类型/系列数、题注和关键文本。
3. 将全局页面和样式配置迁入 `ReportLayout` 与 `StyleProfile`。
4. 按业务职责将 `DocumentBuilder` 代码拆成强类型 `ReportModule<T>`。
5. 将段落、表格、图表等改为语义 Builder，保留暂无抽象的特殊 Aspose 能力作为受控扩展。
6. 同时运行旧新两条路径，进行 OOXML 结构回读和逐页渲染检查。
7. 业务验收后切换调用方；暂不删除旧实现，保留一个可回退的发布周期。

## 10. 迁移边界

- 不要在 `ReportModule` 中获取 Aspose `DocumentBuilder`；这会重新引入游标耦合。
- 不要把固定磅值列宽原样迁移到业务层；业务侧传递列宽比例。
- 不要把 `DocumentSpec` 当作无限制 OOXML 容器；它是 AI 和外部服务的安全协议。
- 若某项 Aspose 能力只对一个业务有意义，优先用自定义 `ReportElement` +
  `ReportElementCompiler<T>` 扩展，不必立即扩展公开 DocumentSpec。
- 迁移完成的标准是业务语义、Word 结构和视觉结果都通过验收，不是新代码的行数更少。

## 11. 建议的长期代码结构

```text
business input
    -> ReportDefinition       全局版式、元数据、模块顺序
    -> ReportModule<T>        业务数据到报告语义
    -> ReportDocument         目标格式无关的内容树
    -> DocxReportCompiler     语义到 word-core
    -> AsposeDocxRenderer     Aspose 集中适配边界
    -> DOCX / PDF / HTML
```

迁移后，Aspose 仍是底层 Word 引擎，但业务代码不再直接承担游标、节点、样式污染和输出格式分支。
