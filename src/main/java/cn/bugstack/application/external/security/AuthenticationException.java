package cn.bugstack.application.external.security;

/** 不向调用方泄露凭证细节的认证失败。 */
public final class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
