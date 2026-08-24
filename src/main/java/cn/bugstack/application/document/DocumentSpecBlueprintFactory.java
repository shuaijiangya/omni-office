package cn.bugstack.application.document;

import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.definition.ReportLayout;
import cn.bugstack.export.definition.ReportStyleProfile;
import cn.bugstack.protocol.document.DocumentLayoutSpec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentStyleProfile;
import cn.bugstack.office.docx.model.DocxPageOrientation;
import cn.bugstack.office.docx.model.DocxPaperSize;

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
                .modulePageNumberStart(source.getBodyPageNumberStart())
                .pageSetup(DocxPaperSize.valueOf(source.getPaperSize()),
                        DocxPageOrientation.valueOf(source.getOrientation()),
                        source.getTopMarginPoints(), source.getRightMarginPoints(),
                        source.getBottomMarginPoints(), source.getLeftMarginPoints());
        if (source.getTableOfContentsDepth() != null) {
            layout.tableOfContents(source.getTableOfContentsDepth());
        }
        if (hasText(source.getHeaderText())) {
            layout.header(source.getHeaderText());
        }
        if (hasText(source.getFooterText())) {
            layout.footer(source.getFooterText());
        }
        if (spec.getCover() != null) {
            layout.cover(spec.getCover().getDocumentName(), spec.getCover().getProjectName(),
                    spec.getCover().getVersion());
        }
        spec.getRevisionHistory().forEach(item -> layout.revision(
                item.getVersion(), item.getDate(), item.getDescription(), item.getAuthor()));
        spec.getApprovals().forEach(item -> layout.approval(item.getRole(), item.getPerson(), item.getDate()));
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
