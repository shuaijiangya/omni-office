package cn.bugstack.application.csci;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 在缺少经确认的算法资料时，按方法名和可见源码结构形成可复核的设计草稿。
 *
 * <p>草稿始终明确标识为推断，不覆盖 Javadoc 或 AI 已给出的算法、注意事项和流程。
 * 每个非 private 方法均创建 Visio 流程图；已有的显式流程保持不变。</p>
 */
public final class CsciNameBasedDraftEnricher {

    /**
     * 补齐缺失的方法设计草稿及所有非 private 方法的有序流程步骤。
     *
     * @param design 从源码和 SQL 提取的设计数据
     * @return 同一个已补充草稿的设计对象
     */
    public CsciDesign enrich(CsciDesign design) {
        for (CsciDesign.Module module : design.modules) {
            for (CsciDesign.Feature feature : module.features) {
                for (CsciDesign.JavaClass clazz : feature.classes) {
                    for (CsciDesign.Method method : clazz.methods) {
                        if ("private".equals(method.visibility)) continue;
                        Action action = action(method.name);
                        if (blank(method.algorithm)) {
                            method.algorithm = algorithmDraft(feature.name, method, action);
                        }
                        if (blank(method.caution)) {
                            method.caution = cautionDraft(method, action);
                        }
                        ensureFlowSteps(method);
                    }
                }
            }
        }
        return design;
    }

    /**
     * 从方法名选择最接近的业务动作；未知动词保持原方法名供人工核对。
     *
     * @param methodName Java 方法名
     * @return 推断动作及其分类
     */
    private static Action action(String methodName) {
        String lower = methodName.toLowerCase(Locale.ROOT);
        if (starts(lower, "get", "find", "list", "query", "search", "select", "load", "read")) {
            return new Action("查询或读取 " + methodName + " 对应的数据", "query");
        }
        if (starts(lower, "calculate", "calc", "evaluate", "assess", "compute", "analyse", "analyze")) {
            return new Action("计算或评估 " + methodName + " 对应的结果", "calculate");
        }
        if (starts(lower, "save", "create", "add", "insert", "persist")) {
            return new Action("创建并保存 " + methodName + " 对应的数据", "write");
        }
        if (starts(lower, "update", "edit", "modify", "set")) {
            return new Action("更新 " + methodName + " 对应的数据或状态", "write");
        }
        if (starts(lower, "delete", "remove", "clear")) {
            return new Action("删除或清理 " + methodName + " 对应的数据", "write");
        }
        if (starts(lower, "import", "parse", "convert")) {
            return new Action("解析并导入或转换 " + methodName + " 对应的数据", "transform");
        }
        if (starts(lower, "export", "generate", "build", "assemble")) {
            return new Action("生成或组装 " + methodName + " 对应的输出", "transform");
        }
        if (starts(lower, "validate", "verify", "check", "is", "has")) {
            return new Action("校验 " + methodName + " 对应的条件", "check");
        }
        return new Action("执行 " + methodName + " 操作", "other");
    }

    /**
     * 判断方法名是否具有任一指定前缀。
     *
     * @param value 小写方法名
     * @param prefixes 待匹配的动词前缀
     * @return 命中任一前缀时为 true
     */
    private static boolean starts(String value, String... prefixes) {
        for (String prefix : prefixes) if (value.startsWith(prefix)) return true;
        return false;
    }

