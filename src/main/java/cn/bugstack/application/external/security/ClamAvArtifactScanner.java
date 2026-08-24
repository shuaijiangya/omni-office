package cn.bugstack.application.external.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** 调用已安装 clamdscan/其他兼容命令的病毒扫描适配器。 */
public final class ClamAvArtifactScanner implements ArtifactSecurityScanner {

    private final Path executable;
    private final Duration timeout;

    public ClamAvArtifactScanner(Path executable, Duration timeout) {
        if (executable == null || !executable.isAbsolute() || timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("absolute scanner executable and positive timeout are required");
        }
        this.executable = executable.normalize();
        this.timeout = timeout;
    }

    @Override
    public void scan(byte[] content, String fileName, String mediaType) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("omni-office-scan-", ".bin");
            Files.write(temporary, content);
            Process process = new ProcessBuilder(executable.toString(), "--no-summary", temporary.toString())
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ArtifactSecurityException("virus scan timed out");
            }
            if (process.exitValue() != 0) throw new ArtifactSecurityException("virus scan rejected artifact");
        } catch (IOException e) {
            throw new ArtifactSecurityException("virus scanner could not execute", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactSecurityException("virus scan was interrupted", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void scan(Path contentPath, String fileName, String mediaType) {
        if (contentPath == null || !Files.isRegularFile(contentPath)) {
            throw new ArtifactSecurityException("virus scan input is invalid");
        }
        try {
            Process process = new ProcessBuilder(executable.toString(), "--no-summary", contentPath.toString())
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ArtifactSecurityException("virus scan timed out");
            }
            if (process.exitValue() != 0) throw new ArtifactSecurityException("virus scan rejected artifact");
        } catch (IOException e) {
            throw new ArtifactSecurityException("virus scanner could not execute", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactSecurityException("virus scan was interrupted", e);
        }
    }
}
