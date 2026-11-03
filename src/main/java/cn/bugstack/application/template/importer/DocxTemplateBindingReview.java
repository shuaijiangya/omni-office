package cn.bugstack.application.template.importer;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 审核章节内容模板与源 DOCX 物理样式绑定。
 *
 * <p>在实际写回前重新分析源文件，防止用旧位置和旧样式修改已经变化的模板。</p>
 */
public final class DocxTemplateBindingReview {

    /**
     * 校验人工修改后的模板和绑定；有歧义的样例必须显式审核。
     *
     * @param source 当前源 DOCX
     * @param reportTemplate 经人工审核的章节 JSON
     * @param styleBindings 经人工审核的样式绑定 JSON
     */
    public void validate(Path source, JsonNode reportTemplate, JsonNode styleBindings) {
        if (reportTemplate == null || !reportTemplate.isObject()
                || styleBindings == null || !styleBindings.isObject()) {
            throw new IllegalArgumentException("report template and style bindings must be JSON objects");
        }
        String templateId = required(reportTemplate, "templateId");
        DocxTemplateAnalysis fresh = new DocxTemplateAnalyzer().analyze(source, templateId);
        JsonNode structure = fresh.getStructure();
        if (!templateId.equals(styleBindings.path("templateId").asText())) {
            throw new IllegalArgumentException("templateId mismatch between content and style bindings");
        }
        if (!structure.path("sourceSha256").asText().equals(styleBindings.path("sourceSha256").asText())) {
            throw new IllegalArgumentException("source DOCX changed since style binding was created");
        }
        Map<String, JsonNode> sourceSections = new HashMap<>();
        for (JsonNode section : structure.path("sections")) {
            sourceSections.put(section.path("id").asText(), section);
        }
        Set<String> seen = new HashSet<>();
        JsonNode sections = reportTemplate.path("sections");
        if (!sections.isArray() || sections.size() == 0) {
            throw new IllegalArgumentException("report template requires sections");
        }
        for (JsonNode section : sections) {
            String id = required(section, "id");
            if (!seen.add(id)) throw new IllegalArgumentException("duplicate section id: " + id);
            JsonNode sourceSection = sourceSections.get(id);
            if (sourceSection == null) throw new IllegalArgumentException("unknown source section: " + id);
            if (!sourceSection.path("title").asText().equals(section.path("title").asText())
                    || sourceSection.path("level").asInt() != section.path("level").asInt()) {
                throw new IllegalArgumentException("source heading title or level changed: " + id);
            }
            JsonNode binding = styleBindings.path("sections").path(id);
            if (!binding.isObject()) throw new IllegalArgumentException("missing style binding: " + id);
            if (!sourceSection.path("headingAnchor").asText()
                    .equals(binding.path("headingAnchor").asText())) {
                throw new IllegalArgumentException("heading anchor mismatch: " + id);
            }
            Set<String> placeholders = new HashSet<>();
            for (JsonNode marker : sourceSection.path("placeholders")) {
                placeholders.add(marker.path("anchorId").asText());
            }
            for (JsonNode anchor : binding.path("placeholderAnchors")) {
                if (!placeholders.contains(anchor.asText())) {
                    throw new IllegalArgumentException("unknown placeholder anchor in " + id + ": " + anchor.asText());
                }
            }
            if (!"AI_WRITE".equals(section.path("mode").asText("HEADING_ONLY"))) continue;
            if (binding.path("placeholderAnchors").size() == 0) {
                throw new IllegalArgumentException("AI_WRITE section has no explicit fill marker: " + id);
            }
            JsonNode declaredSlots = section.path("slots");
            if (!declaredSlots.isArray() || declaredSlots.size() != sourceSection.path("placeholders").size()) {
                throw new IllegalArgumentException("every source marker requires one declared slot: " + id);
            }
            for (JsonNode declared : declaredSlots) {
                String slotId = required(declared, "id");
                JsonNode target = binding.path("slots").path(slotId);
                if (!target.isObject() || !declared.path("type").asText().equals(target.path("type").asText())) {
                    throw new IllegalArgumentException("missing or mismatched slot binding: " + slotId);
                }
                boolean found = false;
                for (JsonNode marker : sourceSection.path("placeholders")) {
                    if (slotId.equals(marker.path("slotId").asText())
                            && marker.path("anchorId").asText().equals(target.path("anchorId").asText())
                            && marker.path("name").asText().equals(target.path("name").asText())) {
                        found = true;
                    }
                }
                if (!found) throw new IllegalArgumentException("slot target changed: " + slotId);
            }
            Map<String, Integer> counts = new HashMap<>();
            Map<String, Set<String>> anchors = new HashMap<>();
            for (JsonNode block : sourceSection.path("blocks")) {
                String type = block.path("type").asText();
                counts.merge(type, 1, Integer::sum);
                anchors.computeIfAbsent(type, ignored -> new HashSet<>())
                        .add(block.path("anchorId").asText());
            }
            JsonNode samples = binding.path("blockSamples");
            for (java.util.Iterator<Map.Entry<String, JsonNode>> fields = samples.fields(); fields.hasNext(); ) {
                Map.Entry<String, JsonNode> sample = fields.next();
                if (!anchors.getOrDefault(sample.getKey(), java.util.Collections.emptySet())
                        .contains(sample.getValue().asText())) {
                    throw new IllegalArgumentException("invalid " + sample.getKey() + " sample for " + id);
                }
                if (counts.getOrDefault(sample.getKey(), 0) > 1 && !binding.path("reviewed").asBoolean()) {
                    throw new IllegalArgumentException("ambiguous sample requires reviewed=true: " + id);
                }
            }
        }
    }

    private String required(JsonNode node, String key) {
        String value = node.path(key).asText();
        if (value.trim().isEmpty()) throw new IllegalArgumentException(key + " is required");
        return value;
    }
}
