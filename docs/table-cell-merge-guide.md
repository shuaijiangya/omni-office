# 表格合并单元格使用指南

本指南说明 omni-office 中表格合并单元格的使用方式，覆盖业务 `export`、`DocumentSpec`、
内部 AI、Function Calling、MCP 和底层 `office.docx`。如果是在业务报告中生成表格，优先使用
`ReportTableBuilder.merge(...)`；只有开发通用 Word 组件时，才需要直接操作 `office.docx` 的
`columnSpan` 和 `TableVerticalMerge`。

## 1. 快速示例：纵向合并两行“通过”

在 `ReportModule.composeContent(...)` 中可以直接这样写：

```java
section.table("评估项", "结论", "说明")
        .style("TableHeader")
        .widths(130, 100, 260)
        .row("架构边界", "通过", "模块职责清晰，导出层与渲染层已隔离。")
        .row("可维护性", "通过", "新增模块可通过策略注册表独立接入。")
        .merge(1, 1, 2, 1)
        .caption("评估概览", true)
        .end();
```

生成效果在逻辑上等同于：

| 评估项 | 结论 | 说明 |
| --- | --- | --- |
| 架构边界 | 通过（纵向跨两行） | 模块职责清晰，导出层与渲染层已隔离。 |
| 可维护性 | ↑ | 新增模块可通过策略注册表独立接入。 |

两行都可以保留“通过”。框架会校验两个值相同，并只把左上角的“通过”写入最终合并单元格。

## 2. `merge` 参数与坐标规则

方法签名：

```java
merge(int startRow, int startColumn, int rowSpan, int columnSpan)
```

| 参数 | 含义 | 是否从 0 开始 |
| --- | --- | --- |
| `startRow` | 合并区域左上角所在行 | 是；表头是第 0 行 |
| `startColumn` | 合并区域左上角所在列 | 是 |
| `rowSpan` | 合并区域一共跨多少行 | 不是坐标，最小为 1 |
| `columnSpan` | 合并区域一共跨多少列 | 不是坐标，最小为 1 |

假设表格为：

```java
.table("评估项", "结论", "说明") // 第 0 行：表头
.row("架构边界", "通过", "说明一") // 第 1 行：第一条数据
.row("可维护性", "通过", "说明二") // 第 2 行：第二条数据
```

它的逻辑坐标如下：

| 行坐标 | 第 0 列 | 第 1 列 | 第 2 列 |
| --- | --- | --- | --- |
| 0 | 评估项 | 结论 | 说明 |
| 1 | 架构边界 | 通过 | 说明一 |
| 2 | 可维护性 | 通过 | 说明二 |

因此 `.merge(1, 1, 2, 1)` 表示：

- 从第 1 行、第 1 列的“通过”开始。
- 向下覆盖 2 行。
- 横向只占 1 列。

需要注意：校验错误中的 `row[0]`、`row[1]` 是数据行下标，不包含表头；而 `merge` 的行坐标
包含表头。这是错误路径和合并坐标之间最容易混淆的地方。

## 3. 合并区域的内容规则

框架对被合并区域采用以下规则：

| 被覆盖单元格内容 | 是否允许 | 最终显示 |
| --- | --- | --- |
| 空字符串或纯空白 | 允许 | 显示左上角内容 |
| 与左上角完全相同 | 允许 | 显示左上角内容一次 |
| 与左上角不同 | 不允许 | 校验失败，不生成文档 |

下面两种写法都合法：

```java
// 写法一：保留完整业务数据，推荐用于数据直接映射的场景
.row("架构边界", "通过", "说明一")
.row("可维护性", "通过", "说明二")
.merge(1, 1, 2, 1)
```

```java
// 写法二：主动清空被覆盖单元格
.row("架构边界", "通过", "说明一")
.row("可维护性", "", "说明二")
.merge(1, 1, 2, 1)
```

下面的写法不合法：

