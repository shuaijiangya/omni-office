package cn.bugstack.application.external.security;

/** MCP HTTP 传输的可替换认证器。 */
public interface HttpAuthenticator {

    /**
     * 验证请求凭证并解析租户、主体和权限范围。
     *
     * @param request HTTP 认证信息
     * @return 已认证身份
     * @throws AuthenticationException 凭证缺失、无效或过期时抛出
     */
    RequestIdentity authenticate(HttpAuthenticationRequest request);
}
