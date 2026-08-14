package cn.bugstack.office.docx.design;

import cn.bugstack.office.docx.design.model.ClassDesignDoc;
import cn.bugstack.office.docx.design.model.FieldDesignDoc;
import cn.bugstack.office.docx.design.model.MethodDesignDoc;
import cn.bugstack.office.docx.design.parser.SourceClassMetadataParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceClassMetadataParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesClassFieldsMethodsAndJavadocTagsFromSourceFile() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packageDir = sourceRoot.resolve("cn/bugstack/demo");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("UserService.java"), ""
                + "package cn.bugstack.demo;\n"
                + "\n"
                + "/**\n"
                + " * 用户服务类。\n"
                + " *\n"
                + " * @author luojiang\n"
                + " * @since 1.0\n"
                + " */\n"
                + "public class UserService extends BaseService implements UserApi {\n"
                + "\n"
                + "    /** 用户仓储。 */\n"
                + "    private final UserRepository userRepository;\n"
                + "\n"
                + "    /** 临时缓存。 */\n"
                + "    private String cacheName;\n"
                + "\n"
                + "    private String noCommentCode;\n"
                + "\n"
                + "    /**\n"
                + "     * 创建用户。\n"
                + "     *\n"
                + "     * @param request 用户创建请求\n"
                + "     * @return 创建后的用户信息\n"
                + "     * @throws BusinessException 用户已存在时抛出\n"
                + "     */\n"
                + "    public UserDTO createUser(UserCreateRequest request) throws BusinessException {\n"
                + "        return null;\n"
                + "    }\n"
                + "\n"
                + "    /** 获取缓存名称。 */\n"
                + "    public String getCacheName() {\n"
                + "        return cacheName;\n"
                + "    }\n"
                + "\n"
                + "    /** 内部处理器。 */\n"
                + "    private static class InnerProcessor {\n"
                + "        /** 不应出现在当前类方法列表中。 */\n"
                + "        public void inheritedLikeMethod() {\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        ClassDesignTableOptions options = ClassDesignTableOptions.create()
                .sourceRoot(sourceRoot)
                .className("cn.bugstack.demo.UserService")
                .includePrivate(false)
                .includeGetterSetter(false);

        ClassDesignDoc doc = new SourceClassMetadataParser().parse(options);

        assertEquals("cn.bugstack.demo", doc.getPackageName());
        assertEquals("UserService", doc.getClassName());
        assertEquals("用户服务类。", doc.getDescription());
        assertEquals("luojiang", doc.getAuthor());
        assertEquals("1.0", doc.getSince());
        assertEquals("", doc.getSuperClass());
        assertEquals(0, doc.getInterfaces().size());
        assertEquals(0, doc.getFields().size());
        assertEquals(1, doc.getMethods().size());

        MethodDesignDoc method = doc.getMethods().get(0);
        assertEquals("createUser", method.getName());
        assertEquals("UserDTO", method.getReturnType());
        assertEquals("创建用户。", method.getDescription());
        assertEquals("用户创建请求", method.getParameters().get(0).getDescription());
        assertEquals("创建后的用户信息", method.getReturnDescription());
        assertEquals("BusinessException", method.getThrowsList().get(0).getType());
    }

    @Test
    void parsesPrivateFieldWithoutJavadocWhenPrivateMembersAreIncluded() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packageDir = sourceRoot.resolve("cn/bugstack/demo");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("ImageSourceHolder.java"), ""
                + "package cn.bugstack.demo;\n"
                + "\n"
                + "/** 图片来源持有者。 */\n"
                + "public class ImageSourceHolder {\n"
                + "    private final Path path;\n"
                + "\n"
                + "    /** 获取图片文件路径。 */\n"
                + "    public Path getPath() {\n"
                + "        return path;\n"
                + "    }\n"
                + "}\n");

        ClassDesignTableOptions options = ClassDesignTableOptions.create()
                .sourceRoot(sourceRoot)
                .className("cn.bugstack.demo.ImageSourceHolder")
                .includePrivate(true)
                .includeGetterSetter(true);

        ClassDesignDoc doc = new SourceClassMetadataParser().parse(options);

        assertEquals(1, doc.getFields().size());
        assertEquals("path", doc.getFields().get(0).getName());
        assertEquals("Path", doc.getFields().get(0).getType());
        assertEquals("private final", doc.getFields().get(0).getModifiers());
        assertEquals("", doc.getFields().get(0).getDescription());
        assertEquals("getPath", doc.getMethods().get(0).getName());
    }

    @Test
    void parsesOnlyTargetClassMembersWhenTargetClassHasNoJavadoc() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packageDir = sourceRoot.resolve("cn/bugstack/demo");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("PlainService.java"), ""
                + "package cn.bugstack.demo;\n"
                + "\n"
                + "public class PlainService {\n"
                + "    /** 名称。 */\n"
                + "    private String name;\n"
                + "\n"
                + "    /** 执行业务。 */\n"
                + "    public void execute() {\n"
                + "    }\n"
                + "\n"
                + "    /** 内部处理器。 */\n"
                + "    private static class InnerProcessor {\n"
                + "        /** 不应出现在当前类方法列表中。 */\n"
                + "        public void leak() {\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        ClassDesignTableOptions options = ClassDesignTableOptions.create()
                .sourceRoot(sourceRoot)
                .className("cn.bugstack.demo.PlainService")
                .includePrivate(true)
                .includeGetterSetter(true);

        ClassDesignDoc doc = new SourceClassMetadataParser().parse(options);

        assertEquals("PlainService", doc.getClassName());
        assertEquals("class", doc.getKind());
        assertEquals(1, doc.getFields().size());
        assertEquals("name", doc.getFields().get(0).getName());
        assertEquals("名称。", doc.getFields().get(0).getDescription());
        assertEquals(1, doc.getMethods().size());
        assertEquals("execute", doc.getMethods().get(0).getName());
    }

    @Test
    void ignoresEscapedQuotesAndBracesInsideStringLiterals() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packageDir = sourceRoot.resolve("cn/bugstack/demo");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("LiteralHolder.java"), ""
                + "package cn.bugstack.demo;\n"
                + "\n"
                + "/** 字面量持有者。 */\n"
                + "public class LiteralHolder {\n"
                + "    private String pattern = \"\\\"{\";\n"
                + "\n"
                + "    /** 获取模式。 */\n"
                + "    public String getPattern() {\n"
                + "        return pattern;\n"
                + "    }\n"
                + "}\n");

        ClassDesignTableOptions options = ClassDesignTableOptions.create()
                .sourceRoot(sourceRoot)
                .className("cn.bugstack.demo.LiteralHolder")
                .includePrivate(true)
                .includeGetterSetter(true);

        ClassDesignDoc doc = new SourceClassMetadataParser().parse(options);

        assertEquals("LiteralHolder", doc.getClassName());
        assertEquals(1, doc.getFields().size());
        assertEquals("pattern", doc.getFields().get(0).getName());
        assertEquals("\"\\\"{\"", doc.getFields().get(0).getDefaultValue());
        assertEquals(1, doc.getMethods().size());
        assertEquals("getPattern", doc.getMethods().get(0).getName());
    }

    @Test
    void parsesFieldsWithBlockInitializersAndMultipleDeclarators() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packageDir = sourceRoot.resolve("cn/bugstack/demo");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("ComplexFields.java"), ""
                + "package cn.bugstack.demo;\n"
                + "\n"
                + "/** 复杂字段示例。 */\n"
                + "public class ComplexFields {\n"
                + "    private Runnable runner = () -> { System.out.println(\"run\"); };\n"
                + "    private int first, second;\n"
                + "}\n");

        ClassDesignTableOptions options = ClassDesignTableOptions.create()
                .sourceRoot(sourceRoot)
                .className("cn.bugstack.demo.ComplexFields")
                .includePrivate(true)
                .includeGetterSetter(true);

        ClassDesignDoc doc = new SourceClassMetadataParser().parse(options);

        List<FieldDesignDoc> fields = doc.getFields();
        assertEquals(3, fields.size());
        assertEquals("runner", fields.get(0).getName());
        assertEquals("Runnable", fields.get(0).getType());
        assertEquals("() -> { System.out.println(\"run\"); }", fields.get(0).getDefaultValue());
        assertEquals("first", fields.get(1).getName());
        assertEquals("int", fields.get(1).getType());
        assertEquals("second", fields.get(2).getName());
        assertEquals("int", fields.get(2).getType());
    }

    @Test
    void parsesJavadocFieldWithBlockInitializer() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packageDir = sourceRoot.resolve("cn/bugstack/demo");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("DocumentedComplexField.java"), ""
                + "package cn.bugstack.demo;\n"
                + "\n"
                + "/** 复杂字段示例。 */\n"
                + "public class DocumentedComplexField {\n"
                + "    /** 执行器。 */\n"
                + "    private Runnable runner = () -> { System.out.println(\"run\"); };\n"
                + "}\n");

        ClassDesignTableOptions options = ClassDesignTableOptions.create()
                .sourceRoot(sourceRoot)
                .className("cn.bugstack.demo.DocumentedComplexField")
                .includePrivate(true)
                .includeGetterSetter(true);

        ClassDesignDoc doc = new SourceClassMetadataParser().parse(options);

        assertEquals(1, doc.getFields().size());
        assertEquals("runner", doc.getFields().get(0).getName());
        assertEquals("Runnable", doc.getFields().get(0).getType());
        assertEquals("执行器。", doc.getFields().get(0).getDescription());
        assertEquals("() -> { System.out.println(\"run\"); }", doc.getFields().get(0).getDefaultValue());
    }
}
