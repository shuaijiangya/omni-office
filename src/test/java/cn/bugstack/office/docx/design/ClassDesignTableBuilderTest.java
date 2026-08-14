package cn.bugstack.office.docx.design;

import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.model.DocxBlock;
import cn.bugstack.office.docx.model.ParagraphNode;
import cn.bugstack.office.docx.model.SectionNode;
import cn.bugstack.office.docx.model.TableCellNode;
import cn.bugstack.office.docx.model.TableNode;
import cn.bugstack.office.docx.model.TextRunInline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ClassDesignTableBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void addsHeadingAndClassDesignTableForSpecifiedClass() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packageDir = sourceRoot.resolve("cn/bugstack/demo");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("UserService.java"), ""
                + "package cn.bugstack.demo;\n"
                + "\n"
                + "/** 用户服务类。 */\n"
                + "public class UserService extends BaseService implements UserApi {\n"
                + "    /** 用户编码。 */\n"
                + "    private String userCode;\n"
                + "\n"
                + "    /**\n"
                + "     * 创建用户。\n"
                + "     * @param request 用户创建请求\n"
                + "     * @return 创建后的用户信息\n"
                + "     */\n"
                + "    public UserDTO createUser(UserCreateRequest request) {\n"
                + "        return null;\n"
                + "    }\n"
                + "\n"
                + "    /** 内部处理器。 */\n"
                + "    private static class InnerProcessor {\n"
                + "        /** 不应写入当前类方法表。 */\n"
                + "        public void inheritedLikeMethod() {\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        DocxDocument document = DocxDocument.create()
                .section()
                .classDesignTable("UserService 类设计", config -> config
                        .sourceRoot(sourceRoot)
                        .className("cn.bugstack.demo.UserService")
                        .includePrivate(true))
                .end();

        SectionNode section = document.getNode().getSections().get(0);
        assertEquals(6, section.getBlocks().size());
        assertInstanceOf(ParagraphNode.class, section.getBlocks().get(0));
        assertInstanceOf(TableNode.class, section.getBlocks().get(1));
        assertInstanceOf(ParagraphNode.class, section.getBlocks().get(2));
        assertInstanceOf(TableNode.class, section.getBlocks().get(3));
        assertInstanceOf(ParagraphNode.class, section.getBlocks().get(4));
        assertInstanceOf(TableNode.class, section.getBlocks().get(5));

        ParagraphNode heading = (ParagraphNode) section.getBlocks().get(0);
        assertEquals("Heading2", heading.getStyleName());
        assertEquals("UserService 类设计", paragraphText(heading));

        TableNode classInfoTable = (TableNode) section.getBlocks().get(1);
        assertEquals("项目", cellText(classInfoTable, 0, 0));
        assertEquals("类名", cellText(classInfoTable, 1, 0));
        assertEquals("UserService", cellText(classInfoTable, 1, 1));
        assertEquals("类型", cellText(classInfoTable, 2, 0));
        assertEquals("class", cellText(classInfoTable, 2, 1));
        assertEquals("说明", cellText(classInfoTable, 4, 0));
        assertEquals("用户服务类。", cellText(classInfoTable, 4, 1));

        ParagraphNode propertiesHeading = (ParagraphNode) section.getBlocks().get(2);
        assertEquals("Heading3", propertiesHeading.getStyleName());
        assertEquals("属性说明", paragraphText(propertiesHeading));

        TableNode propertiesTable = (TableNode) section.getBlocks().get(3);
        assertEquals("序号", cellText(propertiesTable, 0, 0));
        assertEquals("属性名", cellText(propertiesTable, 0, 1));
        assertEquals("1", cellText(propertiesTable, 1, 0));
        assertEquals("userCode", cellText(propertiesTable, 1, 1));
        assertEquals("String", cellText(propertiesTable, 1, 2));
        assertEquals("private", cellText(propertiesTable, 1, 3));
        assertEquals("用户编码。", cellText(propertiesTable, 1, 4));

        ParagraphNode methodsHeading = (ParagraphNode) section.getBlocks().get(4);
        assertEquals("Heading3", methodsHeading.getStyleName());
        assertEquals("方法说明", paragraphText(methodsHeading));

        TableNode methodsTable = (TableNode) section.getBlocks().get(5);
        assertEquals("序号", cellText(methodsTable, 0, 0));
        assertEquals("方法名", cellText(methodsTable, 0, 1));
        assertEquals("1", cellText(methodsTable, 1, 0));
        assertEquals("createUser", cellText(methodsTable, 1, 1));
        assertEquals("UserDTO", cellText(methodsTable, 1, 2));
        assertEquals("request: UserCreateRequest - 用户创建请求", cellText(methodsTable, 1, 3));
        assertEquals("创建用户。 返回：创建后的用户信息", cellText(methodsTable, 1, 5));
    }

    /**
     * 获取指定表格单元格中的段落文本。
     *
     * @param table 表格节点
     * @param rowIndex 行索引
     * @param cellIndex 单元格索引
     * @return 单元格文本
     */
    private String cellText(TableNode table, int rowIndex, int cellIndex) {
        TableCellNode cell = table.getRows().get(rowIndex).getCells().get(cellIndex);
        DocxBlock block = cell.getBlocks().get(0);
        return paragraphText((ParagraphNode) block);
    }

    /**
     * 拼接段落中全部文本运行的内容。
     *
     * @param paragraph 段落节点
     * @return 段落文本
     */
    private String paragraphText(ParagraphNode paragraph) {
        StringBuilder text = new StringBuilder();
        paragraph.getInlines().forEach(inline -> {
            if (inline instanceof TextRunInline) {
                text.append(((TextRunInline) inline).getText());
            }
        });
        return text.toString();
    }
}
