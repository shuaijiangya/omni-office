package cn.bugstack.office.docx.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * docx 章节节点，承载段落、表格等块级内容。
 */
public class SectionNode implements DocxNode {

    private final List<DocxBlock> blocks = new ArrayList<>();

    /**
     * 创建空的章节节点。
     */
    public SectionNode() {
    }

    /**
     * 追加块级节点。
     *
     * @param block 块级节点
     */
    public void addBlock(DocxBlock block) {
        blocks.add(block);
    }

    /**
     * 获取章节中的块级节点。
     *
     * @return 不可修改的块级节点列表
     */
    public List<DocxBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }
}
