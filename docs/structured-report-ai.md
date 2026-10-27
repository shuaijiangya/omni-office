# 结构化标题模板＋逐标题 AI 填充

这个入口用于“标题结构已知，正文需要结合业务数据动态撰写”的报告。它不改变已有 `export` 业务模块，也不让模型直接生成整份 `DocumentSpec`。模板控制标题、层级、可用数据和正文类型；AI 逐标题生成正文块；最后统一校验并导出 Word。

## 需要几份 JSON

调用方只需提供 **2 份 JSON**：

| 文件 | 作用 | 更新频率 |
| --- | --- | --- |
| [标题模板](examples/structured-report-template.json) | 定义报告标题、章节层级、各章节提示词、可读取的业务字段及允许生成的正文类型 | 报告结构或写作要求变化时修改；可复用 |
| [业务数据](examples/structured-report-business-data.json) | 提供本次报告的项目事实、模块数据和结论等 | 通常每次生成时提供新数据 |

模型逐标题返回的 JSON 由程序请求、校验并组装，不需要调用方再准备第三份文件。DOCX 是输出文件，也不计入输入。**标准 JSON 不支持注释**，因此上面两份可运行示例保持无注释；以下用字段表解释全部参数。

### 标题模板：顶层参数

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `schemaVersion` | 是 | 模板结构版本，目前只能为字符串 `"1.0"`。 |
| `templateId` | 是 | 非空模板标识，如 `system-assessment`；用于标识模板，不会自动查找同名业务模板。 |
| `version` | 是 | 非空模板版本，如 `"1.0"`；目前作为模板元信息，不做版本兼容性判断。 |
| `title` | 是 | 非空报告名称，写入文档元数据；不会在目录和正文之间自动增加标题。 |
| `styleProfile` | 否 | 文档样式，取 `DEFAULT`、`BUSINESS_BRIEF` 或 `GJB_438C`；省略时为 `DEFAULT`，大小写须一致。 |
| `tableOfContentsDepth` | 否 | 目录深度，整数 `1`～`9`；省略则不配置目录。 |
| `sections` | 是 | 非空章节数组，按数组顺序生成标题；展开重复项后最多 100 个标题。第一项为一级标题，后续层级不能跳级。 |

### `sections[]`：每个章节的参数

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 非空稳定标识；展开后必须唯一。重复章节的实际 ID 为 `id/业务键值`，例如 `module/task`。 |
| `level` | 是 | 标题级别，整数 `1`～`9`；首个章节须为 `1`，后续不能从一级直接跳到三级。 |
| `title` | 是 | 非空标题，展开后最多 200 字符。重复章节可写 `{{item.name}}`，其中 `name` 是当前数组元素的非空字段；不支持任意表达式。 |
| `mode` | 否 | `AI_WRITE`（默认）让模型写正文；`HEADING_ONLY` 只创建标题，不调用模型，适合父标题。 |
| `prompt` | `AI_WRITE` 必填 | 该标题的写作要求，非空；长度还受 AI 策略的指令长度上限约束。建议写明事实边界、输出形式及表格列要求。 |
| `dataRefs` | `AI_WRITE` 必填 | 非空引用数组。元素是业务 JSON 的绝对 JSON Pointer，如 `/project`、`/modules`；重复章节可用 `$item` 引用当前元素。引用必须在生成前解析成功，本标题的模型只能看到这些事实。 |
| `allowedBlocks` | `AI_WRITE` 必填 | 非空正文类型数组；可选 `paragraph`（段落）、`bulletList`（项目列表）、`numberedList`（编号列表）、`table`（表格）。 |
| `requiredBlocks` | 否 | 必须实际生成的正文类型数组，值必须包含在 `allowedBlocks` 中。例如 `['table']` 表示不能只用段落代替表格；实际 JSON 应写双引号。 |
| `maxChars` | 否 | 本章节所有生成正文的字符总数上限，整数 `1`～`20000`，默认 `2000`；包含表头和单元格文字。 |
| `repeatOn` | 否 | 指向业务数据中**非空数组**的绝对 JSON Pointer，例如 `/modules`；每个元素生成一个同级标题。 |
| `itemKey` | 使用 `repeatOn` 时必填 | 当前数组元素中作为唯一业务键的字段名，如 `id`；该字段须为非空值，避免重复章节 ID。 |
| `requiredFactValues` | 否 | 标量事实引用数组，例如 `['$item/risk']` 或 `['/project/name']`；引用须处于本章节已选 `dataRefs` 范围内且解析为非空标量。生成的正文须**原样包含**这些值，否则只重试该章节。实际 JSON 应写双引号。 |

