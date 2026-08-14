package cn.bugstack.office.docx.design.parser;

import cn.bugstack.office.docx.design.ClassDesignTableOptions;
import cn.bugstack.office.docx.design.model.ClassDesignDoc;
import cn.bugstack.office.docx.design.model.FieldDesignDoc;
import cn.bugstack.office.docx.design.model.MethodDesignDoc;
import cn.bugstack.office.docx.design.model.ParameterDesignDoc;
import cn.bugstack.office.docx.design.model.ThrowsDesignDoc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Java 源码文本的类元数据解析器。
 *
 * <p>该实现不依赖第三方源码解析库，适合在当前封装阶段读取标准 Java 类、
 * 字段和方法前的 Javadoc。它保留 {@link ClassMetadataParser} 策略边界，
 * 后续可以平滑替换为 JavaParser 或 QDox 实现。</p>
 */
public class SourceClassMetadataParser implements ClassMetadataParser {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^(?<prefix>.*?)\\b(?<kind>class|interface|enum)\\s+(?<name>\\w+)(?<tail>.*)$");
    private static final Pattern TYPE_NAME_PATTERN = Pattern.compile("\\b(class|interface|enum)\\s+(\\w+)\\b");
    private static final Pattern THROWS_PATTERN = Pattern.compile("\\bthrows\\s+(.+)$");

    /**
     * 创建源码文本类元数据解析器。
     */
    public SourceClassMetadataParser() {
    }

    /**
     * 解析指定类源码。
     *
     * @param options 类设计表格选项
     * @return 类设计文档模型
     */
    @Override
    public ClassDesignDoc parse(ClassDesignTableOptions options) {
        validateOptions(options);
        Path sourceFile = resolveSourceFile(options);
        String source = readSource(sourceFile);
        ClassDesignDoc doc = new ClassDesignDoc();
        doc.setPackageName(packageName(source, options.getClassName()));

        List<JavadocDeclaration> declarations = collectDeclarations(source, options.getClassName());
        for (JavadocDeclaration declaration : declarations) {
            String normalized = normalizeDeclaration(declaration.declaration);
            ParsedJavadoc javadoc = parseJavadoc(declaration.javadoc);
            if (isClassDeclaration(normalized)) {
                fillClass(doc, normalized, javadoc);
            } else if (isMethodDeclaration(normalized)) {
                MethodDesignDoc method = parseMethod(normalized, javadoc, doc.getClassName());
                if (method != null && options.isIncludeMethods() && includeMember(method.getModifiers(), options)
                        && includeMethod(method, options)) {
                    doc.addMethod(method);
                }
            } else if (isFieldDeclaration(normalized)) {
                for (FieldDesignDoc field : parseFields(normalized, javadoc)) {
                    if (options.isIncludeFields() && includeMember(field.getModifiers(), options)) {
                        doc.addField(field);
                    }
                }
            }
        }

        if (doc.getClassName().isEmpty()) {
            doc.setClassName(simpleClassName(options.getClassName()));
        }
        return doc;
    }

