package cn.bugstack.application.csci;

import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import java.nio.file.Path;

/**
 * 命令行示例：将按章节目录组织的 Java/DDL ZIP 填入原始 CSCI 模板。
 *
 * <p>可选第四参数为 Ollama 模型名，用方法源码补全算法与编程注意事项。</p>
 */
public final class CsciDetailedDesignExample {

    /** 命令行示例不允许实例化。 */
    private CsciDetailedDesignExample() {
    }

    /**
     * 运行端到端示例。
     *
     * @param args 原模板、源码 ZIP、输出 DOCX、可选 Ollama 模型名
     * @throws Exception 输入不合规或生成失败时抛出
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 4) {
            throw new IllegalArgumentException("用法: CsciDetailedDesignExample 模板.docx 源码.zip 输出.docx [Ollama模型]");
        }
        CsciDetailedDesignApplication application = args.length == 4
                ? new CsciDetailedDesignApplication(new OllamaStructuredAiClient(args[3]))
                : CsciDetailedDesignApplication.withoutAi();
        application.generate(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.println("生成完成: " + Path.of(args[2]).toAbsolutePath());
    }
}
