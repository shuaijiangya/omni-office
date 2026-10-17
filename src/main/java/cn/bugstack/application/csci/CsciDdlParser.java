package cn.bugstack.application.csci;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析可审计的 {@code CREATE TABLE} DDL 子集，不通过语言模型猜测字段或约束。
 *
 * <p>支持常见 MySQL/PostgreSQL 标识符、列注释、表注释和独立主键声明。</p>
 */
public final class CsciDdlParser {

    /** 定位建表语句及物理表名。 */
    private static final Pattern CREATE = Pattern.compile(
            "(?is)\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([`\"\\w.]+)\\s*\\(");
    /** 拆分单个字段声明的名称与剩余定义。 */
    private static final Pattern COLUMN = Pattern.compile("(?is)^([`\"\\w]+)\\s+(.+)$");
    /** 提取内联 SQL 注释。 */
    private static final Pattern COMMENT = Pattern.compile("(?is)\\bCOMMENT\\s*(?:=|ON)?\\s*'((?:''|[^'])*)'");
    /** 定位独立的表注释语句。 */
    private static final Pattern TABLE_COMMENT = Pattern.compile(
            "(?is)\\bCOMMENT\\s+ON\\s+TABLE\\s+([`\"\\w.]+)\\s+IS\\s+'((?:''|[^'])*)'");
    /** 定位独立的字段注释语句。 */
    private static final Pattern COLUMN_COMMENT = Pattern.compile(
            "(?is)\\bCOMMENT\\s+ON\\s+COLUMN\\s+([`\"\\w.]+)\\s+IS\\s+'((?:''|[^'])*)'");

    /**
     * 解析 SQL 中的所有物理表。
     *
     * @param ddl SQL DDL 内容
     * @return 物理表列表
     * @throws IllegalArgumentException 建表语句或字段声明无法解析时抛出
     */
    public List<CsciDesign.DbTable> parse(String ddl) {
        String sql = ddl.replaceAll("(?m)^\\s*--[^\\r\\n]*", "")
                .replaceAll("(?s)/\\*.*?\\*/", "");
        List<CsciDesign.DbTable> tables = new ArrayList<>();
        Matcher matcher = CREATE.matcher(sql);
        while (matcher.find()) {
            int open = matcher.end() - 1;
            int close = matchingParen(sql, open);
            if (close < 0) {
                throw new IllegalArgumentException("CREATE TABLE 缺少右括号: " + matcher.group(1));
            }
            CsciDesign.DbTable table = new CsciDesign.DbTable();
            table.physicalName = unquote(matcher.group(1));
            String tail = sql.substring(close + 1, statementEnd(sql, close + 1));
            table.description = comment(tail);
            table.logicalName = table.description.isEmpty() ? table.physicalName : table.description;
            List<String> constraints = new ArrayList<>();
            for (String item : splitTopLevel(sql.substring(open + 1, close))) {
                String declaration = item.trim();
                if (declaration.isEmpty()) continue;
                String upper = declaration.toUpperCase(Locale.ROOT);
                if (upper.startsWith("PRIMARY KEY") || upper.startsWith("CONSTRAINT ")
                        || upper.startsWith("UNIQUE ") || upper.startsWith("FOREIGN KEY")
                        || upper.startsWith("CHECK ")) {
                    constraints.add(declaration);
                    continue;
                }
                Matcher column = COLUMN.matcher(declaration);
                if (!column.matches()) {
                    throw new IllegalArgumentException("无法解析列声明: " + declaration);
                }
                CsciDesign.Column value = new CsciDesign.Column();
                value.name = unquote(column.group(1));
                String remainder = column.group(2).trim();
                int typeEnd = typeEnd(remainder);
                value.type = remainder.substring(0, typeEnd).trim();
                if (value.type.isEmpty()) {
                    throw new IllegalArgumentException("字段缺少数据类型: " + value.name);
                }
                String suffix = remainder.substring(typeEnd).trim();
                value.comment = comment(suffix);
                value.constraint = suffix.replaceAll("(?is)\\bCOMMENT\\s*'((?:''|[^'])*)'", "")
                        .replaceAll("\\s+", " ").trim();
                table.columns.add(value);
            }
            for (String constraint : constraints) {
                Matcher primary = Pattern.compile("(?is)PRIMARY\\s+KEY\\s*\\(([^)]*)\\)").matcher(constraint);
                if (primary.find()) {
                    for (String name : primary.group(1).split(",")) {
                        String key = unquote(name.trim());
                        table.columns.stream().filter(c -> c.name.equalsIgnoreCase(key)).forEach(c ->
                                c.constraint = c.constraint.isEmpty() ? "PRIMARY KEY" : c.constraint + " PRIMARY KEY");
                    }
                }
            }
            if (table.columns.isEmpty()) {
                throw new IllegalArgumentException("表没有字段: " + table.physicalName);
            }
            tables.add(table);
            matcher.region(close + 1, sql.length());
        }
        if (tables.isEmpty() && !ddl.isBlank()) {
            throw new IllegalArgumentException("DDL 中未找到 CREATE TABLE");
        }
        Matcher tableComment = TABLE_COMMENT.matcher(sql);
        while (tableComment.find()) {
            String name = unquote(tableComment.group(1));
            for (CsciDesign.DbTable table : tables) {
                if (table.physicalName.equalsIgnoreCase(name)) {
                    table.description = tableComment.group(2).replace("''", "'");
                    table.logicalName = table.description;
                }
            }
        }
        Matcher columnComment = COLUMN_COMMENT.matcher(sql);
        while (columnComment.find()) {
            String qualified = unquote(columnComment.group(1));
            int dot = qualified.lastIndexOf('.');
            if (dot < 0) continue;
            String tableName = qualified.substring(0, dot);
            String columnName = qualified.substring(dot + 1);
            for (CsciDesign.DbTable table : tables) {
                if (table.physicalName.equalsIgnoreCase(tableName)) {
                    table.columns.stream().filter(c -> c.name.equalsIgnoreCase(columnName))
                            .forEach(c -> c.comment = columnComment.group(2).replace("''", "'"));
                }
            }
        }
        return tables;
    }

