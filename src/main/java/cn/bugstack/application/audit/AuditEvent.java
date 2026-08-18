package cn.bugstack.application.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 不包含凭证与文档正文的结构化审计事件。 */
public final class AuditEvent {

    private Instant time;
    private String action;
    private String tenantId;
    private String principalId;
    private String outcome;
    private Map<String, String> attributes = new LinkedHashMap<>();

    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPrincipalId() { return principalId; }
    public void setPrincipalId(String principalId) { this.principalId = principalId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
}
