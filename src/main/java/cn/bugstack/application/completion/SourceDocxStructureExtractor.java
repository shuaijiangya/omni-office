package cn.bugstack.application.completion;

import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.Cell;
import com.aspose.words.CellMerge;
import com.aspose.words.Document;
import com.aspose.words.FileFormatInfo;
import com.aspose.words.FileFormatUtil;
import com.aspose.words.LoadFormat;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Row;
import com.aspose.words.Run;
import com.aspose.words.Section;
import com.aspose.words.Table;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从上传的 .doc 或 .docx 中提取供 AI 规划使用的安全结构快照。
 */
public final class SourceDocxStructureExtractor {

    private static final long MAX_SOURCE_BYTES = 100L * 1024L * 1024L;
    private static final byte[] COMPOUND_FILE_SIGNATURE = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    /**
     * 提取 Word 正文段落与表格结构。
     *
     * @param source .doc 或 .docx 文件
     * @return 文档结构快照
     */
    public DocumentStructureSnapshot extract(Path source) {
        validateSource(source);
        if (isLegacyDoc(source)) validateLegacyDoc(source);
        else new SourceDocxPackageValidator().validate(source);
        try {
            AsposeWordsLicenseLoader.applyConfiguredLicense();
            Document document = new Document(source.toString());
            NodeCollection tableNodes = document.getChildNodes(NodeType.TABLE, true);
            List<SourceTableSnapshot> tables = new ArrayList<>();
            List<SourceParagraphSnapshot> paragraphs = new ArrayList<>();
            for (int sectionIndex = 0; sectionIndex < document.getSections().getCount(); sectionIndex++) {
                Section section = document.getSections().get(sectionIndex);
                NodeCollection bodyChildren = section.getBody().getChildNodes(NodeType.ANY, false);
                for (int childIndex = 0; childIndex < bodyChildren.getCount(); childIndex++) {
                    Node child = bodyChildren.get(childIndex);
                    if (!(child instanceof Paragraph)) {
                        continue;
                    }
                    Paragraph paragraph = (Paragraph) child;
                    boolean containsObjects = false;
                    List<SourceRunSnapshot> runs = new ArrayList<>();
                    NodeCollection paragraphChildren = paragraph.getChildNodes(NodeType.ANY, false);
                    for (int nodeIndex = 0; nodeIndex < paragraphChildren.getCount(); nodeIndex++) {
                        Node paragraphChild = paragraphChildren.get(nodeIndex);
                        if (paragraphChild instanceof Run) {
                            Run run = (Run) paragraphChild;
                            runs.add(new SourceRunSnapshot(runs.size(), run.getText(),
                                    isPlainTextRun(paragraphChild)));
                        }
                        if (!isPlainTextRun(paragraphChild)) {
                            containsObjects = true;
                        }
                    }
                    paragraphs.add(new SourceParagraphSnapshot(
                            "paragraph-s" + sectionIndex + "-b" + childIndex,
                            sectionIndex, childIndex, paragraph.getParagraphFormat().getStyleName(),
                            cleanText(paragraph.getText()), containsObjects, runs));
                }
            }
            Map<String, Integer> fingerprintOccurrences = new HashMap<>();
            for (int index = 0; index < tableNodes.getCount(); index++) {
                Table table = (Table) tableNodes.get(index);
                List<List<String>> grid = extractGrid(table);
                if (grid.isEmpty()) {
                    continue;
                }
                String caption = adjacentCaption(table);
                List<String> headers = grid.get(0);
                List<List<String>> rows = new ArrayList<>();
                for (int row = 1; row < grid.size(); row++) {
                    rows.add(grid.get(row));
                }
                String fingerprint = digest(canonicalTableKey(caption, headers));
                int occurrence = fingerprintOccurrences.merge(fingerprint, 1, Integer::sum);
                String tableId = "table-" + fingerprint.substring(0, 16)
                        + (occurrence == 1 ? "" : "-" + occurrence);
                tables.add(new SourceTableSnapshot(tableId, fingerprint, index,
                        sectionIndex(document, table), caption, headers, rows,
                        hasMergedCells(table) || hasRaggedGrid(grid), hasNestedTables(table),
                        hasComplexContent(table),
                        extractWritableCells(table)));
            }
            return new DocumentStructureSnapshot(source.getFileName().toString(), fileDigest(source),
                    tables, paragraphs);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to extract DOCX structure", e);
        }
    }

    private boolean hasRaggedGrid(List<List<String>> grid) {
        if (grid.isEmpty()) return false;
        int width = grid.get(0).size();
        for (List<String> row : grid) {
            if (row.size() != width) return true;
        }
        return false;
    }

