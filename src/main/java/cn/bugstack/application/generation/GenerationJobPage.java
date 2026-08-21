package cn.bugstack.application.generation;

import java.util.ArrayList;
import java.util.List;

/** 稳定游标分页结果。 */
public final class GenerationJobPage {

    private final List<GenerationJobRecord> jobs;
    private final String nextCursor;

    /**
     * 创建任务分页结果。
     *
     * @param jobs 当前页任务；传入列表会被防御性复制
     * @param nextCursor 下一页不透明游标，没有后续数据时为 {@code null}
     */
    public GenerationJobPage(List<GenerationJobRecord> jobs, String nextCursor) {
        this.jobs = jobs == null ? new ArrayList<>() : new ArrayList<>(jobs);
        this.nextCursor = nextCursor;
    }

    /** @return 当前页任务的防御性副本 */
    public List<GenerationJobRecord> getJobs() { return new ArrayList<>(jobs); }

    /** @return 下一页不透明游标，没有后续数据时返回 {@code null} */
    public String getNextCursor() { return nextCursor; }
}
