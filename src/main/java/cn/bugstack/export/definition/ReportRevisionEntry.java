package cn.bugstack.export.definition;

/** Word 前置页中的修订记录值对象。 */
public final class ReportRevisionEntry {
    private final String version;
    private final String date;
    private final String description;
    private final String author;

    public ReportRevisionEntry(String version, String date, String description, String author) {
        this.version = version; this.date = date; this.description = description; this.author = author;
    }
    public String getVersion() { return version; }
    public String getDate() { return date; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
}