`HEADING_ONLY` 不需要 `prompt`、`dataRefs` 和 `allowedBlocks`。模板允许不写 `mode`，此时按 `AI_WRITE` 处理。`requiredBlocks` 与 `requiredFactValues` 是两种不同约束：前者检查正文结构，后者检查关键事实是否原样出现。

例如，要求每个模块一节且保留风险原文，可以这样定义一个章节（以下是说明用的 JSONC，`//` 注释不能直接放入实际 `.json` 文件）：

```jsonc
{
  "id": "module",                        // 章节基础 ID，展开后成为 module/业务键
  "level": 2,                            // 二级标题
  "title": "{{item.name}}",               // 当前模块的名称用作标题
  "mode": "AI_WRITE",                    // 省略时也是 AI_WRITE
  "repeatOn": "/modules",                // 从业务数据的 modules 数组逐项生成
  "itemKey": "id",                       // 用每项的 id 保证标题 ID 唯一
  "prompt": "描述模块职责、证据与风险。", // 当前标题专用的写作指令
  "dataRefs": ["$item"],                 // 只把当前模块交给模型
  "allowedBlocks": ["paragraph"],       // 只允许段落
  "requiredFactValues": ["$item/risk"], // 风险值必须原样出现在正文
  "maxChars": 800                        // 本节正文最多 800 字符
}
```

### 业务数据：示例字段

业务 JSON **没有固定的通用字段名**。下表只是当前示例模板引用的数据结构；换业务时可以改字段名和层级，但要同步修改模板中的 `dataRefs`、`repeatOn`、`itemKey`、标题占位符和提示词。顶层须为 JSON 对象。

| 参数路径 | 当前示例中的作用 |
| --- | --- |
| `project` | 项目事实对象；被 `/project` 引用。 |
| `project.name` | 软件或项目名称。 |
| `project.assessmentTarget` | 本次评估对象。 |
| `project.assessmentDate` | 评估日期；示例采用 `YYYY-MM-DD` 字符串，框架不强制日期格式。 |
| `modules` | 模块对象数组；被 `/modules` 引用，也作为 `repeatOn` 的逐项来源；使用重复章节时不能为空。 |
| `modules[].id` | 模块唯一业务键，对应章节的 `itemKey: "id"`。 |
| `modules[].name` | 模块名称，对应标题中的 `{{item.name}}`。 |
| `modules[].responsibility` | 模块职责，供正文和汇总表使用。 |
| `modules[].evidence` | 已有证据，供正文和汇总表使用。 |
| `modules[].risk` | 待验证风险；可用 `$item/risk` 作为必须原样出现的事实。 |
| `conclusion` | 结论事实对象；被 `/conclusion` 引用。 |
| `conclusion.assessment` | 当前结论描述。 |
| `conclusion.actions` | 后续行动字符串数组。 |

业务数据不由模型修改。当前示例的 `模块评估汇总` 使用 `/modules` 生成四列表格，并通过 `requiredBlocks: ["table"]` 防止模型用普通段落替代表格。

### 多级标题＋按提示词选择段落或表格

另一套可运行输入是[三级标题模板](examples/structured-report-multilevel-template.json)和[配套业务数据](examples/structured-report-multilevel-business-data.json)。展开后的标题顺序如下：

```text
1 项目概述
  1.1 评估背景
2 质量评估
  2.1 功能质量
    2.1.1 功能验证结果
  2.2 风险与建议
3 评估结论
```

`项目概述`、`质量评估`、`功能质量` 用 `HEADING_ONLY` 建立父标题。三级标题 `功能验证结果` 同时设置 `"allowedBlocks": ["paragraph", "table"]`，**不设置** `requiredBlocks`，所以模型可以选择其中一种。它的 `prompt` 明确约定：`checks` 只有 1 条时写段落；有 2 条及以上时写表格，每条一行。配套业务数据有 3 条检查记录，按提示应生成表格；把 `checks` 缩减为 1 条，则应生成段落。其他标题可以保持只允许段落。

