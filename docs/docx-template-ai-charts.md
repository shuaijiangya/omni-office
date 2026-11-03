# 上传 Word 模板、AI 填充与图表

这条链路适用于“用户提供现成 DOCX，保持原排版后补充内容”。它与从零构建的 `DocumentSpec → Word` 并存，不改动现有 export 业务模板。

## 上传模板后由 AI 自行填充

如果不想维护 `reportTemplate`、`styleBindings` 和逐章节业务 JSON，可使用 `UploadedDocxAiReportApplication`。调用方只传上传的 DOCX 字节、原始业务文字和输出路径；应用分析模板，按物理槽位逐次调用模型，最后组装新 Word。标题、表头、固定文字与格式取自上传模板；**需要填充的段落全文、动态表格的每一行，以及图表类型、标题、类别、系列名和数值均取自模型响应**，不是在 Java 中预先映射成报告块。

```java
byte[] uploadedDocx = Files.readAllBytes(Path.of("/data/upload/report-template.docx"));
String businessText = "项目原始业务数据和图表要求……";
StructuredAiClient model = new OllamaStructuredAiClient("qwen3.5:2b");
new UploadedDocxAiReportApplication(model).generate(
        uploadedDocx, "report-v1", businessText, Path.of("/data/output/report.docx"));
```

上传文件仍需标明可变位置，例如 `{{summary}}`、`{{table:metrics}}`、`{{chart:trend}}`。这不是一份额外的填充 JSON：标记只说明“哪里可以写、写什么类型”。普通槽位可用内容控件或 `fill_` 书签。未标记内容保持不变；标题和表头不由模型改写。若模板有含歧义的重复样本、标题层级跳跃或没有语义标题，自动模式会要求人工审核，避免猜错位置。

模型每个槽位返回一个小 JSON：段落为 `{"text":"..."}`，表格为 `{"rows":[["...","..."]]}`，图表为 `{"chartType":"COLUMN","title":"...","categories":["..."],"seriesName":"...","values":[1]}`。这些 JSON 是模型响应，不是调用方要准备的输入文件。系统校验行宽、类别与数值一一对应、原文数字来源；如果图表标题与已生成表格的指标列匹配，还会逐类别核对对应数值。校验失败时带原因重试，仍失败就停止导出，不用程序猜测或补造数据。图表采用可编辑的 Word 原生图表。

真实 Ollama 示例为 [UploadedDocxAiReportApplicationLiveTest](../src/test/java/cn/bugstack/application/template/importer/UploadedDocxAiReportApplicationLiveTest.java)。它使用[示例模板](examples/项目源码结构评估报告模板.docx)的上传字节、运行时统计的仓库源码事实，执行以下命令并生成 `target/uploaded-template-all-ai-report.docx`：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.17.jdk/Contents/Home \
  /Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -Dmaven.repo.local=/Users/luojiang/maven/repository \
  -Dtest=UploadedDocxAiReportApplicationLiveTest \
  -Domni.office.example.allAiOllama=true test
