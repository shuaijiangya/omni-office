package cn.bugstack.office.docx.model;

/**
 * 行内节点标记接口。
 *
 * <p>行内节点只能作为 Paragraph 的 child，例如文本、图片和 Visio 预览图。</p>
 */
public interface DocxInline extends DocxNode {
}
