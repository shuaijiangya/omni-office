package cn.bugstack.application.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 单实例部署使用的追加式 JSON Lines 审计日志。 */
public final class JsonLinesAuditLog implements AuditLog {

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public JsonLinesAuditLog(Path path) {
        if (path == null) throw new IllegalArgumentException("audit log path is required");
        this.path = path.toAbsolutePath().normalize();
        try { if (this.path.getParent() != null) Files.createDirectories(this.path.getParent()); }
        catch (IOException e) { throw new IllegalStateException("failed to create audit log directory", e); }
    }

    @Override
    public synchronized void record(AuditEvent event) {
        if (event == null) throw new IllegalArgumentException("audit event is required");
        try {
            Files.write(path, (mapper.writeValueAsString(event) + System.lineSeparator())
                            .getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException e) { throw new IllegalStateException("failed to append audit event", e); }
    }
}
