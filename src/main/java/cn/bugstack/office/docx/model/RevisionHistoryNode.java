package cn.bugstack.office.docx.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 修订记录节点。
 */
public class RevisionHistoryNode implements DocxBlock {

    private final List<RevisionRecord> records = new ArrayList<>();

    /**
     * 创建空的修订记录节点。
     */
    public RevisionHistoryNode() {
    }

    /**
     * 追加修订记录。
     *
     * @param version 版本号
     * @param date 修订日期
     * @param description 修订说明
     * @param author 修订人
     */
    public void addRecord(String version, String date, String description, String author) {
        records.add(new RevisionRecord(version, date, description, author));
    }

    /**
     * 获取修订记录列表。
     *
     * @return 不可修改的修订记录列表
     */
    public List<RevisionRecord> getRecords() {
        return Collections.unmodifiableList(records);
    }

    /**
     * 单条修订记录。
     */
    public static class RevisionRecord {

        /** 修订版本号。 */
        private final String version;
        /** 修订日期。 */
        private final String date;
        /** 修订内容说明。 */
        private final String description;
        /** 修订人员。 */
        private final String author;

        /**
         * 创建修订记录。
         *
         * @param version 版本号
         * @param date 修订日期
         * @param description 修订说明
         * @param author 修订人
         */
        public RevisionRecord(String version, String date, String description, String author) {
            this.version = version;
            this.date = date;
            this.description = description;
            this.author = author;
        }

        /**
         * 获取版本号。
         *
         * @return 版本号
         */
        public String getVersion() {
            return version;
        }

        /**
         * 获取修订日期。
         *
         * @return 修订日期
         */
        public String getDate() {
            return date;
        }

        /**
         * 获取修订说明。
         *
         * @return 修订说明
         */
        public String getDescription() {
            return description;
        }

        /**
         * 获取修订人。
         *
         * @return 修订人
         */
        public String getAuthor() {
            return author;
        }
    }
}
