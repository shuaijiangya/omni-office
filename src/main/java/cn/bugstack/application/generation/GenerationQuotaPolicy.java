package cn.bugstack.application.generation;

/** 按租户解析生成任务配额。 */
public interface GenerationQuotaPolicy {

    /**
     * 解析指定租户的生效配额。
     *
     * @param tenantId 租户 ID
     * @return 生效配额
     */
    GenerationQuota quota(String tenantId);

    /**
     * 创建不限制任务数量的策略。
     *
     * @return 无限配额策略
     */
    static GenerationQuotaPolicy unlimited() {
        return tenantId -> GenerationQuota.unlimited();
    }
}