这里的条件选择是**提示词引导**：程序保证输出只使用允许的块类型、表格列数一致等结构约束，但目前不检查“3 条数据是否一定生成表格”的业务条件。需要强制该条件时，应在调用前根据数据选择不同模板，或增加专门的选择规则校验。`StructuredReportMultilevelExampleTest` 用可控模型响应分别验证了两种输出路径；真实模型仍可能不完全遵循提示词，应审核结果。

使用本页后面的 `StructuredReportOllamaExample` 命令行入口时，将两个输入路径替换为上述三级标题示例文件即可。

### 所有标题共用一段业务字符串

[共享文字模板](examples/structured-report-shared-text-template.json)展示了更简化的输入：业务代码先把项目、功能检查、风险和结论组装为一个 `String`，然后包装为 JSON 对象。模板中所有 `AI_WRITE` 章节都声明 `"dataRefs": ["/content"]`，各自的 `prompt` 指定从同一段文字中提取哪部分内容；父标题仍使用 `HEADING_ONLY`。例如“验证结果”的提示词要求模型统计文字中的功能检查记录：1 条写 `paragraph`，2 条及以上写 `table`。

```java
String assembledText = "项目：智能体训练软件\n"
        + "功能检查：\n1. 任务创建｜通过｜创建接口测试通过\n"
        + "2. 任务查询｜通过｜查询接口测试通过\n"
        + "风险：任务归档尚未覆盖并发场景\n"
        + "结论：当前范围的功能检查已完成";
ObjectNode businessData = new ObjectMapper().createObjectNode()
        .put("content", assembledText);
DocumentSpec report = app.generate(template, businessData);
```

完整测试位于 `StructuredReportSharedTextExampleTest`：默认执行的离线测试检查每个 AI 章节确实收到同一段文字、框架能接收段落或表格，并实际导出再打开 DOCX；显式启用的集成测试调用本机 Ollama，验证它能从三条检查记录中提取并生成表格。运行前确认 Ollama 已加载 `qwen3.5:2b`：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.17.jdk/Contents/Home \
  /Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -Dmaven.repo.local=/Users/luojiang/maven/repository \
  -Dtest=StructuredReportSharedTextExampleTest \
  -Domni.ai.ollama.integration=true test
```

只运行“真实模型＋导出 Word”用例时，执行下面的命令。默认生成文件为 `docs/generated/shared-text-assessment-ollama.docx`；可通过 `-Domni.ai.output=/绝对路径/报告.docx` 改写输出位置。用例会重新打开 DOCX，检查一级标题和表格（表头加三条记录共四行）：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.17.jdk/Contents/Home \
  /Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -Dmaven.repo.local=/Users/luojiang/maven/repository \
  -Dtest=StructuredReportSharedTextExampleTest#localOllamaExportsWordFromAssembledString \
  -Domni.ai.ollama.integration=true test
```

本方式只要求业务数据有一个 `content` 字段，不需要先拆分 `project`、`checks` 等 key。代价是每个标题都会重新发送并解析整段文字；事实较多时可能漏取或串用其他章节的信息。程序目前校验引用和返回结构，不负责证明模型从自由文本提取的事实完全正确。实际试验中，本地 `qwen3.5:2b` 对三条记录选了表格，但对单条记录也曾选表格：**提示词引导不等于条件必然满足**。如果“单条必须段落、多条必须表格”是强业务规则，需要另加程序校验或由调用方选择不同模板。

### 从一段文字自动生成可编辑的模板

如果连标题模板也不想手写，可用 `StructuredReportTemplateAiApplication` 先让模型根据一段业务文字或报告需求拟定模板。输出是与[共享文字模板](examples/structured-report-shared-text-template.json)同结构的普通 JSON，保存后可直接人工修改；`validate(...)` 可在导出前检查标题层级、类型和引用。所有自动生成的 `AI_WRITE` 标题都只引用 `/content`，因此同一段业务文字可以用于后续逐标题填充。

