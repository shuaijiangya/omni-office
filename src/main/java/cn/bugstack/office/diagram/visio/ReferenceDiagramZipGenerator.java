package cn.bugstack.office.diagram.visio;

import cn.bugstack.office.diagram.api.VisioDiagramArtifact;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 批量读取 {@code 功能模块/功能/*.json} ZIP，为每个功能绘制两张固定模板的 Visio 图。
 *
 * <p>每个 JSON 使用数据流图与库管理流程图共用的简化结构：
 * {@code {"function":"功能3","inputs":["数据1","数据2","数据3"],"persistenceLabel":"持久化"}}。
 * 输出保存在 {@code 输出目录/功能模块/功能/visio/}，其中每张 VSDX 都有同名 PNG 预览。
 * 输入 ZIP 仅在内存中读取；所有图绘制成功后才复制到目标目录。</p>
 */
public final class ReferenceDiagramZipGenerator {

    /** 每个 JSON 最大解压大小。 */
    private static final int MAX_JSON_BYTES = 64 * 1024;
    /** ZIP 最多包含的功能数。 */
    private static final int MAX_FEATURES = 256;
    /** ZIP 中 JSON 解压后的总大小上限。 */
    private static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;
    /** JSON 解析器。 */
    private final ObjectMapper mapper = new ObjectMapper();
    /** 数据流图绘制器。 */
    private final ReferenceDataFlowRenderer dataFlowRenderer = new ReferenceDataFlowRenderer();
    /** 库管理流程图绘制器。 */
    private final ReferenceLibraryFlowRenderer libraryFlowRenderer = new ReferenceLibraryFlowRenderer();

    /**
     * 从 ZIP 批量生成两种 Visio 图，并按功能目录落盘。
     *
     * @param zipPath 包含 {@code 功能模块/功能/*.json} 的 ZIP 文件
     * @param outputRoot 输出根目录；其下建立 {@code 功能模块/功能/visio}
     * @return 按 ZIP 顺序排列的产物，每个功能依次为数据流图、库管理流程图
     * @throws IOException ZIP 读取、Visio 绘制或输出文件写入失败时抛出
     * @throws IllegalArgumentException ZIP 路径、JSON 内容或功能目录不合法时抛出
     */
    public List<VisioDiagramArtifact> generate(Path zipPath, Path outputRoot) throws IOException {
        Objects.requireNonNull(zipPath, "zipPath must not be null");
        Objects.requireNonNull(outputRoot, "outputRoot must not be null");
        List<FeatureInput> features = readFeatures(zipPath);
        Path root = outputRoot.toAbsolutePath().normalize();
        Path staging = Files.createTempDirectory("reference-diagrams-");
        try {
            List<PendingArtifact> pending = new ArrayList<>();
            for (int index = 0; index < features.size(); index++) {
                FeatureInput feature = features.get(index);
                Path input = staging.resolve("input-" + index + ".json");
                Files.write(input, feature.json);
                Path stagedDirectory = staging.resolve("generated").resolve(Integer.toString(index));
                Path targetDirectory = root.resolve(feature.module).resolve(feature.function).resolve("visio");
                try {
                    VisioDiagramArtifact dataFlow = dataFlowRenderer.render(input,
                            stagedDirectory.resolve(feature.function + "-数据流图.vsdx"));
                    VisioDiagramArtifact libraryFlow = libraryFlowRenderer.render(input,
                            stagedDirectory.resolve(feature.function + "-库管理流程图.vsdx"));
                    pending.add(new PendingArtifact(dataFlow, targetDirectory));
                    pending.add(new PendingArtifact(libraryFlow, targetDirectory));
                } catch (Exception exception) {
                    throw new IOException("Visio 生成失败，ZIP 条目：" + feature.sourcePath, exception);
                }
            }
            List<VisioDiagramArtifact> result = new ArrayList<>(pending.size());
            for (PendingArtifact artifact : pending) result.add(publish(artifact));
            return List.copyOf(result);
        } finally {
            deleteStaging(staging);
        }
    }

    /** 在生成目标文件前，校验 ZIP 路径、数量和业务 JSON。 */
    private List<FeatureInput> readFeatures(Path zipPath) throws IOException {
        List<FeatureInput> features = new ArrayList<>();
        Set<String> featurePaths = new HashSet<>();
        int totalBytes = 0;
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || isMacMetadata(entry.getName())) continue;
                String sourcePath = entry.getName();
                String[] parts = sourcePath.split("/", -1);
                if (parts.length != 3 || !parts[2].endsWith(".json") || parts[2].length() <= 5) {
                    throw new IllegalArgumentException("ZIP 条目必须是 功能模块/功能/*.json：" + sourcePath);
                }
                for (String part : parts) validateSegment(part, sourcePath);
                if (features.size() >= MAX_FEATURES) {
                    throw new IllegalArgumentException("ZIP 功能数量超过 " + MAX_FEATURES);
                }
                if (!featurePaths.add(parts[0] + "/" + parts[1])) {
                    throw new IllegalArgumentException("功能目录存在多个 JSON：" + parts[0] + "/" + parts[1]);
                }
                byte[] json;
                try (InputStream input = zip.getInputStream(entry)) {
                    json = input.readNBytes(MAX_JSON_BYTES + 1);
                }
                if (json.length > MAX_JSON_BYTES || totalBytes + json.length > MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("ZIP 中 JSON 大小超限：" + sourcePath);
                }
                totalBytes += json.length;
                validateJson(json, parts[1], sourcePath);
                features.add(new FeatureInput(parts[0], parts[1], sourcePath, json));
            }
        }
        if (features.isEmpty()) throw new IllegalArgumentException("ZIP 不包含 功能模块/功能/*.json");
        return features;
    }

    /** 跳过 macOS 压缩工具可能附带的元数据文件。 */
    private boolean isMacMetadata(String name) {
        return name.startsWith("__MACOSX/") || name.equals(".DS_Store") || name.endsWith("/.DS_Store");
    }

    /** 阻止 ZIP 路径穿越和跨平台非法文件名。 */
    private void validateSegment(String segment, String sourcePath) {
        if (segment.isBlank() || !segment.equals(segment.trim()) || segment.equals(".") || segment.equals("..")
                || segment.matches(".*[\\\\<>:\"|?*].*") || segment.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("ZIP 路径不安全：" + sourcePath);
        }
    }

    /** 保证目录名与 JSON 功能名一致，且输入数量可同时供两种模板使用。 */
    private void validateJson(byte[] json, String directoryFunction, String sourcePath) throws IOException {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (IOException exception) {
            throw new IllegalArgumentException("JSON 解析失败：" + sourcePath, exception);
        }
        JsonNode function = root == null ? null : root.get("function");
        JsonNode inputs = root == null ? null : root.get("inputs");
        if (root == null || !root.isObject()
                || root.size() != (root.has("persistenceLabel") ? 3 : 2) || function == null
                || !function.isTextual() || !directoryFunction.equals(function.asText().trim())
                || inputs == null || !inputs.isArray() || inputs.size() < 3 || inputs.size() > 4) {
            throw new IllegalArgumentException("JSON 必须包含与目录同名的 function 及 3～4 个 inputs：" + sourcePath);
        }
        JsonNode persistenceLabel = root.get("persistenceLabel");
        if (persistenceLabel != null && (!persistenceLabel.isTextual()
                || persistenceLabel.asText().length() > 40)) {
            throw new IllegalArgumentException("persistenceLabel 必须是长度不超过 40 的文本：" + sourcePath);
        }
    }

    /** 将绘制完成的 VSDX 和 PNG 一起放到指定功能的 visio 目录。 */
    private VisioDiagramArtifact publish(PendingArtifact pending) throws IOException {
        if (Files.isSymbolicLink(pending.directory.getParent().getParent())
                || Files.isSymbolicLink(pending.directory.getParent())
                || Files.isSymbolicLink(pending.directory)) {
            throw new IOException("输出功能目录不能是符号链接：" + pending.directory);
        }
        Files.createDirectories(pending.directory);
        Path vsdx = pending.directory.resolve(pending.artifact.getVsdxPath().getFileName());
        Path preview = pending.directory.resolve(pending.artifact.getPreviewPngPath().getFileName());
        Files.copy(pending.artifact.getVsdxPath(), vsdx, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(pending.artifact.getPreviewPngPath(), preview, StandardCopyOption.REPLACE_EXISTING);
        return new VisioDiagramArtifact(pending.artifact.getType(), vsdx, preview);
    }

    /** 清理本次调用创建的临时绘制目录。 */
    private void deleteStaging(Path staging) throws IOException {
        Files.walkFileTree(staging, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) throw exception;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** 已校验的单个功能输入。 */
    private static final class FeatureInput {
        private final String module;
        private final String function;
        private final String sourcePath;
        private final byte[] json;

        private FeatureInput(String module, String function, String sourcePath, byte[] json) {
            this.module = module;
            this.function = function;
            this.sourcePath = sourcePath;
            this.json = json;
        }
    }

    /** 完成绘制、等待复制到最终目录的图。 */
    private static final class PendingArtifact {
        private final VisioDiagramArtifact artifact;
        private final Path directory;

        private PendingArtifact(VisioDiagramArtifact artifact, Path directory) {
            this.artifact = artifact;
            this.directory = directory;
        }
    }
}
