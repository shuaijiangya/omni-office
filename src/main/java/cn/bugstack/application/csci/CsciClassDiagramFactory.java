package cn.bugstack.application.csci;

import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 将同一功能下多个 Java 类及其类型引用转换为类图语义。 */
final class CsciClassDiagramFactory {

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
            clazz.fields.forEach(f -> fields.add(symbol(f.visibility) + " " + f.name + ": " + f.type));
            clazz.methods.forEach(m -> methods.add(symbol(m.visibility) + " " + m.name
                    + "(): " + m.returnType));
            builder.node(DiagramNode.classNode(clazz.name, clazz.name, fields, methods));
        }
        for (CsciDesign.JavaClass source : classes) {
            for (CsciDesign.JavaClass target : classes) {
                if (source == target) continue;
                boolean fieldReference = source.fields.stream().anyMatch(f -> references(f.type, target.name));
                boolean methodReference = source.methods.stream().anyMatch(m ->
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
}
