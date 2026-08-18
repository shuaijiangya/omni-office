package cn.bugstack.application.external;

import cn.bugstack.application.artifact.DiagramArtifactReference;
import cn.bugstack.application.artifact.LocalDiagramArtifactStore;
import cn.bugstack.application.artifact.ResolvedDiagramArtifact;
import cn.bugstack.application.document.DocumentGenerationApplication;
import cn.bugstack.application.template.DocumentTemplateApplication;
import cn.bugstack.application.template.DocumentTemplateCatalog;
import cn.bugstack.application.template.DocumentTemplateDescriptor;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.diagram.DiagramSpec;
import cn.bugstack.protocol.diagram.DiagramSpecJsonCodec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentSpecJsonCodec;
import cn.bugstack.protocol.template.DocumentTemplateSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

/**
 * M5 共享外部工具门面。
 *
 * <p>Function Calling 与 MCP 都只调用本门面。工具层不包含模型逻辑，也不绕过 M1～M3 的
 * JSON、DocumentSpec、DiagramSpec、模板数据和 export 校验。生成文件进入受控工件库，
 * 外部只能拿到资源 URI，不能指定或获知服务器路径。</p>
 */
public final class ExternalDocumentToolApplication {

    public static final String LIST_TEMPLATES = "omni_templates_list";
    public static final String GET_TEMPLATE_SCHEMA = "omni_template_schema";
    public static final String EXPORT_TEMPLATE = "omni_template_export";
    public static final String EXPORT_DOCUMENT = "omni_document_export";
    public static final String GENERATE_DIAGRAM = "omni_diagram_generate";

    private final ObjectMapper mapper;
    private final DocumentSpecJsonCodec documentCodec;
    private final DiagramSpecJsonCodec diagramCodec;
    private final LocalDiagramArtifactStore diagramStore;
    private final DocumentGenerationApplication documents;
    private final DocumentTemplateApplication templates;
    private final ExternalArtifactStore outputStore;
    private final Map<String, ExternalToolDefinition> definitions;

    public ExternalDocumentToolApplication(Path artifactRoot) {
        this(artifactRoot, new ObjectMapper());
    }

    public ExternalDocumentToolApplication(Path artifactRoot, DocumentTemplateCatalog templateCatalog) {
        this(artifactRoot, new ObjectMapper(), templateCatalog);
    }

    ExternalDocumentToolApplication(Path artifactRoot, ObjectMapper mapper) {
        this(artifactRoot, mapper, null);
    }

    private ExternalDocumentToolApplication(Path artifactRoot, ObjectMapper mapper,
                                            DocumentTemplateCatalog templateCatalog) {
        if (artifactRoot == null || mapper == null) {
            throw new IllegalArgumentException("external document tool root and mapper are required");
        }
        this.mapper = mapper.copy();
        this.documentCodec = new DocumentSpecJsonCodec(this.mapper);
        this.diagramCodec = new DiagramSpecJsonCodec(this.mapper);
        Path root = artifactRoot.toAbsolutePath().normalize();
        this.diagramStore = new LocalDiagramArtifactStore(root.resolve("diagrams"));
        this.documents = new DocumentGenerationApplication(diagramStore);
        this.templates = templateCatalog == null ? new DocumentTemplateApplication(documents)
                : new DocumentTemplateApplication(templateCatalog, documents);
        this.outputStore = new LocalExternalArtifactStore(root.resolve("outputs"));
        this.definitions = Collections.unmodifiableMap(createDefinitions());
    }

    public void registerTemplate(DocumentTemplateSpec template) {
        templates.register(template);
    }

    public void registerTemplate(InputStream templateJson) {
        templates.register(templateJson);
    }

    public void registerTemplate(String templateJson) {
        templates.register(templateJson);
    }

    public List<ExternalToolDefinition> listTools() {
        return Collections.unmodifiableList(new ArrayList<>(definitions.values()));
    }

    public ExternalToolResult call(String name, JsonNode arguments) {
        if (!definitions.containsKey(name)) {
            throw new UnknownExternalToolException(name);
        }
        JsonNode args = requireArguments(arguments);
        switch (name) {
            case LIST_TEMPLATES:
                return listTemplates();
            case GET_TEMPLATE_SCHEMA:
                return templateSchema(args);
            case EXPORT_TEMPLATE:
                return exportTemplate(args);
            case EXPORT_DOCUMENT:
                return exportDocument(args);
            case GENERATE_DIAGRAM:
                return generateDiagram(args);
            default:
                throw new UnknownExternalToolException(name);
        }
    }

