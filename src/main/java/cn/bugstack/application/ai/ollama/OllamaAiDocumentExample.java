package cn.bugstack.application.ai.ollama;

import cn.bugstack.application.ai.AiDocumentResult;
import cn.bugstack.application.ai.AiGenerationException;
import cn.bugstack.application.ai.InternalAiDocumentApplication;
import cn.bugstack.export.api.ReportOutputFormat;

import java.nio.file.Path;

/** 使用本地 Ollama 按数据模板生成并导出 Word 与 HTML 文档。 */
public final class OllamaAiDocumentExample {

    private OllamaAiDocumentExample() {
    }

    public static void main(String[] args) {
        String model = args.length > 0 ? args[0] : "qwen3.5:2b";
        Path output = args.length > 1 ? Path.of(args[1])
                : Path.of("target", "ai-demo", "omni-office-ai-demo.docx");
        Path htmlOutput = args.length > 2 ? Path.of(args[2])
                : output.resolveSibling("omni-office-ai-demo.html");
        InternalAiDocumentApplication application = new InternalAiDocumentApplication(
                new OllamaStructuredAiClient(model));

        String instruction = "请严格按下面的字段事实清单填写模板；每个字段只使用自己清单中的事实，禁止跨字段发挥。\n"
                + "executiveSummary：说明本报告用本地 qwen3.5:2b 填充受控数据，随后经 DocumentTemplate、"
                + "DocumentSpec 和统一 export 生成 Word 与 HTML，并确认 M1 至 M10 已落地。\n"
                + "architectureOverview：只按此顺序说明：用户指令 -> Ollama 返回 JSON 业务数据 -> JSON Schema 校验 -> "
                + "DocumentTemplate 映射 -> DocumentSpec 结构及 AI 安全边界校验 -> 统一 export 生成 Word、PDF 或 HTML。"
                + "图形块存在时，由 DiagramSpec 生成 VSDX、PNG 预览和 OLE 工件，再写入 Word。\n"
                + "implementedCapabilities：只生成九项且每项各写一次："
                + "第一项为 DocumentSpec 通用章节、段落、列表、表格和图形结构；"
                + "第二项为 DocumentTemplate 使用 JSON Schema 校验数据并映射；"
                + "第三项为内部 AI 的自由 DocumentSpec 与模板数据填充双模式；"
                + "第四项为 DiagramSpec 生成 VSDX、PNG 预览、OLE 工件并写入 Word。\n"
                + "第五项为 Function Calling 与 MCP 复用同一外部工具门面并返回受控资源 URI；"
                + "第六项为 Streamable HTTP 鉴权、Origin、限流、会话和租户隔离；"
                + "第七项为模板审核发布和 Schema 兼容迁移；"
                + "第八项为 AI 追踪、评测和人工审批；"
                + "第九项为产物生命周期、安全扫描、对象存储、审计和异步任务。\n"
                + "securityBoundaries：只生成五项且每项各写一次："
                + "第一项为所有模型输出都不受信，必须通过 JSON Schema、DocumentSpec 和 AI 安全边界校验；"
                + "第二项为只允许受信目录内的工件路径，并限制结构数量、文本长度和协议字段；"
                + "第三项为 Function Calling 与 MCP 都不能绕过模板、DocumentSpec、DiagramSpec 和 export 校验；"
                + "第四项为 HTTP 会话、任务及租户工件隔离；"
                + "第五项为 ClamAV 与公网 OAuth 只有配置后才可声明启用。\n"
                + "nextSteps：只生成三项生产化建议：企业 OAuth 与短期令牌、真实对象存储与 ClamAV、"
                + "以及持续业务评测和容量基线；不得把建议写成未实现缺陷。\n"
                + "所有字段值必须使用简体中文；DocumentSpec、DocumentTemplate、AI、Visio、VSDX、PNG、"
                + "Word、OLE、JSON Schema、Function Calling、MCP 等必要技术名词可以保留英文，"
                + "除此以外不得输出英文句子。";

        try {
            try (java.io.InputStream template = OllamaAiDocumentExample.class.getResourceAsStream(
                    "/internal-ai/1.0/omni-office-demo-template.json")) {
                if (template == null) {
                    throw new IllegalStateException("missing Ollama demo template resource");
                }
                application.registerTemplate(template);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("failed to close Ollama demo template", e);
            }
            AiDocumentResult result = application.generateFromTemplate(
                    "omni.office.ai-demo", "1.0.0", instruction, null);
            application.export(result, ReportOutputFormat.DOCX, output);
            application.export(result, ReportOutputFormat.HTML, htmlOutput);
            System.out.println("Generated DOCX: " + output.toAbsolutePath());
            System.out.println("Generated HTML: " + htmlOutput.toAbsolutePath());
            System.out.println("AI attempts: " + result.getAttempts());
        } catch (AiGenerationException e) {
            System.err.println("AI generation failed after " + e.getAttempts() + " attempts:");
            e.getValidationErrors().forEach(error -> System.err.println("- " + error));
            throw e;
        }
    }
}