```java
String text = "项目：智能体训练软件；功能检查包含任务创建和任务查询；"
        + "需要项目背景、验证结果、风险与建议、评估结论。"
        + "验证结果有多条记录时使用表格，只有一条时使用段落。";
StructuredAiClient client = new OllamaStructuredAiClient("qwen3.5:2b");
StructuredReportTemplateAiApplication designer =
        new StructuredReportTemplateAiApplication(client);
ObjectNode template = designer.generate(text, "shared-text-assessment");
Files.writeString(Path.of("target/shared-text-template.json"),
        designer.toPrettyJson(template));

// 也可读回人工编辑的 JSON，再检查是否仍适配共享文本输入。
JsonNode edited = new ObjectMapper().readTree(
        Files.readString(Path.of("target/shared-text-template.json")));
designer.validate(edited);

// 基于修改要求重新生成完整模板；原 template 对象不会被修改。
ObjectNode revised = designer.revise(edited, "把验证结果改为因果对分析，并允许表格");

ObjectNode facts = new ObjectMapper().createObjectNode().put("content", text);
new StructuredReportAiApplication(client).generateDocx(
        revised, facts, Path.of("target/shared-text-report.docx"));
```

`generate` 的文字用于**规划模板**；真正生成报告时仍须把当次业务事实放在 `facts.content` 中。两者可以是同一段文字，也可以先用示例文字设计模板，再用每次的新业务文字填充。修改接口返回完整的新 JSON，不返回局部补丁；人工修改则直接编辑 JSON 文件。模型可能设计出语义上不合适的标题或提示词，结构校验不能代替人工审核。现有“1 条用段落、多条用表格”仍属于提示词引导，不会因为模板自动生成而变成强制规则。

命令行示例 `StructuredReportTemplateOllamaExample` 支持 `generate <text.txt> <templateId> <output.json> [model]` 和 `revise <existing.json> <changes.txt> <output.json> [model]`；生成或修改后的 JSON 可直接传给 `StructuredReportOllamaExample`。统一门面 `InternalAiDocumentApplication` 也提供 `generateStructuredReportTemplate(...)` 和 `reviseStructuredReportTemplate(...)`。

可直接查看本地 `qwen3.5:2b` 根据[输入文字](examples/structured-report-template-request.txt)生成的[可编辑模板 JSON](examples/structured-report-ai-generated-template.json)。输入如明确写出“一级标题依次为……”“某一级标题下设‘……’”以及“父标题不写正文”，生成器会核对这些标题和层级，整理父子顺序并清除父标题的正文配置；没有明确写出结构时，则由模型拟定标题，仍会执行基本结构校验。模型生成的 `prompt` 是否准确体现业务意图，仍需人工检查。

### 模型内部响应（调用方无需准备）

| 参数 | 说明 |
| --- | --- |
| `sectionId` | 必须与当前已展开的章节 ID 完全一致。 |
| `status` | `READY` 表示可用，`NEEDS_INPUT` 表示事实不足。后者会阻止导出，并返回待补充问题。 |
| `blocks` | `READY` 时为 1～50 个正文块；每个块的 `type` 必须在该章节的 `allowedBlocks` 中。 |
| `questions` | `NEEDS_INPUT` 时为非空问题数组，最多 10 项；`READY` 时的问题不会写入正文。 |
| `blocks[].type` | `paragraph`、`bulletList`、`numberedList` 或 `table`。 |
| `blocks[].factRefs` | 非空事实引用数组，最多 20 项；每项必须位于该章节 `dataRefs` 已提供的事实范围内。 |
| `blocks[].text` | `paragraph` 的非空正文。 |
| `blocks[].items` | 列表块的非空字符串数组，最多 100 项。 |
| `blocks[].headers` | 表格块的非空表头字符串数组，最多 50 列。 |
| `blocks[].rows` | 表格块的行数组，1～1000 行；每行单元格数须与表头列数一致，当前单元格内容必须为非空字符串。 |

每个 `sections` 条目包含稳定 `id`、`level`、`title`。`mode=AI_WRITE` 时必须提供 `prompt`、`dataRefs` 和 `allowedBlocks`；可用 `requiredBlocks` 要求模型必须返回特定正文块，例如表格。`mode=HEADING_ONLY` 只建立父标题。`level` 从 1 开始，不能跨级。`maxChars` 限制该标题生成的正文总字符数。
若关键事实不能遗漏，可配置 `requiredFactValues`，引用本标题已提供的标量数据，如 `$item/risk`。生成的正文必须原样包含对应值；遗漏时仅重试当前标题。`NEEDS_INPUT` 会阻止导出并返回问题；`READY` 中的可选问题不写入正文。

