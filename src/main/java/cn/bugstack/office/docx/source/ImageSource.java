package cn.bugstack.office.docx.source;

import java.io.IOException;
import java.io.InputStream;

/**
 * 图片来源策略接口。
 *
 * <p>后续可通过该接口扩展本地文件、字节数组、输入流和 URL 等来源。</p>
 */
public interface ImageSource {

    /**
     * 打开图片输入流。
     *
     * @return 图片输入流
     * @throws IOException 打开失败时抛出
     */
    InputStream openStream() throws IOException;

    /**
     * 获取图片来源名称。
     *
     * @return 图片名称
     */
    String name();
}