    public ResolvedExternalArtifact readResource(String resourceUri) {
        return outputStore.resolve(resourceUri);
    }

    public int purgeExpiredArtifacts(Instant now) {
        return outputStore.purgeExpired(now);
    }

    private ExternalToolResult listTemplates() {
        ArrayNode values = mapper.createArrayNode();
        for (DocumentTemplateDescriptor template : templates.listTemplates()) {
            ObjectNode value = values.addObject();
            value.put("templateId", template.getTemplateId());
            value.put("version", template.getVersion());
            value.put("name", template.getName());
            if (template.getDescription() != null) {
                value.put("description", template.getDescription());
            }
        }
        return ExternalToolResult.data(mapper.createObjectNode().set("templates", values));
    }

    private ExternalToolResult templateSchema(JsonNode arguments) {
        String templateId = requiredText(arguments, "templateId");
        String version = requiredText(arguments, "version");
        ObjectNode result = mapper.createObjectNode();
        result.put("templateId", templateId);
        result.put("version", version);
        result.set("dataSchema", templates.getDataSchema(templateId, version));
        return ExternalToolResult.data(result);
    }

    private ExternalToolResult exportTemplate(JsonNode arguments) {
        String templateId = requiredText(arguments, "templateId");
        String version = requiredText(arguments, "version");
        JsonNode data = requiredObject(arguments, "data");
        ReportOutputFormat format = outputFormat(arguments);
        byte[] content = templates.exportToBytes(templateId, version, data, format);
        ExternalArtifactReference artifact = storeDocument(content, format);
        ObjectNode result = mapper.createObjectNode();
        result.put("templateId", templateId);
        result.put("version", version);
        result.set("artifact", artifactNode(artifact));
        return ExternalToolResult.artifact(result, artifact);
    }

    private ExternalToolResult exportDocument(JsonNode arguments) {
        ObjectNode specJson = (ObjectNode) arguments.deepCopy();
        ReportOutputFormat format = outputFormat(specJson);
        specJson.remove("outputFormat");
        DocumentSpec spec = documentCodec.read(specJson.toString());
        byte[] content = documents.exportToBytes(spec, format);
        ExternalArtifactReference artifact = storeDocument(content, format);
        ObjectNode result = mapper.createObjectNode();
        result.set("artifact", artifactNode(artifact));
        return ExternalToolResult.artifact(result, artifact);
    }

    private ExternalToolResult generateDiagram(JsonNode arguments) {
        DiagramSpec spec = diagramCodec.read(arguments.toString());
        DiagramArtifactReference generated = documents.generateDiagram(spec);
        ResolvedDiagramArtifact resolved = diagramStore.resolve(generated.getDiagramArtifactId());
        try {
            ExternalArtifactReference vsdx = outputStore.store(Files.readAllBytes(resolved.getVsdxPath()),
                    "diagram.vsdx", "application/vnd.ms-visio.drawing");
            ExternalArtifactReference preview = outputStore.store(Files.readAllBytes(resolved.getPreviewPath()),
                    "preview.png", "image/png");
            ObjectNode result = mapper.createObjectNode();
            result.put("diagramArtifactId", generated.getDiagramArtifactId());
            result.set("vsdx", artifactNode(vsdx));
            result.set("preview", artifactNode(preview));
            return new ExternalToolResult(result, Arrays.asList(vsdx, preview));
        } catch (IOException e) {
            throw new IllegalStateException("failed to publish generated diagram artifacts", e);
        }
    }

