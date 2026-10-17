package cn.bugstack.application.csci;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 在 Aspose 保存后对 CSCI DOCX 做最小范围的包级收尾。
 *
 * <p>Aspose 会在以表格结尾的正文后自动补一个空段落；该段落可能形成空白末页。
 * 仅在最后一个正文元素为真正空段落且其前一个元素为表格时移除它。</p>
 */
final class CsciDocxFinalizer {

    /** Word 正文 XML 在 DOCX 包内的固定路径。 */
    private static final String DOCUMENT_XML = "word/document.xml";
    /** WordprocessingML 命名空间。 */
    private static final String WORD_NAMESPACE =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /** 工具类不允许实例化。 */
    private CsciDocxFinalizer() {
    }

    /**
     * 删除 Aspose 序列化时补入的表格后空尾段，其他包部件内容保持不变。
     *
     * @param docx 已保存的 DOCX 文件
     * @throws Exception DOCX 读取、XML 处理或文件替换失败时抛出
     */
    static void removeTrailingEmptyParagraph(Path docx) throws Exception {
        Path temporary = null;
        try {
            try (ZipFile source = new ZipFile(docx.toFile())) {
                ZipEntry documentEntry = source.getEntry(DOCUMENT_XML);
                if (documentEntry == null) throw new IOException("DOCX 缺少 " + DOCUMENT_XML);
                byte[] original;
                try (InputStream input = source.getInputStream(documentEntry)) {
                    original = input.readAllBytes();
                }
                byte[] updated = withoutTrailingEmptyParagraph(original);
                if (updated == original) return;

                temporary = Files.createTempFile(docx.toAbsolutePath().getParent(), "csci-docx-", ".docx");
                try (ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(temporary))) {
                    Enumeration<? extends ZipEntry> entries = source.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        byte[] content;
                        if (DOCUMENT_XML.equals(entry.getName())) content = updated;
                        else {
                            try (InputStream input = source.getInputStream(entry)) {
                                content = input.readAllBytes();
                            }
                        }
                        ZipEntry copy = new ZipEntry(entry.getName());
                        copy.setTime(entry.getTime());
                        target.putNextEntry(copy);
                        target.write(content);
                        target.closeEntry();
                    }
                }
            }
            try {
                Files.move(temporary, docx, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, docx, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    /**
     * 仅在末尾满足“表格、空段落、节属性”的结构时清理正文 XML。
     *
     * @param original 原始 {@code word/document.xml} 字节
     * @return 修改后的字节；无需修改时返回原数组
     * @throws Exception XML 无法安全解析或序列化时抛出
     */
    private static byte[] withoutTrailingEmptyParagraph(byte[] original) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        org.w3c.dom.Document xml = factory.newDocumentBuilder().parse(new ByteArrayInputStream(original));
        Element body = (Element) xml.getElementsByTagNameNS(WORD_NAMESPACE, "body").item(0);
        if (body == null) throw new IOException("DOCX 缺少 Word 正文");
        Element last = null;
        Element previous = null;
        NodeList children = body.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element && !isWordElement(node, "sectPr")) {
                previous = last;
                last = (Element) node;
            }
        }
        if (!isWordElement(last, "p") || !isWordElement(previous, "tbl") || last.hasChildNodes()) {
            return original;
        }
        body.removeChild(last);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        ByteArrayOutputStream output = new ByteArrayOutputStream(original.length);
        transformer.transform(new DOMSource(xml), new StreamResult(output));
        return output.toByteArray();
    }

    /**
     * 判断节点是否为指定的 WordprocessingML 元素。
     *
     * @param node 待检查的节点
     * @param localName 元素本地名称
     * @return 命名空间和名称均相符时返回 {@code true}
     */
    private static boolean isWordElement(Node node, String localName) {
        return node != null && WORD_NAMESPACE.equals(node.getNamespaceURI())
                && localName.equals(node.getLocalName());
    }
}