```java
.row("架构边界", "通过", "说明一")
.row("可维护性", "不通过", "说明二")
.merge(1, 1, 2, 1)
```

因为合并会隐藏第二行的“不通过”，框架会拒绝这种静默数据丢失。Report 层会报告：

```text
merged report table cell must be blank or equal to its top-left cell
```

DocumentSpec 层对应的错误码为：

```text
MERGED_CELL_CONTENT_MISMATCH
```

“完全相同”按字符串原值判断。例如 `"通过"` 和 `"通过 "` 不相同；纯空白字符串仍按空值处理。

## 4. 业务 `export` 常用示例

### 4.1 纵向合并

将同一个业务域纵向合并三行：

```java
section.table("业务域", "组件", "状态")
        .widths(2, 4, 2)
        .row("业务域 A", "文档生成", "正常")
        .row("业务域 A", "图形生成", "正常")
        .row("业务域 A", "资产管理", "正常")
        .merge(1, 0, 3, 1)
        .end();
```

合并起点是第一条数据的第 0 列，因此使用 `startRow=1`、`startColumn=0`。

### 4.2 横向合并表头

将“年度指标”横向覆盖两个表头列：

```java
section.table("区域", "年度指标", "年度指标")
        .widths(2, 2, 2)
        .row("华东", "2025 年", "2026 年")
        .row("华南", "80", "92")
        .merge(0, 1, 1, 2)
        .end();
```

这里从表头第 1 列开始，横向跨 2 列，所以是 `.merge(0, 1, 1, 2)`。两个表头值相同，
最终只显示一个“年度指标”。如果需要复杂的两级表头，应确认第二行数据确实承担子表头语义。

### 4.3 横向合并数据行

让一条说明占据后两列：

```java
section.table("序号", "问题", "处理说明")
        .widths(1, 3, 4)
        .row("1", "暂无问题", "暂无问题")
        .row("2", "接口需补充鉴权", "计划下个版本完成")
        .merge(1, 1, 1, 2)
        .end();
```

第一条数据行的第 1、2 列内容必须相同或其中后续单元格为空。

### 4.4 矩形合并

同时跨两行、两列：

```java
section.table("区域", "分组", "任务", "状态")
        .widths(2, 2, 4, 2)
        .row("核心域", "核心域", "任务 A", "正常")
        .row("核心域", "核心域", "任务 B", "正常")
        .merge(1, 0, 2, 2)
        .end();
```

`.merge(1, 0, 2, 2)` 会生成一个 2×2 的矩形合并区域。区域内四个单元格必须为空或与左上角
“核心域”完全相同。

### 4.5 多个互不重叠的合并区域

```java
section.table("业务域", "模块", "负责人", "状态")
        .row("业务域 A", "模块一", "张三", "正常")
        .row("业务域 A", "模块二", "张三", "正常")
        .row("业务域 B", "模块三", "李四", "正常")
        .row("业务域 B", "模块四", "李四", "正常")
        .merge(1, 0, 2, 1)
        .merge(1, 2, 2, 1)
        .merge(3, 0, 2, 1)
        .merge(3, 2, 2, 1)
        .end();
```

合并区域可以有多个，声明顺序不影响结果，但它们不能相互覆盖。

## 5. DocumentSpec JSON 示例

内部 AI 自由模式和外部 `omni_document_export` 都使用相同的 table block：

```json
{
  "schemaVersion": "1.0",
  "metadata": {
    "title": "系统评估报告",
    "author": "评估组",
    "subject": "合并单元格示例"
  },
  "layout": {
    "styleProfile": "DEFAULT"
  },
  "sections": [
    {
      "title": "评估概览",
      "blocks": [
        {
          "type": "table",
          "styleName": "TableHeader",
          "headers": ["评估项", "结论", "说明"],
          "columnWidths": [130, 100, 260],
          "rows": [
            ["架构边界", "通过", "模块职责清晰"],
            ["可维护性", "通过", "新增模块可独立接入"]
          ],
          "merges": [
            {
              "startRow": 1,
              "startColumn": 1,
              "rowSpan": 2,
              "columnSpan": 1
            }
          ],
          "caption": "评估概览",
          "captionAutoNumbered": true,
          "captionPosition": "BELOW"
        }
      ]
    }
  ]
}
```

