package cn.bugstack.office.docx.style;

/**
 * GJB 438C 软件开发文档样式画像。
 *
 * <p>该画像当前复用封装层定义的中文公文式默认样式，并作为后续细化封面、
 * 修订记录、目录、页眉页脚和检查规则的标准入口。</p>
 */
public final class Gjb438cStyleProfile implements StyleProfile {

    private static final Gjb438cStyleProfile INSTANCE = new Gjb438cStyleProfile();

    private Gjb438cStyleProfile() {
    }

    /**
     * 获取 GJB 438C 标准样式画像。
     *
     * @return GJB 438C 样式画像
     */
    public static Gjb438cStyleProfile standard() {
        return INSTANCE;
    }

    /**
     * 创建 GJB 438C 样式注册表。
     *
     * @return 样式注册表
     */
    @Override
    public StyleRegistry createRegistry() {
        return DefaultStyles.createRegistry();
    }
}