`dataRefs` 是业务 JSON 的绝对 JSON Pointer，例如 `/project`。重复标题用 `repeatOn` 指向非空数组、`itemKey` 指定唯一业务键；标题可使用 `{{item.name}}`，当前条数据在该标题内以 `$item` 引用。示例中 `模块评估` 是一级父标题，每个模块生成一个二级标题。模板扩展后最多 100 个标题，最多 9 级。
示例的 `模块评估汇总` 标题使用 `/modules` 生成四列表格，并通过 `requiredBlocks:["table"]` 防止模型将表格要求简化为普通段落。

## 生成过程

1. 先展开并检查完整标题大纲、数据引用和重复键。缺少数据时，在调用模型前报错。
2. 每个 `AI_WRITE` 标题单独请求模型。请求只包含该标题声明的 `facts`，不发送整个业务对象；`HEADING_ONLY` 不调用模型。
3. 模型返回 `sectionId`、`status`、`blocks`、`questions`。`READY` 的块只可为该标题允许的 `paragraph`、`bulletList`、`numberedList`、`table`，每块须给出范围内的 `factRefs`。表格行宽、内容长度也会校验。
4. 不合规响应仅重试当前标题。`NEEDS_INPUT` 会抛出 `ReportNeedsInputException`，包含标题 ID 和待补充问题；不输出半成品 Word。
5. 模板标题和验证后的正文组装为 `DocumentSpec`，交给既有 `DefaultDynamicDocumentExporter` 输出 DOCX。目录后直接进入模板的第一个一级标题，不插入隐式正文。

`factRefs` 可引用本标题提供的对象本身或其存在的子路径，例如提供 `/project` 后可以引用 `/project/name`；越界路径会被拒绝。当前 `DocumentSpec` 不持久化块与事实的映射，它也不是语义真实性证明。正式上线建议保留人工审核，尤其是结论、风险和数值判断。

## Java 调用

```java
ObjectMapper mapper = new ObjectMapper();
JsonNode template = mapper.readTree(Files.readString(Path.of("docs/examples/structured-report-template.json")));
JsonNode data = mapper.readTree(Files.readString(Path.of("docs/examples/structured-report-business-data.json")));

StructuredReportAiApplication app = new StructuredReportAiApplication(
        new OllamaStructuredAiClient("qwen3.5:2b"));
app.generateDocx(template, data, Path.of("target/system-assessment.docx"));
```

也可以执行 `StructuredReportOllamaExample` 的 `main`，依次传入模板、业务数据、输出 DOCX 和可选模型名。应用入口的 `generate(...)` 可只返回 `DocumentSpec`，便于接入审批或其他导出流程。
已有 `InternalAiDocumentApplication` 的调用方也可直接使用 `generateStructuredReport(...)` 或 `generateStructuredReportDocx(...)`，复用注入的模型、校验器和导出器。

在当前本机环境运行示例：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.17.jdk/Contents/Home \
  /Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -Dmaven.repo.local=/Users/luojiang/maven/repository -DskipTests compile
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.17.jdk/Contents/Home \
  /Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -Dmaven.repo.local=/Users/luojiang/maven/repository \
  -Dmdep.outputFile=target/structured-report-classpath.txt \
  org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath
/Library/Java/JavaVirtualMachines/jdk-11.0.17.jdk/Contents/Home/bin/java \
  -cp "target/classes:$(<target/structured-report-classpath.txt)" \
  cn.bugstack.application.ai.report.StructuredReportOllamaExample \
  docs/examples/structured-report-template.json \
  docs/examples/structured-report-business-data.json \
  docs/generated/system-assessment-ollama.docx qwen3.5:2b
```

当前项目的 Aspose JAR 为 Maven `system` 依赖。上面的显式类路径运行方式可避免 `exec:java` 默认运行时范围遗漏该 JAR。

本地 `qwen3.5:2b` 实测生成的 [Word 示例](generated/system-assessment-ollama.docx) 包含目录、模块正文和“模块评估汇总”四列表格。该业务数据为演示数据，结论不代表真实项目评估结果。

## 边界

- 本阶段支持逐标题文字、列表和纯文本表格；图表、图片、Visio 不能由模型直接指定文件路径。若有这类内容，应在后续受控工具调用和人工审核后添加到 `DocumentSpec`。
- 目前不根据前文自动推导后文。需要跨标题依据时，把已审核的摘要显式放进该标题的 `dataRefs` 对应业务数据。
- 本地模型能力会影响文案质量。结构、数据范围和输出校验由程序控制，业务事实是否被正确理解仍需审核。
