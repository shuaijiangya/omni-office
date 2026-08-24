package cn.bugstack.protocol.document;

/** 文档修订记录项。 */
public final class DocumentRevisionSpec {
    private String version;
    private String date;
    private String description;
    private String author;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
}
