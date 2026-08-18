package cn.bugstack.protocol.document;

/**
 * DocumentSpec 文档元数据。
 */
public final class DocumentMetadataSpec {

    private String title;
    private String author;
    private String subject;

    public DocumentMetadataSpec() {
    }

    public DocumentMetadataSpec(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
