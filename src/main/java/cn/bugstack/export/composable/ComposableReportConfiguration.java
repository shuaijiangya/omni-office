package cn.bugstack.export.composable;

import cn.bugstack.export.context.ReportBasicInfo;
import cn.bugstack.export.definition.ReportCoverTemplate;
import cn.bugstack.export.definition.ReportLayout;
import cn.bugstack.export.definition.ReportStyleProfile;
import cn.bugstack.office.docx.style.StyleProfile;

import java.util.List;

/**
 * 可组合报告定义所需的公共配置契约。
 *
 * <p>业务输入可以直接实现该接口。报告定义父类只读取版式和模块选择，模块业务数据仍由
 * {@code ReportDefinition.contributeData(...)} 独立装配。</p>
 */
public interface ComposableReportConfiguration {

    /** 获取报告标题。 */
    String getReportTitle();

    /** 获取目录之前写入的封面。 */
    ReportCoverTemplate getReportCover();

    /** 获取按最终输出顺序排列的模块编码。 */
    List<String> getSelectedModuleCodes();

    /** 获取文档作者；默认不设置。 */
    default String getReportAuthor() {
        return null;
    }

    /** 获取文档主题；默认不设置。 */
    default String getReportSubject() {
        return null;
    }

    /** 获取基础信息；默认不输出基础信息表格。 */
    default ReportBasicInfo getReportBasicInfo() {
        return null;
    }

    /** 获取样式画像；默认使用框架默认样式。 */
    default StyleProfile getReportStyleProfile() {
        return ReportStyleProfile.DEFAULT;
    }

    /** 获取可选页眉；默认不设置。 */
    default String getReportHeader() {
        return null;
    }

    /** 获取正文页脚模板；默认仅显示页码。 */
    default String getModuleFooter() {
        return ReportLayout.PAGE_NUMBER_ONLY_FOOTER;
    }

    /** 获取目录页脚模板；默认与正文页脚一致。 */
    default String getTableOfContentsFooter() {
        return getModuleFooter();
    }

    /** 获取目录深度；返回 {@code null} 时不生成目录。 */
    default Integer getTableOfContentsDepth() {
        return 3;
    }

    /** 是否启用标题编号。 */
    default boolean isHeadingNumberingEnabled() {
        return true;
    }

    /** 是否在正文重复输出报告标题。 */
    default boolean isBodyTitleEnabled() {
        return false;
    }

    /** 获取业务模块正文起始页码。 */
    default int getModulePageNumberStart() {
        return 1;
    }
}
