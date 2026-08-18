package cn.bugstack.application.external.security;

/** 与 HTTP 框架无关的鉴权入参。 */
public final class HttpAuthenticationRequest {

    private final String authorization;
    private final String apiKey;

    public HttpAuthenticationRequest(String authorization, String apiKey) {
        this.authorization = authorization;
        this.apiKey = apiKey;
    }

    public String getAuthorization() {
        return authorization;
    }

    public String getApiKey() {
        return apiKey;
    }
}
