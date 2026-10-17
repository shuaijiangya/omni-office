package cn.bugstack.application.csci;

import com.aspose.words.Document;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 以用户模板和示例源码 ZIP 直接生成可交付的 CSCI 详细设计文档。
 *
 * <p>运行时传入 {@code csci.template} 和 {@code csci.source.zip}；
 * 可选传入 {@code csci.output} 指定最终文件，或传入
 * {@code csci.ollama.model} 补全源码中未明确给出的算法和注意事项。未配置输入时跳过，
 * 以免普通测试环境依赖本机文件。</p>
 */
class CsciDetailedDesignFinalExampleTest {

    /**
     * 验证 Java 生成流程无需 Python 后处理即可清理 Javadoc 标记和空尾段。
     *
     * @throws Exception 输入读取、文档生成或校验失败时抛出
     */
    @Test
    void generatesFinalDocumentWithoutPythonPostProcessing() throws Exception {
        String templateProperty = System.getProperty("csci.template");
        String archiveProperty = System.getProperty("csci.source.zip");
        assumeTrue(templateProperty != null && archiveProperty != null,
                "需传入 -Dcsci.template 和 -Dcsci.source.zip");
        Path template = Path.of(templateProperty);
        Path archive = Path.of(archiveProperty);
        assumeTrue(Files.isRegularFile(template) && Files.isRegularFile(archive),
                "模板 DOCX 与示例源码 ZIP 必须存在");
        Path output = Path.of(System.getProperty("csci.output",
                "target/csci-final-test/CSCI详细设计-最终验证.docx")).toAbsolutePath();
        Files.createDirectories(output.getParent());

        byte[] templateHash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(template));
        String model = System.getProperty("csci.ollama.model");
        String[] arguments = model == null || model.isBlank()
                ? new String[]{template.toString(), archive.toString(), output.toString()}
                : new String[]{template.toString(), archive.toString(), output.toString(), model};
        CsciDetailedDesignExample.main(arguments);

        assertTrue(Files.size(output) > 20_000);
        assertArrayEquals(templateHash,
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(template)));
        Document document = new Document(output.toString());
        assertFalse(document.getText().contains("{@code null}"));
        assertTrue(document.getText().contains("空值"));
        assertTrue(Files.isRegularFile(output.getParent().resolve("csci-diagrams/feature-001-class-01.vsdx")));
        int editableDiagrams = 0;
        for (Object node : document.getChildNodes(NodeType.SHAPE, true)) {
            if (((Shape) node).getOleFormat() != null) editableDiagrams++;
        }
        assertTrue(editableDiagrams >= 1);
        assertEndsWithTableWithoutEmptyParagraph(output);
        System.out.println("最终文档: " + output);
    }

    /**
     * 从 OOXML 检查正文末尾是表格，而不是可能形成空白页的空段落。
     *
     * @param docx 待检查的 Word 文件
     * @throws Exception DOCX 包或 XML 读取失败时抛出
     */
    private static void assertEndsWithTableWithoutEmptyParagraph(Path docx) throws Exception {
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.Document xml = factory.newDocumentBuilder().parse(
                    zip.getInputStream(zip.getEntry("word/document.xml")));
            String namespace = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            Element body = (Element) xml.getElementsByTagNameNS(namespace, "body").item(0);
            NodeList children = body.getChildNodes();
            Element lastContent = null;
            for (int index = 0; index < children.getLength(); index++) {
                Node child = children.item(index);
                if (child instanceof Element && !"sectPr".equals(child.getLocalName())) {
                    lastContent = (Element) child;
                }
            }
            assertTrue(lastContent != null);
            assertEquals("tbl", lastContent.getLocalName(), "正文末尾不应保留空段落");
        }
    }
}
