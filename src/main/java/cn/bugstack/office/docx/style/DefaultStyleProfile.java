package cn.bugstack.office.docx.style;

/**
 * 默认文档样式画像。
 */
public final class DefaultStyleProfile implements StyleProfile {

    private static final DefaultStyleProfile INSTANCE = new DefaultStyleProfile();

    private DefaultStyleProfile() {
    }

    /**
     * 获取默认样式画像。
     *
     * @return 默认样式画像
     */
    public static DefaultStyleProfile standard() {
        return INSTANCE;
    }

    /**
     * 创建默认样式注册表。
     *
     * @return 样式注册表
     */
    @Override
    public StyleRegistry createRegistry() {
        return DefaultStyles.createRegistry();
    }
}
