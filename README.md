# Omni Office

Omni Office 是一个面向 Java 应用的 Office 文档生成与报告编排项目。项目在 Aspose Words、Aspose Diagram 之上提供了更稳定的领域模型、Builder API 和报告模块机制，使业务代码不需要直接操作 Word 游标、段落节点或底层 OOXML。

当前重点能力是生成结构化 Word/PDF 报告：业务可以按入参自由组合报告模块，替换封面模板，配置目录、样式、页眉、页脚和分节页码，同时保持每个模块的数据对象和实现类相互独立。

## 核心特性

- 使用强类型业务对象实现报告模块，不需要把所有模块数据硬编码为 `String`。
- 八个评估分析模块可任意选择、排序和组合。
- 支持默认封面和实现 `ReportCoverTemplate` 的动态封面。
- 内置“文档修改记录”表格封面模板，支持动态记录和空白填写行。
- 封面、目录、业务正文使用三个独立 Word Section。
- 目录页使用大写罗马数字 `I、II、III...`，业务正文重新从阿拉伯数字 `1` 开始。
- 页脚默认仅显示页码，也可选择“第 N 页”格式。
- 业务正文页眉可选，不设置时不会创建页眉。
- 支持 Word 原生多级标题编号、目录域、页码域、题注和交叉引用。
- 支持段落、列表、表格、图片、Visio 预览、分页和类设计表格。
- 支持 DOCX 和 PDF 输出，并使用临时文件保证文件导出的原子性。
- 提供 SVG 用例图、流程图、ER 图以及 VSDX 图形输出能力。
- 提供模块计划、条件判断、依赖校验、语义文档校验和阶段化异常信息。

## 技术栈

| 技术 | 版本/用途 |
| --- | --- |
| Java | 源码兼容级别为 Java 11；由于当前 Aspose Words 使用 `jdk17` classifier，运行时推荐 JDK 17 或更高版本 |
| Maven | 项目构建、依赖描述和测试执行 |
| Aspose Words for Java | DOCX 创建、Word 域、目录、页眉页脚、分节和 PDF 转换 |
| Aspose Diagram for Java | VSDX 图形生成与编辑 |
| JUnit Jupiter | 单元测试和文档结构回归测试，版本 5.10.2 |
| SVG/XML | 不依赖 Word 的用例图、流程图和 ER 图输出 |

## 项目结构

```text
src/main/java/cn/bugstack
├── export
│   ├── api                 # 导出请求、结果、格式和异常
│   ├── core                # 导出生命周期、计划、校验和编排
│   ├── definition          # 报告蓝图、布局、模块槽位和封面模板协议
│   ├── document            # 与 Word 实现无关的报告语义模型
│   ├── docx                # 语义文档到 DOCX/PDF 的编译适配器
│   ├── module              # 报告模块、注册表、条件和强类型数据上下文
│   ├── template/cover      # 可直接用于业务的正式封面模板
│   └── example             # 完整报告和可组合模块示例
└── office
    ├── docx                # DOCX 组件树、Builder、样式、校验和 Aspose 渲染器
    └── diagram             # SVG/VSDX 图形定义与渲染器
```

## 设计思路

### 1. 报告语义与 Word 实现分离

业务模块只创建 `ReportSection`、`ReportParagraph`、`ReportTable` 等语义元素，不直接依赖 Aspose。`DocxReportCompiler` 负责把语义树编译为内部 DOCX 组件树，最后由 `AsposeDocxRenderer` 写入真实文件。

```mermaid
flowchart LR
    A[业务入参] --> B[ReportDefinition]
    B --> C[ReportBlueprint]
    C --> D[ReportPlanner]
    D --> E[ReportModule]
    E --> F[ReportDocument 语义树]
    F --> G[ReportDocumentValidator]
    G --> H[DocxReportCompiler]
    H --> I[DocxDocument 组件树]
    I --> J[AsposeDocxRenderer]
    J --> K[DOCX / PDF]
```

这种边界让业务模块可以被独立测试，也让后续增加 HTML 或其他输出格式时不必重写业务组装逻辑。

### 2. 模块采用注册表和策略模式

每个报告模块都是独立的 `AbstractReportModule<T>` 实现，并拥有自己的数据类型、数据键、模块编码和章节标题。`ReportModuleRegistry` 负责注册与查找模块，`ReportPlanner` 按蓝图顺序解析依赖、条件和最终执行计划。

