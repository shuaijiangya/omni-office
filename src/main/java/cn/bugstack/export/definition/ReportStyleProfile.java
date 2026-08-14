package cn.bugstack.export.definition;

import cn.bugstack.office.docx.style.DefaultStyleProfile;
import cn.bugstack.office.docx.style.Gjb438cStyleProfile;
import cn.bugstack.office.docx.style.StyleProfile;
import cn.bugstack.office.docx.style.StyleRegistry;

/**
 * 报告内置的标准样式画像。
 *
 * <p>该枚举只提供框架内置画像，同时实现公共 {@link StyleProfile} 扩展接口。
 * 业务侧需要自定义样式时无需修改本枚举，直接实现 {@code StyleProfile} 并传给
 * {@link ReportLayout.Builder#styleProfile(StyleProfile)} 即可。</p>
 */
public enum ReportStyleProfile implements StyleProfile {

    /** 通用技术文档样式。 */
    DEFAULT(DefaultStyleProfile.standard()),

    /** GJB 438C 软件开发文档样式。 */
    GJB_438C(Gjb438cStyleProfile.standard());

    /** 实际创建样式注册表的内置画像。 */
    private final StyleProfile delegate;

    ReportStyleProfile(StyleProfile delegate) {
        this.delegate = delegate;
    }

    /**
     * 创建当前内置画像的样式注册表。
     *
     * @return 独立的样式注册表
     */
    @Override
    public StyleRegistry createRegistry() {
        return delegate.createRegistry();
    }
}
