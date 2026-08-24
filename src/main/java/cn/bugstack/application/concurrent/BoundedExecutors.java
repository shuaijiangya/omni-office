package cn.bugstack.application.concurrent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 创建具名、守护、有限队列的应用线程池，避免默认无界队列隐藏过载。 */
public final class BoundedExecutors {

    private BoundedExecutors() {
    }

    /**
     * 创建固定大小且队列有界的线程池。
     *
     * @param threads 工作线程数
     * @param queueCapacity 等待队列容量
     * @param threadPrefix 线程名前缀
     * @param rejectionHandler 队列已满时的拒绝策略
     * @return 可观测的线程池执行器
     */
    public static ThreadPoolExecutor fixed(int threads, int queueCapacity, String threadPrefix,
                                           RejectedExecutionHandler rejectionHandler) {
        if (threads < 1 || queueCapacity < 1 || threadPrefix == null || threadPrefix.isBlank()
                || rejectionHandler == null) {
            throw new IllegalArgumentException("bounded executor configuration is invalid");
        }
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), daemonFactory(threadPrefix), rejectionHandler);
        return executor;
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
