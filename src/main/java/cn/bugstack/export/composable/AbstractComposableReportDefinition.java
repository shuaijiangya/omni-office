package cn.bugstack.export.composable;

import cn.bugstack.export.definition.AbstractReportDefinition;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.definition.ReportCoverTemplate;
import cn.bugstack.export.definition.ReportLayout;
import cn.bugstack.export.definition.StandardReportCoverTemplate;

import java.util.List;

/**
 * 负责封面、目录、页眉页脚和动态模块顺序的可组合报告定义父类。
 *
 * <p>子类不再重复实现通用蓝图编排，只需保留报告身份，并通过
 * {@code contributeData(...)} 装配各个强类型模块的数据。</p>
 *
 * @param <I> 同时携带报告配置和业务数据的输入类型
 */
public abstract class AbstractComposableReportDefinition<I extends ComposableReportConfiguration>
        extends AbstractReportDefinition<I> {

    /** 创建固定编码、名称和版本的可组合报告定义。 */
    protected AbstractComposableReportDefinition(String code, String name, String version) {
        super(code, name, version);
    }

    /**
     * 根据公共配置生成报告蓝图，模块数据装配不在此方法中处理。
     */
    @Override
    protected final void configure(ReportBlueprint.Builder builder, I input) {
        requireInput(input);
        ReportLayout.Builder layout = ReportLayout.builder()
                .styleProfile(input.getReportStyleProfile())
                .headingNumberingEnabled(input.isHeadingNumberingEnabled())
                .bodyTitle(input.isBodyTitleEnabled())
                .footer(input.getModuleFooter())
                .tableOfContentsFooter(input.getTableOfContentsFooter())
                .modulePageNumberStart(input.getModulePageNumberStart());

        Integer tableOfContentsDepth = input.getTableOfContentsDepth();
        if (tableOfContentsDepth != null) {
            layout.tableOfContents(tableOfContentsDepth);
        }
        if (hasText(input.getReportHeader())) {
            layout.header(input.getReportHeader().trim());
        }
        configureCover(layout, input.getReportCover());
        customizeLayout(layout, input);

        builder.title(input.getReportTitle())
                .metadata(input.getReportAuthor(), input.getReportSubject())
                .basicInfo(input.getReportBasicInfo())
                .layout(layout.build());

        List<String> moduleCodes = input.getSelectedModuleCodes();
        if (moduleCodes == null) {
            throw new IllegalArgumentException("composable report module codes must not be null");
        }
        for (String moduleCode : moduleCodes) {
            builder.module(moduleCode);
        }
        customizeBlueprint(builder, input);
    }

    /** 允许具体报告在公共版式生成后补充少量报告专属配置。 */
    protected void customizeLayout(ReportLayout.Builder layout, I input) {
    }

    /** 允许具体报告在公共模块槽位加入后补充蓝图配置。 */
    protected void customizeBlueprint(ReportBlueprint.Builder builder, I input) {
    }

    /** 把标准封面和任意动态封面映射到各自已有的框架能力。 */
    private void configureCover(ReportLayout.Builder layout, ReportCoverTemplate cover) {
        if (cover == null) {
            throw new IllegalArgumentException("composable report cover must not be null");
        }
        if (cover instanceof StandardReportCoverTemplate) {
            StandardReportCoverTemplate standardCover = (StandardReportCoverTemplate) cover;
            layout.cover(standardCover.getDocumentName(), standardCover.getProjectName(), standardCover.getVersion());
        } else {
            layout.coverTemplate(cover);
        }
    }

    private void requireInput(I input) {
        if (input == null) {
            throw new IllegalArgumentException("composable report input must not be null");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