模块是否导出由入参决定，调用方添加模块的顺序就是最终 Word 章节顺序。重复模块和空模块组合会在构建阶段被拒绝。

### 3. 文档内部使用 Composite + Builder

`DocumentNode`、`SectionNode`、`ParagraphNode`、`TableNode` 和各种 Inline 节点构成内部组件树。调用方使用 `DocxDocument`、`SectionBuilder`、`TableBuilder` 等 Builder 创建内容，渲染器统一处理节点遍历、游标位置和样式恢复。

### 4. 封面使用可插拔模板

`ReportCoverTemplate` 返回与目标格式无关的有序语义元素。默认封面继续使用标准文档名称、项目名称和版本布局；调用方也可以替换为表格、段落或自定义组合。

动态封面位于目录之前，并拥有独立 Section。模板内容不会自动加入目录，也不会被正文标题编号影响。

### 5. 分节和页码独立控制

可组合报告的默认结构为：

```text
Section 1：封面，无页眉页脚
Section 2：目录，独立页脚，页码从 I 开始
Section 3：业务模块，可选页眉，页码重新从 1 开始
```

目录和正文页脚会断开“链接到前一节”。正文内部后续增加 Section 时只在第一个正文 Section 重启页码，避免每个章节都重新回到 1。

### 6. 可扩展编译边界

内置编译器支持常用语义元素。业务如果新增自定义 `ReportElement`，可以注册 `ReportElementCompiler<E>`，而不需要修改所有报告模块或直接侵入 Aspose 渲染器。

## 环境准备

### 前置要求

- JDK 17 或更高版本。
- Maven 3.8 或更高版本。
- Aspose Words 26.6 和 Aspose Diagram 26.6 对应 JAR。

### 本地依赖

`lib/` 已被 Git 忽略，不会提交 Aspose 二进制文件。克隆项目后需要准备以下文件：

```text
lib/
├── aspose-words-26.6-jdk17.jar
└── aspose-diagram-26.6.jar
```

当前 `pom.xml` 使用 `systemPath` 引用这两个文件，因此缺少 JAR 时 Maven 无法编译。生产项目可以根据自己的制品仓库规范，将依赖安装到私服并把 `system` 依赖改为普通 Maven 依赖。

Aspose License 不应提交到代码仓库。可以通过环境变量或 JVM 系统属性指定：

```bash
export ASPOSE_WORDS_LICENSE_PATH=/absolute/path/Aspose.Words.Java.lic
```

或：

```bash
java -Daspose.words.license.path=/absolute/path/Aspose.Words.Java.lic ...
```

未配置 License 时会按照 Aspose 的评估模式运行。

## 快速开始

### 编译与测试

```bash
mvn clean test
```

生成的 DOCX、PDF、SVG、VSDX 和测试产物位于 `target/`，该目录不会提交到 Git。

### 使用默认封面组合报告

下面的代码选择四个模块。模块的添加顺序就是报告章节顺序；未调用 `header(...)` 时正文没有页眉。

```java
ComposableReportModuleModel modules = ComposableReportModuleModel.builder()
        .module(new AssessmentScenarioConstructionModuleData(
                "根据任务目标、参评对象和环境约束构设评估场景。"))
        .module(new ImpactAnalysisModuleData(
                "分析关键要素变化对任务结果产生的影响。"))
        .module(new CombatProcessAnalysisModuleData(
                "围绕任务阶段梳理作战流程及关键活动。"))
        .module(new FunctionalOptimizationAnalysisModuleData(
                "结合分析结论提出功能优化方向和改进建议。"))
        .build();

ComposableReportInput input = ComposableReportInput.builder(modules)
        .preparedBy("评估分析组")
        .build();

new ComposableTextReportExporter().export(
        input,
        Path.of("target", "assessment-report.docx"));
```

### 设置标准封面和可选页眉

```java
ComposableReportCoverModel cover = new ComposableReportCoverModel(
        "评估分析报告",
        "联合任务方案",
        "V1.0");

ComposableReportModuleModel modules = ComposableReportModuleModel.builder()
        .header("评估分析报告")
        .module(new ImpactAnalysisModuleData("影响分析正文。"))
        .build();

ComposableReportInput input = ComposableReportInput.builder(cover, modules)
        .preparedBy("评估分析组")
        .build();
```

### 使用文档修改记录表格封面

该模板位于正式的 `cn.bugstack.export.template.cover` 包中，不依赖示例类。序号按照记录顺序自动生成。

