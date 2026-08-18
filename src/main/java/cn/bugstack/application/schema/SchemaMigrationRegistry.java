package cn.bugstack.application.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 显式迁移链注册表；不会猜测最新版，也不会隐式跨越缺失版本。 */
public final class SchemaMigrationRegistry {

    private final Map<String, SchemaMigration> migrations = new HashMap<>();

    public synchronized void register(SchemaMigration migration) {
        if (migration == null) {
            throw new IllegalArgumentException("schema migration is required");
        }
        String key = key(migration.protocol(), migration.fromVersion());
        if (migration.fromVersion().equals(migration.toVersion()) || migrations.putIfAbsent(key, migration) != null) {
            throw new IllegalStateException("duplicate or cyclic schema migration: " + key);
        }
    }

    public synchronized JsonNode migrate(String protocol, String fromVersion,
                                         String toVersion, JsonNode input) {
        if (input == null) {
            throw new IllegalArgumentException("migration input is required");
        }
        JsonNode value = input.deepCopy();
        String current = fromVersion;
        Set<String> visited = new HashSet<>();
        while (!current.equals(toVersion)) {
            if (!visited.add(current)) {
                throw new IllegalStateException("schema migration cycle detected");
            }
            SchemaMigration migration = migrations.get(key(protocol, current));
            if (migration == null) {
                throw new IllegalArgumentException("no complete migration path for " + protocol + " "
                        + fromVersion + " -> " + toVersion);
            }
            value = migration.migrate(value.deepCopy());
            if (value == null) {
                throw new IllegalStateException("schema migration returned null");
            }
            current = migration.toVersion();
        }
        return value.deepCopy();
    }

    public synchronized List<String> registeredEdges() {
        List<String> result = new ArrayList<>();
        for (SchemaMigration value : migrations.values()) {
            result.add(value.protocol() + ":" + value.fromVersion() + "->" + value.toVersion());
        }
        Collections.sort(result);
        return result;
    }

    private String key(String protocol, String version) {
        if (protocol == null || version == null || protocol.isBlank() || version.isBlank()) {
            throw new IllegalArgumentException("protocol and schema version are required");
        }
        return protocol + "@" + version;
    }
}
