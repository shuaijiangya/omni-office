package cn.bugstack.application.csci;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 安全读取源码 ZIP，按目录映射软件、模块和功能，并使用 JDK AST 解析 Java 声明。
 *
 * <p>目录契约为 {@code CSCI详细设计/软件/模块/功能/.../*.java} 和可选的
 * {@code 数据库设计/逻辑结构设计/软件数据库设计/.../*.sql}。只读取源文本，
 * 不解压文件，也不执行上传的代码。</p>
 */
public final class CsciSourceArchiveReader {

    /** ZIP 中允许的最大文件数。 */
    private static final int MAX_FILES = 500;
    /** 单个 Java 或 SQL 文件的最大字节数。 */
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    /** 单张 PNG 图片的最大字节数。 */
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    /** ZIP 解压后允许读取的总字节数。 */
    private static final int MAX_ARCHIVE_BYTES = 20 * 1024 * 1024;
    /** 单个功能允许的最大类数。 */
    private static final int MAX_CLASSES_PER_FEATURE = 60;
    /** 单个类或数据库表允许的最大成员数。 */
    private static final int MAX_MEMBERS_PER_CLASS = 200;
    /** ZIP 中允许的最大数据库表数。 */
    private static final int MAX_TABLES = 100;

    /**
     * 解析上传 ZIP。
     *
     * @param archive ZIP 文件
     * @return 按目录分组的 CSCI 数据
     * @throws Exception 当 ZIP 路径、大小、Java 语法或 DDL 不合法时抛出
     */
    public CsciDesign read(Path archive) throws Exception {
        Objects.requireNonNull(archive, "archive");
        try (InputStream input = Files.newInputStream(archive)) {
            return read(input);
        }
    }

    /**
     * 直接读取上传流，适合由受控 HTTP/MCP 上传层调用；本方法会关闭输入流。
     *
     * @param input 上传的 ZIP 流
     * @return 按章节目录分组的设计数据
     * @throws Exception ZIP 或源文件不合规时抛出
     */
    public CsciDesign read(InputStream input) throws Exception {
        Objects.requireNonNull(input, "input");
        Map<String, byte[]> files = readEntries(input);
        CsciDesign result = new CsciDesign();
        List<Map.Entry<String, byte[]>> entries = new ArrayList<>(files.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, byte[]> entry : entries) {
            String[] path = entry.getKey().split("/");
            if (entry.getKey().endsWith(".java")) {
                if (path.length < 5 || !"CSCI详细设计".equals(path[0])) {
                    throw new IllegalArgumentException("Java 文件必须位于 CSCI详细设计/软件/模块/功能: " + entry.getKey());
                }
                setSoftware(result, path[1]);
                CsciDesign.Module module = module(result, path[2]);
                CsciDesign.Feature feature = feature(module, path[3]);
                String source = new String(entry.getValue(), StandardCharsets.UTF_8);
                feature.classes.addAll(parseJava(entry.getKey(), source, feature));
            } else if (entry.getKey().endsWith(".sql")) {
                if (path.length < 4 || !"数据库设计".equals(path[0])
                        || !"逻辑结构设计".equals(path[1])) {
                    throw new IllegalArgumentException("DDL 必须位于 数据库设计/逻辑结构设计/软件数据库设计: "
                            + entry.getKey());
                }
                String software = path[2].endsWith("数据库设计")
                        ? path[2].substring(0, path[2].length() - "数据库设计".length()) : path[2];
                setSoftware(result, software);
                result.databaseTables.addAll(new CsciDdlParser().parse(
                        new String(entry.getValue(), StandardCharsets.UTF_8)));
            } else if (entry.getKey().endsWith(".png")) {
                if (path.length < 5 || !"CSCI详细设计".equals(path[0])) {
                    throw new IllegalArgumentException("PNG 必须位于功能目录: " + entry.getKey());
                }
                setSoftware(result, path[1]);
                CsciDesign.Feature feature = feature(module(result, path[2]), path[3]);
                if (feature.uiImagePng != null) {
                    throw new IllegalArgumentException("每个功能最多一张界面 PNG: " + feature.name);
                }
                validatePng(entry.getValue(), entry.getKey());
                feature.uiImagePng = entry.getValue();
            }
        }
        if (result.modules.isEmpty()) {
            throw new IllegalArgumentException("ZIP 中没有按章节目录放置的 Java 源文件");
        }
        java.util.Set<String> physicalNames = new java.util.HashSet<>();
        if (result.databaseTables.size() > MAX_TABLES) {
            throw new IllegalArgumentException("DDL 物理表数量超过 " + MAX_TABLES);
        }
        for (CsciDesign.DbTable table : result.databaseTables) {
            if (!physicalNames.add(table.physicalName.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("DDL 中物理表名重复: " + table.physicalName);
            }
            if (table.columns.size() > MAX_MEMBERS_PER_CLASS) {
                throw new IllegalArgumentException("DDL 字段数量超限: " + table.physicalName);
            }
        }
        for (CsciDesign.Module module : result.modules) {
            for (CsciDesign.Feature feature : module.features) {
                if (feature.classes.isEmpty()) {
                    throw new IllegalArgumentException("功能目录中没有可解析的类: " + feature.name);
                }
                if (feature.classes.size() > MAX_CLASSES_PER_FEATURE) {
                    throw new IllegalArgumentException("功能类数量超过 " + MAX_CLASSES_PER_FEATURE + ": "
                            + feature.name);
                }
                java.util.Set<String> classNames = new java.util.HashSet<>();
                for (CsciDesign.JavaClass clazz : feature.classes) {
                    if (!classNames.add(clazz.name)) {
                        throw new IllegalArgumentException("功能目录中类名重复，请拆分或显式重命名: "
                                + feature.name + "/" + clazz.name);
                    }
                    if (clazz.fields.size() > MAX_MEMBERS_PER_CLASS
                            || clazz.methods.size() > MAX_MEMBERS_PER_CLASS) {
                        throw new IllegalArgumentException("类成员数量超限: " + clazz.name);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 读取并校验 ZIP 条目，不将上传内容写入磁盘。
     *
     * @param archive ZIP 输入流
     * @return 按路径索引的文件内容
     * @throws IOException 读取 ZIP 内容失败时抛出
     * @throws IllegalArgumentException 条目数量、类型、路径或大小不合规时抛出
     */
    private static Map<String, byte[]> readEntries(InputStream archive) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        int total = 0;
        try (ZipInputStream input = new ZipInputStream(archive, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                validatePath(name);
                if (!name.endsWith(".java") && !name.endsWith(".sql") && !name.endsWith(".png")) {
                    throw new IllegalArgumentException("ZIP 只允许 .java、.sql 和 .png: " + name);
                }
                if (result.size() >= MAX_FILES) {
                    throw new IllegalArgumentException("ZIP 文件数量超过 " + MAX_FILES);
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    int fileLimit = name.endsWith(".png") ? MAX_IMAGE_BYTES : MAX_FILE_BYTES;
                    if (bytes.size() + read > fileLimit || total + bytes.size() + read > MAX_ARCHIVE_BYTES) {
                        throw new IllegalArgumentException("ZIP 源文件大小超限: " + name);
                    }
                    bytes.write(buffer, 0, read);
                }
                total += bytes.size();
                if (result.putIfAbsent(name, bytes.toByteArray()) != null) {
                    throw new IllegalArgumentException("ZIP 存在重复路径: " + name);
                }
            }
        }
        return result;
    }

    /**
     * 拒绝绝对路径、空目录段和目录穿越路径。
     *
     * @param name ZIP 条目路径
     * @throws IllegalArgumentException 路径不安全时抛出
     */
    private static void validatePath(String name) {
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*") || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("ZIP 路径不安全: " + name);
        }
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("ZIP 路径不安全: " + name);
            }
        }
    }

    /**
     * 校验 PNG 签名、可读取性和像素尺寸。
     *
     * @param bytes 图片字节
     * @param name ZIP 条目路径，用于错误定位
     * @throws IOException 图片读取失败时抛出
     * @throws IllegalArgumentException 图片格式或尺寸不合规时抛出
     */
    private static void validatePng(byte[] bytes, String name) throws IOException {
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length < 8 || !java.util.Arrays.equals(signature, java.util.Arrays.copyOf(bytes, 8))) {
            throw new IllegalArgumentException("文件不是 PNG: " + name);
        }
        try (ImageInputStream image = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) throw new IllegalArgumentException("PNG 无法读取: " + name);
            ImageReader reader = readers.next();
            try {
                reader.setInput(image, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096
                        || width * height > 16_000_000) {
                    throw new IllegalArgumentException("PNG 像素尺寸超限: " + name);
                }
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * 设置软件名称，并确保源码与数据库目录使用同一名称。
     *
     * @param design 待填充的设计对象
     * @param name 从目录提取的软件名称
     * @throws IllegalArgumentException 名称为空或前后不一致时抛出
     */
    private static void setSoftware(CsciDesign design, String name) {
        if (name.isBlank() || (design.software != null && !design.software.equals(name))) {
            throw new IllegalArgumentException("ZIP 中软件名称不一致: " + name);
        }
        design.software = name;
    }

    /**
     * 查找或创建指定功能模块。
     *
     * @param design 设计对象
     * @param name 模块名称
     * @return 已存在或新创建的模块
     */
    private static CsciDesign.Module module(CsciDesign design, String name) {
        return design.modules.stream().filter(m -> m.name.equals(name)).findFirst().orElseGet(() -> {
            CsciDesign.Module value = new CsciDesign.Module();
            value.name = name;
            design.modules.add(value);
            return value;
        });
    }

    /**
     * 查找或创建模块下的指定功能。
     *
     * @param module 所属模块
     * @param name 功能名称
     * @return 已存在或新创建的功能
     */
    private static CsciDesign.Feature feature(CsciDesign.Module module, String name) {
        return module.features.stream().filter(f -> f.name.equals(name)).findFirst().orElseGet(() -> {
            CsciDesign.Feature value = new CsciDesign.Feature();
            value.name = name;
            module.features.add(value);
            return value;
        });
    }

    /**
     * 使用 JDK 编译器 AST 提取 Java 文件中的类型声明。
     *
     * @param name 源文件在 ZIP 中的路径
     * @param source Java 源文本
     * @param feature 所属功能，用于保存界面说明
     * @return 文件中声明的类、接口及枚举
     * @throws Exception 编译器不可用或源文件语法不合法时抛出
     */
    private static List<CsciDesign.JavaClass> parseJava(String name, String source,
                                                          CsciDesign.Feature feature) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("运行时需要 JDK 17+ 的 JavaCompiler");
        }
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///" + name.replace(" ", "%20")),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostic -> {
            if (diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR) {
                throw new IllegalArgumentException("Java 语法错误 " + name + ": " + diagnostic.getMessage(null));
            }
        }, List.of("-proc:none"), null, List.of(file));
        List<CsciDesign.JavaClass> classes = new ArrayList<>();
        Trees trees = Trees.instance(task);
        for (CompilationUnitTree unit : task.parse()) {
            SourcePositions positions = trees.getSourcePositions();
            for (Tree declaration : unit.getTypeDecls()) {
                if (declaration instanceof ClassTree) {
                    appendClass((ClassTree) declaration, "", source, unit, positions, feature, classes);
                }
            }
        }
        return classes;
    }

    /**
     * 提取类及其字段、方法，并递归处理内部类。
     *
     * @param type 当前类型 AST
     * @param prefix 内部类名前缀
     * @param source 完整源文本
     * @param unit 编译单元
     * @param positions AST 在源文本中的位置映射
     * @param feature 所属功能
     * @param classes 类信息输出列表
     */
    private static void appendClass(ClassTree type, String prefix, String source,
                                    CompilationUnitTree unit, SourcePositions positions,
                                    CsciDesign.Feature feature, List<CsciDesign.JavaClass> classes) {
        String simpleName = type.getSimpleName().toString();
        if (simpleName.isEmpty()) return;
        CsciDesign.JavaClass clazz = new CsciDesign.JavaClass();
        clazz.name = prefix + simpleName;
        clazz.kind = type.getKind() == Tree.Kind.ENUM ? "枚举"
                : type.getKind() == Tree.Kind.INTERFACE ? "接口" : "类";
        String doc = docBefore(source, positions.getStartPosition(unit, type));
        clazz.description = firstDescription(doc);
        if (feature.uiDescription == null || feature.uiDescription.isBlank()) {
            String ui = tag(doc, "csci.ui");
            if (!ui.isBlank()) feature.uiDescription = ui;
        }
        classes.add(clazz);
        for (Tree member : type.getMembers()) {
            if (member instanceof VariableTree) {
                VariableTree variable = (VariableTree) member;
                CsciDesign.Field field = new CsciDesign.Field();
                field.name = variable.getName().toString();
                field.type = variable.getType() == null ? clazz.name : variable.getType().toString();
                field.visibility = visibility(variable.getModifiers().getFlags().toString());
                if ("接口".equals(clazz.kind) && "package".equals(field.visibility)) {
                    field.visibility = "public";
                }
                field.description = firstDescription(docBefore(source, positions.getStartPosition(unit, member)));
                clazz.fields.add(field);
            } else if (member instanceof MethodTree) {
                MethodTree methodTree = (MethodTree) member;
                if (methodTree.getReturnType() == null) continue;
                CsciDesign.Method method = new CsciDesign.Method();
                method.name = methodTree.getName().toString();
                method.returnType = methodTree.getReturnType().toString();
                method.visibility = visibility(methodTree.getModifiers().getFlags().toString());
                if ("接口".equals(clazz.kind) && "package".equals(method.visibility)) {
                    method.visibility = "public";
                }
                String methodDoc = docBefore(source, positions.getStartPosition(unit, member));
                method.description = firstDescription(methodDoc);
                method.returnDescription = tag(methodDoc, "return");
                method.algorithm = tag(methodDoc, "implNote");
                method.caution = tag(methodDoc, "csci.caution");
                if (methodTree.getBody() != null) {
                    long bodyStart = positions.getStartPosition(unit, methodTree.getBody());
                    long bodyEnd = positions.getEndPosition(unit, methodTree.getBody());
                    if (bodyStart >= 0 && bodyEnd > bodyStart && bodyEnd <= source.length()) {
                        method.sourceBody = source.substring((int) bodyStart, (int) bodyEnd);
                    }
                }
                String flow = tag(methodDoc, "csci.flow");
                if (!flow.isBlank()) {
                    for (String step : flow.split("\\s*(?:->|→)\\s*")) {
                        if (!step.isBlank()) method.flowSteps.add(step.trim());
                    }
                    if (method.flowSteps.size() < 2) {
                        throw new IllegalArgumentException("@csci.flow 至少需要两个步骤: " + method.name);
                    }
                    if (method.flowSteps.size() > 30) {
                        throw new IllegalArgumentException("@csci.flow 步骤超过 30: " + method.name);
                    }
                }
                for (VariableTree parameter : methodTree.getParameters()) {
                    CsciDesign.Parameter p = new CsciDesign.Parameter();
                    p.name = parameter.getName().toString();
                    p.type = parameter.getType().toString();
                    p.description = paramTag(methodDoc, p.name);
                    method.parameters.add(p);
                }
                clazz.methods.add(method);
            } else if (member instanceof ClassTree) {
                appendClass((ClassTree) member, clazz.name + ".", source, unit, positions, feature, classes);
            }
        }
    }

    /**
     * 读取声明之前紧邻的 Javadoc 原文。
     *
     * @param source 完整源文本
     * @param start 声明起始位置
     * @return 去除注释边界与行首星号后的文字；不存在时返回空串
     */
    private static String docBefore(String source, long start) {
        if (start < 0 || start > source.length()) {
            return "";
        }
        int end = (int) start;
        while (end > 0 && Character.isWhitespace(source.charAt(end - 1))) {
            end--;
        }
        if (end < 2 || !source.substring(0, end).endsWith("*/")) {
            return "";
        }
        int begin = source.lastIndexOf("/**", end - 2);
        return begin < 0 ? "" : source.substring(begin + 3, end - 2)
                .replaceAll("(?m)^\\s*\\* ?", "").trim();
    }

    /**
     * 提取 Javadoc 标签之前的主体描述。
     *
     * @param doc Javadoc 文字
     * @return 合并空白后的描述
     */
    private static String firstDescription(String doc) {
        return doc.replaceFirst("(?s)(?:^|\\s)@[A-Za-z][\\w.]*\\b.*$", "")
                .replaceAll("\\s+", " ").trim();
    }

    /**
     * 提取指定 Javadoc 标签的说明文字。
     *
     * @param doc Javadoc 文字
     * @param tag 标签名称，不含 {@code @}
     * @return 标签内容；不存在时返回空串
     */
    private static String tag(String doc, String tag) {
        return taggedText(doc, tag, null);
    }

    /**
     * 提取指定形参的 Javadoc 说明。
     *
     * @param doc Javadoc 文字
     * @param name 形参名称
     * @return 形参说明；不存在时返回空串
     */
    private static String paramTag(String doc, String name) {
        return taggedText(doc, "param", name);
    }

    /**
     * 匹配普通标签或带参数名的标签，并截取下一标签前的内容。
     *
     * @param doc Javadoc 文字
     * @param tag 标签名称
     * @param parameter 可选的形参名称
     * @return 匹配到的说明；不存在时返回空串
     */
    private static String taggedText(String doc, String tag, String parameter) {
        String prefix = "(?:^|\\s)@" + Pattern.quote(tag)
                + (parameter == null ? "" : "\\s+" + Pattern.quote(parameter)) + "\\s+";
        Matcher matcher = Pattern.compile(prefix + "(.*?)(?=\\s+@[A-Za-z][\\w.]*\\b|$)", Pattern.DOTALL)
                .matcher(doc);
        return matcher.find() ? matcher.group(1).replaceAll("\\s+", " ").trim() : "";
    }

    /**
     * 从 AST 修饰符转换为文档所用的访问级别。
     *
     * @param modifiers AST 修饰符文字
     * @return {@code public}、{@code protected}、{@code private} 或 {@code package}
     */
    private static String visibility(String modifiers) {
        String value = modifiers.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("public")) return "public";
        if (value.contains("protected")) return "protected";
        if (value.contains("private")) return "private";
        return "package";
    }
}
