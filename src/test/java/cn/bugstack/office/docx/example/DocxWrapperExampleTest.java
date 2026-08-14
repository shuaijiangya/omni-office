package cn.bugstack.office.docx.example;

import com.aspose.words.Document;
import com.aspose.words.Paragraph;
import com.aspose.words.Section;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxWrapperExampleTest {

    @Test
    void exampleExportsPrivatePropertyAndGetterMethodInClassDesignTable() throws Exception {
        DocxWrapperExample.main(new String[0]);

        Path output = Path.of("target/docx-wrapper-example.docx");
        Document document = new Document(output.toString());
        String text = document.getText();
        document.updateListLabels();

        assertTrue(Files.exists(output));
        assertHeadingNumber(document, "行内元素", "2.1");
        assertHeadingNumber(document, "九级标题示例", "3.1");
        assertTrue(text.contains("属性说明"));
        assertTrue(text.contains("path"));
        assertTrue(text.contains("private"));
        assertTrue(text.contains("方法说明"));
        assertTrue(text.contains("getPath"));
        assertTrue(text.contains("图片文件路径"));
        assertTrue(text.contains("ClassDesignTableOptions"));
        assertTrue(text.contains("SectionBuilder"));
        assertTrue(text.contains("DefaultStyles"));
        assertTrue(text.contains("SourceClassMetadataParser"));
    }

    /**
     * 验证指定标题使用 Word 原生列表编号，而非在正文中写入编号字符串。
     *
     * @param document 已更新列表标签的 Word 文档
     * @param headingText 标题正文
     * @param expectedNumber 预期列表标签
     */
    private void assertHeadingNumber(Document document, String headingText, String expectedNumber) {
        for (Section section : document.getSections()) {
            for (Paragraph paragraph : section.getBody().getParagraphs()) {
                if (headingText.equals(paragraph.getText().trim())) {
                    assertTrue(paragraph.getListFormat().isListItem());
                    assertTrue(expectedNumber.equals(paragraph.getListLabel().getLabelString()));
                    return;
                }
            }
        }
        throw new AssertionError("heading not found: " + headingText);
    }
}
