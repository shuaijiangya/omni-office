package cn.bugstack.application.csci;

import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 命令行示例：将按章节目录组织的 Java/DDL ZIP 填入原始 CSCI 模板。
 *
 * <p>可选第四参数为 Ollama 模型名，用方法源码补全算法与编程注意事项；
 * {@code --draft-from-names} 则显式启用带推断标记的名称草稿；所有模式均为
 * 非 private 方法生成流程 Visio。</p>
 */
public final class CsciDetailedDesignExample {

    /** 命令行示例不允许实例化。 */
    private CsciDetailedDesignExample() {
    }

    /**
     * 运行端到端示例。
     *
     * @param args 原模板、源码 ZIP、输出 DOCX、可选 Ollama 模型名或草稿开关
     * @throws Exception 输入不合规或生成失败时抛出
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 4) {
            throw new IllegalArgumentException("用法: CsciDetailedDesignExample 模板.docx 源码.zip 输出.docx [Ollama模型|--draft-from-names]");
        }
        if (args.length == 4 && "--draft-from-names".equals(args[3])) {
            Path template = Path.of(args[0]);
            Path output = Path.of(args[2]);
            if (!Files.isRegularFile(template) || template.toAbsolutePath().normalize()
                    .equals(output.toAbsolutePath().normalize())
                    || (Files.exists(output) && Files.isSameFile(template, output))) {
                throw new IllegalArgumentException("模板必须存在，且输出文件不能覆盖模板");
            }
            CsciDesign design = new CsciSourceArchiveReader().read(Path.of(args[1]));
            new CsciNameBasedDraftEnricher().enrich(design);
            new CsciTemplateRenderer().render(template, design, output);
            System.out.println("生成完成（设计草稿，需人工复核）: " + output.toAbsolutePath());
            return;
        }
        CsciDetailedDesignApplication application = args.length == 4
                ? new CsciDetailedDesignApplication(new OllamaStructuredAiClient(args[3]))
                : CsciDetailedDesignApplication.withoutAi();
        application.generate(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.println("生成完成: " + Path.of(args[2]).toAbsolutePath());
    }
}