    /**
     * 校验类设计表格解析所需的必要配置。
     *
     * @param options 类设计表格选项
     */
    private void validateOptions(ClassDesignTableOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("class design table options must not be null");
        }
        if (options.getSourceRoot() == null) {
            throw new IllegalArgumentException("sourceRoot must not be null");
        }
        if (options.getClassName() == null || options.getClassName().trim().isEmpty()) {
            throw new IllegalArgumentException("className must not be blank");
        }
    }

    /**
     * 根据类全限定名定位 Java 源码文件。
     *
     * @param options 类设计表格选项
     * @return Java 源码文件路径
     */
    private Path resolveSourceFile(ClassDesignTableOptions options) {
        String relativePath = options.getClassName().replace('.', '/') + ".java";
        Path sourceFile = options.getSourceRoot().resolve(relativePath);
        if (!Files.exists(sourceFile)) {
            throw new IllegalArgumentException("Java source file does not exist: " + sourceFile);
        }
        return sourceFile;
    }

    /**
     * 按 UTF-8 读取 Java 源码文件内容。
     *
     * @param sourceFile Java 源码文件路径
     * @return 源码文本
     */
    private String readSource(Path sourceFile) {
        try {
            return Files.readString(sourceFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read Java source file: " + sourceFile, e);
        }
    }

    /**
     * 解析源码包名；源码缺少 package 声明时从类全限定名中推断。
     *
     * @param source 源码文本
     * @param className 类全限定名
     * @return 包名；默认包返回空字符串
     */
    private String packageName(String source, String className) {
        Matcher matcher = PACKAGE_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        int index = className.lastIndexOf('.');
        return index < 0 ? "" : className.substring(0, index);
    }

    /**
     * 收集目标类及其直接成员的 Javadoc 与声明。
     *
     * <p>该方法按文本顺序扫描 {@code /** ... *&#47;}，并跳过 Javadoc 后面的空白和注解，
     * 将目标类注释与 class 声明配对，同时只保留目标类第一层属性和方法声明。
     * 内部类、父类和接口中的成员不会进入返回结果。</p>
     *
     * @param source 源码文本
     * @param className 目标类全限定名或简单类名
     * @return 目标类 Javadoc 与直接成员声明的配对列表
     */
    private List<JavadocDeclaration> collectDeclarations(String source, String className) {
        List<JavadocDeclaration> scanned = scanJavadocDeclarations(source);
        JavadocDeclaration targetClass = findTargetClass(source, scanned, className);
        if (targetClass == null) {
            targetClass = findTargetClassWithoutJavadoc(source, className);
        }
        if (targetClass == null) {
            return new ArrayList<>();
        }

        List<JavadocDeclaration> declarations = new ArrayList<>();
        declarations.add(targetClass);
        int bodyStart = source.indexOf('{', targetClass.declarationStart);
        int bodyEnd = findMatchingBrace(source, bodyStart);
        if (bodyStart < 0 || bodyEnd < 0) {
            return declarations;
        }

        for (JavadocDeclaration declaration : scanned) {
            if (declaration == targetClass) {
                continue;
            }
            if (declaration.javadocStart > bodyStart
                    && declaration.javadocStart < bodyEnd
                    && braceDepth(source, bodyStart + 1, declaration.javadocStart) == 0
                    && !isClassDeclaration(normalizeDeclaration(declaration.declaration))) {
                declarations.add(declaration);
            }
        }
        collectDirectFieldDeclarations(source, bodyStart + 1, bodyEnd, declarations);
        return declarations;
    }

    /**
     * 收集目标类第一层没有 Javadoc 的字段声明。
     *
     * <p>带 Javadoc 的字段已经由 {@link #scanJavadocDeclarations(String)} 收集。
     * 该方法只补齐无注释字段，确保类设计表格可以完整展示当前类属性。</p>
     *
     * @param source 源码文本
     * @param bodyStart 类体起始偏移量，不包含左大括号
     * @param bodyEnd 类体结束偏移量，不包含右大括号
     * @param declarations 已收集的 Javadoc 与声明配对列表
     */
    private void collectDirectFieldDeclarations(String source, int bodyStart, int bodyEnd,
                                                List<JavadocDeclaration> declarations) {
        int statementStart = bodyStart;
        LexicalState state = LexicalState.CODE;
        int initializerBraceDepth = 0;
        for (int i = bodyStart; i < bodyEnd && i < source.length(); i++) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            state = nextState(source, state, i, next);
            if (state == LexicalState.CODE) {
                if (initializerBraceDepth > 0) {
                    if (ch == '{') {
                        initializerBraceDepth++;
                    } else if (ch == '}') {
                        initializerBraceDepth--;
                    }
                } else if (ch == '{') {
                    String declarationPrefix = cleanDirectDeclaration(source.substring(statementStart, i));
                    if (findTopLevelChar(declarationPrefix, '=') >= 0) {
                        initializerBraceDepth = 1;
                    } else {
                        int matchingBrace = findMatchingBrace(source, i);
                        if (matchingBrace > i) {
                            i = matchingBrace;
                            statementStart = i + 1;
                            continue;
                        }
                    }
                }
            }
            if (state == LexicalState.CODE && initializerBraceDepth == 0 && ch == ';') {
                String declaration = cleanDirectDeclaration(source.substring(statementStart, i));
                if (isFieldDeclaration(declaration) && !hasDeclaration(declarations, declaration)) {
                    declarations.add(new JavadocDeclaration(statementStart, statementStart, "", declaration));
                }
                statementStart = i + 1;
            }
            if (shouldSkipNext(state, ch, next)) {
                i++;
            }
        }
    }

    /**
     * 清理第一层字段声明前的注释和注解。
     *
     * @param declaration 原始声明文本
     * @return 可用于字段解析的声明文本
     */
    private String cleanDirectDeclaration(String declaration) {
        return declaration.replaceAll("(?s)/\\*\\*.*?\\*/", "")
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "")
                .replaceAll("(?m)^\\s*@[^\\r\\n]+", "")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 判断声明列表中是否已经包含指定声明。
     *
     * @param declarations Javadoc 与声明配对列表
     * @param declaration 待判断声明
     * @return 已存在时返回 {@code true}
     */
    private boolean hasDeclaration(List<JavadocDeclaration> declarations, String declaration) {
        String normalized = normalizeDeclaration(declaration);
        for (JavadocDeclaration existing : declarations) {
            if (normalized.equals(normalizeDeclaration(existing.declaration))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 扫描源码中所有 Javadoc 与紧邻声明。
     *
     * @param source 源码文本
     * @return 所有 Javadoc 与声明的配对列表
     */
    private List<JavadocDeclaration> scanJavadocDeclarations(String source) {
        List<JavadocDeclaration> declarations = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            int start = source.indexOf("/**", index);
            if (start < 0) {
                break;
            }
            int end = source.indexOf("*/", start);
            if (end < 0) {
                break;
            }
            String javadoc = source.substring(start, end + 2);
            int declarationStart = skipWhitespaceAndAnnotations(source, end + 2);
            String declaration = readDeclaration(source, declarationStart);
            if (!declaration.isEmpty()) {
                declarations.add(new JavadocDeclaration(start, declarationStart, javadoc, declaration));
            }
            index = end + 2;
        }
        return declarations;
    }

    /**
     * 在扫描结果中查找目标类声明。
     *
     * @param declarations Javadoc 与声明的配对列表
     * @param className 目标类全限定名或简单类名
     * @return 目标类声明配对；未找到时返回 {@code null}
     */
    private JavadocDeclaration findTargetClass(String source, List<JavadocDeclaration> declarations, String className) {
        String targetSimpleName = simpleClassName(className);
        for (JavadocDeclaration declaration : declarations) {
            String normalized = normalizeDeclaration(declaration.declaration);
            Matcher matcher = CLASS_PATTERN.matcher(normalized);
            if (matcher.matches()
                    && targetSimpleName.equals(matcher.group("name"))
                    && braceDepth(source, 0, declaration.declarationStart) == 0) {
                return declaration;
            }
        }
        return null;
    }

    /**
     * 在缺少类级 Javadoc 时，通过源码文本查找目标顶层类型声明。
     *
     * <p>该方法只接受大括号深度为 0 且处于普通代码区域的类型声明，
     * 避免内部类或注释里的文本被当作当前类边界。</p>
     *
     * @param source 源码文本
     * @param className 目标类全限定名或简单类名
     * @return 目标类声明配对；未找到时返回 {@code null}
     */
    private JavadocDeclaration findTargetClassWithoutJavadoc(String source, String className) {
        String targetSimpleName = simpleClassName(className);
        Matcher matcher = TYPE_NAME_PATTERN.matcher(source);
        while (matcher.find()) {
            if (!targetSimpleName.equals(matcher.group(2))) {
                continue;
            }
            if (braceDepth(source, 0, matcher.start()) != 0 || lexicalStateAt(source, matcher.start()) != LexicalState.CODE) {
                continue;
            }
            int declarationStart = findDeclarationStart(source, matcher.start());
            String declaration = readDeclaration(source, declarationStart);
            if (isClassDeclaration(normalizeDeclaration(declaration))) {
                return new JavadocDeclaration(declarationStart, declarationStart, "", declaration);
            }
        }
        return null;
    }

    /**
     * 从类型关键字位置向前寻找声明起点。
     *
     * <p>声明起点位于上一个顶层分号或大括号之后，因此可以保留 public、final
     * 和注解等声明前缀，同时不会把 package/import 语句并入类型声明。</p>
     *
     * @param source 源码文本
     * @param keywordIndex {@code class}/{@code interface}/{@code enum} 关键字位置
     * @return 声明起始偏移量
     */
    private int findDeclarationStart(String source, int keywordIndex) {
        for (int i = keywordIndex - 1; i >= 0; i--) {
            char ch = source.charAt(i);
            if (ch == ';' || ch == '{' || ch == '}') {
                return skipWhitespaceAndAnnotations(source, i + 1);
            }
        }
        return skipWhitespaceAndAnnotations(source, 0);
    }

    /**
     * 查找左大括号对应的右大括号。
     *
     * @param source 源码文本
     * @param openBraceIndex 左大括号偏移量
     * @return 右大括号偏移量；未找到时返回 {@code -1}
     */
    private int findMatchingBrace(String source, int openBraceIndex) {
        if (openBraceIndex < 0) {
            return -1;
        }
        int depth = 0;
        LexicalState state = LexicalState.CODE;
        for (int i = openBraceIndex; i < source.length(); i++) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            state = nextState(source, state, i, next);
            if (state == LexicalState.CODE) {
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            if (shouldSkipNext(state, ch, next)) {
                i++;
            }
        }
        return -1;
    }

    /**
     * 计算指定源码区间内的顶层大括号深度。
     *
     * @param source 源码文本
     * @param from 起始偏移量
     * @param to 结束偏移量，不包含该位置
     * @return 大括号深度
     */
    private int braceDepth(String source, int from, int to) {
        int depth = 0;
        LexicalState state = LexicalState.CODE;
        for (int i = from; i < to && i < source.length(); i++) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            state = nextState(source, state, i, next);
            if (state == LexicalState.CODE) {
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                }
            }
            if (shouldSkipNext(state, ch, next)) {
                i++;
            }
        }
        return depth;
    }

    /**
     * 推进源码扫描的词法状态。
     *
     * @param source 源码文本
     * @param state 当前词法状态
     * @param index 当前字符偏移量
     * @param next 下一个字符
     * @return 新的词法状态
     */
    private LexicalState nextState(String source, LexicalState state, int index, char next) {
        char ch = source.charAt(index);
        if (state == LexicalState.CODE) {
            if (ch == '/' && next == '/') {
                return LexicalState.LINE_COMMENT;
            }
            if (ch == '/' && next == '*') {
                return LexicalState.BLOCK_COMMENT;
            }
            if (ch == '"') {
                return LexicalState.STRING;
            }
            if (ch == '\'') {
                return LexicalState.CHARACTER;
            }
            return state;
        }
        if (state == LexicalState.LINE_COMMENT && (ch == '\n' || ch == '\r')) {
            return LexicalState.CODE;
        }
        if (state == LexicalState.BLOCK_COMMENT && ch == '*' && next == '/') {
            return LexicalState.CODE;
        }
        if (state == LexicalState.STRING && ch == '"' && !isEscaped(source, index)) {
            return LexicalState.CODE;
        }
        if (state == LexicalState.CHARACTER && ch == '\'' && !isEscaped(source, index)) {
            return LexicalState.CODE;
        }
        return state;
    }

    /**
     * 判断当前位置的字符是否被反斜杠转义。
     *
     * @param source 源码文本
     * @param index 当前字符偏移量
     * @return 被奇数个连续反斜杠转义时返回 {@code true}
     */
    private boolean isEscaped(String source, int index) {
        int slashCount = 0;
        for (int i = index - 1; i >= 0 && source.charAt(i) == '\\'; i--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }

    /**
     * 获取指定源码位置之前的词法状态。
     *
     * @param source 源码文本
     * @param index 待检查偏移量
     * @return 该位置前的词法状态
     */
    private LexicalState lexicalStateAt(String source, int index) {
        LexicalState state = LexicalState.CODE;
        for (int i = 0; i < index && i < source.length(); i++) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            state = nextState(source, state, i, next);
            if (shouldSkipNext(state, ch, next)) {
                i++;
            }
        }
        return state;
    }

    /**
     * 判断当前扫描是否应跳过双字符词法标记的第二个字符。
     *
     * @param state 当前词法状态
     * @param ch 当前字符
     * @param next 下一个字符
     * @return 需要跳过时返回 {@code true}
     */
    private boolean shouldSkipNext(LexicalState state, char ch, char next) {
        return (state == LexicalState.LINE_COMMENT && ch == '/' && next == '/')
                || (state == LexicalState.BLOCK_COMMENT && ch == '/' && next == '*')
                || (state == LexicalState.CODE && ch == '*' && next == '/');
    }

    /**
     * 从指定位置开始跳过空白字符和声明注解。
     *
     * @param source 源码文本
     * @param index 起始偏移量
     * @return 第一个非空白、非注解声明字符的偏移量
     */
    private int skipWhitespaceAndAnnotations(String source, int index) {
        int current = index;
        while (current < source.length()) {
            while (current < source.length() && Character.isWhitespace(source.charAt(current))) {
                current++;
            }
            if (current < source.length() && source.charAt(current) == '@') {
                current = skipAnnotationLine(source, current);
                continue;
            }
            return current;
        }
        return current;
    }

    /**
     * 跳过一条声明注解。
     *
     * <p>注解可能包含括号参数，因此只有在括号平衡后遇到换行才认为注解结束。</p>
     *
     * @param source 源码文本
     * @param index 注解 {@code @} 符号所在偏移量
     * @return 注解结束后的偏移量
     */
    private int skipAnnotationLine(String source, int index) {
        int current = index;
        int parentheses = 0;
        while (current < source.length()) {
            char ch = source.charAt(current);
            if (ch == '(') {
                parentheses++;
            } else if (ch == ')') {
                parentheses--;
            } else if ((ch == '\n' || ch == '\r') && parentheses <= 0) {
                return current + 1;
            }
            current++;
        }
        return current;
    }

    /**
     * 从指定位置读取一段 Java 声明。
     *
     * <p>读取过程在顶层 {@code \{} 或 {@code ;} 处停止，同时跟踪泛型尖括号和方法参数括号，
     * 避免在泛型或参数列表内部提前截断。</p>
     *
     * @param source 源码文本
     * @param index 声明起始偏移量
     * @return 声明文本
     */
    private String readDeclaration(String source, int index) {
        StringBuilder declaration = new StringBuilder();
        int genericsDepth = 0;
        int parenthesesDepth = 0;
        int bracesDepth = 0;
        LexicalState state = LexicalState.CODE;
        for (int i = index; i < source.length(); i++) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            state = nextState(source, state, i, next);
            if (state == LexicalState.CODE) {
                if (ch == '<') {
                    genericsDepth++;
                } else if (ch == '>') {
                    genericsDepth--;
                } else if (ch == '(') {
                    parenthesesDepth++;
                } else if (ch == ')') {
                    parenthesesDepth--;
                } else if (ch == '{') {
                    boolean fieldInitializerBlock = genericsDepth <= 0
                            && parenthesesDepth <= 0
                            && bracesDepth == 0
                            && findTopLevelChar(declaration.toString(), '=') >= 0;
                    if (genericsDepth <= 0 && parenthesesDepth <= 0 && bracesDepth == 0 && !fieldInitializerBlock) {
                        break;
                    }
                    bracesDepth++;
                } else if (ch == '}') {
                    bracesDepth--;
                }
                if (ch == ';' && genericsDepth <= 0 && parenthesesDepth <= 0 && bracesDepth <= 0) {
                    break;
                }
            }
            declaration.append(ch);
            if (shouldSkipNext(state, ch, next)) {
                i++;
                declaration.append(next);
            }
        }
        return declaration.toString().trim();
    }

    /**
     * 规范化声明文本，压缩换行和连续空白。
     *
     * @param declaration 原始声明文本
     * @return 单行声明文本
     */
    private String normalizeDeclaration(String declaration) {
        return declaration.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 判断声明是否为类、接口或枚举声明。
     *
     * @param declaration 规范化后的声明文本
     * @return 是类型声明时返回 {@code true}
     */
    private boolean isClassDeclaration(String declaration) {
        return CLASS_PATTERN.matcher(declaration).matches();
    }

    /**
     * 判断声明是否为方法或构造器声明。
     *
     * @param declaration 规范化后的声明文本
     * @return 是方法或构造器声明时返回 {@code true}
     */
    private boolean isMethodDeclaration(String declaration) {
        return declaration.contains("(") && declaration.contains(")") && !isClassDeclaration(declaration)
                && !isFieldDeclaration(declaration);
    }

    /**
     * 判断声明是否为字段声明。
     *
     * @param declaration 规范化后的声明文本
     * @return 是字段声明时返回 {@code true}
     */
    private boolean isFieldDeclaration(String declaration) {
        return !isClassDeclaration(declaration)
                && (findTopLevelChar(declaration, '=') >= 0 || !declaration.contains("("));
    }

    /**
     * 将类型声明和类型 Javadoc 写入类设计模型。
     *
     * @param doc 类设计文档模型
     * @param declaration 规范化后的类型声明文本
     * @param javadoc 类型 Javadoc 解析结果
     */
    private void fillClass(ClassDesignDoc doc, String declaration, ParsedJavadoc javadoc) {
        Matcher matcher = CLASS_PATTERN.matcher(declaration);
        if (!matcher.matches()) {
            return;
        }
        doc.setKind(matcher.group("kind"));
        doc.setClassName(matcher.group("name"));
        doc.setModifiers(extractModifiers(matcher.group("prefix")));
        doc.setDescription(javadoc.description);
        doc.setAuthor(javadoc.author);
        doc.setSince(javadoc.since);

    }

    /**
     * 将字段声明和字段 Javadoc 转换为字段设计模型。
     *
     * @param declaration 规范化后的字段声明文本
     * @param javadoc 字段 Javadoc 解析结果
     * @return 字段设计模型；无法识别时返回 {@code null}
     */
    private List<FieldDesignDoc> parseFields(String declaration, ParsedJavadoc javadoc) {
        List<FieldDesignDoc> fields = new ArrayList<>();
        List<String> declarators = splitTopLevel(declaration, ',');
        String baseType = "";
        String baseModifiers = "";
        for (int i = 0; i < declarators.size(); i++) {
            String declarator = declarators.get(i).trim();
            if (declarator.isEmpty()) {
                continue;
            }
            ParsedFieldDeclarator parsed = parseFieldDeclarator(declarator, i == 0, baseType, baseModifiers);
            if (parsed == null) {
                continue;
            }
            if (i == 0) {
                baseType = parsed.type;
                baseModifiers = parsed.modifiers;
            }
            FieldDesignDoc field = new FieldDesignDoc();
            field.setName(parsed.name);
            field.setType(parsed.type);
            field.setModifiers(parsed.modifiers);
            field.setDescription(javadoc.description);
            field.setDefaultValue(parsed.defaultValue);
            fields.add(field);
        }
        return fields;
    }

    /**
     * 解析字段声明中的单个变量声明片段。
     *
     * @param declarator 变量声明片段
     * @param first 是否为字段声明中的第一个变量
     * @param baseType 已解析出的基础字段类型
     * @param baseModifiers 已解析出的基础字段修饰符
     * @return 字段变量解析结果；无法解析时返回 {@code null}
     */
    private ParsedFieldDeclarator parseFieldDeclarator(String declarator, boolean first,
                                                       String baseType, String baseModifiers) {
        int assignIndex = findTopLevelChar(declarator, '=');
        String left = assignIndex >= 0 ? declarator.substring(0, assignIndex).trim() : declarator.trim();
        String defaultValue = assignIndex >= 0 ? declarator.substring(assignIndex + 1).trim() : "";
        if (first) {
            int nameSplitIndex = left.lastIndexOf(' ');
            if (nameSplitIndex < 0) {
                return null;
            }
            String prefixAndType = left.substring(0, nameSplitIndex).trim();
            String name = left.substring(nameSplitIndex + 1).trim();
            String modifiers = extractModifiers(prefixAndType);
            String type = removeLeadingModifiers(prefixAndType);
            return new ParsedFieldDeclarator(name, type, modifiers, defaultValue);
        }
        if (baseType.isEmpty()) {
            return null;
        }
        return new ParsedFieldDeclarator(left, baseType, baseModifiers, defaultValue);
    }

    /**
     * 将方法或构造器声明转换为方法设计模型。
     *
     * @param declaration 规范化后的方法声明文本
     * @param javadoc 方法 Javadoc 解析结果
     * @param className 当前类简单名称，用于识别构造器
     * @return 方法设计模型；无法识别时返回 {@code null}
     */
    private MethodDesignDoc parseMethod(String declaration, ParsedJavadoc javadoc, String className) {
        int parametersStart = declaration.indexOf('(');
        int parametersEnd = declaration.lastIndexOf(')');
        if (parametersStart < 0 || parametersEnd < parametersStart) {
            return null;
        }

        String left = declaration.substring(0, parametersStart).trim();
        String parametersText = declaration.substring(parametersStart + 1, parametersEnd).trim();
        String throwsText = "";
        String afterParameters = declaration.substring(parametersEnd + 1).trim();
        Matcher throwsMatcher = THROWS_PATTERN.matcher(afterParameters);
        if (throwsMatcher.find()) {
            throwsText = throwsMatcher.group(1).trim();
        }

        int nameSplitIndex = left.lastIndexOf(' ');
        if (nameSplitIndex < 0) {
            return null;
        }
        String methodName = left.substring(nameSplitIndex + 1).trim();
        String prefixAndReturn = left.substring(0, nameSplitIndex).trim();
        String modifiers = extractModifiers(prefixAndReturn);
        String returnType = removeLeadingModifiers(prefixAndReturn);
        if (methodName.equals(className)) {
            returnType = "";
        }

        MethodDesignDoc method = new MethodDesignDoc();
        method.setName(methodName);
        method.setReturnType(returnType);
        method.setModifiers(modifiers);
        method.setDescription(javadoc.description);
        method.setReturnDescription(javadoc.returnDescription);
        for (ParameterDesignDoc parameter : parseParameters(parametersText, javadoc.paramDescriptions)) {
            method.addParameter(parameter);
        }
        for (String throwsType : splitTopLevel(throwsText, ',')) {
            String type = throwsType.trim();
            if (!type.isEmpty()) {
                ThrowsDesignDoc throwsDoc = new ThrowsDesignDoc();
                throwsDoc.setType(type);
                throwsDoc.setDescription(javadoc.throwsDescriptions.get(type));
                method.addThrows(throwsDoc);
            }
        }
        return method;
    }

    /**
     * 解析方法参数列表，并绑定 {@code @param} 说明。
     *
     * @param parametersText 方法括号内的参数文本
     * @param descriptions 参数名到参数说明的映射
     * @return 参数设计模型列表
     */
    private List<ParameterDesignDoc> parseParameters(String parametersText, Map<String, String> descriptions) {
        List<ParameterDesignDoc> parameters = new ArrayList<>();
        if (parametersText.isEmpty()) {
            return parameters;
        }
        for (String rawParameter : splitTopLevel(parametersText, ',')) {
            String parameterText = rawParameter.trim()
                    .replaceAll("@\\w+(\\([^)]*\\))?\\s*", "")
                    .replaceAll("\\bfinal\\s+", "")
                    .trim();
            int splitIndex = parameterText.lastIndexOf(' ');
            if (splitIndex < 0) {
                continue;
            }
            String type = parameterText.substring(0, splitIndex).trim();
            String name = parameterText.substring(splitIndex + 1).trim();
            ParameterDesignDoc parameter = new ParameterDesignDoc();
            parameter.setName(name);
            parameter.setType(type);
            parameter.setDescription(descriptions.get(name));
            parameters.add(parameter);
        }
        return parameters;
    }

    /**
     * 解析原始 Javadoc 文本。
     *
     * <p>解析结果包含正文描述、{@code @param}、{@code @return}、{@code @throws}、
     * {@code @author} 和 {@code @since} 等常用标签。</p>
     *
     * @param rawJavadoc 原始 Javadoc 文本
     * @return Javadoc 结构化解析结果
     */
    private ParsedJavadoc parseJavadoc(String rawJavadoc) {
        ParsedJavadoc parsed = new ParsedJavadoc();
        String currentTag = "";
        String currentName = "";
        for (String line : cleanJavadocLines(rawJavadoc)) {
            if (line.startsWith("@")) {
                String[] parts = line.split("\\s+", 3);
                currentTag = parts[0];
                currentName = parts.length > 1 ? parts[1] : "";
                String text = parts.length > 2 ? parts[2] : "";
                applyTag(parsed, currentTag, currentName, text);
            } else if (!line.isEmpty()) {
                appendContinuation(parsed, currentTag, currentName, line);
            }
        }
        parsed.description = parsed.description.trim();
        return parsed;
    }

    /**
     * 清理 Javadoc 行前缀。
     *
     * @param rawJavadoc 原始 Javadoc 文本
     * @return 去掉 {@code /**}、{@code *&#47;} 和行首星号后的文本行
     */
    private List<String> cleanJavadocLines(String rawJavadoc) {
        String body = rawJavadoc.replace("/**", "").replace("*/", "");
        String[] lines = body.split("\\R");
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            cleaned.add(line.replaceFirst("^\\s*\\*\\s?", "").trim());
        }
        return cleaned;
    }

    /**
     * 处理一行新的 Javadoc 标签。
     *
     * @param parsed Javadoc 解析结果
     * @param tag 标签名称，例如 {@code @param}
     * @param name 标签关联名称，例如参数名或异常类型
     * @param text 标签说明文本
     */
    private void applyTag(ParsedJavadoc parsed, String tag, String name, String text) {
        if ("@param".equals(tag)) {
            parsed.paramDescriptions.put(name, text);
        } else if ("@return".equals(tag)) {
            parsed.returnDescription = join(parsed.returnDescription, name + (text.isEmpty() ? "" : " " + text));
        } else if ("@throws".equals(tag) || "@exception".equals(tag)) {
            parsed.throwsDescriptions.put(name, text);
        } else if ("@author".equals(tag)) {
            parsed.author = join(parsed.author, name + (text.isEmpty() ? "" : " " + text));
        } else if ("@since".equals(tag)) {
            parsed.since = join(parsed.since, name + (text.isEmpty() ? "" : " " + text));
        }
    }

    /**
     * 处理 Javadoc 多行说明的续行。
     *
     * @param parsed Javadoc 解析结果
     * @param tag 当前续写的标签；空字符串表示正文描述
     * @param name 当前标签关联名称
     * @param line 当前续行文本
     */
    private void appendContinuation(ParsedJavadoc parsed, String tag, String name, String line) {
        if (tag.isEmpty()) {
            parsed.description = join(parsed.description, line);
        } else if ("@param".equals(tag)) {
            parsed.paramDescriptions.put(name, join(parsed.paramDescriptions.get(name), line));
        } else if ("@return".equals(tag)) {
            parsed.returnDescription = join(parsed.returnDescription, line);
        } else if ("@throws".equals(tag) || "@exception".equals(tag)) {
            parsed.throwsDescriptions.put(name, join(parsed.throwsDescriptions.get(name), line));
        } else if ("@author".equals(tag)) {
            parsed.author = join(parsed.author, line);
        } else if ("@since".equals(tag)) {
            parsed.since = join(parsed.since, line);
        }
    }

    /**
     * 合并两段说明文本。
     *
     * @param left 已有文本
     * @param right 新增文本
     * @return 以单个空格拼接后的文本
     */
    private String join(String left, String right) {
        if (right == null || right.trim().isEmpty()) {
            return left == null ? "" : left;
        }
        if (left == null || left.trim().isEmpty()) {
            return right.trim();
        }
        return left.trim() + " " + right.trim();
    }

    /**
     * 从声明前缀中提取 Java 修饰符。
     *
     * @param text 声明前缀文本
     * @return 以空格分隔的修饰符文本
     */
    private String extractModifiers(String text) {
        StringJoiner joiner = new StringJoiner(" ");
        for (String token : text.trim().split("\\s+")) {
            if (isModifier(token)) {
                joiner.add(token);
            }
        }
        return joiner.toString();
    }

    /**
     * 移除声明前缀中的 Java 修饰符，保留类型部分。
     *
     * @param text 声明前缀文本
     * @return 移除修饰符后的文本
     */
    private String removeLeadingModifiers(String text) {
        String result = text.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String modifier : new String[]{"public", "protected", "private", "static", "final", "abstract",
                    "synchronized", "native", "default", "transient", "volatile", "strictfp"}) {
                String prefix = modifier + " ";
                if (result.startsWith(prefix)) {
                    result = result.substring(prefix.length()).trim();
                    changed = true;
                }
            }
        }
        return result;
    }

    /**
     * 判断 token 是否为 Java 修饰符。
     *
     * @param token 待判断 token
     * @return 是 Java 修饰符时返回 {@code true}
     */
    private boolean isModifier(String token) {
        return "public".equals(token) || "protected".equals(token) || "private".equals(token)
                || "static".equals(token) || "final".equals(token) || "abstract".equals(token)
                || "synchronized".equals(token) || "native".equals(token) || "default".equals(token)
                || "transient".equals(token) || "volatile".equals(token) || "strictfp".equals(token);
    }

    /**
     * 根据访问修饰符和配置判断成员是否应写入设计表格。
     *
     * @param modifiers 成员修饰符
     * @param options 类设计表格选项
     * @return 应包含该成员时返回 {@code true}
     */
    private boolean includeMember(String modifiers, ClassDesignTableOptions options) {
        return options.isIncludePrivate() || modifiers == null || !modifiers.contains("private");
    }

    /**
     * 根据配置判断方法是否应写入设计表格。
     *
     * @param method 方法设计模型
     * @param options 类设计表格选项
     * @return 应包含该方法时返回 {@code true}
     */
    private boolean includeMethod(MethodDesignDoc method, ClassDesignTableOptions options) {
        if (options.isIncludeGetterSetter()) {
            return true;
        }
        int parameterCount = method.getParameters().size();
        String name = method.getName();
        return !((name.startsWith("get") && parameterCount == 0)
                || (name.startsWith("is") && parameterCount == 0)
                || (name.startsWith("set") && parameterCount == 1));
    }

    /**
     * 从类全限定名中提取简单类名。
     *
     * @param className 类全限定名或简单类名
     * @return 简单类名
     */
    private String simpleClassName(String className) {
        int index = className.lastIndexOf('.');
        return index < 0 ? className : className.substring(index + 1);
    }

    /**
     * 按顶层分隔符拆分文本。
     *
     * <p>该方法会跟踪泛型尖括号和方法参数括号，避免拆分
     * {@code Map<String, List<User>>} 或注解参数中的逗号。</p>
     *
     * @param text 待拆分文本
     * @param delimiter 分隔符
     * @return 拆分后的文本片段列表
     */
    private List<String> splitTopLevel(String text, char delimiter) {
        List<String> values = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return values;
        }
        StringBuilder current = new StringBuilder();
        int genericsDepth = 0;
        int parenthesesDepth = 0;
        int bracesDepth = 0;
        LexicalState state = LexicalState.CODE;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            state = nextState(text, state, i, next);
            if (state == LexicalState.CODE) {
                if (ch == '<') {
                    genericsDepth++;
                } else if (ch == '>') {
                    genericsDepth--;
                } else if (ch == '(') {
                    parenthesesDepth++;
                } else if (ch == ')') {
                    parenthesesDepth--;
                } else if (ch == '{') {
                    bracesDepth++;
                } else if (ch == '}') {
                    bracesDepth--;
                }
            }
            if (ch == delimiter && state == LexicalState.CODE
                    && genericsDepth <= 0 && parenthesesDepth <= 0 && bracesDepth <= 0) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
            if (shouldSkipNext(state, ch, next)) {
                i++;
                current.append(next);
            }
        }
        values.add(current.toString());
        return values;
    }

    /**
     * 查找指定顶层字符的位置。
     *
     * @param text 待扫描文本
     * @param target 目标字符
     * @return 目标字符的顶层偏移量；未找到时返回 {@code -1}
     */
    private int findTopLevelChar(String text, char target) {
        int genericsDepth = 0;
        int parenthesesDepth = 0;
        int bracesDepth = 0;
        LexicalState state = LexicalState.CODE;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            state = nextState(text, state, i, next);
            if (state == LexicalState.CODE) {
                if (ch == '<') {
                    genericsDepth++;
                } else if (ch == '>') {
                    genericsDepth--;
                } else if (ch == '(') {
                    parenthesesDepth++;
                } else if (ch == ')') {
                    parenthesesDepth--;
                } else if (ch == '{') {
                    bracesDepth++;
                } else if (ch == '}') {
                    bracesDepth--;
                }
                if (ch == target && genericsDepth <= 0 && parenthesesDepth <= 0 && bracesDepth <= 0) {
                    return i;
                }
            }
            if (shouldSkipNext(state, ch, next)) {
                i++;
            }
        }
        return -1;
    }

    /**
     * 字段声明片段解析结果。
     */
    private static class ParsedFieldDeclarator {

        /** 字段名称。 */
        private final String name;
        /** 字段类型。 */
        private final String type;
        /** 字段修饰符。 */
        private final String modifiers;
        /** 字段默认值表达式。 */
        private final String defaultValue;

        /**
         * 创建字段声明片段解析结果。
         *
         * @param name 字段名
         * @param type 字段类型
         * @param modifiers 字段修饰符
         * @param defaultValue 字段默认值表达式
         */
        private ParsedFieldDeclarator(String name, String type, String modifiers, String defaultValue) {
            this.name = name;
            this.type = type;
            this.modifiers = modifiers;
            this.defaultValue = defaultValue;
        }
    }

    /**
     * Javadoc 与其后声明的配对结果。
     */
    private static class JavadocDeclaration {

        /** Javadoc 在源码中的起始偏移量。 */
        private final int javadocStart;
        /** 关联声明在源码中的起始偏移量。 */
        private final int declarationStart;
        /** 原始 Javadoc 内容。 */
        private final String javadoc;
        /** 与 Javadoc 配对的声明内容。 */
        private final String declaration;

        /**
         * 创建 Javadoc 与声明的配对结果。
         *
         * @param javadocStart Javadoc 起始偏移量
         * @param declarationStart 声明起始偏移量
         * @param javadoc 原始 Javadoc 文本
         * @param declaration 声明文本
         */
        private JavadocDeclaration(int javadocStart, int declarationStart, String javadoc, String declaration) {
            this.javadocStart = javadocStart;
            this.declarationStart = declarationStart;
            this.javadoc = javadoc;
            this.declaration = declaration;
        }
    }

    /**
     * 源码扫描时用于忽略注释和字符串的大括号状态。
     */
    private enum LexicalState {

        /**
         * 普通 Java 代码区域。
         */
        CODE,

        /**
         * 行注释区域。
         */
        LINE_COMMENT,

        /**
         * 块注释或 Javadoc 区域。
         */
        BLOCK_COMMENT,

        /**
         * 字符串字面量区域。
         */
        STRING,

        /**
         * 字符字面量区域。
         */
        CHARACTER
    }

    /**
     * Javadoc 结构化解析结果。
     */
    private static class ParsedJavadoc {

        /** Javadoc 主描述。 */
        private String description = "";
        /** {@code @return} 标签描述。 */
        private String returnDescription = "";
        /** {@code @author} 标签内容。 */
        private String author = "";
        /** {@code @since} 标签内容。 */
        private String since = "";
        private final Map<String, String> paramDescriptions = new LinkedHashMap<>();
        private final Map<String, String> throwsDescriptions = new LinkedHashMap<>();
    }
}