```java
DocumentModificationRecordCoverTemplate cover =
        DocumentModificationRecordCoverTemplate.builder()
                .documentName("评估分析报告")
                .record("张三", "2026-08-13 10:30")
                .record("李四", "2026-08-14 09:00")
                .build();

ComposableReportInput input = ComposableReportInput.builder(cover, modules)
        .preparedBy("评估分析组")
        .build();

new ComposableTextReportExporter().export(
        input,
        Path.of("target", "report-with-modification-cover.docx"));
```

如果没有修改记录，模板默认保留一行空白填写行；也可以指定行数：

```java
DocumentModificationRecordCoverTemplate cover =
        DocumentModificationRecordCoverTemplate.builder()
                .documentName("评估分析报告")
                .blankRows(5)
                .build();
```

`documentName` 用于报告蓝图和文档元数据，可组合报告默认不会把它额外写入封面表格或正文。

### 页眉和页脚格式

```java
ComposableReportModuleModel modules = ComposableReportModuleModel.builder()
        .header("评估分析报告")
        .pageNumberFooterFormat(
                ComposablePageNumberFooterFormat.CHINESE_DECORATED)
        .module(new ImpactAnalysisModuleData("影响分析正文。"))
        .build();
```

可选值：

- `PAGE_ONLY`：仅显示 `I` 或 `1`，这是默认值。
- `CHINESE_DECORATED`：显示“第 I 页”或“第 1 页”。

### 八个可组合评估模块

| 模块 | 编码 | 数据对象 |
| --- | --- | --- |
| 评估场景构设 | `assessment-scenario-construction` | `AssessmentScenarioConstructionModuleData` |
| 评估计算分析 | `assessment-calculation-analysis` | `AssessmentCalculationAnalysisModuleData` |
| 贡献率分析 | `contribution-rate-analysis` | `ContributionRateAnalysisModuleData` |
| 影响分析 | `impact-analysis` | `ImpactAnalysisModuleData` |
| 对比分析 | `comparison-analysis` | `ComparisonAnalysisModuleData` |
| 作战流程分析 | `combat-process-analysis` | `CombatProcessAnalysisModuleData` |
| 脆弱性分析 | `vulnerability-analysis` | `VulnerabilityAnalysisModuleData` |
| 功能优化分析 | `functional-optimization-analysis` | `FunctionalOptimizationAnalysisModuleData` |

八个模块都拥有独立的 `AbstractReportModule<具体数据类型>` 实现。当前模板为每个模块输出一个标题和一段纯文本，后续可以独立扩展为表格、图片、子章节或其他业务对象，不会影响其他模块。

## 运行内置示例

先编译项目：

```bash
mvn -DskipTests package
```

然后运行对应入口。macOS/Linux 使用冒号分隔 classpath：

```bash
java -cp "target/classes:lib/*" \
  cn.bugstack.export.example.composable.ComposableTextReportExportExample

java -cp "target/classes:lib/*" \
  cn.bugstack.office.docx.example.DocxWrapperExample

java -cp "target/classes:lib/*" \
  cn.bugstack.office.diagram.example.SvgDiagramExample
```

Windows 请把 classpath 中的 `:` 替换为 `;`。

主要示例输出：

| 示例 | 输出内容 |
| --- | --- |
| `ComposableTextReportExportExample` | 任意组合评估模块的 DOCX 报告 |
| `AssessmentReportExportExample` | 使用通用定义、模块计划和语义文档生成评估报告 |
| `DocxWrapperExample` | 封面、修订记录、审批页、目录、列表、图片、题注、表格和类设计表格 |
| `EditableVisioWordExample` | Word 中的 Visio 预览和可编辑 Visio 文件 |
| `MultiLevelHeadingExample` | Word 原生一至九级标题编号 |
| `SvgDiagramExample` | 用例图、流程图和 ER 图 SVG 文件 |

## 自定义报告模块

新增业务模块通常需要四步：

1. 定义模块自己的业务数据对象。
2. 创建类型化 `ReportDataKey<T>`。
3. 实现 `AbstractReportModule<T>`，在 `composeContent(...)` 中写入语义内容。
4. 把模块注册到 `ReportModuleRegistry`，并在报告蓝图中声明 `ModuleSlot`。

简化结构如下：

