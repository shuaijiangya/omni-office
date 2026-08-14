package cn.bugstack.office.docx.model;

/**
 * Word 文档元数据。
 */
public class DocxMetadata {

    /** 文档标题元数据。 */
    private String title;
    /** 文档作者元数据。 */
    private String author;
    /** 文档主题元数据。 */
    private String subject;

    /**
     * 创建空的 Word 文档元数据。
     */
    public DocxMetadata() {
    }

    /**
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置标题。
     *
     * @param title 标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取作者。
     *
     * @return 作者
     */
    public String getAuthor() {
        return author;
    }

    /**
     * 设置作者。
     *
     * @param author 作者
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * 获取主题。
     *
     * @return 主题
     */
    public String getSubject() {
        return subject;
    }

    /**
     * 设置主题。
     *
     * @param subject 主题
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }
}
