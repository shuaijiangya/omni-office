import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.Document;
import com.aspose.words.PdfCompliance;
import com.aspose.words.PdfSaveOptions;

/**
 * Aspose 测试
 *
 * @author admin
 */
public class AsposeTest {

    /**
     * 将 classpath 下的 {@code 1.docx} 转换为 PDF。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 文档读取或保存失败时抛出
     */
    public static void main(String[] args) throws Exception {
        AsposeWordsLicenseLoader.applyConfiguredLicense();
        String path = AsposeTest.class.getClassLoader().getResource("1.docx").getPath();
        Document doc = new Document(String.valueOf(path));  // 替换为你的输入文件路径
        // 创建 PDF 保存选项
        PdfSaveOptions options = new PdfSaveOptions();
        // 可以设置各种 PDF 选项，例如：
        options.setCompliance(PdfCompliance.PDF_17); // 设置 PDF 版本兼容性
        // 将文档保存为 PDF
        doc.save("1.pdf", options);  // 替换为你想要的输出文件路径
        System.out.println("文档转换成功！");
    }
}
