# Aspose Docx 二次封装设计

## 1. 背景与目标

当前项目已经引入 `aspose-words`，但直接使用 Aspose 创建 Word 文档时，业务代码需要理解大量底层概念，例如 `Document`、`DocumentBuilder`、`ParagraphFormat`、`CellFormat`、`InlineShape`、`Style` 等。二次封装的目标是提供一套更接近 Word 结构、更适合业务代码表达的 Java API。

本设计从 0 到 1 建立一个基于 Aspose Words 的 docx 生成封装层，重点区分以下能力：

- Document：文档入口、全局配置、保存与导出。
- Section：章节、页面设置、块级内容容器。
- Paragraph：段落、文本与行内元素容器。
- Table：表格、行、单元格、单元格内块级内容。
- Image：图片行内元素。
- Visio：现阶段按图片行内元素插入，保留后续 Visio 专属扩展点。
- Style：默认标准样式、样式继承、局部覆盖、与 docx 样式体系保持一致。

核心目标：

- 隔离 Aspose 底层 API，业务代码不直接依赖复杂的 Aspose 操作。
- 用代码方式创建丰富、多样、结构正确的 docx 文档。
- 保持与 Word/docx 文档模型一致，避免后续扩展时推翻已有 API。
- 提供默认标准样式，同时支持文档级、章节级、节点级样式覆盖。

## 2. 总体方案

采用“对外 Builder 风格 + 对内组件树模型 + 渲染器输出 Aspose 文档”的方案。

```text
业务代码
  -> Fluent Builder API
      -> 内部 DocxNode 组件树
          -> Aspose Renderer
              -> com.aspose.words.Document
```

对外 API 使用 Builder 风格，让创建文档的代码接近文档阅读顺序。内部不直接边调用边写 Aspose，而是先构建一个组件树，再由渲染器统一渲染成 Aspose 文档。

这样做的原因：

- Builder 让业务代码更直观，适合按顺序描述文档内容。
- 组件树可以准确表达 `document -> section -> block -> inline` 的层级。
- 渲染器把 Aspose 依赖隔离在内部，后续可替换、测试或增加导出策略。
- 样式可以在渲染前完成解析、继承、合并，避免散落在业务代码中。

## 3. 为什么使用 Builder 风格

创建 Word 文档时，业务代码本质上是在描述“先写标题，再写段落，再插入表格，再补充图片”。Builder 风格天然适合这种顺序式、层级式构建过程。

示例目标 API：

```java
DocxDocument.create()
    .useDefaultStyles()
    .section()
        .heading1("系统架构设计")
        .paragraph()
            .text("整体流程如下：")
            .image("architecture.png")
            .text("请参考上图。")
        .end()
        .table()
            .headers("模块", "职责", "说明")
            .row("Document", "文档入口", "负责保存、导出、全局配置")
            .row("Paragraph", "行内容器", "承载文本、图片、Visio 等行内元素")
        .end()
    .end()
    .save("output.docx");
```

选择 Builder 的原因：

- 易用：用户不必直接理解 Aspose 的 `DocumentBuilder` 游标移动、节点插入、格式对象设置。
- 可读：生成文档的代码顺序接近最终文档顺序。
- 约束正确：Section 只暴露 `paragraph()`、`table()` 等块级 API；图片和 Visio 只暴露在 Paragraph 中。
- 便于默认值：创建节点时可自动注入默认样式、默认间距、默认表格边框等。
- 便于扩展：后续可加入 `chart()`、`toc()`、`bookmark()`、`hyperlink()` 等能力，不破坏已有 API。

Builder 只负责构建内部模型，不直接操作 Aspose。这样避免链式 API 与 Aspose 渲染细节强耦合。

## 4. 核心设计模式

### 4.1 建造者模式 Builder

使用位置：

- `DocxDocumentBuilder`
- `SectionBuilder`
- `ParagraphBuilder`
- `TableBuilder`
- `RowBuilder`
- `CellBuilder`

作用：

- 提供链式 API。
- 限制错误调用范围。
- 简化复杂对象创建。
- 对用户隐藏节点创建、父子关系维护、默认样式注入。

例如，`ParagraphBuilder` 可以提供：

```java
paragraph.text("说明")
         .image("flow.png")
         .visio("flow-preview.png")
         .lineBreak();
```

而 `SectionBuilder` 不提供 `image()`，从 API 层面表达“图片是 paragraph 的 child”。

### 4.2 组合模式 Composite

使用位置：

- `DocxDocument` 组合 `SectionNode`
- `SectionNode` 组合 `DocxBlock`
- `ParagraphNode` 组合 `DocxInline`
- `TableNode` 组合 `TableRowNode`
- `TableCellNode` 组合 `DocxBlock`

