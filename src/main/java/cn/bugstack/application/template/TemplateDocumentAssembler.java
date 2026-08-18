package cn.bugstack.application.template;

import cn.bugstack.application.document.DocumentSpecLimits;
import cn.bugstack.application.document.DocumentSpecValidationResult;
import cn.bugstack.application.document.DocumentSpecValidator;
import cn.bugstack.application.document.DocumentSpecViolation;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentSpecJsonCodec;
import cn.bugstack.protocol.template.DocumentTemplateSpec;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** 模板数据校验、JSON 结构展开和 DocumentSpec 二次校验流水线。 */
public final class TemplateDocumentAssembler {

    private final TemplateDataValidator dataValidator;
    private final DocumentTemplateExpander expander;
    private final DocumentSpecJsonCodec documentCodec;
    private final DocumentSpecValidator documentValidator;

    public TemplateDocumentAssembler() {
        this(new JsonSchemaTemplateDataValidator(), new DocumentTemplateExpander(),
                new DocumentSpecJsonCodec(), new DocumentSpecValidator(DocumentSpecLimits.defaults(), true));
    }

    public TemplateDocumentAssembler(TemplateDataValidator dataValidator, DocumentTemplateExpander expander,
                                     DocumentSpecJsonCodec documentCodec,
                                     DocumentSpecValidator documentValidator) {
        if (dataValidator == null || expander == null || documentCodec == null || documentValidator == null) {
            throw new IllegalArgumentException("template assembler dependencies must not be null");
        }
        this.dataValidator = dataValidator;
        this.expander = expander;
        this.documentCodec = documentCodec;
        this.documentValidator = documentValidator;
    }

    public DocumentSpec assemble(DocumentTemplateSpec template, JsonNode data) {
        if (template == null) {
            throw new IllegalArgumentException("document template must not be null");
        }
        dataValidator.validateOrThrow(template.getDataSchema(), data);
        JsonNode expanded = expander.expand(template.getDocumentTemplate(), data);
        DocumentSpec document;
        try {
            document = documentCodec.read(expanded.toString());
        } catch (IllegalArgumentException e) {
            List<DocumentTemplateViolation> errors = new ArrayList<>();
            errors.add(new DocumentTemplateViolation("/documentTemplate", "INVALID_DOCUMENT_SPEC", e.getMessage()));
            throw new DocumentTemplateValidationException("expanded template is not a DocumentSpec", errors);
        }
        DocumentSpecValidationResult result = documentValidator.validate(document);
        if (!result.isValid()) {
            List<DocumentTemplateViolation> errors = new ArrayList<>();
            for (DocumentSpecViolation violation : result.getViolations()) {
                errors.add(new DocumentTemplateViolation("/documentTemplate" + violation.getPath(),
                        violation.getCode(), violation.getMessage()));
            }
            throw new DocumentTemplateValidationException("expanded DocumentSpec is invalid", errors);
        }
        return document;
    }
}
