package it.gdr.diary.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import it.gdr.diary.config.FeedbackConfig;
import it.gdr.diary.services.FeedbackRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedbackRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final String CLIENT = "203.0.113.7";

    @Mock
    private Clock clock;

    private final FeedbackConfig config = new FeedbackConfig();

    private FeedbackRateLimiter limiter;

    @BeforeEach
    void setUp() {
        config.setRateLimitMaxRequests(3);
        config.setRateLimitWindow(Duration.ofHours(1));
        limiter = new FeedbackRateLimiter(config, clock);
    }

    private void acquireAt(Instant instant, int times) {
        when(clock.instant()).thenReturn(instant);
        IntStream.range(0, times).forEach(index -> limiter.tryAcquire(CLIENT));
    }

    @Test
    void tryAcquire_firstRequest_allows() {
        when(clock.instant()).thenReturn(NOW);

        boolean allowed = limiter.tryAcquire(CLIENT);

        assertThat(allowed).isTrue();
    }

    @Test
    void tryAcquire_lastRequestWithinLimit_allows() {
        acquireAt(NOW, 2);

        boolean allowed = limiter.tryAcquire(CLIENT);

        assertThat(allowed).isTrue();
    }

    @Test
    void tryAcquire_requestOverLimit_refuses() {
        acquireAt(NOW, 3);

        boolean allowed = limiter.tryAcquire(CLIENT);

        assertThat(allowed).isFalse();
    }

    @Test
    void tryAcquire_otherClientAfterLimit_allows() {
        acquireAt(NOW, 3);

        boolean allowed = limiter.tryAcquire("198.51.100.4");

        assertThat(allowed).isTrue();
    }

    @Test
    void tryAcquire_windowElapsed_allowsAgain() {
        acquireAt(NOW, 3);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(1)));

        boolean allowed = limiter.tryAcquire(CLIENT);

        assertThat(allowed).isTrue();
    }

    @Test
    void tryAcquire_oneSecondBeforeWindowEnds_refuses() {
        acquireAt(NOW, 3);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(1)).minusSeconds(1));

        boolean allowed = limiter.tryAcquire(CLIENT);

        assertThat(allowed).isFalse();
    }

    @Test
    void tryAcquire_refusedRequest_doesNotExtendTheWindow() {
        acquireAt(NOW, 3);
        acquireAt(NOW.plus(Duration.ofMinutes(30)), 5);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(1)));

        boolean allowed = limiter.tryAcquire(CLIENT);

        assertThat(allowed).isTrue();
    }

    @Test
    void tryAcquire_oldestRequestExpired_allowsOnlyOneMore() {
        acquireAt(NOW, 1);
        acquireAt(NOW.plus(Duration.ofMinutes(30)), 2);
        acquireAt(NOW.plus(Duration.ofHours(1)), 1);

        boolean allowed = limiter.tryAcquire(CLIENT);

        assertThat(allowed).isFalse();
    }
}
