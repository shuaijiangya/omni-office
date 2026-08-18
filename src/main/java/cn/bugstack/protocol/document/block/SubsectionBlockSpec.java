package cn.bugstack.protocol.document.block;

import java.util.ArrayList;
import java.util.List;

/**
 * 可递归嵌套的子章节。
 */
public final class SubsectionBlockSpec extends BlockSpec {

    private String title;
    private List<BlockSpec> blocks = new ArrayList<>();

    public SubsectionBlockSpec() {
    }

    public SubsectionBlockSpec(String title) {
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

    public SubsectionBlockSpec addBlock(BlockSpec block) {
        if (block != null) {
            blocks.add(block);
        }
        return this;
    }
}
