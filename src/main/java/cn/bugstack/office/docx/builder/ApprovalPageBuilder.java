package cn.bugstack.office.docx.builder;

import cn.bugstack.office.docx.model.ApprovalPageNode;

/**
 * 签署页 Builder。
 */
public class ApprovalPageBuilder {

    /** 正在构建的审批页节点。 */
    private final ApprovalPageNode node;

    /**
     * 创建签署页 Builder。
     *
     * @param node 签署页节点
     */
    public ApprovalPageBuilder(ApprovalPageNode node) {
        this.node = node;
    }

    /**
     * 追加签署记录。
     *
     * @param role 签署角色
     * @param person 签署人
     * @param date 签署日期
     * @return 当前 Builder
     */
    public ApprovalPageBuilder approval(String role, String person, String date) {
        node.addRecord(role, person, date);
        return this;
    }
}
