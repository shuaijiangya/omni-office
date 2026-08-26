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

## 3. API 对照表

| 目标 | Aspose.Words 原生写法 | omni-office 框架写法 |
| --- | --- | --- |
| 创建文档 | `new Document()` + `new DocumentBuilder(document)` | `ReportRequest` + `DefaultReportExporter` |
| 文档元数据 | `document.getBuiltInDocumentProperties()` | `ReportBlueprint.Builder.metadata(...)` |
| 页面设置 | `builder.getCurrentSection().getPageSetup()` | `ReportLayout.Builder.pageSetup(...)` |
| 页眉页脚 | `moveToHeaderFooter(...)` + `insertField("PAGE")` | `layout.header(...)` + `layout.footer("第 PAGE 页")` |
| 正文标题 | `ParagraphFormat.setStyleIdentifier(...)` | `ReportDefinition` 标题 + `ReportSection` |
| 普通段落 | `builder.writeln(...)` | `section.paragraph(...)` |
| 同段多样式 | 反复修改 `builder.getFont()` 并记得恢复 | `section.richParagraph().text(text, style -> ...)` |
| 中西文字体 | `Font.setNameAscii/setNameFarEast` | `ReportTextRangeStyle.setAsciiFontFamily/setFarEastFontFamily` |
| 项目符号 | `ListFormat.applyBulletDefault/removeNumbers` | `section.bullet(...)` |
| 表格 | `startTable/insertCell/endRow/endTable` | `section.table(...).row(...).end()` |
| 页面宽度自适应 | 手工扣减页边距并计算每列 point | `.widths(2, 5, 2)` 只表示比例，框架计算实际宽度 |
| 表头/表内容样式 | 每个 Cell 手工设置 `Font` | `.headerTextStyle(...)` / `.bodyTextStyle(...)` 或继承 `StyleProfile` |
| 表格合并 | `CellMerge.FIRST/PREVIOUS` | `.merge(startRow, startColumn, rowSpan, columnSpan)` |
| 题注上下位置 | 手工决定在表格/图表前后写段落 | `.caption(text, autoNumbered, CaptionPosition)` |
| Word 原生图表 | `insertChart` + `series.clear/add` + 标题/图例/标签设置 | `section.chart(type).categories(...).series(...).end()` |
| 图片 | `builder.insertImage(...)` | `section.image(...)` |
| 目录 | `insertTableOfContents(...)` + 分节/页码手工管理 | `layout.tableOfContents(depth)` |
| 保存不同格式 | `document.save(path, SaveFormat...)` | `ReportOutputFormat.DOCX/PDF/HTML` |

## 4. 同段多样式对照

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

## 5. 表格对照

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

## 6. 原生图表对照

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

## 7. 渐进迁移步骤

1. 先提取业务输入对象，让旧 Aspose 方法与新框架方法共享同一输入。
2. 为旧文档建立结构契约：标题、表格行列数、合并区、图表类型/系列数、题注和关键文本。
3. 将全局页面和样式配置迁入 `ReportLayout` 与 `StyleProfile`。
4. 按业务职责将 `DocumentBuilder` 代码拆成强类型 `ReportModule<T>`。
5. 将段落、表格、图表等改为语义 Builder，保留暂无抽象的特殊 Aspose 能力作为受控扩展。
6. 同时运行旧新两条路径，进行 OOXML 结构回读和逐页渲染检查。
7. 业务验收后切换调用方；暂不删除旧实现，保留一个可回退的发布周期。

## 8. 迁移边界

- 不要在 `ReportModule` 中获取 Aspose `DocumentBuilder`；这会重新引入游标耦合。
- 不要把固定磅值列宽原样迁移到业务层；业务侧传递列宽比例。
- 不要把 `DocumentSpec` 当作无限制 OOXML 容器；它是 AI 和外部服务的安全协议。
- 若某项 Aspose 能力只对一个业务有意义，优先用自定义 `ReportElement` +
  `ReportElementCompiler<T>` 扩展，不必立即扩展公开 DocumentSpec。
- 迁移完成的标准是业务语义、Word 结构和视觉结果都通过验收，不是新代码的行数更少。

## 9. 建议的长期代码结构

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
