package cn.bugstack.export.example.composable.model;

/** 模块数据对象的内部校验工具。 */
final class ModuleDataSupport {

    private ModuleDataSupport() {
    }

    /**
     * 校验并规范化当前示例要求的纯文本字段。
     *
     * @param value 原始文本
     * @param name 字段名称
     * @return 去除首尾空白后的文本
     */
    static String requiredText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
