package cn.bugstack.application.external.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Pattern;

/** 可选的文本敏感信息阻断器，适合 HTML/JSON 等文本产物。 */
public final class SensitiveDataArtifactScanner implements ArtifactSecurityScanner {

    private static final Pattern SECRET = Pattern.compile(
            "(?i)(-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|"
                    + "(?:password|passwd|secret|api[_-]?key)\\s*[:=]\\s*[^\\s]{8,})");

    @Override
    public void scan(byte[] content, String fileName, String mediaType) {
        if (mediaType != null && (mediaType.startsWith("text/") || mediaType.endsWith("json"))) {
            String value = new String(content, StandardCharsets.UTF_8);
            if (SECRET.matcher(value).find()) {
                throw new ArtifactSecurityException("artifact contains a potential secret");
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void scan(Path contentPath, String fileName, String mediaType) {
        if (mediaType == null || !(mediaType.startsWith("text/") || mediaType.endsWith("json"))) return;
        try (Reader reader = Files.newBufferedReader(contentPath, StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            String carry = "";
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                String candidate = carry + new String(buffer, 0, read);
                if (SECRET.matcher(candidate).find()) {
                    throw new ArtifactSecurityException("artifact contains a potential secret");
                }
                carry = candidate.substring(Math.max(0, candidate.length() - 4096));
            }
        } catch (IOException e) {
            throw new ArtifactSecurityException("text artifact cannot be scanned", e);
        }
    }
}