```

此模式适用于事实来源已经整理为原始文字、填充位置明确的模板；原文没有的信息不能要求模型可靠补齐。上线时应保存上传模板版本、原始业务文字、模型响应与最终 DOCX，以便复核；当前示例应用本身不提供 HTTP 上传端点或这些内容的持久化服务。

## 输入与输出

1. `DocxTemplateAnalyzer.analyze(source, templateId)` 读取源 DOCX，返回三份 JSON：
   - `structure`：源文件 SHA-256、页面尺寸、标题层级、段落样式与 Run 字体、表格行/列/合并结构、图表示例、占位符和审核问题。
   - `reportTemplate`：章节 ID、标题、级别、填充提示词、数据引用、允许的块类型，以及 `slots`。
   - `styleBindings`：章节 ID 到原 DOCX 物理锚点、样式样本和槽位的映射。
2. 业务方审核并修改 `reportTemplate` 的 `prompt`、`dataRefs`、`chartStylePreset` 等；若同一章节有多个同类样本，审核后在该节绑定设置 `reviewed: true`。
3. `DocxTemplateReportApplication.generate(...)` 先核验源文件 SHA、标题与所有槽位，再逐标题调用 AI；每个槽位必须恰好返回一个同类型正文块。全部成功后才写入新 DOCX。

`reportTemplate` 的 `id` 与 `styleBindings.sections` 的 key 对应；`slotId` 与 `styleBindings.sections.<id>.slots` 的 key 对应。业务数据由 `dataRefs` 的 JSON Pointer 精确选择，模型不能自行访问其它字段。

## 在模板内标记填充位置

普通文字使用 `{{project}}`；即使 Word 把占位符拆成多个 Run 也能识别。一个段落可以有多个标记，每个标记分别成为一个槽位，未标记的前后缀保留。

动态表格：保留固定表头，在其下一行放 `{{table:metrics}}`。该行是重复行的样式原型；AI 返回一个 `table` 块，`headers` 必须与紧邻的固定表头逐列一致，`rows` 有几项就克隆几行。复杂合并、嵌套表或不同列宽的重复行不做自动推断，应先人工指定专用规则或使用代码模板。

图表：在独立段落放 `{{chart:trend}}`。AI 返回 `chart` 块，包含 `chartType`（`COLUMN`、`BAR`、`PIE`、`LINE`、`RADAR`）、`categories` 和 `series`。默认 `NATIVE_WORD`，生成后仍是可编辑的 Word 图表；`ECHARTS_IMAGE` 生成 SVG 图片，视觉表现更丰富，但不能在 Word 内编辑数据。若要求可编辑，设置 `editableRequired: true`，系统会拒绝图片模式。

也识别内容控件的 Tag/Title 和 `fill_` 前缀的书签。它们对应普通文字填充；图表和动态表格建议使用上述显式文本标记。

## 图表风格分工

| 选择 | Word 内编辑 | 风格能力 | 额外运行环境 |
| --- | --- | --- | --- |
| `NATIVE_WORD` | 可以 | Word 原生系列配色，`DEFAULT`、`BUSINESS_BLUE`、`MONOCHROME` | 无 |
| `ECHARTS_IMAGE` | 不可以 | ECharts SVG 主题、标签和布局 | Node.js；执行 `npm ci --prefix tools/echarts` |

两者共用受控的图表数据结构。AI 只提供数据和图表类型，不能提交任意 JavaScript 或 ECharts `option`。后端的 `tools/echarts/render.cjs` 管理展示细节；不要将该脚本开放为模型可写代码。

## Java 接入

```java
Path source = Path.of("/data/templates/report.docx");
DocxTemplateAnalysis analysis = new DocxTemplateAnalyzer().analyze(source, "assessment-v1");
ObjectNode reportTemplate = analysis.getReportTemplate();
ObjectNode bindings = analysis.getStyleBindings();

// 审核 structure.reviewIssues，并按业务需要修改 reportTemplate 的 prompt/dataRefs。
JsonNode facts = new ObjectMapper().readTree("{\"content\":\"项目事实...\"}");
StructuredAiClient client = /* OpenAI 协议客户端，可连接 Ollama */;
new DocxTemplateReportApplication(new StructuredReportAiApplication(client))
        .generate(source, reportTemplate, bindings, facts, Path.of("/data/output/report.docx"));
```

上线时应把三份 JSON 与源 DOCX 作为同一审核版本保存。源文件改动后必须重新分析和审核；绑定中的 SHA 不匹配会拒绝生成。无标记章节保持原文，系统不会猜测它应被覆盖。

## 当前边界

- 不根据视觉相似性猜测任意表格的可变区域；仅显式槽位可写入。复杂表格需业务规则或代码模板。
- 原生图表插入新对象，不复制旧图表的每一项自定义格式；可通过命名预设控制系列颜色。
- 自动识别的是 Word 语义标题/大纲级别；没有标题样式的纯视觉标题会列入审核问题，不自动归类。
- 输出尚未自动比较每页像素差异；正式模板建议用代表性业务数据做 Word/LibreOffice 人工审阅。

可运行测试见 `DocxTemplateAnalyzerTest`、`DocxTemplateReportApplicationTest`、`StructuredReportAiApplicationTest` 和可选的 `EChartsChartIntegrationTest`。测试结果见 [测试报告](docx-template-ai-charts-test-report.md)。
