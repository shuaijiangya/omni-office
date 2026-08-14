package cn.bugstack.export.template.cover;

/**
 * 文档修改记录封面中的单条业务数据。
 */
public final class DocumentModificationRecord {

    /** 修改人。 */
    private final String modifiedBy;
    /** 修改时间，保留调用方需要的展示格式。 */
    private final String modificationTime;

    public DocumentModificationRecord(String modifiedBy, String modificationTime) {
        this.modifiedBy = requiredText(modifiedBy, "modified by");
        this.modificationTime = requiredText(modificationTime, "modification time");
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public String getModificationTime() {
        return modificationTime;
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
