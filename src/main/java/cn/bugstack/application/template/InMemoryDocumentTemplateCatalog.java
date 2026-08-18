package cn.bugstack.application.template;

import cn.bugstack.protocol.template.DocumentTemplateSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 线程安全、显式版本且拒绝覆盖的内存模板目录。 */
public final class InMemoryDocumentTemplateCatalog implements DocumentTemplateCatalog {

    private final Map<DocumentTemplateKey, DocumentTemplateSpec> templates = new ConcurrentHashMap<>();
    private final DocumentTemplateSpecValidator validator;

    public InMemoryDocumentTemplateCatalog() {
        this(new DocumentTemplateSpecValidator());
    }

    public InMemoryDocumentTemplateCatalog(DocumentTemplateSpecValidator validator) {
        if (validator == null) {
            throw new IllegalArgumentException("template definition validator must not be null");
        }
        this.validator = validator;
    }

    @Override
    public void register(DocumentTemplateSpec template) {
        validator.validateOrThrow(template);
        DocumentTemplateSpec snapshot = copy(template);
        DocumentTemplateKey key = new DocumentTemplateKey(snapshot.getTemplateId(), snapshot.getVersion());
        if (templates.putIfAbsent(key, snapshot) != null) {
            throw new IllegalStateException("document template is already registered: " + key);
        }
    }

    @Override
    public DocumentTemplateSpec require(String templateId, String version) {
        if (templateId == null || version == null) {
            throw new IllegalArgumentException("template id and explicit version must not be null");
        }
        DocumentTemplateKey key = new DocumentTemplateKey(templateId, version);
        DocumentTemplateSpec template = templates.get(key);
        if (template == null) {
            throw new IllegalArgumentException("document template is not registered: " + key);
        }
        return copy(template);
    }

    @Override
    public List<DocumentTemplateDescriptor> list() {
        List<DocumentTemplateKey> keys = new ArrayList<>(templates.keySet());
        Collections.sort(keys);
        List<DocumentTemplateDescriptor> descriptors = new ArrayList<>();
        for (DocumentTemplateKey key : keys) {
            DocumentTemplateSpec template = templates.get(key);
            descriptors.add(new DocumentTemplateDescriptor(template.getTemplateId(), template.getVersion(),
                    template.getName(), template.getDescription()));
        }
        return Collections.unmodifiableList(descriptors);
    }

    private DocumentTemplateSpec copy(DocumentTemplateSpec source) {
        DocumentTemplateSpec target = new DocumentTemplateSpec();
        target.setSchemaVersion(source.getSchemaVersion());
        target.setTemplateId(source.getTemplateId());
        target.setVersion(source.getVersion());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setDataSchema(source.getDataSchema() == null ? null : source.getDataSchema().deepCopy());
        target.setDocumentTemplate(source.getDocumentTemplate() == null
                ? null : source.getDocumentTemplate().deepCopy());
        return target;
    }
}
