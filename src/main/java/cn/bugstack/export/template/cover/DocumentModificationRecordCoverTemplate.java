package cn.bugstack.export.template.cover;

import cn.bugstack.export.definition.ReportCoverTemplate;
import cn.bugstack.export.document.ReportElement;
import cn.bugstack.export.document.ReportTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 文档修改记录表格封面模板。
 *
 * <p>该类是可直接用于业务代码的正式模板，不依赖示例包。表格固定包含“序号、修改人、
 * 修改时间”三列；序号按记录传入顺序自动生成。没有记录时默认生成一行空白填写行。</p>
 */
public final class DocumentModificationRecordCoverTemplate implements ReportCoverTemplate {

    /** 模板默认文档名称。 */
    public static final String DEFAULT_DOCUMENT_NAME = "文档修改记录";

    /** 文档名称，同时作为整份报告的主标题。 */
    private final String documentName;
    /** 动态修改记录。 */
    private final List<DocumentModificationRecord> records;
    /** 无数据时生成的空白填写行数量。 */
    private final int blankRowCount;

    private DocumentModificationRecordCoverTemplate(Builder builder) {
        this.documentName = builder.documentName;
        this.records = Collections.unmodifiableList(new ArrayList<>(builder.records));
        this.blankRowCount = builder.blankRowCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getDocumentName() {
        return documentName;
    }

    public List<DocumentModificationRecord> getRecords() {
        return records;
    }

    @Override
    public List<ReportElement> createElements() {
        ReportTable table = new ReportTable();
        table.setStyleName("TableHeader");
        table.setColumnWidths(new double[]{70D, 180D, 250D});
        table.setHeaders(Arrays.asList("序号", "修改人", "修改时间"));
        List<List<String>> rows = new ArrayList<>();
        for (int index = 0; index < records.size(); index++) {
            DocumentModificationRecord record = records.get(index);
            rows.add(Arrays.asList(String.valueOf(index + 1),
                    record.getModifiedBy(), record.getModificationTime()));
        }
        if (records.isEmpty()) {
            for (int index = 0; index < blankRowCount; index++) {
                rows.add(Arrays.asList(String.valueOf(index + 1), "", ""));
            }
        }
        table.setRows(rows);
        return Collections.singletonList(table);
    }

    /** 文档修改记录封面模板构建器。 */
    public static final class Builder {

        private String documentName = DEFAULT_DOCUMENT_NAME;
        private final List<DocumentModificationRecord> records = new ArrayList<>();
        private int blankRowCount = 1;

        /** 设置作为报告主标题使用的文档名称。 */
        public Builder documentName(String documentName) {
            this.documentName = requiredText(documentName, "document name");
            return this;
        }

        /** 增加一条修改记录，序号由模板自动生成。 */
        public Builder record(DocumentModificationRecord record) {
            if (record == null) {
                throw new IllegalArgumentException("document modification record must not be null");
            }
            this.records.add(record);
            return this;
        }

        /** 增加一条修改记录，序号由模板自动生成。 */
        public Builder record(String modifiedBy, String modificationTime) {
            return record(new DocumentModificationRecord(modifiedBy, modificationTime));
        }

        /** 设置无记录时预留的空白填写行数，默认一行。 */
        public Builder blankRows(int blankRowCount) {
            if (blankRowCount < 1) {
                throw new IllegalArgumentException("blank row count must be greater than 0");
            }
            this.blankRowCount = blankRowCount;
            return this;
        }

        public DocumentModificationRecordCoverTemplate build() {
            return new DocumentModificationRecordCoverTemplate(this);
        }

        private static String requiredText(String value, String name) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}
