package cn.bugstack.application.external.security;

/** 生成产物未通过安全检查。 */
public final class ArtifactSecurityException extends RuntimeException {

    public ArtifactSecurityException(String message) { super(message); }
    public ArtifactSecurityException(String message, Throwable cause) { super(message, cause); }
}
