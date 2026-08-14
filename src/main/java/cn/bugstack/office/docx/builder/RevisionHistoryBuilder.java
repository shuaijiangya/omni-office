package cn.bugstack.office.docx.builder;

import cn.bugstack.office.docx.model.RevisionHistoryNode;

/**
 * 修订记录 Builder。
 */
public class RevisionHistoryBuilder {

    /** 正在构建的修订历史节点。 */
    private final RevisionHistoryNode node;

    /**
     * 创建修订记录 Builder。
     *
     * @param node 修订记录节点
     */
    public RevisionHistoryBuilder(RevisionHistoryNode node) {
        this.node = node;
    }

    /**
     * 追加修订记录。
     *
     * @param version 版本号
     * @param date 修订日期
     * @param description 修订说明
     * @param author 修订人
     * @return 当前 Builder
     */
    public RevisionHistoryBuilder revision(String version, String date, String description, String author) {
        node.addRecord(version, date, description, author);
        return this;
    }
}
