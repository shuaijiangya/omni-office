package cn.bugstack.export.module;

import cn.bugstack.export.document.ReportSection;
import cn.bugstack.export.document.ReportSectionBuilder;

/**
 * 报告模块模板方法基类。
 *
 * <p>外部模块只需提供描述符并实现 {@link #composeContent(ReportSectionBuilder, Object,
 * ReportModuleContext)}。标题覆盖、章节创建和空值防御由该基类统一处理。</p>
 *
 * @param <T> 模块输入数据类型
 */
public abstract class AbstractReportModule<T> implements ReportModule<T> {

    /**
     * 按模板方法创建模块章节并执行前后置钩子。
     *
     * @param context 当前模块执行上下文
     * @param data 模块输入数据
     * @return 已完成组装的语义章节
     */
    @Override
    public final ReportSection compose(ReportModuleContext context, T data) {
        if (context == null) {
            throw new IllegalArgumentException("report module context must not be null");
        }
        if (data == null) {
            throw new IllegalArgumentException("report module data must not be null: " + descriptor().getCode());
        }
        String title = context.getSlot().getTitleOverride();
        if (title == null || title.trim().isEmpty()) {
            title = descriptor().getDefaultTitle();
        }
        ReportSectionBuilder section = ReportSectionBuilder.section(title);
        beforeCompose(section, data, context);
        composeContent(section, data, context);
        afterCompose(section, data, context);
        return section.build();
    }

    /**
     * 模块写入前钩子。
     */
    protected void beforeCompose(ReportSectionBuilder section, T data, ReportModuleContext context) {
    }

    /**
     * 写入模块业务内容。
     *
     * @param section 当前语义章节构建器
     * @param data 模块业务数据
     * @param context 模块上下文
     */
    protected abstract void composeContent(ReportSectionBuilder section, T data, ReportModuleContext context);

    /**
     * 模块写入后钩子。
     */
    protected void afterCompose(ReportSectionBuilder section, T data, ReportModuleContext context) {
    }
}
