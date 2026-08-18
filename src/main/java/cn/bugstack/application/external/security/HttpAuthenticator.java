package cn.bugstack.application.external.security;

/** MCP HTTP 传输的可替换认证器。 */
public interface HttpAuthenticator {

    RequestIdentity authenticate(HttpAuthenticationRequest request);
}
