package cn.bugstack.protocol.document;

import cn.bugstack.protocol.document.block.BlockSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * DocumentSpec 的一级章节；更深层级使用 subsection block 表达。
 */
public final class SectionSpec {

    private String title;
    private List<BlockSpec> blocks = new ArrayList<>();

    public SectionSpec() {
    }

    public SectionSpec(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<BlockSpec> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<BlockSpec> blocks) {
        this.blocks = blocks == null ? new ArrayList<>() : blocks;
    }

    public SectionSpec addBlock(BlockSpec block) {
        if (block != null) {
            blocks.add(block);
        }
        return this;
    }
}