```java
public final class CustomAnalysisModule
        extends AbstractReportModule<CustomAnalysisData> {

    public static final ReportDataKey<CustomAnalysisData> DATA_KEY =
            ReportDataKey.of("custom-analysis-data", CustomAnalysisData.class);
    public static final ModuleDescriptor<CustomAnalysisData> DESCRIPTOR =
            ModuleDescriptor.of("custom-analysis", "自定义分析", DATA_KEY);

    @Override
    public ModuleDescriptor<CustomAnalysisData> descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected void composeContent(ReportSectionBuilder section,
                                  CustomAnalysisData data,
                                  ReportModuleContext context) {
        section.paragraph(data.getContent());
    }
}
```

## 自定义封面模板

实现 `ReportCoverTemplate` 即可完全替换封面内容：

```java
public final class CustomCoverTemplate implements ReportCoverTemplate {

    @Override
    public String getDocumentName() {
        return "评估分析报告";
    }

    @Override
    public List<ReportElement> createElements() {
        ReportParagraph paragraph = new ReportParagraph("自定义封面内容");
        paragraph.setStyleName("Title");
        return Collections.singletonList(paragraph);
    }
}
```

模板返回的元素会在目录前写入独立的动态封面 Section。除段落外，还可以返回 `ReportTable`、`ReportImage`，或者已注册编译器的自定义语义元素。

## 当前已实现内容

### 报告框架

- 报告请求、输出格式、导出结果和阶段化异常。
- 报告定义模板方法和不可变报告蓝图。
- 模块槽位、模块注册表、条件注册表和计划解析。
- 强类型 `ReportDataContext` 和模块数据键。
- 报告语义树及其结构校验。
- DOCX/PDF 编译器、内存字节导出和文件导出。
- 自定义语义元素编译器扩展点。

### DOCX 封装

- 文档、Section、段落、表格、行、单元格和 Inline 组件树。
- A4/Letter/A3、横竖版、页边距和文档元数据。
- 样式注册表、默认样式和 GJB 438C 样式入口。
- Word 原生九级标题编号和自动目录域。
- 分节页眉页脚、PAGE 域和独立页码体系。
- 默认封面、动态封面、修订记录页和审批页。
- 表格列宽、表头、跨列、纵向合并和单元格对齐。
- 图片、Visio 预览、题注、题注引用、列表和分页。
- 基于源码和 Javadoc 的类设计表格生成。
- Aspose License 的安全外部加载。

### 图形输出

- 统一的 `DiagramDefinition`、节点和边模型。
- SVG 用例图、流程图、ER 图和系统 ER 图布局。
- VSDX 图形渲染与可编辑 Visio 产物。

### 可组合评估报告

- 八个独立类、独立数据对象的评估模块。
- 入参决定模块选择和最终章节顺序。
- 默认封面和用户自定义封面模板。
- 文档修改记录表格封面。
- 独立目录 Section 和大写罗马页码。
- 正文页码从 1 重新开始。
- 可选正文页眉和两种页脚外观。
- 不生成固定的报告基础信息表格或额外正文主标题。

## 测试与质量保证

测试覆盖以下边界：

- 模块组合、顺序、重复模块和空模块校验。
- 八个强类型模块的完整导出。
- 默认封面、动态表格封面和空白填写行。
- 封面、目录、正文三个 Section 的结构。
- 目录罗马页码和正文阿拉伯页码重启。
- 页眉可选、页脚独立和 PAGE 域。
- 标题编号、目录域、题注引用和表格结构。
- DOCX 编译器、输出文件保护和自定义语义元素。
- SVG/VSDX 图形渲染和源码类设计解析。

建议提交代码前运行：

```bash
mvn test
```

## 当前限制与后续方向

- Aspose JAR 使用本地 `systemPath`，开源仓库不会携带二进制文件；后续可迁移到企业 Maven 私服。
- 可组合评估模块当前只输出一段文本，业务可基于独立数据类型逐个扩展复杂内容。
- Word 目录和页码属于动态域，不同阅读器可能在首次打开时重新计算。
- 中文排版依赖运行环境字体；Linux 容器应安装宋体、黑体或配置合适的字体替代。
- 当前没有绑定 Spring、数据库或 Web 框架，应用层需要自行完成依赖注入和 HTTP 下载接口。
- 后续可以增加 HTML 渲染器、模板持久化、模块配置中心和更多报告样式画像。

## 安全与仓库约定

- 不提交 `lib/`、`target/`、Aspose License、IDE 工作区文件和本地生成文档。
- License 通过环境变量或系统属性从仓库外部加载。
- 输出文件先写入临时文件，成功后再替换目标文件，降低失败导出破坏已有报告的风险。
