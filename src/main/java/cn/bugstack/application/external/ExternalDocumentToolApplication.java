package cn.bugstack.application.external;

import cn.bugstack.application.artifact.DiagramArtifactReference;
import cn.bugstack.application.artifact.LocalDiagramArtifactStore;
import cn.bugstack.application.artifact.ResolvedDiagramArtifact;
import cn.bugstack.application.ai.DefaultInternalAiDocumentService;
import cn.bugstack.application.ai.InternalAiDocumentService;
import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.AiDocumentResult;
import cn.bugstack.application.ai.review.AiDraftRecord;
import cn.bugstack.application.ai.review.FileAiDraftReviewService;
import cn.bugstack.application.document.DocumentGenerationApplication;
import cn.bugstack.application.document.DocumentSpecLimits;
import cn.bugstack.application.document.DocumentSpecValidator;
import cn.bugstack.application.document.DocumentCostEstimate;
import cn.bugstack.application.document.DocumentCostEstimator;
import cn.bugstack.application.template.DocumentTemplateApplication;
import cn.bugstack.application.template.DocumentTemplateCatalog;
import cn.bugstack.application.template.DocumentTemplateDescriptor;
import cn.bugstack.application.template.governance.TemplatePublicationGate;
import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.diagram.DiagramSpec;
import cn.bugstack.protocol.diagram.DiagramSpecJsonCodec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.DocumentSpecJsonCodec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.BlockSpec;
import cn.bugstack.protocol.document.block.ImageBlockSpec;
import cn.bugstack.protocol.document.block.SubsectionBlockSpec;
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
import java.util.Base64;
import java.time.Instant;
import java.time.Duration;

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
    public static final String STORE_ASSET = "omni_asset_store";
    public static final String GET_ASSET = "omni_asset_get";
    public static final String DELETE_ASSET = "omni_asset_delete";

    private final ObjectMapper mapper;
    private final DocumentSpecJsonCodec documentCodec;
    private final DiagramSpecJsonCodec diagramCodec;
    private final LocalDiagramArtifactStore diagramStore;
    private final DocumentGenerationApplication documents;
    private final DocumentTemplateApplication templates;
    private final ExternalArtifactStore outputStore;
    private final FileAiDraftReviewService aiReviews;
    private final Map<String, ExternalToolDefinition> definitions;
    private final ExternalDocumentSpecSafetyValidator externalSafety = new ExternalDocumentSpecSafetyValidator();
    private final DocumentCostEstimator costEstimator = new DocumentCostEstimator();

    public ExternalDocumentToolApplication(Path artifactRoot) {
        this(artifactRoot, new ObjectMapper());
    }

    public ExternalDocumentToolApplication(Path artifactRoot, DocumentTemplateCatalog templateCatalog) {
        this(artifactRoot, new ObjectMapper(), templateCatalog);
    }

    public ExternalDocumentToolApplication(Path artifactRoot, DocumentTemplateCatalog templateCatalog,
                                           ExternalArtifactStore outputStore) {
        this(artifactRoot, new ObjectMapper(), templateCatalog, outputStore, Duration.ofDays(30));
    }

    /** 创建输出工件和图中间工件使用相同保留策略的工具门面。 */
    public ExternalDocumentToolApplication(Path artifactRoot, DocumentTemplateCatalog templateCatalog,
                                           ExternalArtifactStore outputStore, Duration retention) {
        this(artifactRoot, new ObjectMapper(), templateCatalog, outputStore, retention);
    }

    ExternalDocumentToolApplication(Path artifactRoot, ObjectMapper mapper) {
        this(artifactRoot, mapper, null);
    }

    private ExternalDocumentToolApplication(Path artifactRoot, ObjectMapper mapper,
                                            DocumentTemplateCatalog templateCatalog) {
        this(artifactRoot, mapper, templateCatalog, null, Duration.ofDays(30));
    }

    private ExternalDocumentToolApplication(Path artifactRoot, ObjectMapper mapper,
                                            DocumentTemplateCatalog templateCatalog,
                                            ExternalArtifactStore configuredOutputStore,
                                            Duration retention) {
        if (artifactRoot == null || mapper == null) {
            throw new IllegalArgumentException("external document tool root and mapper are required");
        }
        this.mapper = mapper.copy();
        this.documentCodec = new DocumentSpecJsonCodec(this.mapper);
        this.diagramCodec = new DiagramSpecJsonCodec(this.mapper);
        Path root = artifactRoot.toAbsolutePath().normalize();
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("artifact retention must be positive");
        }
        this.diagramStore = new LocalDiagramArtifactStore(root.resolve("diagrams"), retention,
                java.time.Clock.systemUTC());
        this.documents = new DocumentGenerationApplication(diagramStore);
        this.aiReviews = new FileAiDraftReviewService(root.resolve("ai-drafts"), documents, retention);
        this.templates = templateCatalog == null ? new DocumentTemplateApplication(documents)
                : new DocumentTemplateApplication(templateCatalog, documents);
        this.outputStore = configuredOutputStore == null
                ? new LocalExternalArtifactStore(root.resolve("outputs"), retention,
                new cn.bugstack.application.external.security.BasicArtifactSecurityScanner()) : configuredOutputStore;
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
        return call(name, arguments, "system");
    }

    /**
     * 使用调用主体上下文执行工具，使所有新工件持久化绑定到该主体。
     *
     * @param name 工具名称
     * @param arguments 工具参数
     * @param principalId 调用主体 ID
     * @return 工具结果
     */
    public ExternalToolResult call(String name, JsonNode arguments, String principalId) {
        if (!definitions.containsKey(name)) {
            throw new UnknownExternalToolException(name);
        }
        String owner = requirePrincipalId(principalId);
        JsonNode args = requireArguments(arguments);
        switch (name) {
            case LIST_TEMPLATES:
                return listTemplates();
            case GET_TEMPLATE_SCHEMA:
                return templateSchema(args);
            case EXPORT_TEMPLATE:
                return exportTemplate(args, owner);
            case EXPORT_DOCUMENT:
                return exportDocument(args, owner);
            case GENERATE_DIAGRAM:
                return generateDiagram(args, owner);
            case STORE_ASSET:
                return storeAsset(args, owner);
            case GET_ASSET:
                return getAsset(args, owner);
            case DELETE_ASSET:
                return deleteAsset(args, owner);
            default:
                throw new UnknownExternalToolException(name);
        }
    }

    public ResolvedExternalArtifact readResource(String resourceUri) {
        return outputStore.resolve(resourceUri);
    }

    /**
     * 按主体所有权读取工件。
     *
     * @param resourceUri 工件 URI
     * @param principalId 当前主体 ID
     * @param allowAny 是否具有租户内跨主体读取权限
     * @return 已授权工件
     */
    public ResolvedExternalArtifact readResource(String resourceUri, String principalId, boolean allowAny) {
        return outputStore.resolveForPrincipal(resourceUri, requirePrincipalId(principalId), allowAny);
    }

    public int purgeExpiredArtifacts(Instant now) {
        return outputStore.purgeExpired(now) + diagramStore.purgeExpired(now) + aiReviews.purgeExpired(now);
    }

    /** 供 REST 等协议适配器在提交任务前执行无副作用 DocumentSpec 校验。 */
    public void validateDocument(JsonNode documentSpec) {
        if (documentSpec == null || !documentSpec.isObject()) {
            throw new IllegalArgumentException("documentSpec must be a JSON object");
        }
        DocumentSpec spec = documentCodec.read(documentSpec.toString());
        documents.validate(spec);
        externalSafety.validateOrThrow(spec);
    }

    /** 校验后返回文档规模和预估页数，供提交前容量提示使用。 */
    public DocumentCostEstimate estimateDocument(JsonNode documentSpec) {
        validateDocument(documentSpec);
        return costEstimator.estimate(documentCodec.read(documentSpec.toString()));
    }

    /** 校验模板数据、执行受限映射并校验映射后的 DocumentSpec，但不生成文件或图工件。 */
    public void validateTemplateData(String templateId, String version, JsonNode data) {
        if (templateId == null || templateId.isBlank() || version == null || version.isBlank()
                || data == null || !data.isObject()) {
            throw new IllegalArgumentException("template id, version and object data are required");
        }
        documents.validate(templates.renderSpec(templateId, version, data));
    }

    /**
     * 创建复用当前租户模板目录和 DocumentSpec 导出器的内部 AI 服务。
     *
     * @param aiClient 结构化模型客户端
     * @return 与外部工具共享模板和文档语义的内部 AI 服务
     */
    public InternalAiDocumentService createInternalAiService(StructuredAiClient aiClient) {
        if (aiClient == null) throw new IllegalArgumentException("structured AI client is required");
        return new DefaultInternalAiDocumentService(aiClient,
                new DocumentSpecValidator(DocumentSpecLimits.defaults(), true), templates, documents);
    }

    /** 保存 AI 生成的结构化草稿，等待租户审核人处理。 */
    public AiDraftRecord submitAiDraft(AiDocumentResult result, String requestedBy) {
        return aiReviews.submit(result, requestedBy);
    }

    /** 审批 AI 草稿。 */
    public AiDraftRecord approveAiDraft(String draftId, String reviewer, String comment) {
        return aiReviews.approve(draftId, reviewer, comment);
    }

    /** 驳回 AI 草稿。 */
    public AiDraftRecord rejectAiDraft(String draftId, String reviewer, String comment) {
        return aiReviews.reject(draftId, reviewer, comment);
    }

    /** 获取草稿中的结构化文档快照。 */
    public DocumentSpec aiDraftDocumentSpec(String draftId) {
        return aiReviews.documentSpec(draftId);
    }

    /** 创建复用当前确定性文档链的模板发布门禁。 */
    public TemplatePublicationGate createTemplatePublicationGate() {
        return new TemplatePublicationGate(documents);
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

    private ExternalToolResult exportTemplate(JsonNode arguments, String principalId) {
        String templateId = requiredText(arguments, "templateId");
        String version = requiredText(arguments, "version");
        JsonNode data = requiredObject(arguments, "data");
        ReportOutputFormat format = outputFormat(arguments);
        Path output = temporaryOutput(format);
        ExternalArtifactReference artifact;
        try {
            templates.export(templateId, version, data, format, output);
            artifact = storeDocument(output, format, principalId);
        } finally {
            deleteTemporary(output);
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("templateId", templateId);
        result.put("version", version);
        result.set("artifact", artifactNode(artifact));
        return ExternalToolResult.artifact(result, artifact);
    }

    private ExternalToolResult exportDocument(JsonNode arguments, String principalId) {
        ObjectNode specJson = (ObjectNode) arguments.deepCopy();
        ReportOutputFormat format = outputFormat(specJson);
        specJson.remove("outputFormat");
        DocumentSpec spec = documentCodec.read(specJson.toString());
        externalSafety.validateOrThrow(spec);
        materializeAssets(spec, principalId);
        Path output = temporaryOutput(format);
        ExternalArtifactReference artifact;
        try {
            documents.export(spec, format, output);
            artifact = storeDocument(output, format, principalId);
        } finally {
            deleteTemporary(output);
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("artifact", artifactNode(artifact));
        return ExternalToolResult.artifact(result, artifact);
    }

    private ExternalToolResult generateDiagram(JsonNode arguments, String principalId) {
        DiagramSpec spec = diagramCodec.read(arguments.toString());
        DiagramArtifactReference generated = documents.generateDiagram(spec);
        ResolvedDiagramArtifact resolved = diagramStore.resolve(generated.getDiagramArtifactId());
        try {
            ExternalArtifactReference vsdx = outputStore.storeForPrincipal(
                    Files.readAllBytes(resolved.getVsdxPath()), "diagram.vsdx",
                    "application/vnd.ms-visio.drawing", principalId);
            ExternalArtifactReference preview = outputStore.storeForPrincipal(
                    Files.readAllBytes(resolved.getPreviewPath()), "preview.png", "image/png", principalId);
            ObjectNode result = mapper.createObjectNode();
            result.put("diagramArtifactId", generated.getDiagramArtifactId());
            result.set("vsdx", artifactNode(vsdx));
            result.set("preview", artifactNode(preview));
            return new ExternalToolResult(result, Arrays.asList(vsdx, preview));
        } catch (IOException e) {
            throw new IllegalStateException("failed to publish generated diagram artifacts", e);
        }
    }

    private ExternalToolResult storeAsset(JsonNode arguments, String principalId) {
        String fileName = requiredText(arguments, "fileName");
        String mediaType = requiredText(arguments, "mediaType");
        if (!"image/png".equals(mediaType) && !"image/jpeg".equals(mediaType)) {
            throw new IllegalArgumentException("managed assets currently support image/png and image/jpeg");
        }
        String lowerName = fileName.toLowerCase(java.util.Locale.ROOT);
        if (("image/png".equals(mediaType) && !lowerName.endsWith(".png"))
                || ("image/jpeg".equals(mediaType)
                && !lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg"))) {
            throw new IllegalArgumentException("managed image file extension must match mediaType");
        }
        String encoded = requiredText(arguments, "contentBase64");
        if (encoded.length() > 13_981_016) {
            throw new IllegalArgumentException("managed image asset exceeds the Base64 input limit");
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("asset contentBase64 is invalid", e);
        }
        if (content.length == 0 || content.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("managed image asset must contain 1 to 10485760 bytes");
        }
        ExternalArtifactReference asset = outputStore.storeForPrincipal(content, fileName, mediaType, principalId);
        ObjectNode result = mapper.createObjectNode();
        result.put("assetId", asset.getArtifactId());
        result.set("artifact", artifactNode(asset));
        return ExternalToolResult.artifact(result, asset);
    }

    private ExternalToolResult getAsset(JsonNode arguments, String principalId) {
        ResolvedExternalArtifact resolved = resolveAsset(requiredText(arguments, "assetId"), principalId);
        ObjectNode result = mapper.createObjectNode();
        result.put("assetId", resolved.getReference().getArtifactId());
        result.set("artifact", artifactNode(resolved.getReference()));
        return ExternalToolResult.data(result);
    }

    private ExternalToolResult deleteAsset(JsonNode arguments, String principalId) {
        ResolvedExternalArtifact resolved = resolveAsset(requiredText(arguments, "assetId"), principalId);
        boolean deleted = outputStore.delete(resolved.getReference().getResourceUri());
        ObjectNode result = mapper.createObjectNode();
        result.put("assetId", resolved.getReference().getArtifactId());
        result.put("deleted", deleted);
        return ExternalToolResult.data(result);
    }

    private void materializeAssets(DocumentSpec spec, String principalId) {
        if (spec == null || spec.getSections() == null) return;
        for (SectionSpec section : spec.getSections()) materializeAssets(section, principalId);
    }

    private void materializeAssets(SectionSpec section, String principalId) {
        if (section == null || section.getBlocks() == null) return;
        for (BlockSpec block : section.getBlocks()) {
            if (block instanceof ImageBlockSpec) {
                ImageBlockSpec image = (ImageBlockSpec) block;
                ResolvedExternalArtifact resolved = resolveAsset(image.getAssetId(), principalId);
                if (!resolved.getReference().getMediaType().startsWith("image/")) {
                    throw new IllegalArgumentException("managed asset is not an image");
                }
                image.setSource(resolved.getContentPath().toString());
                image.setAssetId(null);
            } else if (block instanceof SubsectionBlockSpec) {
                SubsectionBlockSpec subsection = (SubsectionBlockSpec) block;
                SectionSpec child = new SectionSpec(subsection.getTitle());
                child.setBlocks(subsection.getBlocks());
                materializeAssets(child, principalId);
            }
        }
    }

    private ResolvedExternalArtifact resolveAsset(String assetId, String principalId) {
        final String id;
        try {
            id = java.util.UUID.fromString(assetId).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("assetId must be a UUID", e);
        }
        ResolvedExternalArtifact resolved = outputStore.resolveForPrincipal(
                "omni-office://artifacts/" + id, principalId, false);
        if (!resolved.getReference().getMediaType().startsWith("image/")) {
            throw new IllegalArgumentException("managed asset is not an image");
        }
        return resolved;
    }

    private ExternalArtifactReference storeDocument(byte[] content, ReportOutputFormat format,
                                                    String principalId) {
        switch (format) {
            case DOCX:
                return outputStore.storeForPrincipal(content, "document.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", principalId);
            case PDF:
                return outputStore.storeForPrincipal(content, "document.pdf", "application/pdf", principalId);
            case HTML:
                return outputStore.storeForPrincipal(content, "document.html", "text/html", principalId);
            default:
                throw new IllegalArgumentException("unsupported output format: " + format);
        }
    }

    private ExternalArtifactReference storeDocument(Path contentPath, ReportOutputFormat format,
                                                    String principalId) {
        switch (format) {
            case DOCX:
                return outputStore.storeForPrincipal(contentPath, "document.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", principalId);
            case PDF:
                return outputStore.storeForPrincipal(contentPath, "document.pdf", "application/pdf", principalId);
            case HTML:
                return outputStore.storeForPrincipal(contentPath, "document.html", "text/html", principalId);
            default:
                throw new IllegalArgumentException("unsupported output format: " + format);
        }
    }

    private Path temporaryOutput(ReportOutputFormat format) {
        try {
            return Files.createTempFile("omni-document-", "." + format.name().toLowerCase());
        } catch (IOException e) {
            throw new IllegalStateException("failed to create temporary document output", e);
        }
    }

    private void deleteTemporary(Path output) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException ignored) {
            // Lifecycle cleanup can remove a rare leftover after an operating-system file lock.
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

    private String requirePrincipalId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("external tool principal id is invalid");
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
        tools.put(STORE_ASSET, new ExternalToolDefinition(STORE_ASSET, "保存受控图片资产",
                "保存 PNG/JPEG 图片并返回只能由同一主体在 DocumentSpec 中引用的 assetId。",
                assetStoreInputSchema(), assetOutputSchema(artifactSchema), annotations(false, false)));
        tools.put(GET_ASSET, new ExternalToolDefinition(GET_ASSET, "读取图片资产元数据",
                "读取当前主体拥有的图片资产元数据。", assetKeyInputSchema(),
                assetOutputSchema(artifactSchema), annotations(true, true)));
        tools.put(DELETE_ASSET, new ExternalToolDefinition(DELETE_ASSET, "删除图片资产",
                "删除当前主体拥有的图片资产。", assetKeyInputSchema(),
                deleteAssetOutputSchema(), annotations(false, true)));
        return tools;
    }

    private JsonNode documentExportInputSchema() {
        ObjectNode schema = (ObjectNode) classPathJson("/document-spec/1.0/schema.json").deepCopy();
        schema.remove("$id");
        schema.put("title", "DocumentSpec export arguments");
        ((ObjectNode) schema.path("properties")).set("outputFormat", outputFormatSchema());
        ((ArrayNode) schema.path("required")).add("outputFormat");
        ObjectNode definitions = (ObjectNode) schema.path("$defs");
        ObjectNode imageBlock = (ObjectNode) definitions.path("imageBlock");
        ((ObjectNode) imageBlock.path("properties")).remove("source");
        imageBlock.set("oneOf", mapper.createArrayNode().add(mapper.createObjectNode()
                .set("required", mapper.createArrayNode().add("assetId"))));
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

    private JsonNode assetStoreInputSchema() {
        ObjectNode schema = emptyObjectSchema();
        ObjectNode properties = (ObjectNode) schema.path("properties");
        ObjectNode fileName = nonBlankString("Safe PNG/JPEG file name whose extension matches mediaType");
        fileName.put("maxLength", 128);
        fileName.put("pattern", "^[A-Za-z0-9._-]+\\.(?:[Pp][Nn][Gg]|[Jj][Pp][Ee]?[Gg])$");
        properties.set("fileName", fileName);
        ObjectNode mediaType = mapper.createObjectNode();
        mediaType.put("type", "string");
        mediaType.putArray("enum").add("image/png").add("image/jpeg");
        properties.set("mediaType", mediaType);
        ObjectNode content = nonBlankString("Base64 encoded image bytes");
        content.put("maxLength", 13_981_016);
        properties.set("contentBase64", content);
        schema.putArray("required").add("fileName").add("mediaType").add("contentBase64");
        return schema;
    }

    private JsonNode assetKeyInputSchema() {
        ObjectNode schema = emptyObjectSchema();
        ObjectNode id = nonBlankString("Managed image asset UUID");
        id.put("format", "uuid");
        ((ObjectNode) schema.path("properties")).set("assetId", id);
        schema.putArray("required").add("assetId");
        return schema;
    }

    private JsonNode assetOutputSchema(JsonNode artifactSchema) {
        ObjectNode schema = emptyObjectSchema();
        ObjectNode properties = (ObjectNode) schema.path("properties");
        properties.set("assetId", nonBlankString("Managed image asset UUID"));
        properties.set("artifact", artifactSchema);
        schema.putArray("required").add("assetId").add("artifact");
        return schema;
    }

    private JsonNode deleteAssetOutputSchema() {
        ObjectNode schema = emptyObjectSchema();
        ObjectNode properties = (ObjectNode) schema.path("properties");
        properties.set("assetId", nonBlankString("Managed image asset UUID"));
        properties.set("deleted", mapper.createObjectNode().put("type", "boolean"));
        schema.putArray("required").add("assetId").add("deleted");
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