`columnWidths` 是相对比例权重，不是固定磅值。上面的 `[130, 100, 260]` 会按比例分配当前页面
正文可用宽度。

Java 方式构造相同的 DocumentSpec：

```java
TableBlockSpec table = new TableBlockSpec();
table.setHeaders(Arrays.asList("评估项", "结论", "说明"));
table.setRows(Arrays.asList(
        Arrays.asList("架构边界", "通过", "模块职责清晰"),
        Arrays.asList("可维护性", "通过", "新增模块可独立接入")));
table.setMerges(Collections.singletonList(
        new TableMergeSpec(1, 1, 2, 1)));
```

## 6. 内部 AI、Function Calling 和 MCP 如何使用

三种入口不需要单独定义一套合并协议，它们都使用 DocumentSpec 的 `table.merges`。

| 入口 | 合并数据由谁产生 | 使用位置 |
| --- | --- | --- |
| 内部 AI 自由模式 | AI 生成完整表格和 `merges` | `generateFreeform` 输出的 DocumentSpec |
| 内部 AI 模板模式 | 模板定义合并区域，AI 只填写业务数据 | `generateFromTemplate` 编译后的 DocumentSpec |
| Function Calling | 外部模型按工具 JSON Schema 填写 | `omni_document_export` 参数 |
| MCP | MCP Host 按 `tools/list` Schema 填写 | `tools/call` 的 `arguments` |

调用 `omni_document_export` 时，参数就是完整 DocumentSpec，并在顶层额外增加输出格式：

```json
{
  "schemaVersion": "1.0",
  "metadata": {
    "title": "系统评估报告"
  },
  "layout": {},
  "sections": [
    {
      "title": "评估概览",
      "blocks": [
        {
          "type": "table",
          "headers": ["评估项", "结论", "说明"],
          "rows": [
            ["架构边界", "通过", "说明一"],
            ["可维护性", "通过", "说明二"]
          ],
          "merges": [
            {"startRow": 1, "startColumn": 1, "rowSpan": 2, "columnSpan": 1}
          ]
        }
      ]
    }
  ],
  "outputFormat": "DOCX"
}
```

外部入口仍会执行 Schema、合并边界、重叠区域和合并内容一致性校验，不能绕过校验直接操作 Aspose。

## 7. 底层 `office.docx` 合并方式

`office.docx` 更接近 Word 物理结构，不提供 `merge(startRow, startColumn, rowSpan, columnSpan)`。
横向合并通过 `columnSpan` 表达，纵向合并通过 `TableVerticalMerge.FIRST/PREVIOUS` 表达。

### 7.1 纵向合并

```java
DocxDocument.create()
        .section()
        .table()
        .row(row -> row
                .cell(cell -> cell
                        .verticalMerge(TableVerticalMerge.FIRST)
                        .verticalAlign(TableCellVerticalAlignment.CENTER)
                        .paragraph().text("通过").end())
                .cell(cell -> cell.paragraph().text("第一行").end()))
        .row(row -> row
                .cell(cell -> cell
                        .verticalMerge(TableVerticalMerge.PREVIOUS))
                .cell(cell -> cell.paragraph().text("第二行").end()))
        .end()
        .end()
        .save(output);
```

第一行使用 `FIRST`，后续所有延续行使用 `PREVIOUS`。底层延续单元格通常不再写正文。

### 7.2 横向合并

```java
.table()
.row(row -> row
        .cell(2, cell -> cell.paragraph().text("横向跨两列").end())
        .cell(cell -> cell.paragraph().text("第三列").end()))
.row("第一列", "第二列", "第三列")
.end()
```

