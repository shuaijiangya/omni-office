package cn.bugstack.application.external.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** 在迁移期同时接受明确配置的 HS256 与 OIDC/JWKS Bearer 令牌。 */
public final class CompositeBearerAuthenticator implements HttpAuthenticator {

    private final List<HttpAuthenticator> delegates;

    /**
     * 创建按顺序尝试多个 Bearer 认证器的组合认证器。
     *
     * @param delegates 至少一个 Bearer 认证器
     */
    public CompositeBearerAuthenticator(Collection<? extends HttpAuthenticator> delegates) {
        List<HttpAuthenticator> values = new ArrayList<>();
        if (delegates != null) delegates.stream().filter(java.util.Objects::nonNull).forEach(values::add);
        if (values.isEmpty()) throw new IllegalArgumentException("at least one bearer authenticator is required");
        this.delegates = Collections.unmodifiableList(values);
    }

    @Override
    public RequestIdentity authenticate(HttpAuthenticationRequest request) {
        AuthenticationException last = null;
        for (HttpAuthenticator delegate : delegates) {
            try {
                return delegate.authenticate(request);
            } catch (AuthenticationException e) {
                last = e;
            }
        }
        throw new AuthenticationException("invalid credentials", last);
    }
}
