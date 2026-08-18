package cn.bugstack.application.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 不可覆盖的协议 Schema 版本目录，记录规范化内容摘要。 */
public final class ProtocolSchemaRegistry {

    private final Map<String, Entry> schemas = new LinkedHashMap<>();
    private final JsonSchemaCompatibilityChecker compatibilityChecker = new JsonSchemaCompatibilityChecker();

    public synchronized Entry publish(String protocol, String version, JsonNode schema,
                                      String previousVersion, boolean requireBackwardCompatibility) {
        String key = key(protocol, version);
        if (schemas.containsKey(key)) {
            throw new IllegalStateException("published schema version is immutable: " + key);
        }
        if (schema == null || !schema.isObject()) {
            throw new IllegalArgumentException("protocol schema must be a JSON object");
        }
        if (previousVersion != null) {
            Entry previous = require(protocol, previousVersion);
            if (requireBackwardCompatibility) {
                SchemaCompatibilityResult result = compatibilityChecker
                        .backwardCompatible(previous.getSchema(), schema);
                if (!result.isCompatible()) {
                    throw new IllegalArgumentException("schema is not backward compatible: "
                            + String.join("; ", result.getViolations()));
                }
            }
        }
        Entry entry = new Entry(protocol, version, schema.deepCopy(), sha256(schema.toString()));
        schemas.put(key, entry);
        return entry.copy();
    }

    public synchronized Entry require(String protocol, String version) {
        Entry value = schemas.get(key(protocol, version));
        if (value == null) {
            throw new IllegalArgumentException("schema version is not published: " + protocol + "@" + version);
        }
        return value.copy();
    }

    public synchronized List<Entry> list() {
        List<Entry> values = new ArrayList<>();
        schemas.values().forEach(value -> values.add(value.copy()));
        return Collections.unmodifiableList(values);
    }

    private String key(String protocol, String version) {
        if (protocol == null || version == null || !protocol.matches("[a-z][a-z0-9.-]{0,63}")
                || !version.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException("protocol or schema version is invalid");
        }
        return protocol + "@" + version;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static final class Entry {
        private final String protocol;
        private final String version;
        private final JsonNode schema;
        private final String sha256;

        private Entry(String protocol, String version, JsonNode schema, String sha256) {
            this.protocol = protocol;
            this.version = version;
            this.schema = schema;
            this.sha256 = sha256;
        }
        public String getProtocol() { return protocol; }
        public String getVersion() { return version; }
        public JsonNode getSchema() { return schema.deepCopy(); }
        public String getSha256() { return sha256; }
        private Entry copy() { return new Entry(protocol, version, schema.deepCopy(), sha256); }
    }
}
