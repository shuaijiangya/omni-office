package cn.bugstack.application.generation;

/** 租户生成任务配额；正整数上限在任务持久化时原子执行。 */
public final class GenerationQuota {

    private static final GenerationQuota UNLIMITED = new GenerationQuota(Integer.MAX_VALUE,
            Integer.MAX_VALUE);

    private final int maxActiveJobs;
    private final int maxJobsPerDay;

    /**
     * 创建租户生成任务配额。
     *
     * @param maxActiveJobs 同时处于排队或运行状态的最大任务数
     * @param maxJobsPerDay 每个 UTC 自然日允许创建的最大任务数
     * @throws IllegalArgumentException 任一上限小于 1 时抛出
     */
    public GenerationQuota(int maxActiveJobs, int maxJobsPerDay) {
        if (maxActiveJobs < 1 || maxJobsPerDay < 1) {
            throw new IllegalArgumentException("generation quota limits must be positive");
        }
        this.maxActiveJobs = maxActiveJobs;
        this.maxJobsPerDay = maxJobsPerDay;
    }

    /**
     * 获取不施加实际业务限制的配额。
     *
     * @return 共享的无限制配额实例
     */
    public static GenerationQuota unlimited() { return UNLIMITED; }

    /** @return 最大活动任务数 */
    public int getMaxActiveJobs() { return maxActiveJobs; }

    /** @return 每个 UTC 自然日的最大任务创建数 */
    public int getMaxJobsPerDay() { return maxJobsPerDay; }
}
