# 本地 Ollama 多样本 DOCX 补全实测

验证日期：2026-10-14。使用本机 Ollama 的 `qwen3.5:2b`，温度 `0.0`，对已有 DOCX 做真实模型规划、审核计划校验与确定性写回。测试源码见 `SourceDocxOllamaLiveExampleTest`；输出在 `target/ollama-completion-samples/`，不会随 Git 提交。

## 复现

先确认 Ollama 已启动且模型已安装：

```bash
ollama list
curl http://127.0.0.1:11434/api/tags
```

如服务尚未运行，在单独的终端执行 `ollama serve`。运行全部样本：

```bash
/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -q \
  -Dtest=SourceDocxOllamaLiveExampleTest \
  -Dollama.live=true -Dollama.model=qwen3.5:2b \
  -Dollama.temperature=0.0 test
```

可用 `-Dollama.case=table` 只执行一个样本。此验收测试默认跳过，不影响普通 `mvn test`。它会保存原 DOCX、结构摘要、每次模型原始响应、审核计划和成功写回的 DOCX；失败的样本不会留下旧的成功输出。

## 实测结果

| 样本 | 输入与目标 | 结果 |
| --- | --- | --- |
| `paragraph` | 自然语言指定占位正文和 `businessFacts.scope` | 未通过：模型两次返回原占位文本，安全校验拒绝无效替换 |
| `paragraph-explicit` | 明确给出替换文字和补丁类型 | `READY`，成功替换正文 |
| `table` | 按接口列识别行，补充空白说明格 | `READY`，填入“创建订单”，其他行未改 |
| `merged-cell` | 自然语言描述合并标题行及待填单元格 | 未通过：模型选择行补丁；表格行宽不规则，安全校验拒绝 |
| `merged-cell-explicit` | 明确目标物理行列和 `cellPatches` | `READY`，填入“王工”，合并标题保留 |
| `styled-run` | 自然语言要求只替换多样式段落的一个 Run | 未通过：模型给出 `INSERT_AFTER`，未覆写目标；安全校验拒绝 |
| `styled-run-explicit` | 明确 Run 索引、预期原文和覆写策略 | `READY`，填入红色加粗“通过”，其他 Run 保留 |
| `needs-input` | 未提供实施日期，禁止猜测 | `NEEDS_INPUT`，模型询问具体日期，未生成 DOCX |

总计 8 个样本：4 个成功写回、1 个正确要求补充事实、3 个因模型计划不合规而被拦截。全部样本测试命令因此以非零状态结束，**不代表 8/8 通过**。明确目标和补丁结构的样本表现明显好于自由描述；当前 `qwen3.5:2b` 不能作为无需审核的通用文档补全器。

成功输出：

- [段落补全 DOCX](../target/ollama-completion-samples/paragraph-explicit-completed.docx)
- [普通表格补全 DOCX](../target/ollama-completion-samples/table-completed.docx)
- [含合并单元格表格补全 DOCX](../target/ollama-completion-samples/merged-cell-explicit-completed.docx)
- [多样式 Run 补全 DOCX](../target/ollama-completion-samples/styled-run-explicit-completed.docx)

这四份 DOCX 均已渲染为单页图像并逐页检查；macOS Quick Look 中可见中文、表格边界、合并标题、独立红色加粗 Run，未见裁剪或重叠。打包的 LibreOffice 渲染器在本机缺少中文字体映射，因此中文可视检查以 Quick Look 为准；最终业务模板仍应在目标 Word/WPS 环境验收。

## 结论与后续

框架的结构提取、白名单计划、预期文本校验、合并表格保护和确认写回在这些样本中发挥了作用：模型不合规时没有错误地生成 Word。当前最优先的改进是让自然语言请求在规划前定位到具体段落、单元格或 Run，再向模型暴露更小的可选补丁集合；同时保留人工审核。小模型的自由规划成功率需要继续扩大样本集评估，不能由这 8 例推断生产准确率。
