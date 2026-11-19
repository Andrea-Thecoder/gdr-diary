package it.gdr.diary.security;

import it.gdr.diary.exception.RecentAuthenticationRequiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RecentAuthentication {

    private final JwtInspector jwtInspector;
    private final Clock clock;

    public void require(Duration maxAge) {
        Instant limit = clock.instant().minus(maxAge);
        boolean recent = jwtInspector.getAuthTime().filter(authTime -> !authTime.isBefore(limit)).isPresent();
        if (!recent) {
            throw new RecentAuthenticationRequiredException(maxAge.toSeconds());
        }
    }
}
