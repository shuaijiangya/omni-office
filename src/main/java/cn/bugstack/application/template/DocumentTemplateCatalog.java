package cn.bugstack.application.template;

import cn.bugstack.protocol.template.DocumentTemplateSpec;

import java.util.List;

/** 仅管理数据文档模板，不注册或发现现有 BusinessReport。 */
public interface DocumentTemplateCatalog {

    void register(DocumentTemplateSpec template);

    DocumentTemplateSpec require(String templateId, String version);

    List<DocumentTemplateDescriptor> list();
}
