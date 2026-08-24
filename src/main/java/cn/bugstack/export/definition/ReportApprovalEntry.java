package cn.bugstack.export.definition;

/** Word 前置签署页中的审批记录值对象。 */
public final class ReportApprovalEntry {
    private final String role;
    private final String person;
    private final String date;

    public ReportApprovalEntry(String role, String person, String date) {
        this.role = role; this.person = person; this.date = date;
    }
    public String getRole() { return role; }
    public String getPerson() { return person; }
    public String getDate() { return date; }
}
