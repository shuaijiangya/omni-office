package cn.bugstack.application.document;

import cn.bugstack.protocol.document.DocumentMetadataSpec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.DiagramBlockSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentSpecValidatorTest {

    @Test
    void acceptsAValidDocumentSpec() {
        DocumentSpec spec = validSpec();

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertTrue(result.isValid(), () -> result.getViolations().toString());
    }

    @Test
    void reportsPrecisePathsForStructuralErrors() {
        DocumentSpec spec = validSpec();
        spec.getMetadata().setTitle(" ");
        TableBlockSpec table = new TableBlockSpec();
        table.setHeaders(Arrays.asList("A", "B"));
        table.setRows(Collections.singletonList(Collections.singletonList("only one cell")));
        spec.getSections().get(0).addBlock(table);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> "/metadata/title".equals(v.getPath()) && "REQUIRED".equals(v.getCode())));
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> "/sections/0/blocks/1/rows/0".equals(v.getPath())
                        && "COLUMN_MISMATCH".equals(v.getCode())));
    }

    @Test
    void enforcesRecursiveSectionDepthLimit() {
        DocumentSpec spec = validSpec();
        SubsectionBlockSpec current = new SubsectionBlockSpec("level-2");
        spec.getSections().get(0).addBlock(current);
        for (int depth = 3; depth <= 10; depth++) {
            SubsectionBlockSpec child = new SubsectionBlockSpec("level-" + depth);
            current.addBlock(child);
            current = child;
        }

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> "LIMIT_EXCEEDED".equals(v.getCode())
                        && v.getMessage().contains("section depth")));
    }

    @Test
    void reservesDiagramBlockUntilM2CapabilityIsInstalled() {
        DocumentSpec spec = validSpec();
        DiagramBlockSpec diagram = new DiagramBlockSpec();
        diagram.setDiagramArtifactId("diagram-001");
        spec.getSections().get(0).addBlock(diagram);

        DocumentSpecValidationResult result = new DocumentSpecValidator().validate(spec);

        assertFalse(result.isValid());
        assertEquals("CAPABILITY_NOT_AVAILABLE", result.getViolations().stream()
                .filter(v -> v.getPath().endsWith("/blocks/1"))
                .findFirst().orElseThrow().getCode());
    }

    private DocumentSpec validSpec() {
        DocumentSpec spec = new DocumentSpec();
        spec.setMetadata(new DocumentMetadataSpec("Valid document"));
        spec.addSection(new SectionSpec("Overview").addBlock(new ParagraphBlockSpec("Body text")));
        return spec;
    }
}
