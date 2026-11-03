# DOCX 模板识别、AI 槽位填充与图表测试报告

测试日期：2026-11-02。环境：macOS，Oracle JDK 11.0.17，Maven 3.6.3；本地仓库 `/Users/luojiang/maven/repository`。项目没有仓库内 `AGENTS.md`，执行遵循用户提供的 Java 环境要求；`pom.xml` 编译目标为 Java 11。`mvn -version` 已核对 Java 11.0.17。

## 结论

自动识别 Word 语义标题、段落/Run 样式、表格布局和显式占位符；通过章节 ID + 槽位 ID 将业务事实、AI 输出与原 DOCX 位置关联。验证通过后能在原模板上保留固定内容，填充跨 Run 文本、动态表格行和 Word 原生可编辑图表。可选 ECharts 路径输出 SVG 图片，视觉更丰富但不具备 Word 图表数据编辑性。

| 验证项 | 样本与断言 | 结果 |
| --- | --- | --- |
| 模板识别 | Heading 1/2、跨 Run 占位符、表格、原生图表示例、样式绑定及源 SHA | 通过 |
| 无语义标题 | 仅字号较大但无标题样式的 DOCX | 报告 `NO_HEADINGS`，不猜测章节 |
| 精确写回 | 同段两个槽位、固定表头＋两行 AI 数据、原生图表 | 通过；原模板未覆盖 |
| 安全失败 | 源模板改变、AI 漏填一个槽位 | 拒绝生成，无输出 DOCX |
| AI 结构限制 | 图表块、分类和系列数值、样式预设 | 通过；不允许无效类型/数值 |
| 图表展示 | Word 原生图表与可选 ECharts SVG | 两条路径均通过集成测试 |

完整回归命令：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.17.jdk/Contents/Home \
  /Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -Dmaven.repo.local=/Users/luojiang/maven/repository clean test
```

结果：**313 tests，0 failures，0 errors，10 skipped；BUILD SUCCESS**。跳过项是需要外部 CSCI 模板/ZIP、预生成复杂样本或显式启用本地 Ollama 的示例，不是断言失败。构建仅报告已有的 Aspose `systemPath` Maven 警告；本次未修改依赖或许可证。

真实 ECharts 渲染另行执行：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.17.jdk/Contents/Home \
  /Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
  -Dmaven.repo.local=/Users/luojiang/maven/repository \
  -Dtest=EChartsChartIntegrationTest -Domni.office.echarts.integration=true test
```

结果：**1 test，0 failures，0 errors，0 skipped；BUILD SUCCESS**。此测试实际调用本地 Node/ECharts，检查 DOCX 内是 SVG 图片且不是 Word 原生图表。`npm ci --prefix tools/echarts` 已执行成功。

## 尚需人工验收

- 用正式业务 DOCX 和代表性事实样本，在目标 Microsoft Word 版本中核对逐页排版、分页、字体替换与图表外观；自动测试只检查结构和关键内容。
- 未标记的任意复杂表格不会自动推断可变单元格。需在模板内明确 `{{table:name}}` 重复行，或编写业务专用填充规则。
- 当前原模板写回对图表插入使用命名预设，而非复制样例图表的全部格式细节。若必须与特定图表模板像素级一致，需要增加受审核的图表样式映射。
- 本轮没有调用线上/本地真实 LLM；AI 响应使用确定性测试替身验证协议和写回链路，真实 Ollama 效果应以业务数据另做验收。

使用说明见 [上传 Word 模板、AI 填充与图表](docx-template-ai-charts.md)。
