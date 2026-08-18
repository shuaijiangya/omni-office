package cn.bugstack.application.audit;

/** 安全审计落点 SPI。 */
public interface AuditLog {

    void record(AuditEvent event);

    static AuditLog noop() { return event -> { }; }
}