    /**
     * 生成包含证据边界的算法草稿，不将推断表述为实际执行路径。
     *
     * @param featureName 所属功能名称
     * @param method 待补充的方法
     * @param action 推断出的业务动作
     * @return 带有“设计草稿”标记的算法说明
     */
    private static String algorithmDraft(String featureName, CsciDesign.Method method, Action action) {
        String body = method.sourceBody;
        StringBuilder result = new StringBuilder("设计草稿（依据方法名");
        if (!blank(body)) result.append("及源码结构");
        result.append("，需人工复核）：在“").append(featureName).append("”功能中")
                .append(action.description).append("。");
        if (!blank(body)) {
            if (body.matches("(?s).*\\bif\\s*\\(.*")) result.append("源码包含条件分支；需按实际判断条件核对各分支。");
            if (body.matches("(?s).*\\b(for|while)\\s*\\(.*") || body.contains(".stream()")) {
                result.append("源码包含集合遍历或迭代处理；需核对循环边界与空集合行为。");
            }
            if (body.matches("(?s).*\\bthrow\\s+.*")) result.append("源码存在异常抛出路径。");
        } else {
            result.append("当前仅有方法声明，没有可核对的实现体。");
        }
        return result.toString();
    }

    /**
     * 给推断草稿添加与业务动作相符的待核对事项。
     *
     * @param method 待补充的方法
     * @param action 推断出的业务动作
     * @return 明确需复核的编程注意事项
     */
    private static String cautionDraft(CsciDesign.Method method, Action action) {
        String caution;
        switch (action.category) {
            case "query": caution = "核对查询范围、权限与空结果处理"; break;
            case "calculate": caution = "核对计算口径、精度与边界值"; break;
            case "write": caution = "核对事务边界、重复提交与失败回滚"; break;
            case "transform": caution = "核对输入格式、编码与异常数据处理"; break;
            case "check": caution = "核对判定条件与空值分支"; break;
            default: caution = "核对输入约束、异常路径与输出约定";
        }
        return "设计草稿（需人工复核）：" + caution
                + (blank(method.sourceBody) ? "；当前无实现体可验证。" : "；最终以方法实现及业务规则为准。");
    }

    /**
     * 为尚无显式流程的非 private 方法补充可绘制的步骤。
     *
     * @param method 待检查的方法
     */
    static void ensureFlowSteps(CsciDesign.Method method) {
        if (!"private".equals(method.visibility) && method.flowSteps.isEmpty()) {
            method.flowSteps.addAll(flowSteps(method, action(method.name)));
        }
    }

    /**
     * 构造与可见源码结构一致的 Visio 流程步骤；无方法体时仅显示声明边界。
     *
     * @param method 待绘制的方法
     * @param action 方法名推断的动作
     * @return 至少三个从开始到结束的有序步骤
     */
    private static List<String> flowSteps(CsciDesign.Method method, Action action) {
        List<String> steps = new ArrayList<>();
        steps.add("开始");
        if (!method.parameters.isEmpty()) steps.add("接收输入参数");
        if (blank(method.sourceBody)) {
            steps.add("方法声明待实现");
        } else {
            if (method.sourceBody.matches("(?s).*\\bif\\s*\\(.*")) steps.add("执行条件处理");
            steps.add(shortAction(action.category));
            if (!"void".equals(method.returnType)) steps.add("返回处理结果");
        }
        steps.add("结束");
        return steps;
    }

    /**
     * 为 Visio 节点生成简短动作标签，方法名已由图标题承载。
     *
     * @param category 方法名推断的动作类别
     * @return 适合流程节点宽度的动作标签
     */
    private static String shortAction(String category) {
        switch (category) {
            case "query": return "查询目标数据";
            case "calculate": return "计算评估结果";
            case "write": return "更新业务数据";
            case "transform": return "转换业务数据";
            case "check": return "校验业务条件";
            default: return "执行业务操作";
        }
    }

    /**
     * 判断可选说明是否没有有效内容。
     *
     * @param value 待检查的字符串
     * @return 空值或空白文本返回 true
     */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** 方法名推断的动作及类别。 */
    private static final class Action {
        /** 推断出的业务动作描述。 */
        private final String description;
        /** 动作所属类别。 */
        private final String category;

        /**
         * 创建一个方法名推断动作。
         *
         * @param description 业务动作描述
         * @param category 动作类别
         */
        private Action(String description, String category) {
            this.description = description;
            this.category = category;
        }
    }

}
