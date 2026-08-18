package cn.bugstack.application.external.http;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 按认证身份执行固定一分钟窗口限流。 */
final class IdentityRateLimiter {

    private final int limit;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    IdentityRateLimiter(int limit) {
        this(limit, Clock.systemUTC());
    }

    IdentityRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    boolean allow(String identity) {
        long minute = clock.millis() / 60_000L;
        Window window = windows.compute(identity, (key, current) ->
                current == null || current.minute != minute ? new Window(minute) : current);
        return window.count.incrementAndGet() <= limit;
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();
        private Window(long minute) { this.minute = minute; }
    }
}
