package cn.bugstack.application.document;

import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.definition.ReportLayout;
import cn.bugstack.export.definition.ReportStyleProfile;
import cn.bugstack.protocol.document.DocumentLayoutSpec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentStyleProfile;

/**
 * 将 DocumentSpec 的公开版式选项转换为现有报告蓝图。
 */
public final class DocumentSpecBlueprintFactory {

    public ReportBlueprint create(DocumentSpec spec) {
        if (spec == null || spec.getMetadata() == null || spec.getLayout() == null) {
            throw new IllegalArgumentException("document spec metadata and layout must not be null");
        }
        DocumentLayoutSpec source = spec.getLayout();
        ReportLayout.Builder layout = ReportLayout.builder()
                .styleProfile(styleProfile(source.getStyleProfile()))
                .headingNumberingEnabled(source.isHeadingNumberingEnabled())
                .bodyTitle(source.isBodyTitleEnabled())
                .pageNumberFooter(source.isPageNumberFooterEnabled())
                .modulePageNumberStart(source.getBodyPageNumberStart());
        if (source.getTableOfContentsDepth() != null) {
            layout.tableOfContents(source.getTableOfContentsDepth());
        }
        if (hasText(source.getHeaderText())) {
            layout.header(source.getHeaderText());
        }
        if (hasText(source.getFooterText())) {
            layout.footer(source.getFooterText());
        }
        return ReportBlueprint.builder("dynamic-document", "Dynamic Document", spec.getSchemaVersion())
                .title(spec.getMetadata().getTitle())
                .metadata(spec.getMetadata().getAuthor(), spec.getMetadata().getSubject())
                .layout(layout.build())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private ReportStyleProfile styleProfile(DocumentStyleProfile value) {
        if (value == DocumentStyleProfile.GJB_438C) return ReportStyleProfile.GJB_438C;
        if (value == DocumentStyleProfile.BUSINESS_BRIEF) return ReportStyleProfile.BUSINESS_BRIEF;
        return ReportStyleProfile.DEFAULT;
    }
}
