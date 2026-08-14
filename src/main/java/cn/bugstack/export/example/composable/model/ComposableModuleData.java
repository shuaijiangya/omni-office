package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.example.composable.ComposableReportModule;

/**
 * 可组合报告模块数据对象的统一标记。
 *
 * <p>该接口只用于组合入参识别模块类型。真正的内容字段由八个具体数据对象分别定义。</p>
 */
public interface ComposableModuleData {

    /**
     * 获取该数据对象所属的模块。
     *
     * @return 模块类型
     */
    ComposableReportModule getModule();
}
