package cn.bugstack.application.audit;

/** 安全审计落点 SPI。 */
public interface AuditLog {

    /**
     * 记录不包含凭证和文档正文的审计事件。
     *
     * @param event 审计事件
     */
    void record(AuditEvent event);

    /** @return 丢弃所有事件的无操作审计实现 */
    static AuditLog noop() { return event -> { }; }
}
