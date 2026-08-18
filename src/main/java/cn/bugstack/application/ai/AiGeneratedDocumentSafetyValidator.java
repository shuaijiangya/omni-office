package cn.bugstack.application.ai;

import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.BlockSpec;
import cn.bugstack.protocol.document.block.DiagramBlockSpec;
import cn.bugstack.protocol.document.block.ImageBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;

import java.util.ArrayList;
import java.util.List;

/** AI 自由文档的附加安全约束，阻止模型发明路径、URL 或已有工件标识。 */
public final class AiGeneratedDocumentSafetyValidator {

    public List<String> validate(DocumentSpec document) {
        List<String> errors = new ArrayList<>();
        if (document == null || document.getSections() == null) {
            return errors;
        }
        for (int i = 0; i < document.getSections().size(); i++) {
            validateSection(document.getSections().get(i), "/sections/" + i, errors);
        }
        return errors;
    }

    private void validateSection(SectionSpec section, String path, List<String> errors) {
        if (section == null || section.getBlocks() == null) {
            return;
        }
        for (int i = 0; i < section.getBlocks().size(); i++) {
            BlockSpec block = section.getBlocks().get(i);
            String blockPath = path + "/blocks/" + i;
            if (block instanceof ImageBlockSpec) {
                errors.add(blockPath + " [AI_IMAGE_NOT_ALLOWED] AI freeform output must not create image sources");
            } else if (block instanceof DiagramBlockSpec) {
                DiagramBlockSpec diagram = (DiagramBlockSpec) block;
                if (diagram.getDiagramArtifactId() != null) {
                    errors.add(blockPath + "/diagramArtifactId [AI_ARTIFACT_NOT_ALLOWED] "
                            + "AI freeform output must use an inline diagram definition");
                }
            } else if (block instanceof SubsectionBlockSpec) {
                SubsectionBlockSpec subsection = (SubsectionBlockSpec) block;
                SectionSpec child = new SectionSpec(subsection.getTitle());
                child.setBlocks(subsection.getBlocks());
                validateSection(child, blockPath, errors);
            }
        }
    }
}