    private void validateSource(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("source DOCX must be a regular file");
        }
        try {
            long size = Files.size(source);
            if (size <= 0L || size > MAX_SOURCE_BYTES) {
                throw new IllegalArgumentException("source DOCX size must be between 1 byte and 100 MiB");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to inspect source DOCX", e);
        }
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".docx") && !name.endsWith(".doc")) {
            throw new IllegalArgumentException("only .doc and .docx source documents are supported");
        }
    }

    /** 旧版 Word 二进制文件不能按 OOXML ZIP 检查，需验证复合文件签名与真实格式。 */
    private void validateLegacyDoc(Path source) {
        try (InputStream input = Files.newInputStream(source)) {
            if (!Arrays.equals(COMPOUND_FILE_SIGNATURE, input.readNBytes(COMPOUND_FILE_SIGNATURE.length))) {
                throw new IllegalArgumentException("file is not a binary Word .doc document");
            }
            FileFormatInfo format = FileFormatUtil.detectFileFormat(source.toString());
            if (format.getLoadFormat() != LoadFormat.DOC || format.isEncrypted()
                    || format.hasMacros()) {
                throw new IllegalArgumentException("only unencrypted, macro-free binary Word .doc is supported");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to inspect binary Word .doc", e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("binary Word .doc format cannot be inspected", e);
        }
    }

    private boolean isLegacyDoc(Path source) {
        return source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".doc");
    }

    private List<List<String>> extractGrid(Table table) {
        List<List<String>> grid = new ArrayList<>();
        for (Row row : table.getRows()) {
            List<String> values = new ArrayList<>();
            for (Cell cell : row.getCells()) {
                values.add(cleanText(cell.getText()));
            }
            grid.add(values);
        }
        return grid;
    }

    private String adjacentCaption(Table table) {
        Node previous = table.getPreviousSibling();
        while (previous != null && previous.getNodeType() != NodeType.PARAGRAPH) {
            previous = previous.getPreviousSibling();
        }
        if (previous instanceof Paragraph) {
            return cleanText(((Paragraph) previous).getText());
        }
        return "";
    }

    private int sectionIndex(Document document, Table table) {
        Node ancestor = table.getAncestor(NodeType.SECTION);
        if (!(ancestor instanceof Section)) {
            return -1;
        }
        for (int index = 0; index < document.getSections().getCount(); index++) {
            if (document.getSections().get(index) == ancestor) {
                return index;
            }
        }
        return -1;
    }

    private boolean hasMergedCells(Table table) {
        for (Row row : table.getRows()) {
            for (Cell cell : row.getCells()) {
                if (cell.getCellFormat().getHorizontalMerge() != CellMerge.NONE
                        || cell.getCellFormat().getVerticalMerge() != CellMerge.NONE) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasNestedTables(Table table) {
        NodeCollection descendants = table.getChildNodes(NodeType.TABLE, true);
        return table.getAncestor(NodeType.CELL) != null || descendants.getCount() > 0;
    }

    private boolean hasComplexContent(Table table) {
        for (Row row : table.getRows()) {
            for (Cell cell : row.getCells()) {
                if (cell.getParagraphs().getCount() != 1) return true;
                NodeCollection children = cell.getFirstParagraph().getChildNodes(NodeType.ANY, false);
                for (int index = 0; index < children.getCount(); index++) {
                    if (!isPlainTextRun(children.get(index))) return true;
                }
            }
        }
        return false;
    }

    private List<List<Boolean>> extractWritableCells(Table table) {
        List<List<Boolean>> result = new ArrayList<>();
        for (Row row : table.getRows()) {
            List<Boolean> writable = new ArrayList<>();
            for (Cell cell : row.getCells()) {
                writable.add(isPlainWritableCell(cell));
            }
            result.add(writable);
        }
        return result;
    }

    private boolean isPlainWritableCell(Cell cell) {
        if (cell.getCellFormat().getHorizontalMerge() == CellMerge.PREVIOUS
                || cell.getCellFormat().getVerticalMerge() == CellMerge.PREVIOUS) {
            return false;
        }
        NodeCollection blocks = cell.getChildNodes(NodeType.ANY, false);
        if (blocks.getCount() != 1 || !(blocks.get(0) instanceof Paragraph)) return false;
        NodeCollection children = ((Paragraph) blocks.get(0)).getChildNodes(NodeType.ANY, false);
        for (int index = 0; index < children.getCount(); index++) {
            if (!isPlainTextRun(children.get(index))) return false;
        }
        return true;
    }

    private boolean isPlainTextRun(Node node) {
        if (!(node instanceof Run)) return false;
        String text = ((Run) node).getText();
        if (text == null) return true;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < 0x20 && character != '\t' && character != '\n' && character != '\r') {
                return false;
            }
        }
        return true;
    }

    private String canonicalTableKey(String caption, List<String> headers) {
        StringBuilder value = new StringBuilder(normalize(caption));
        for (String header : headers) {
            value.append('|').append(normalize(header));
        }
        return value.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replace("\u0007", "")
                .replace('\r', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
    }

    private String fileDigest(Path source) throws IOException {
        return digest(Files.readAllBytes(source));
    }

    private String digest(String value) {
        return digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String digest(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
