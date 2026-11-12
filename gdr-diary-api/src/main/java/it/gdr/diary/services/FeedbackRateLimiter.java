package it.gdr.diary.services;

import it.gdr.diary.config.FeedbackConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class FeedbackRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final FeedbackConfig config;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public boolean tryAcquire(String client) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(config.getRateLimitWindow());
        if (attempts.size() > MAX_TRACKED_CLIENTS) {
            evictExpired(windowStart);
        }
        AtomicBoolean allowed = new AtomicBoolean(false);
        attempts.compute(client, (key, current) -> {
            Deque<Instant> recent = current == null ? new ArrayDeque<>() : current;
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(windowStart)) {
                recent.pollFirst();
            }
            if (recent.size() < config.getRateLimitMaxRequests()) {
                recent.addLast(now);
                allowed.set(true);
            }
            return recent;
        });
        return allowed.get();
    }

    private void evictExpired(Instant windowStart) {
        for (String client : attempts.keySet()) {
            attempts.computeIfPresent(client,
                    (key, recent) -> recent.isEmpty() || !recent.peekLast().isAfter(windowStart) ? null : recent);
        }
    }
}
