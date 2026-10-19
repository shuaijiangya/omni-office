# CSCI 源码驱动详细设计

这条链路以用户提供的 `详细设计模板表格.docx` 为版式来源。上传 ZIP 的目录名决定“软件 → 功能模块 → 功能”，Java 声明和 Javadoc 提供类、数据结构、数据元素、类属性、类操作、参数与文字说明；可选的 DDL 提供数据库表设计。生成器从模板克隆九级标题、六种表格、图位和题注样式，并重新按完整文档的一级章节顺序编号题注。

## 输入 ZIP

```text
设计源码.zip
├── CSCI详细设计/
│   └── 智能体训练软件/
│       ├── 方案管理模块/
│       │   └── 创建方案/
│       │       └── TestPlanManager.java
│       └── 任务管理模块/
│           └── 查询任务/
│               ├── Task.java
│               ├── TaskReader.java
│               └── TaskRepository.java
└── 数据库设计/
    └── 逻辑结构设计/
        └── 智能体训练软件数据库设计/
            └── schema.sql
```

每个 Java 文件必须位于“CSCI详细设计/软件/模块/功能”之下；同一 ZIP 的软件名必须一致。ZIP 当前接受 `.java`、`.sql` 和可选的界面 `.png`，最多 500 个文件；Java/SQL 单文件不超过 1 MiB，PNG 不超过 4 MiB，解压总内容不超过 20 MiB。每个功能最多 60 个类和一张 PNG；每类字段/方法各最多 200 个，数据库最多 200 张表、每表最多 200 字段。拒绝绝对路径、`..`、重复路径及过大的图片像素尺寸。Java 解析使用 JDK 语法树，不编译、加载或执行上传代码。

## Java 内容约定

```java
/**
 * 方案管理器。
 * @csci.ui 用户填写方案名称和编号后提交创建。
 */
public class TestPlanManager {
    /** 方案列表。 */
    private List<TestPlan> plans;

    /**
     * 创建方案。
     * @param testId 方案 ID
     * @return 创建是否成功
     * @implNote 校验编号、持久化方案、返回创建结果。
     * @csci.caution 编号不可为空；重复编号须拒绝。
     * @csci.flow 开始 -> 校验编号 -> 持久化方案 -> 结束
     */
    public boolean createTestPlan(String testId) { /* 业务实现 */ }
}
```

普通 Javadoc 首段填“说明”列；字段和方法的名称、类型、访问权限、参数类型从 Java AST 提取。`@csci.ui` 填界面说明，`@implNote` 填算法/逻辑，`@csci.caution` 填编程注意事项，`@param`/`@return` 分别填参数/返回值说明。标签可以独占一行，也可以与描述写在同一行。例如 `/** 查询任务。 @return 任务编号 */` 对应的返回值说明是 `long：任务编号`，而不是 `long：待补充`。

同一功能目录内的多个 Java 文件共同生成一张类图；类名、字段、方法、字段类型、方法返回类型和参数类型构成类节点与依赖线。每超过 6 个类会拆成带序号的多个图；超大类的图节点各最多展示 14 个字段和 14 个方法，其余成员在节点中标明数量，完整内容仍在 Word 表格中。生成器输出 `.vsdx`，并把它作为可编辑 OLE 对象嵌入 Word；Word 中可见的 PNG 只是该 Visio 的预览，不是唯一交付的图。`@csci.flow` 显式提供流程步骤时，另生成方法流程 VSDX。若额外上传 `ui.png`，它只用于界面设计截图，与动态类图无关。

可选的本地 Ollama 补全会读取 AST 精确截取的方法体，并参考所在类的字段、方法参数和显式说明，为缺失的“算法/逻辑”“编程注意事项”生成简短文字。它不会覆盖 `@implNote` 或 `@csci.caution`；没有方法体（如接口抽象方法）不调用 AI。对于 `return taskId;` 这样可静态确定的简单字段读取，生成器直接写出准确说明，不调用模型，以避免小模型把未执行的仓储调用误写进设计。其余方法的模型输出应只描述源码可证实的行为，无法确认时留空，由文档呈现“待补充”；仍需人工复核，特别是业务约束、异常和并发语义。

本机目前的 Aspose.Diagram 评估版会在保存多类 VSDX 时截掉部分图元，尽管预览图已包含全部类与关系。正式使用前应在具备可用许可证的部署环境中打开并验证 VSDX；本功能不尝试修改 Aspose JAR 或绕过评估限制。

## 可选 DDL

```sql
CREATE TABLE basic_equip (
    equip_id INT NOT NULL COMMENT '装备ID',
    equip_name VARCHAR(64) NOT NULL COMMENT '装备名称',
    PRIMARY KEY (equip_id)
) COMMENT='武器装备表';
```

支持常见 MySQL/PostgreSQL 风格 `CREATE TABLE`、列类型、`NOT NULL`/`PRIMARY KEY` 等约束和单引号注释。每张表的物理名取自 DDL，同一值同时写入四级标题和数据库表格“表名”单元格；表注按最终“数据库设计”的章节编号重新生成。DDL 不包含的表或字段不会凭空补充。对于当前解析器不认识的 SQL 结构，请先规范化为受支持的 `CREATE TABLE` 输入并复核结果。