内部结构：

```text
DocxDocument
  -> SectionNode
      -> ParagraphNode
          -> TextRunInline
          -> ImageInline
          -> VisioInline
      -> TableNode
          -> TableRowNode
              -> TableCellNode
                  -> ParagraphNode
```

使用原因：

- docx 文档天然是树形结构。
- 节点职责清晰，便于递归渲染。
- 可以统一处理新增节点，例如目录、分页符、超链接、书签、公式。
- TableCell 内部继续使用 Block 组合，保证单元格内也能放段落、图片、嵌套表格。

核心接口建议：

```java
public interface DocxNode {
}

public interface DocxBlock extends DocxNode {
}

public interface DocxInline extends DocxNode {
}
```

块级节点只能进入 Section 或 TableCell，行内节点只能进入 Paragraph。

### 4.3 适配器模式 Adapter

使用位置：

- `AsposeDocumentAdapter`
- `AsposeParagraphAdapter`
- `AsposeTableAdapter`
- `AsposeStyleAdapter`
- `AsposeImageAdapter`

作用：

- 把 Aspose Words 的复杂 API 包装在内部。
- 避免业务层和核心模型直接依赖 Aspose 细节。
- 统一处理 Aspose 异常、单位转换、样式名称映射、图片插入等问题。

例如，业务层只关心：

```java
paragraph.image("flow.png", ImageOptions.widthCm(10));
```

内部适配器负责转换为 Aspose 的：

- `DocumentBuilder.insertImage(...)`
- `Shape`
- 宽高单位转换
- 图片环绕方式
- 对齐方式

### 4.4 访问者模式 Visitor / Renderer

使用位置：

- `DocxRenderer`
- `AsposeDocxRenderer`
- `NodeRenderer<T extends DocxNode>`

作用：

- 统一遍历内部组件树。
- 将不同节点渲染成 Aspose 对象。
- 把“结构定义”和“输出实现”分开。

渲染流程：

```text
AsposeDocxRenderer.render(documentNode)
  -> renderSection(sectionNode)
      -> renderParagraph(paragraphNode)
          -> renderText(textRunInline)
          -> renderImage(imageInline)
          -> renderVisio(visioInline)
      -> renderTable(tableNode)
```

使用原因：

- 新增节点类型时，只需要新增对应 Renderer。
- 后续若支持 PDF、HTML 或 Markdown 导出，可以复用组件树，替换渲染器。
- 渲染前可以统一解析样式和校验结构。

### 4.5 策略模式 Strategy

使用位置一：图片来源。

```java
public interface ImageSource {
    InputStream openStream();
    String name();
}
```

实现：

- `PathImageSource`
- `BytesImageSource`
- `StreamImageSource`
- `UrlImageSource`

使用位置二：Visio 插入。

现阶段 Visio 按图片插入：

```java
paragraph.visio("flow-preview.png");
```

内部保留：

```java
public interface VisioInsertStrategy {
    void insert(VisioInline visio, RenderContext context);
}
```

默认实现：

- `PreviewImageVisioInsertStrategy`

未来可扩展：

- `VsdxToImageVisioInsertStrategy`
- `OleObjectVisioInsertStrategy`

使用位置三：样式合并。

不同节点可以采用不同样式合并策略，例如段落样式、文本样式、表格样式、图片样式。

### 4.6 工厂模式 Factory

使用位置：

- `DocxNodeFactory`
- `StyleFactory`
- `DefaultStyleFactory`

作用：

- 统一创建节点。
- 注入默认样式。
- 避免 Builder 直接 new 复杂节点。

例如：

```java
ParagraphNode paragraph = nodeFactory.createParagraph(parentStyleContext);
ImageInline image = nodeFactory.createImage(source, options);
```

使用原因：

- 节点创建规则集中管理。
- 后续新增审计、默认值、ID、元数据更方便。
- Builder 保持轻量，只表达用户意图。

### 4.7 原型模式 Prototype

使用位置：

- 默认样式复制。
- 样式局部覆盖。

默认样式不应直接被节点修改，而应复制后再覆盖：

```java
ParagraphStyle body = styleRegistry.getParagraphStyle("BodyText").copy();
body.setFirstLineIndent(2);
```

使用原因：

- 防止修改某个段落样式时污染全局默认样式。
- 保持样式对象可复用。
- 适合“基于标准样式做局部调整”的场景。

### 4.8 责任链模式 Chain of Responsibility

使用位置：

- 样式查找与继承。

样式解析顺序：

```text
节点自身样式
  -> 父级上下文样式
      -> 文档主题样式
          -> 默认标准样式
```

