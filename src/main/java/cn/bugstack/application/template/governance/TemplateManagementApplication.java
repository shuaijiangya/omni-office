package cn.bugstack.application.template.governance;

import cn.bugstack.application.schema.JsonSchemaCompatibilityChecker;
import cn.bugstack.application.schema.SchemaCompatibilityResult;
import cn.bugstack.protocol.template.DocumentTemplateSpec;
import cn.bugstack.protocol.template.DocumentTemplateSpecJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.stream.Collectors;

/** 模板管理用例层；HTTP/CLI 只负责身份和协议映射。 */
public final class TemplateManagementApplication {

    private final FileDocumentTemplateCatalog catalog;
    private final DocumentTemplateSpecJsonCodec codec = new DocumentTemplateSpecJsonCodec();
    private final JsonSchemaCompatibilityChecker compatibility = new JsonSchemaCompatibilityChecker();
    private final TemplatePublicationGate publicationGate;

    /**
     * 创建模板管理应用服务。
     *
     * @param catalog 模板治理目录
     */
    public TemplateManagementApplication(FileDocumentTemplateCatalog catalog) {
        this(catalog, null);
    }

    /** 创建强制执行真实渲染发布门禁的模板管理服务。 */
    public TemplateManagementApplication(FileDocumentTemplateCatalog catalog,
                                         TemplatePublicationGate publicationGate) {
        if (catalog == null) throw new IllegalArgumentException("template catalog is required");
        this.catalog = catalog;
        this.publicationGate = publicationGate;
    }

    /**
     * 创建不可覆盖的模板草稿版本。
     *
     * @param templateJson 完整模板 JSON
     * @param actor 创建者
     * @return 新建草稿版本
     */
    public TemplateRevision createDraft(JsonNode templateJson, String actor) {
        if (templateJson == null || !templateJson.isObject()) {
            throw new IllegalArgumentException("template definition must be a JSON object");
        }
        return catalog.createDraft(codec.read(templateJson.toString()), actor);
    }

    /**
     * 查询模板版本及治理元数据。
     *
     * @param templateId 模板 ID
     * @param version 模板版本
     * @return 模板版本快照
     */
    public TemplateRevision get(String templateId, String version) {
        return catalog.getRevision(templateId, version);
    }

    /**
     * 查询模板版本。
     *
     * @param status 可选生命周期状态
     * @param limit 最大返回数量，范围为 1～100
     * @return 模板版本列表
     */
    public List<TemplateRevision> list(TemplateLifecycleStatus status, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("template list limit is invalid");
        return catalog.listRevisions().stream()
                .filter(item -> status == null || item.getStatus() == status)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 提交草稿或已驳回版本进入审核。
     *
     * @param templateId 模板 ID
     * @param version 模板版本
     * @param actor 操作者
     * @return 审核中版本
     */
    public TemplateRevision submit(String templateId, String version, String actor) {
        return catalog.submit(templateId, version, actor);
    }

    /**
     * 审批并发布模板版本。
     *
     * @param templateId 模板 ID
     * @param version 模板版本
     * @param actor 审核者；不能与创建者相同
     * @param comment 可选审核意见
     * @return 已发布版本
     */
    public TemplateRevision approve(String templateId, String version, String actor, String comment) {
        if (publicationGate != null) {
            throw new IllegalArgumentException("template publication requires sampleData");
        }
        return catalog.approve(templateId, version, actor, comment);
    }

    /** 使用审核样例完成真实渲染门禁后发布模板。 */
    public TemplateRevision approve(String templateId, String version, String actor,
                                    String comment, JsonNode sampleData) {
        if (publicationGate == null) return catalog.approve(templateId, version, actor, comment);
        TemplateRevision candidate = catalog.getRevision(templateId, version);
        TemplatePublicationEvidence evidence = publicationGate.validate(candidate.getTemplate(), sampleData);
        return catalog.approve(templateId, version, actor, comment, evidence);
    }

    /**
     * 驳回审核中的模板版本。
     *
     * @param templateId 模板 ID
     * @param version 模板版本
     * @param actor 审核者
     * @param comment 必填驳回原因
     * @return 已驳回版本
     */
    public TemplateRevision reject(String templateId, String version, String actor, String comment) {
        return catalog.reject(templateId, version, actor, comment);
    }

    /**
     * 退役已发布模板版本。
     *
     * @param templateId 模板 ID
     * @param version 模板版本
     * @param actor 操作者
     * @param reason 必填退役原因
     * @return 已退役版本
     */
    public TemplateRevision retire(String templateId, String version, String actor, String reason) {
        return catalog.retire(templateId, version, actor, reason);
    }

    /**
     * 比较同一模板两个版本的数据 Schema 是否向后兼容。
     *
     * @param templateId 模板 ID
     * @param fromVersion 基准版本
     * @param toVersion 候选版本
     * @return Schema 兼容性结果
     */
    public SchemaCompatibilityResult compare(String templateId, String fromVersion, String toVersion) {
        DocumentTemplateSpec previous = catalog.getRevision(templateId, fromVersion).getTemplate();
        DocumentTemplateSpec candidate = catalog.getRevision(templateId, toVersion).getTemplate();
        return compatibility.backwardCompatible(previous.getDataSchema(), candidate.getDataSchema());
    }
}
