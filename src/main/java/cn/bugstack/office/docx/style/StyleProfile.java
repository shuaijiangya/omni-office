package cn.bugstack.office.docx.style;

/**
 * 文档样式画像。
 *
 * <p>一个画像代表一套完整的文档标准，例如默认办公文档、GJB 438C 文档或项目自定义文档。
 * 业务侧通过切换画像获得整套样式，而不是散落地修改各个字体、行距和段距。</p>
 */
public interface StyleProfile {

    /**
     * 创建该画像对应的样式注册表。
     *
     * @return 样式注册表
     */
    StyleRegistry createRegistry();
}
