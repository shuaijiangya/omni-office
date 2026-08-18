package cn.bugstack.application.external.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 测试和嵌入式场景使用的对象存储实现。 */
public final class InMemoryArtifactObjectStorage implements ArtifactObjectStorage {

    private final ConcurrentMap<String, byte[]> values = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] content, String mediaType) {
        if (key == null || content == null) throw new IllegalArgumentException("object key and content are required");
        values.put(key, Arrays.copyOf(content, content.length));
    }

    @Override
    public byte[] get(String key) {
        byte[] value = values.get(key);
        if (value == null) throw new IllegalArgumentException("object does not exist: " + key);
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean delete(String key) { return values.remove(key) != null; }

    @Override
    public List<String> list(String prefix) {
        List<String> result = new ArrayList<>();
        values.keySet().stream().filter(key -> key.startsWith(prefix)).forEach(result::add);
        Collections.sort(result);
        return result;
    }
}
