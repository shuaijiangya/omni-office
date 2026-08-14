package cn.bugstack.export.module;

import cn.bugstack.export.document.ReportSection;

/**
 * 可插拔的报告模块策略。
 *
 * @param <T> 模块输入数据类型
 */
public interface ReportModule<T> {

    /**
     * 获取模块静态描述。
     *
     * @return 模块描述
     */
    ModuleDescriptor<T> descriptor();

    /**
     * 组装模块语义章节。
     *
     * @param context 模块上下文
     * @param data 模块输入数据
     * @return 语义章节
     */
    ReportSection compose(ReportModuleContext context, T data);
}
