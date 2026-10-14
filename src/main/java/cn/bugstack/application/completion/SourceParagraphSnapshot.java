package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 上传 DOCX 正文中一个可定位段落的只读摘要。 */
public final class SourceParagraphSnapshot {

    private final String paragraphId;
    private final int sectionIndex;
    private final int bodyChildIndex;
    private final String styleName;
    private final String text;
    private final boolean containsObjects;
    private final List<SourceRunSnapshot> runs;

    /** 创建段落摘要。 */
    public SourceParagraphSnapshot(String paragraphId, int sectionIndex, int bodyChildIndex,
                                   String styleName, String text, boolean containsObjects,
                                   List<SourceRunSnapshot> runs) {
        this.paragraphId = paragraphId;
        this.sectionIndex = sectionIndex;
        this.bodyChildIndex = bodyChildIndex;
        this.styleName = styleName;
        this.text = text;
        this.containsObjects = containsObjects;
        this.runs = Collections.unmodifiableList(new ArrayList<>(runs));
    }

    /** @return 段落定位标识 */
    public String getParagraphId() { return paragraphId; }

    /** @return Section 序号 */
    public int getSectionIndex() { return sectionIndex; }

    /** @return Section 正文中的直接子节点序号 */
    public int getBodyChildIndex() { return bodyChildIndex; }

    /** @return Word 段落样式名称 */
    public String getStyleName() { return styleName; }

    /** @return 可见文本 */
    public String getText() { return text; }

    /** @return 是否包含非普通 Run 节点，例如域、图片或内容控件 */
    public boolean isContainsObjects() { return containsObjects; }

    /** @return 段落内直接 Run 的只读快照，用于保留局部样式的精准补全 */
    public List<SourceRunSnapshot> getRuns() { return runs; }
}
