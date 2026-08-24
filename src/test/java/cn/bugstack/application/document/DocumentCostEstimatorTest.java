package cn.bugstack.application.document;

import cn.bugstack.protocol.document.DocumentApprovalSpec;
import cn.bugstack.protocol.document.DocumentCoverSpec;
import cn.bugstack.protocol.document.DocumentMetadataSpec;
import cn.bugstack.protocol.document.DocumentRevisionSpec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.ImageBlockSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCostEstimatorTest {

    @Test
    void includesFrontMatterTablesTextAndMediaInTheEstimate() {
        DocumentSpec document = new DocumentSpec();
        document.setMetadata(new DocumentMetadataSpec("Report"));
        DocumentCoverSpec cover = new DocumentCoverSpec();
        cover.setDocumentName("Cover");
        document.setCover(cover);

        DocumentRevisionSpec revision = new DocumentRevisionSpec();
        revision.setVersion("V1");
        revision.setDescription("Initial");
        document.setRevisionHistory(Collections.singletonList(revision));
        DocumentApprovalSpec approval = new DocumentApprovalSpec();
        approval.setRole("Reviewer");
        approval.setPerson("Alice");
        document.setApprovals(Collections.singletonList(approval));

        TableBlockSpec table = new TableBlockSpec();
        table.setHeaders(Arrays.asList("A", "B"));
        table.setRows(Collections.singletonList(Arrays.asList("C", "D")));
        ImageBlockSpec image = new ImageBlockSpec();
        image.setAssetId("00000000-0000-0000-0000-000000000001");
        document.addSection(new SectionSpec("Section")
                .addBlock(new ParagraphBlockSpec("Body"))
                .addBlock(table)
                .addBlock(image));

        DocumentCostEstimate estimate = new DocumentCostEstimator().estimate(document);

        assertEquals(1, estimate.getSections());
        assertEquals(3, estimate.getBlocks());
        assertEquals(11, estimate.getTableCells());
        assertEquals(1, estimate.getMediaBlocks());
        assertTrue(estimate.getTextCharacters() >= 45);
        assertTrue(estimate.getEstimatedPages() >= 2);
    }
}
