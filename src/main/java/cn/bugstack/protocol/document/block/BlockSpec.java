package cn.bugstack.protocol.document.block;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * DocumentSpec 块级元素的公共协议。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ParagraphBlockSpec.class, name = "paragraph"),
        @JsonSubTypes.Type(value = BulletListBlockSpec.class, name = "bulletList"),
        @JsonSubTypes.Type(value = NumberedListBlockSpec.class, name = "numberedList"),
        @JsonSubTypes.Type(value = TableBlockSpec.class, name = "table"),
        @JsonSubTypes.Type(value = ImageBlockSpec.class, name = "image"),
        @JsonSubTypes.Type(value = DiagramBlockSpec.class, name = "diagram"),
        @JsonSubTypes.Type(value = ChartBlockSpec.class, name = "chart"),
        @JsonSubTypes.Type(value = SubsectionBlockSpec.class, name = "subsection"),
        @JsonSubTypes.Type(value = PageBreakBlockSpec.class, name = "pageBreak")
})
public abstract class BlockSpec {
}
