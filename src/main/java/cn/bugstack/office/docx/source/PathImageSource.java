package cn.bugstack.office.docx.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 基于本地文件路径的图片来源。
 */
public class PathImageSource implements ImageSource {

    /**
     * 地址路径
     */
    private final Path path;

    /**
     * 创建本地路径图片来源。
     *
     * @param path 图片文件路径
     */
    public PathImageSource(Path path) {
        this.path = path;
    }

    /**
     * 打开图片文件输入流。
     *
     * @return 图片输入流
     * @throws IOException 打开失败时抛出
     */
    @Override
    public InputStream openStream() throws IOException {
        return Files.newInputStream(path);
    }

    /**
     * 获取图片文件名。
     *
     * @return 图片文件名
     */
    @Override
    public String name() {
        return path.getFileName().toString();
    }

    /**
     * 获取图片文件路径。
     *
     * @return 图片文件路径
     */
    public Path getPath() {
        return path;
    }
}