    /**
     * 查找从指定位置开始的 SQL 语句结束位置。
     *
     * @param sql 完整 SQL 文本
     * @param start 起始偏移量
     * @return 分号位置；没有分号时返回文本长度
     */
    private static int statementEnd(String sql, int start) {
        int semicolon = sql.indexOf(';', start);
        return semicolon < 0 ? sql.length() : semicolon;
    }

    /**
     * 查找建表字段列表的配对右括号，忽略字符串中的括号。
     *
     * @param text 待扫描文本
     * @param open 左括号位置
     * @return 配对右括号位置；未找到时返回 -1
     */
    private static int matchingParen(String text, int open) {
        int depth = 0;
        boolean quote = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                if (quote && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                quote = !quote;
            }
            if (quote) continue;
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    /**
     * 按顶层逗号分割字段与约束声明。
     *
     * @param text 建表语句括号内的内容
     * @return 保持原有顺序的声明片段
     */
    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean quote = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                if (quote && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                quote = !quote;
            }
            if (quote) continue;
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    /**
     * 定位字段类型与约束之间的边界。
     *
     * @param text 字段名后的声明内容
     * @return 类型结束偏移量
     */
    private static int typeEnd(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (Character.isWhitespace(c) && depth == 0) {
                String tail = text.substring(i).trim().toUpperCase(Locale.ROOT);
                if (tail.matches("^(NOT\\b|NULL\\b|DEFAULT\\b|PRIMARY\\b|UNIQUE\\b|COMMENT\\b|REFERENCES\\b|CHECK\\b|AUTO_INCREMENT\\b|GENERATED\\b|COLLATE\\b|CONSTRAINT\\b).*")) {
                    return i;
                }
            }
        }
        return text.length();
    }

    /**
     * 提取 SQL 片段中的内联注释。
     *
     * @param text SQL 片段
     * @return 还原单引号后的注释；不存在时返回空串
     */
    private static String comment(String text) {
        Matcher match = COMMENT.matcher(text);
        return match.find() ? match.group(1).replace("''", "'") : "";
    }

    /**
     * 去除标识符中的反引号和双引号。
     *
     * @param value 原始标识符
     * @return 未加引号的标识符
     */
    private static String unquote(String value) {
        return value.replace("`", "").replace("\"", "");
    }
}
