package cn.bugstack.office.docx.design;

import cn.bugstack.office.docx.builder.SectionBuilder;
import cn.bugstack.office.docx.builder.TableBuilder;
import cn.bugstack.office.docx.design.model.ClassDesignDoc;
import cn.bugstack.office.docx.design.model.FieldDesignDoc;
import cn.bugstack.office.docx.design.model.MethodDesignDoc;
import cn.bugstack.office.docx.design.model.ParameterDesignDoc;
import cn.bugstack.office.docx.design.model.ThrowsDesignDoc;

/**
 * 类设计表格写入器。
 *
 * <p>该类只负责把 {@link ClassDesignDoc} 转换为当前封装层已有的表格节点，
 * 不读取源码，也不直接操作 Aspose 对象。</p>
 */
public class ClassDesignTableWriter {

    /**
     * 创建类设计表格写入器。
     */
    public ClassDesignTableWriter() {
    }

    /**
     * 向指定章节追加类设计表格。
     *
     * <p>输出内容分为类基本信息、属性说明和方法说明三部分。当前源码解析策略只写入
     * 目标类自身的一层字段和方法，避免把父类、接口或内部类成员误认为当前类成员。</p>
     *
     * @param section 章节 Builder
     * @param classDesignDoc 类设计文档模型
     */
    public void write(SectionBuilder section, ClassDesignDoc classDesignDoc) {
        write(section, classDesignDoc, 4);
    }

    /**
     * 向指定章节追加类设计表格，并使用指定层级输出属性和方法标题。
     *
     * @param section 章节 Builder
     * @param classDesignDoc 类设计文档模型
     * @param detailHeadingLevel 属性说明和方法说明的标题层级，范围为 1 到 9
     */
    public void write(SectionBuilder section, ClassDesignDoc classDesignDoc, int detailHeadingLevel) {
        write(section, classDesignDoc, detailHeadingLevel, ClassDesignTableOptions.create());
    }

    /**
     * 向指定章节追加类设计表格，并根据选项决定是否写入属性和方法设计表。
     *
     * @param section 章节 Builder
     * @param classDesignDoc 类设计文档模型
     * @param detailHeadingLevel 属性说明和方法说明的标题层级，范围为 1 到 9
     * @param options 类设计表格选项
     */
    public void write(SectionBuilder section, ClassDesignDoc classDesignDoc, int detailHeadingLevel,
                      ClassDesignTableOptions options) {
        if (detailHeadingLevel < 1 || detailHeadingLevel > 9) {
            throw new IllegalArgumentException("class design detail heading level must be between 1 and 9: "
                    + detailHeadingLevel);
        }
        if (options == null) {
            throw new IllegalArgumentException("class design table options must not be null");
        }
        writeClassInfo(section, classDesignDoc);
        if (options.isIncludeFields()) {
            section.heading(detailHeadingLevel, "属性说明");
            writeProperties(section, classDesignDoc);
        }
        if (options.isIncludeMethods()) {
            section.heading(detailHeadingLevel, "方法说明");
            writeMethods(section, classDesignDoc);
        }
    }

    /**
     * 写入类基本信息表。
     *
     * @param section 章节 Builder
     * @param classDesignDoc 类设计文档模型
     */
    private void writeClassInfo(SectionBuilder section, ClassDesignDoc classDesignDoc) {
        section.table()
                .style("TableNormal")
                .widths(120, 420)
                .headers("项目", "内容")
                .row("类名", classDesignDoc.getClassName())
                .row("类型", classDesignDoc.getKind())
                .row("访问修饰符", dash(classDesignDoc.getModifiers()))
                .row("说明", dash(classDesignDoc.getDescription()))
                .end();
    }

    /**
     * 写入属性说明表。
     *
     * @param section 章节 Builder
     * @param classDesignDoc 类设计文档模型
     */
    private void writeProperties(SectionBuilder section, ClassDesignDoc classDesignDoc) {
        TableBuilder<SectionBuilder> table = section.table()
                .style("TableNormal")
                .widths(45, 130, 130, 90, 220)
                .headers("序号", "属性名", "类型", "访问修饰符", "说明");

        if (classDesignDoc.getFields().isEmpty()) {
            table.row("-", "无", "-", "-", "-").end();
            return;
        }

        int index = 1;
        for (FieldDesignDoc field : classDesignDoc.getFields()) {
            table.row(String.valueOf(index++), field.getName(), field.getType(), dash(field.getModifiers()),
                    dash(field.getDescription()));
        }
        table.end();
    }

    /**
     * 写入方法说明表。
     *
     * @param section 章节 Builder
     * @param classDesignDoc 类设计文档模型
     */
    private void writeMethods(SectionBuilder section, ClassDesignDoc classDesignDoc) {
        TableBuilder<SectionBuilder> table = section.table()
                .style("TableNormal")
                .widths(45, 110, 100, 210, 120, 260)
                .headers("序号", "方法名", "返回值", "参数", "异常", "说明");

        if (classDesignDoc.getMethods().isEmpty()) {
            table.row("-", "无", "-", "-", "-", "-").end();
            return;
        }

        int index = 1;
        for (MethodDesignDoc method : classDesignDoc.getMethods()) {
            table.row(String.valueOf(index++), method.getName(), dash(method.getReturnType()),
                    parameterSummary(method), throwsSummary(method), methodSummary(method));
        }
        table.end();
    }

    /**
     * 汇总方法参数。
     *
     * @param method 方法设计模型
     * @return 参数摘要
     */
    private String parameterSummary(MethodDesignDoc method) {
        if (method.getParameters().isEmpty()) {
            return "-";
        }
        StringBuilder summary = new StringBuilder();
        for (ParameterDesignDoc parameter : method.getParameters()) {
            if (summary.length() > 0) {
                summary.append("\n");
            }
            summary.append(parameter.getName()).append(": ").append(parameter.getType());
            if (!parameter.getDescription().isEmpty()) {
                summary.append(" - ").append(parameter.getDescription());
            }
        }
        return summary.toString();
    }

    /**
     * 汇总方法异常。
     *
     * @param method 方法设计模型
     * @return 异常摘要
     */
    private String throwsSummary(MethodDesignDoc method) {
        if (method.getThrowsList().isEmpty()) {
            return "-";
        }
        StringBuilder summary = new StringBuilder();
        for (ThrowsDesignDoc throwsDoc : method.getThrowsList()) {
            if (summary.length() > 0) {
                summary.append("\n");
            }
            summary.append(throwsDoc.getType());
            if (!throwsDoc.getDescription().isEmpty()) {
                summary.append(" - ").append(throwsDoc.getDescription());
            }
        }
        return summary.toString();
    }

    /**
     * 汇总方法说明和返回值说明。
     *
     * @param method 方法设计模型
     * @return 方法说明摘要
     */
    private String methodSummary(MethodDesignDoc method) {
        String summary = dash(method.getDescription());
        if (!method.getReturnDescription().isEmpty()) {
            summary = "-".equals(summary) ? "" : summary + " ";
            summary += "返回：" + method.getReturnDescription();
        }
        return dash(summary);
    }

    /**
     * 将空文本转换为短横线占位。
     *
     * @param value 原始文本
     * @return 非空文本或 {@code -}
     */
    private String dash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }
}
