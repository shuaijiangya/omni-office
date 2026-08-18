package cn.bugstack.application.external.security;

/** 根据请求携带的凭证类型选择 API Key 或 Bearer JWT。 */
public final class CompositeHttpAuthenticator implements HttpAuthenticator {

    private final HttpAuthenticator apiKeyAuthenticator;
    private final HttpAuthenticator bearerAuthenticator;

    public CompositeHttpAuthenticator(HttpAuthenticator apiKeyAuthenticator,
                                      HttpAuthenticator bearerAuthenticator) {
        if (apiKeyAuthenticator == null && bearerAuthenticator == null) {
            throw new IllegalArgumentException("at least one HTTP authenticator is required");
        }
        this.apiKeyAuthenticator = apiKeyAuthenticator;
        this.bearerAuthenticator = bearerAuthenticator;
    }

    @Override
    public RequestIdentity authenticate(HttpAuthenticationRequest request) {
        if (request != null && request.getApiKey() != null && apiKeyAuthenticator != null) {
            return apiKeyAuthenticator.authenticate(request);
        }
        if (bearerAuthenticator != null) {
            return bearerAuthenticator.authenticate(request);
        }
        throw new AuthenticationException("missing credentials");
    }
}
