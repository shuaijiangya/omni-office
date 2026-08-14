package cn.bugstack.office.docx.render;

import com.aspose.words.License;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Aspose Words 官方 License 加载器。
 *
 * <p>该类只调用 Aspose 官方 {@link License#setLicense(String)} API，不修改 Aspose 内部状态。</p>
 */
public final class AsposeWordsLicenseLoader {

    /**
     * License 文件路径系统属性名称。
     */
    public static final String LICENSE_PATH_PROPERTY = "aspose.words.license.path";

    /**
     * License 文件路径环境变量名称。
     */
    public static final String LICENSE_PATH_ENV = "ASPOSE_WORDS_LICENSE_PATH";

    private AsposeWordsLicenseLoader() {
    }

    /**
     * 从系统属性或环境变量加载 License。
     *
     * @return 成功配置并加载 License 时返回 {@code true}，未配置时返回 {@code false}
     */
    public static boolean applyConfiguredLicense() {
        String configuredPath = System.getProperty(LICENSE_PATH_PROPERTY);
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            configuredPath = System.getenv(LICENSE_PATH_ENV);
        }
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            return false;
        }
        return apply(Path.of(configuredPath));
    }

    /**
     * 从指定路径加载 License。
     *
     * @param licensePath License 文件路径
     * @return 成功加载时返回 {@code true}，路径为 {@code null} 时返回 {@code false}
     */
    public static boolean apply(Path licensePath) {
        if (licensePath == null) {
            return false;
        }
        if (!Files.isRegularFile(licensePath)) {
            throw new IllegalArgumentException("Aspose Words license file does not exist: " + licensePath);
        }
        try {
            new License().setLicense(licensePath.toString());
            return true;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load Aspose Words license: " + licensePath, e);
        }
    }
}
