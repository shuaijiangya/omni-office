package cn.bugstack.application.external;

import cn.bugstack.application.document.DocumentSpecValidationException;
import cn.bugstack.application.document.DocumentSpecViolation;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.BlockSpec;
import cn.bugstack.protocol.document.block.ImageBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部 DocumentSpec 的附加边界。外部调用只允许引用同一主体已托管的 Asset ID，禁止服务器路径。
 */
final class ExternalDocumentSpecSafetyValidator {

    void validateOrThrow(DocumentSpec document) {
        List<DocumentSpecViolation> violations = new ArrayList<>();
        if (document != null && document.getSections() != null) {
            for (int i = 0; i < document.getSections().size(); i++) {
                validateSection(document.getSections().get(i), "/sections/" + i, violations);
            }
        }
        if (!violations.isEmpty()) throw new DocumentSpecValidationException(violations);
    }

    private void validateSection(SectionSpec section, String path, List<DocumentSpecViolation> violations) {
        if (section == null || section.getBlocks() == null) return;
        for (int i = 0; i < section.getBlocks().size(); i++) {
            BlockSpec block = section.getBlocks().get(i);
            String blockPath = path + "/blocks/" + i;
            if (block instanceof ImageBlockSpec) {
                ImageBlockSpec image = (ImageBlockSpec) block;
                if (image.getSource() != null) {
                    violations.add(new DocumentSpecViolation(blockPath + "/source", "EXTERNAL_IMAGE_SOURCE_NOT_ALLOWED",
                            "external DocumentSpec images must use a managed assetId"));
                }
            } else if (block instanceof SubsectionBlockSpec) {
                SubsectionBlockSpec subsection = (SubsectionBlockSpec) block;
                SectionSpec child = new SectionSpec(subsection.getTitle());
                child.setBlocks(subsection.getBlocks());
                validateSection(child, blockPath, violations);
            }
        }
    }
}
