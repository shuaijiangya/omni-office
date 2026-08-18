package cn.bugstack.application.ai.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 适合单实例部署的 JSON Lines 轨迹库。 */
public final class JsonLinesAiTraceStore implements AiTraceStore {

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public JsonLinesAiTraceStore(Path path) {
        if (path == null) throw new IllegalArgumentException("AI trace path is required");
        this.path = path.toAbsolutePath().normalize();
        try {
            if (this.path.getParent() != null) Files.createDirectories(this.path.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("failed to create AI trace directory", e);
        }
    }

    @Override
    public synchronized void append(AiCallTrace trace) {
        if (trace == null) throw new IllegalArgumentException("AI trace is required");
        try {
            String line = mapper.writeValueAsString(trace) + System.lineSeparator();
            Files.write(path, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("failed to append AI trace", e);
        }
    }

    @Override
    public synchronized List<AiCallTrace> readAll() {
        if (!Files.exists(path)) return Collections.emptyList();
        try {
            List<AiCallTrace> values = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) values.add(mapper.readValue(line, AiCallTrace.class));
            }
            return Collections.unmodifiableList(values);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read AI traces", e);
        }
    }
}
