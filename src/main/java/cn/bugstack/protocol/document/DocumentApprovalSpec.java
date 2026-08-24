package cn.bugstack.protocol.document;

/** 文档审批/签署记录项。 */
public final class DocumentApprovalSpec {
    private String role;
    private String person;
    private String date;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPerson() { return person; }
    public void setPerson(String person) { this.person = person; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}