使用原因：

- 符合 Word/docx 的样式继承思想。
- 节点可以只声明差异，不必重复完整样式。
- 样式解析逻辑集中，避免散落在渲染代码中。

## 5. 对象模型设计

### 5.1 Document

职责：

- 文档入口。
- 管理 Section。
- 管理全局样式、主题、元数据。
- 提供保存 docx、导出 PDF 等能力。

建议类：

```text
DocxDocument
DocxDocumentBuilder
DocumentOptions
DocumentMetadata
```

核心 API：

```java
DocxDocument.create()
DocxDocument.load(templatePath)
document.section()
document.save(path)
document.exportPdf(path)
```

### 5.2 Section

职责：

- 表达 Word 章节。
- 管理页面大小、页边距、页眉页脚、分栏等章节级配置。
- 承载块级节点。

允许的 child：

- `ParagraphNode`
- `TableNode`
- `PageBreakNode`

不允许直接 child：

- `ImageInline`
- `VisioInline`
- `TextRunInline`

### 5.3 Paragraph

职责：

- 表达 Word 段落。
- 承载行内节点。
- 管理段落对齐、缩进、行距、段前段后间距。

允许的 child：

- `TextRunInline`
- `ImageInline`
- `VisioInline`
- `LineBreakInline`
- `HyperlinkInline`
- `BookmarkInline`

图片和 Visio 都是 Paragraph 的 child。这样与 Word 的真实模型一致，也支持图文混排。

### 5.4 Table

职责：

- 表达表格结构。
- 管理表格宽度、边框、表头、单元格样式。

结构：

```text
TableNode
  -> TableRowNode
      -> TableCellNode
          -> DocxBlock
```

TableCell 内部使用 Block 列表，而不是直接文本。这样单元格内可以继续放段落、图片、Visio、嵌套表格。

### 5.5 Image

职责：

- 表达行内图片。
- 支持图片来源、宽高、对齐、标题、替代文本。

建议模型：

```text
ImageInline
ImageSource
ImageOptions
ImageStyle
```

图片不作为 Section child，而是 Paragraph child。

### 5.6 Visio

职责：

- 表达带有 Visio 语义的行内元素。
- 现阶段按图片插入。
- 保留后续 `.vsdx` 转图片、OLE 嵌入能力。

建议模型：

```text
VisioInline extends DocxInline
VisioOptions
VisioInsertStrategy
```

现阶段：

```java
paragraph.visio("process-preview.png");
```

未来：

```java
paragraph.visio(VisioSource.fromVsdx("process.vsdx"));
paragraph.visio(VisioSource.fromOle("process.vsdx"));
```

## 6. 样式体系设计

样式是这层封装的核心能力之一。设计目标是既保留 Word/docx 标准样式思想，又提供好用的 Java API。

### 6.1 默认标准样式

默认提供：

- `Normal`：默认正文。
- `Title`：文档标题。
- `Subtitle`：副标题。
- `Heading1`：一级标题。
- `Heading2`：二级标题。
- `Heading3`：三级标题。
- `BodyText`：正文段落。
- `Caption`：图表标题。
- `TableNormal`：普通表格。
- `TableHeader`：表头。
- `TableCell`：普通单元格。
- `ImageCaption`：图片标题。
- `CodeBlock`：代码块。

默认样式需要映射到 Aspose/Word 样式，并在文档创建时注册到 Aspose `Document.getStyles()`。

### 6.2 样式分类

建议拆分为：

```text
DocumentStyle
SectionStyle
ParagraphStyle
RunStyle
TableStyle
CellStyle
ImageStyle
```

拆分原因：

- 不同节点支持的样式属性不同。
- 避免一个巨大的 Style 类承载所有属性。
- 渲染到 Aspose 时更容易映射。

### 6.3 样式解析

样式解析由 `StyleResolver` 完成：

```text
StyleResolver
  -> resolveParagraphStyle(paragraphNode, context)
  -> resolveRunStyle(textRunInline, context)
  -> resolveTableStyle(tableNode, context)
  -> resolveImageStyle(imageInline, context)
```

查找顺序：

```text
节点显式样式
  -> 父节点样式上下文
      -> 文档主题样式
          -> 默认标准样式
```

这样可以支持：

```java
doc.withTheme(DefaultTheme.STANDARD);

section.withStyle(sectionStyle);

paragraph.withStyle("BodyText")
         .text("正文");

paragraph.text("强调内容", RunStyle.bold());
```

## 7. 渲染流程设计

渲染分为四步：

```text
1. 构建内部组件树
2. 校验组件树结构
3. 解析样式与默认值
4. 使用 AsposeRenderer 输出文档
```