    private ExternalArtifactReference storeDocument(byte[] content, ReportOutputFormat format) {
        switch (format) {
            case DOCX:
                return outputStore.store(content, "document.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case PDF:
                return outputStore.store(content, "document.pdf", "application/pdf");
            case HTML:
                return outputStore.store(content, "document.html", "text/html");
            default:
                throw new IllegalArgumentException("unsupported output format: " + format);
        }
    }

    private ObjectNode artifactNode(ExternalArtifactReference artifact) {
        ObjectNode value = mapper.createObjectNode();
        value.put("artifactId", artifact.getArtifactId());
        value.put("resourceUri", artifact.getResourceUri());
        value.put("fileName", artifact.getFileName());
        value.put("mediaType", artifact.getMediaType());
        value.put("size", artifact.getSize());
        value.put("sha256", artifact.getSha256());
        if (artifact.getCreatedAt() != null) {
            value.put("createdAt", artifact.getCreatedAt().toString());
        }
        if (artifact.getExpiresAt() != null) {
            value.put("expiresAt", artifact.getExpiresAt().toString());
        }
        return value;
    }

    private Map<String, ExternalToolDefinition> createDefinitions() {
        Map<String, ExternalToolDefinition> tools = new LinkedHashMap<>();
        JsonNode artifactSchema = artifactSchema();
        tools.put(LIST_TEMPLATES, new ExternalToolDefinition(LIST_TEMPLATES, "列出文档模板",
                "列出外部调用方可以显式选择的 DocumentTemplate 标识和版本。",
                emptyInputSchema(), templatesOutputSchema(), annotations(true, true)));
        tools.put(GET_TEMPLATE_SCHEMA, new ExternalToolDefinition(GET_TEMPLATE_SCHEMA, "读取模板数据结构",
                "读取指定 DocumentTemplate 版本要求的业务数据 JSON Schema。",
                templateKeyInputSchema(), templateSchemaOutputSchema(), annotations(true, true)));
        tools.put(EXPORT_TEMPLATE, new ExternalToolDefinition(EXPORT_TEMPLATE, "按业务模板生成文档",
                "使用指定模板版本校验业务数据，映射为 DocumentSpec，并生成 DOCX、PDF 或 HTML 工件。",
                templateExportInputSchema(), wrapperOutputSchema("artifact", artifactSchema),
                annotations(false, false)));
        tools.put(EXPORT_DOCUMENT, new ExternalToolDefinition(EXPORT_DOCUMENT, "按 DocumentSpec 生成文档",
                "校验完整 DocumentSpec 1.0，并通过现有 export 生成 DOCX、PDF 或 HTML 工件。",
                documentExportInputSchema(), wrapperOutputSchema("artifact", artifactSchema),
                annotations(false, false)));
        tools.put(GENERATE_DIAGRAM, new ExternalToolDefinition(GENERATE_DIAGRAM, "生成 Visio 图工件",
                "校验 DiagramSpec 1.0，生成可编辑 VSDX、PNG 预览以及可供 DocumentSpec 引用的工件标识。",
                classPathJson("/diagram-spec/1.0/schema.json"), diagramOutputSchema(artifactSchema),
                annotations(false, false)));
        return tools;
    }

    private JsonNode documentExportInputSchema() {
        ObjectNode schema = (ObjectNode) classPathJson("/document-spec/1.0/schema.json").deepCopy();
        schema.remove("$id");
        schema.put("title", "DocumentSpec export arguments");
        ((ObjectNode) schema.path("properties")).set("outputFormat", outputFormatSchema());
        ((ArrayNode) schema.path("required")).add("outputFormat");
        ObjectNode definitions = (ObjectNode) schema.path("$defs");
        definitions.set("diagramSpec", classPathJson("/diagram-spec/1.0/schema.json"));
        ObjectNode inlineDiagram = mapper.createObjectNode();
        inlineDiagram.put("$ref", "#/$defs/diagramSpec");
        ((ObjectNode) definitions.path("diagramBlock").path("properties"))
                .set("definition", inlineDiagram);
        return schema;
    }

    private JsonNode emptyInputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.set("properties", mapper.createObjectNode());
        return schema;
    }

    private JsonNode templateKeyInputSchema() {
        ObjectNode schema = emptyObjectSchema();
        ObjectNode properties = (ObjectNode) schema.path("properties");
        properties.set("templateId", nonBlankString("Registered template identifier"));
        properties.set("version", nonBlankString("Exact semantic template version"));
        schema.putArray("required").add("templateId").add("version");
        return schema;
    }

    private JsonNode templateExportInputSchema() {
        ObjectNode schema = (ObjectNode) templateKeyInputSchema();
        ObjectNode properties = (ObjectNode) schema.path("properties");
        ObjectNode data = mapper.createObjectNode();
        data.put("type", "object");
        data.put("description", "Business data conforming to the selected template data schema");
        properties.set("data", data);
        properties.set("outputFormat", outputFormatSchema());
        ((ArrayNode) schema.path("required")).add("data").add("outputFormat");
        return schema;
    }

    private JsonNode outputFormatSchema() {
        ObjectNode format = mapper.createObjectNode();
        format.put("type", "string");
        format.putArray("enum").add("DOCX").add("PDF").add("HTML");
        return format;
    }

