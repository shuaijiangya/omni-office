package cn.bugstack.office.docx.design.parser;

import cn.bugstack.office.docx.design.ClassDesignTableOptions;
import cn.bugstack.office.docx.design.model.ClassDesignDoc;

/**
 * 类元数据解析策略。
 *
 * <p>实现类可以基于源码、反射、JavaParser、QDox 或其他元数据来源生成统一的
 * {@link ClassDesignDoc} 模型。</p>
 */
public interface ClassMetadataParser {

    /**
     * 根据类设计表格选项解析类元数据。
     *
     * @param options 类设计表格选项
     * @return 类设计文档模型
     */
    ClassDesignDoc parse(ClassDesignTableOptions options);
}