### 7.1 构建

Builder 负责创建内部节点并维护父子关系。此阶段不调用 Aspose。

### 7.2 校验

校验规则：

- Document 至少包含一个 Section。
- Section child 只能是 Block。
- Paragraph child 只能是 Inline。
- Image/Visio 必须在 Paragraph 内。
- TableRow 的列数应符合 Table 定义。
- 必填图片资源必须存在或可读取。

### 7.3 样式解析

渲染前统一解析样式，得到最终样式对象，避免渲染器内部到处判断默认值。

### 7.4 Aspose 渲染

Renderer 使用 Aspose `Document` 和 `DocumentBuilder` 写入内容：

- Section 渲染为 Word section/page setup。
- Paragraph 渲染为 Aspose paragraph。
- TextRun 渲染为 run。
- Image/Visio 渲染为 inline shape。
- Table 渲染为 Aspose table/row/cell。
- Style 渲染为 Aspose style、paragraph format、font、cell format。

## 8. API 设计示例

### 8.1 基础文档

```java
DocxDocument.create()
    .useDefaultStyles()
    .section()
        .title("研发效能报告")
        .heading1("一、整体情况")
        .paragraph()
            .text("本报告展示项目研发效能指标。")
        .end()
    .end()
    .save("report.docx");
```

### 8.2 图文混排

```java
doc.section()
    .paragraph()
        .text("架构如下：")
        .image("architecture.png", ImageOptions.widthCm(12))
        .text("该架构分为接入层、业务层和数据层。")
    .end();
```

### 8.3 Visio 插入

```java
doc.section()
    .paragraph()
        .text("流程图：")
        .visio("process-preview.png", VisioOptions.preview())
    .end();
```

### 8.4 表格单元格内插入图片

```java
doc.section()
    .table()
        .headers("模块", "说明")
        .row(row -> row
            .cell(cell -> cell.paragraph().text("架构图").end())
            .cell(cell -> cell.paragraph().image("module.png").end())
        )
    .end();
```

## 9. 包结构建议

```text
cn.bugstack.office.docx
  -> api
      DocxDocument
      DocxDocumentBuilder
  -> model
      DocxNode
      DocxBlock
      DocxInline
      SectionNode
      ParagraphNode
      TableNode
      ImageInline
      VisioInline
  -> builder
      SectionBuilder
      ParagraphBuilder
      TableBuilder
      RowBuilder
      CellBuilder
  -> style
      StyleRegistry
      StyleResolver
      ParagraphStyle
      RunStyle
      TableStyle
      ImageStyle
      DefaultStyles
  -> render
      DocxRenderer
      AsposeDocxRenderer
      RenderContext
  -> adapter
      AsposeDocumentAdapter
      AsposeStyleAdapter
      AsposeImageAdapter
  -> source
      ImageSource
      PathImageSource
      BytesImageSource
      StreamImageSource
      UrlImageSource
  -> validate
      DocxValidator
      ValidationResult
  -> exception
      DocxBuildException
      DocxRenderException
      DocxValidationException
```

## 10. 异常与校验设计

建议提供三类异常：

- `DocxBuildException`：构建阶段错误，例如非法父子关系。
- `DocxValidationException`：保存前结构校验失败。
- `DocxRenderException`：Aspose 渲染阶段失败。

校验不应只依赖异常，也可以提供：

```java
ValidationResult result = doc.validate();
```

用于在保存前主动检查问题。

## 11. 未来扩展点

可扩展能力：

- 模板加载与占位符替换。
- 目录 TOC。
- 页眉页脚。
- 水印。
- 超链接与书签。
- 图表。
- 代码块高亮。
- `.vsdx` 转图片。
- Visio OLE 嵌入。
- 多渲染目标，例如 HTML、PDF。

现阶段重点是先保证结构模型、Builder API、样式系统和 Aspose 渲染边界正确。

## 12. 设计结论

最终设计口径：

```text
对外使用 Builder 提供便捷 API；
对内使用 Composite 表达 docx 树形结构；
使用 Renderer/Visitor 将组件树渲染到 Aspose；
使用 Adapter 隔离 Aspose 底层 API；
使用 Strategy 处理图片来源、Visio 插入和样式合并；
使用 Factory 统一创建节点与注入默认值；
使用 Prototype 保护默认样式不被污染；
使用 Chain of Responsibility 实现样式继承与覆盖。
```

图片和 Visio 的最终结构定位：

```text
ImageInline 和 VisioInline 都是 Paragraph 的 child，
而不是 Section 的 child。
```

这能保证封装层与 Word/docx 的真实结构保持一致，同时支持图文混排、表格单元格内插图以及未来 Visio 能力扩展。
