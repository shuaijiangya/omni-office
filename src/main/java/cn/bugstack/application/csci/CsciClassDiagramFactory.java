package cn.bugstack.application.csci;

import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramType;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.regex.Pattern;

/** 将同一功能下多个 Java 类及其类型引用转换为类图语义。 */
final class CsciClassDiagramFactory {

    /** Visio 节点最多显示的字段数；全部字段仍保留在 Word 数据元素表。 */
    private static final int MAX_VISIBLE_FIELDS = 14;
    /** Visio 节点最多显示的方法数；全部方法仍保留在 Word 类操作表。 */
    private static final int MAX_VISIBLE_METHODS = 14;
    /** Visio 节点单个成员标签的最长字符数。 */
    private static final int MAX_LABEL_LENGTH = 56;

    /** 工具类不允许实例化。 */
    private CsciClassDiagramFactory() {
    }

    /**
     * 构建包含类成员及类间类型依赖的图定义。
     *
     * @param featureName 功能名称，用作图标题
     * @param classes 同一功能下的类
     * @return 包含输入类及字段、参数、返回值依赖的类图
     */
    static DiagramDefinition create(String featureName, List<CsciDesign.JavaClass> classes) {
        DiagramDefinition.Builder builder = DiagramDefinition.builder(DiagramType.CLASS, featureName + "类图");
        for (CsciDesign.JavaClass clazz : classes) {
            List<String> fields = new ArrayList<>();
            List<String> methods = new ArrayList<>();
            clazz.fields.stream().limit(MAX_VISIBLE_FIELDS).forEach(f -> fields.add(
                    compact(symbol(f.visibility) + " " + f.name + ": " + f.type)));
            if (clazz.fields.size() > MAX_VISIBLE_FIELDS) {
                fields.add("其余 " + (clazz.fields.size() - MAX_VISIBLE_FIELDS) + " 个字段见正文");
            }
            List<CsciDesign.Method> visibleMethods = clazz.methods.stream()
                    .filter(m -> !"private".equals(m.visibility))
                    .sorted(Comparator.comparingInt(m -> visibilityPriority(m.visibility)))
                    .collect(java.util.stream.Collectors.toList());
            visibleMethods.stream()
                    .limit(MAX_VISIBLE_METHODS).forEach(m -> methods.add(
                            compact(symbol(m.visibility) + " " + m.name + "(): " + m.returnType)));
            if (visibleMethods.size() > MAX_VISIBLE_METHODS) {
                methods.add("其余 " + (visibleMethods.size() - MAX_VISIBLE_METHODS) + " 个方法见正文");
            }
            builder.node(DiagramNode.classNode(clazz.name, clazz.name, fields, methods));
        }
        for (CsciDesign.JavaClass source : classes) {
            for (CsciDesign.JavaClass target : classes) {
                if (source == target) continue;
                boolean fieldReference = source.fields.stream().anyMatch(f -> references(f.type, target.name));
                boolean methodReference = source.methods.stream().filter(m -> !"private".equals(m.visibility)).anyMatch(m ->
                        references(m.returnType, target.name)
                                || m.parameters.stream().anyMatch(p -> references(p.type, target.name)));
                if (fieldReference || methodReference) {
                    builder.edge(new DiagramEdge(source.name, target.name, "依赖"));
                }
            }
        }
        return builder.build();
    }

    /**
     * 判断类型表达式是否引用指定类名。
     *
     * @param type 字段、参数或返回值类型表达式
     * @param className 待查找的类名
     * @return 类型表达式包含完整类名时返回 {@code true}
     */
    private static boolean references(String type, String className) {
        return type != null && Pattern.compile("(?<![\\w$])" + Pattern.quote(className) + "(?![\\w$])")
                .matcher(type).find();
    }

    /**
     * 将 Java 访问级别映射为 UML 可见性符号。
     *
     * @param visibility Java 访问级别
     * @return 对应的 UML 符号
     */
    private static String symbol(String visibility) {
        if ("public".equals(visibility)) return "+";
        if ("protected".equals(visibility)) return "#";
        if ("private".equals(visibility)) return "-";
        return "~";
    }

    /**
     * 对类图中的方法按可见性排序，使对外接口优先展示。
     *
     * @param visibility Java 访问级别
     * @return 越小越优先的排序值
     */
    private static int visibilityPriority(String visibility) {
        if ("public".equals(visibility)) return 0;
        if ("protected".equals(visibility)) return 1;
        if ("package".equals(visibility)) return 2;
        return 3;
    }

    /**
     * 缩短过长的成员标签，完整声明仍在 Word 表格中。
     *
     * @param label 原始 UML 成员标签
     * @return 适合 Visio 节点宽度的标签
     */
    private static String compact(String label) {
        return label.length() <= MAX_LABEL_LENGTH ? label
                : label.substring(0, MAX_LABEL_LENGTH - 1) + "…";
    }
}