`cell(2, ...)` 表示当前物理单元格占两个逻辑列。每一行的逻辑列数必须一致。

### 7.3 2×2 矩形合并

```java
.table()
.row(row -> row
        .cell(2, cell -> cell
                .verticalMerge(TableVerticalMerge.FIRST)
                .paragraph().text("矩形合并").end())
        .cell(cell -> cell.paragraph().text("第一行第三列").end()))
.row(row -> row
        .cell(2, cell -> cell
                .verticalMerge(TableVerticalMerge.PREVIOUS))
        .cell(cell -> cell.paragraph().text("第二行第三列").end()))
.end()
```

`office.docx` 是底层组件 API，调用方需要自己保证 FIRST/PREVIOUS 连续、每行逻辑列数一致以及
被隐藏内容不会丢失。业务报告通常应使用上层 `ReportTableBuilder.merge(...)`，由框架统一校验。

## 8. 常见错误与处理

| 错误或现象 | 原因 | 处理方式 |
| --- | --- | --- |
| `table merge must span multiple rows or columns` | 使用了 `rowSpan=1` 且 `columnSpan=1` | 至少让一个 span 大于 1 |
| `report table merge exceeds table boundary` | 起点加跨度超出行数或列数 | 重新核对表头第 0 行和列坐标 |
| `report table merges must not overlap` | 两个矩形合并区域互相覆盖 | 拆分或调整合并区域 |
| `MERGED_CELL_CONTENT_MISMATCH` | 被覆盖单元格与左上角内容不同 | 改为相同内容、空字符串，或取消合并 |
| `row[n] cell count must ...` | 某一行实际逻辑列数与表头不一致 | 即使会被合并，每个业务数据行仍要传完整列数 |
| 合并了错误的行 | 忘记 `startRow=0` 是表头 | 第一条数据行从 `startRow=1` 开始 |
| `.widths(130,100,260)` 看起来不是固定宽度 | widths 是比例权重 | 按相对比例填写，表格总宽度由页面决定 |

## 9. 使用建议

1. 先画出包含表头的逻辑坐标表，再填写四个合并参数。
2. 从业务数据映射时可以保留重复值，框架会检查它们是否一致。
3. 不要通过合并隐藏不同业务含义；需要保留不同内容时，应取消合并或重新设计列。
4. 多个合并区域应保持互不重叠，并确保没有超出表格边界。
5. 业务模块优先使用 `ReportTableBuilder.merge(...)`，外部服务使用 DocumentSpec `merges`，
   通用 Word 组件才直接使用 `office.docx`。

## 10. 运行和验证

使用项目指定的 Maven 运行评估报告示例测试：

```bash
/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -q -Dtest=AssessmentReportExportExampleTest test
```

生成文件：

```text
target/assessment-report-example.docx
```

该测试会回读 Word 结构并确认：

- 第一条数据行的“通过”是纵向合并起点 `CellMerge.FIRST`。
- 第二条数据行对应单元格是合并延续 `CellMerge.PREVIOUS`。
- 合并后的最终可见文字仍为“通过”。

同时验证 Report 与 DocumentSpec 合并规则：

```bash
/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -q -Dtest=DefaultReportExporterTest,DocumentSpecValidatorTest test
```

相关可运行代码和测试：

- [`AssessmentReportExportExample`](../src/main/java/cn/bugstack/export/example/AssessmentReportExportExample.java)
- [`AssessmentReportExportExampleTest`](../src/test/java/cn/bugstack/export/example/AssessmentReportExportExampleTest.java)
- [`DocumentSpecValidatorTest`](../src/test/java/cn/bugstack/application/document/DocumentSpecValidatorTest.java)
- [`AsposeDocxRendererTest`](../src/test/java/cn/bugstack/office/docx/AsposeDocxRendererTest.java)