    private JsonNode artifactSchema() {
        ObjectNode schema = emptyObjectSchema();
        ObjectNode properties = (ObjectNode) schema.path("properties");
        properties.set("artifactId", nonBlankString("Opaque artifact identifier"));
        properties.set("resourceUri", nonBlankString("MCP-readable omni-office resource URI"));
        properties.set("fileName", nonBlankString("Safe generated file name"));
        properties.set("mediaType", nonBlankString("Artifact media type"));
        properties.set("size", integerSchema(1));
        ObjectNode sha = nonBlankString("SHA-256 checksum");
        sha.put("pattern", "^[0-9a-f]{64}$");
        properties.set("sha256", sha);
        schema.putArray("required").add("artifactId").add("resourceUri").add("fileName")
                .add("mediaType").add("size").add("sha256");
        return schema;
    }

    private JsonNode wrapperOutputSchema(String property, JsonNode valueSchema) {
        ObjectNode schema = emptyObjectSchema();
        ((ObjectNode) schema.path("properties")).set(property, valueSchema);
        schema.putArray("required").add(property);
        return schema;
    }

    private JsonNode templatesOutputSchema() {
        ObjectNode item = emptyObjectSchema();
        ObjectNode itemProperties = (ObjectNode) item.path("properties");
        itemProperties.set("templateId", nonBlankString("Template identifier"));
        itemProperties.set("version", nonBlankString("Exact version"));
        itemProperties.set("name", nonBlankString("Display name"));
        itemProperties.set("description", mapper.createObjectNode().put("type", "string"));
        item.putArray("required").add("templateId").add("version").add("name");
        ObjectNode array = mapper.createObjectNode();
        array.put("type", "array");
        array.set("items", item);
        return wrapperOutputSchema("templates", array);
    }

    private JsonNode templateSchemaOutputSchema() {
        ObjectNode schema = emptyObjectSchema();
        ObjectNode properties = (ObjectNode) schema.path("properties");
        properties.set("templateId", nonBlankString("Template identifier"));
        properties.set("version", nonBlankString("Exact version"));
        properties.set("dataSchema", mapper.createObjectNode().put("type", "object"));
        schema.putArray("required").add("templateId").add("version").add("dataSchema");
        return schema;
    }

    private JsonNode diagramOutputSchema(JsonNode artifactSchema) {
        ObjectNode schema = emptyObjectSchema();
        ObjectNode properties = (ObjectNode) schema.path("properties");
        properties.set("diagramArtifactId", nonBlankString("Identifier accepted by DocumentSpec diagram blocks"));
        properties.set("vsdx", artifactSchema);
        properties.set("preview", artifactSchema);
        schema.putArray("required").add("diagramArtifactId").add("vsdx").add("preview");
        return schema;
    }

    private ObjectNode emptyObjectSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.set("properties", mapper.createObjectNode());
        return schema;
    }

    private ObjectNode nonBlankString(String description) {
        ObjectNode value = mapper.createObjectNode();
        value.put("type", "string");
        value.put("minLength", 1);
        value.put("description", description);
        return value;
    }

    private ObjectNode integerSchema(int minimum) {
        ObjectNode value = mapper.createObjectNode();
        value.put("type", "integer");
        value.put("minimum", minimum);
        return value;
    }

    private JsonNode annotations(boolean readOnly, boolean idempotent) {
        ObjectNode value = mapper.createObjectNode();
        value.put("readOnlyHint", readOnly);
        value.put("destructiveHint", false);
        value.put("idempotentHint", idempotent);
        value.put("openWorldHint", false);
        return value;
    }

    private JsonNode classPathJson(String resource) {
        try (InputStream input = ExternalDocumentToolApplication.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing external tool schema resource: " + resource);
            }
            return mapper.readTree(input);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read external tool schema resource: " + resource, e);
        }
    }

    private JsonNode requireArguments(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            throw new IllegalArgumentException("external tool arguments must be a JSON object");
        }
        return arguments;
    }

    private String requiredText(JsonNode arguments, String field) {
        JsonNode value = arguments.path(field);
        if (!value.isTextual() || value.asText().trim().isEmpty()) {
            throw new IllegalArgumentException("external tool requires non-blank field: " + field);
        }
        return value.asText().trim();
    }

    private JsonNode requiredObject(JsonNode arguments, String field) {
        JsonNode value = arguments.path(field);
        if (!value.isObject()) {
            throw new IllegalArgumentException("external tool requires object field: " + field);
        }
        return value;
    }

    private ReportOutputFormat outputFormat(JsonNode arguments) {
        try {
            return ReportOutputFormat.valueOf(requiredText(arguments, "outputFormat"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("outputFormat must be DOCX, PDF or HTML", e);
        }
    }
}
