package cn.bugstack.office.docx.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 签署页节点。
 */
public class ApprovalPageNode implements DocxBlock {

    private final List<ApprovalRecord> records = new ArrayList<>();

    /**
     * 创建空的签署页节点。
     */
    public ApprovalPageNode() {
    }

    /**
     * 追加签署记录。
     *
     * @param role 签署角色
     * @param person 签署人
     * @param date 签署日期
     */
    public void addRecord(String role, String person, String date) {
        records.add(new ApprovalRecord(role, person, date));
    }

    /**
     * 获取签署记录列表。
     *
     * @return 不可修改的签署记录列表
     */
    public List<ApprovalRecord> getRecords() {
        return Collections.unmodifiableList(records);
    }

    /**
     * 单条签署记录。
     */
    public static class ApprovalRecord {

        /** 审批角色。 */
        private final String role;
        /** 审批人员姓名。 */
        private final String person;
        /** 审批日期。 */
        private final String date;

        /**
         * 创建签署记录。
         *
         * @param role 签署角色
         * @param person 签署人
         * @param date 签署日期
         */
        public ApprovalRecord(String role, String person, String date) {
            this.role = role;
            this.person = person;
            this.date = date;
        }

        /**
         * 获取签署角色。
         *
         * @return 签署角色
         */
        public String getRole() {
            return role;
        }

        /**
         * 获取签署人。
         *
         * @return 签署人
         */
        public String getPerson() {
            return person;
        }

        /**
         * 获取签署日期。
         *
         * @return 签署日期
         */
        public String getDate() {
            return date;
        }
    }
}
