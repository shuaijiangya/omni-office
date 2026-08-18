package cn.bugstack.application.external.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 已认证的外部调用身份；所有 HTTP 会话、任务和工件都绑定到该身份。 */
public final class RequestIdentity {

    private final String tenantId;
    private final String principalId;
    private final Set<String> scopes;

    public RequestIdentity(String tenantId, String principalId, Set<String> scopes) {
        this.tenantId = requireIdentifier(tenantId, "tenant");
        this.principalId = requireIdentifier(principalId, "principal");
        this.scopes = scopes == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public boolean hasScope(String scope) {
        return scopes.contains("*") || scopes.contains(scope);
    }

    public String bindingKey() {
        return tenantId + ":" + principalId;
    }

    private static String requireIdentifier(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException(field + " id is invalid");
        }
        return value;
    }
}
