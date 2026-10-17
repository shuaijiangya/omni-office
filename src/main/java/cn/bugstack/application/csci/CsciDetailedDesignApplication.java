package cn.bugstack.application.csci;

import cn.bugstack.application.ai.StructuredAiClient;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * CSCI 详细设计的正式生成入口，统一编排源码解析、方法设计补全和 Word 渲染。
 *
 * <p>注入结构化 AI 客户端时，缺失的算法/逻辑与编程注意事项在渲染前自动补全；
 * 显式 Javadoc 始终优先。只有调用 {@link #withoutAi()} 才关闭补全。</p>
 */
public final class CsciDetailedDesignApplication {

    /** 上传 ZIP 的结构化解析器。 */
    private final CsciSourceArchiveReader archiveReader;
    /** 可选的方法设计补全器；仅显式关闭 AI 时为空。 */
    private final CsciMethodDesignEnricher enricher;
    /** 保留原始模板格式的 Word 渲染器。 */
    private final CsciTemplateRenderer renderer;

    /**
     * 使用业务环境注入的结构化 AI 客户端创建生成应用。
     *
     * @param aiClient 已配置的 AI 客户端，不可为空
     */
    public CsciDetailedDesignApplication(StructuredAiClient aiClient) {
        this(new CsciSourceArchiveReader(),
                new CsciMethodDesignEnricher(Objects.requireNonNull(aiClient, "aiClient")),
                new CsciTemplateRenderer());
    }

    /**
     * 显式创建不调用 AI 的确定性生成应用。
     *
     * @return 不执行方法设计补全的应用
     */
    public static CsciDetailedDesignApplication withoutAi() {
        return new CsciDetailedDesignApplication(new CsciSourceArchiveReader(), null,
                new CsciTemplateRenderer());
    }

    /**
     * 创建内部生成应用。
     *
     * @param archiveReader ZIP 解析器
     * @param enricher 方法设计补全器；为空表示明确关闭 AI
     * @param renderer Word 渲染器
     */
    private CsciDetailedDesignApplication(CsciSourceArchiveReader archiveReader,
                                          CsciMethodDesignEnricher enricher,
                                          CsciTemplateRenderer renderer) {
        this.archiveReader = archiveReader;
        this.enricher = enricher;
        this.renderer = renderer;
    }

    /**
     * 解析上传 ZIP 并补全缺失的方法设计，可用于生成前检查。
     *
     * @param archive ZIP 文件路径
     * @return 待渲染的设计数据
     * @throws Exception ZIP 不合规或 AI 补全失败时抛出
     */
    public CsciDesign analyze(Path archive) throws Exception {
        return enrich(archiveReader.read(Objects.requireNonNull(archive, "archive")));
    }

    /**
     * 解析上传 ZIP 流并补全缺失的方法设计；解析器会关闭输入流。
     *
     * @param archive 上传的 ZIP 流
     * @return 待渲染的设计数据
     * @throws Exception ZIP 不合规或 AI 补全失败时抛出
     */
    public CsciDesign analyze(InputStream archive) throws Exception {
        return enrich(archiveReader.read(Objects.requireNonNull(archive, "archive")));
    }

    /**
     * 从 ZIP 文件生成 CSCI 详细设计 Word 文档。
     *
     * @param template 原始 DOCX 模板
     * @param archive 按标题层级组织的源码 ZIP
     * @param output 输出 DOCX
     * @return 实际输出路径
     * @throws Exception 输入、补全或渲染失败时抛出
     */
    public Path generate(Path template, Path archive, Path output) throws Exception {
        validateTargets(template, output);
        return render(template, analyze(archive), output);
    }

    /**
     * 从上传 ZIP 流生成 CSCI 详细设计 Word 文档；解析器会关闭输入流。
     *
     * @param template 原始 DOCX 模板
     * @param archive 上传的源码 ZIP 流
     * @param output 输出 DOCX
     * @return 实际输出路径
     * @throws Exception 输入、补全或渲染失败时抛出
     */
    public Path generate(Path template, InputStream archive, Path output) throws Exception {
        validateTargets(template, output);
        return render(template, analyze(archive), output);
    }

    /**
     * 在解析源码和调用模型之前检查基本输出条件。
     *
     * @param template 原始 DOCX 模板
     * @param output 输出 DOCX 路径
     * @throws IOException 无法判断现有输出文件是否与模板相同时抛出
     */
    private static void validateTargets(Path template, Path output) throws IOException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(output, "output");
        if (!Files.isRegularFile(template)) {
            throw new IllegalArgumentException("CSCI 模板不存在: " + template);
        }
        if (template.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                || (Files.exists(output) && Files.isSameFile(template, output))) {
            throw new IllegalArgumentException("输出文件不能覆盖 CSCI 模板: " + output);
        }
    }

    /**
     * 仅在 AI 已配置时补全缺失的设计说明。
     *
     * @param design 已解析的设计数据
     * @return 同一个已补全的设计对象
     */
    private CsciDesign enrich(CsciDesign design) {
        if (enricher != null) enricher.enrich(design);
        return design;
    }

    /**
     * 将解析及补全后的设计写入用户模板。
     *
     * @param template 原始 DOCX 模板
     * @param design 待渲染的设计数据
     * @param output 输出 DOCX
     * @return 实际输出路径
     * @throws Exception 渲染失败时抛出
     */
    private Path render(Path template, CsciDesign design, Path output) throws Exception {
        renderer.render(template, design, output);
        return output.toAbsolutePath();
    }
}