## Java 调用

```java
Path template = Path.of("/absolute/path/详细设计模板表格.docx");
Path sourceZip = Path.of("/absolute/path/设计源码.zip");
Path output = Path.of("/absolute/path/CSCI详细设计.docx");

StructuredAiClient aiClient = new OllamaStructuredAiClient("qwen3.5:2b");
new CsciDetailedDesignApplication(aiClient).generate(template, sourceZip, output);
```

`CsciDetailedDesignApplication` 是正式生成入口：解析 ZIP 后自动调用 `CsciMethodDesignEnricher`，再渲染 Word，不需要业务调用方自己拼接步骤。上传适配层可调用 `generate(template, uploadStream, output)`；解析器会关闭输入流。生产环境注入自己的 `StructuredAiClient` 实现即可，不限定 Ollama。只有明确调用 `CsciDetailedDesignApplication.withoutAi()` 才跳过 AI；此时缺失说明显示“待补充”。

`CsciDetailedDesignExample.main` 也委托该入口：前三个参数依次为模板、ZIP、输出路径；第四参数可选，为 Ollama 模型名，例如 `qwen3.5:2b`。不传第四参数时选择明确的无 AI 模式。当前尚无独立 CSCI HTTP 路由；接入 REST/MCP 时，应先完成鉴权、病毒扫描和工件隔离，不应把用户上传的 Java 当作可执行插件。

另有显式的 `--draft-from-names` 模式：当方法算法或注意事项缺失时，按方法名和源码中可见的分支、遍历、异常等结构生成“推断草稿”，不覆盖已确认的说明。无论是否启用 AI，每个非 private 方法的“算法/逻辑”都会附带 Visio 流程图；显式 `@csci.flow` 优先，其余按可见源码结构补齐，未提供方法体时仅展示声明占位流程。private 方法不进入类操作表、方法设计和流程图。名称草稿不等同于 AI 对实现的语义分析，更不能替代业务负责人确认计算口径。命令示例：

```bash
java -cp "target/classes:$(< target/runtime-classpath.txt)" \
  cn.bugstack.application.csci.CsciDetailedDesignExample \
  /absolute/path/模板.docx /absolute/path/源码.zip /absolute/path/输出.docx \
  --draft-from-names
```

如果 SQL 原始字段或表缺少注释，可在生成输入 ZIP 前补全 MySQL `COMMENT`。对无法从业务资料核实、仅由名称猜测的说明，应另行保留来源记录与原始 SQL 供逐项复核；生成器不会自动改写上传的 SQL。普通索引、`KEY`、`FULLTEXT` 等 DDL 约束不会被当作数据字段。

文档收尾已内置于 Java：写入正文时将 Javadoc 行内标记 `{@code null}` 表述为“空值”；保存后仅当正文以“表格 + 真正空段落”结束时，删除 Aspose 自动补入的空段落。无需运行 Python 脚本，也不会改动模板原件。

## 验证

在模板文件存在的机器运行：

```bash
/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -Dtest=CsciDetailedDesignIntegrationTest \
  '-Dcsci.template=/absolute/path/详细设计模板表格.docx' test
```

正式入口的回归测试可运行 `CsciDetailedDesignApplicationTest`，同样传入 `-Dcsci.template`；如需保留它生成的 Word，再传 `-Dcsci.application.output=/absolute/path/输出.docx`。该测试使用可控的假 AI 客户端，验证补全结果确实写入最终 Word，不依赖本地 Ollama。

如需让集成测试调用本地 Ollama 并生成带 AI 补全文字的示例文档，加上 `-Dcsci.ollama.model=qwen3.5:2b` 和 `-Dcsci.output=/absolute/path/输出.docx`。该测试依赖本机 Ollama 可访问；默认测试使用假客户端，不依赖网络或模型。

测试检查章节层级、六类表格的列数与动态行、数据库合并行、物理表名一致性、题注重排、可编辑 Visio OLE、页面尺寸和模板原件哈希。视觉验证仍需在最终部署的 Word 环境逐页检查，特别是中文字体替换、跨页表格、图尺寸与 Aspose.Diagram 许可证状态。

如需直接保留一份可打开的最终示例文档，运行以下专用测试；其中 ZIP 应包含示例中的 `{@code null}` 返回值说明，以验证 Java 清理效果：

```bash
/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -Dtest=CsciDetailedDesignFinalExampleTest \
  '-Dcsci.template=/absolute/path/详细设计模板表格.docx' \
  '-Dcsci.source.zip=/absolute/path/设计源码.zip' \
  '-Dcsci.output=/absolute/path/CSCI详细设计-最终版.docx' test
```

需要 AI 补全时追加 `-Dcsci.ollama.model=qwen3.5:2b`。测试会检查正文占位标记、末尾 XML 结构、Visio 对象以及模板未被修改；未传模板和 ZIP 时在普通测试环境自动跳过。
