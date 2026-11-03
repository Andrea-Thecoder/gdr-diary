package it.gdr.diary.exception;

import lombok.Getter;

@Getter
public class RecentAuthenticationRequiredException extends RuntimeException {

    private final long maxAgeSeconds;

    public RecentAuthenticationRequiredException(long maxAgeSeconds) {
        super("Recent authentication required");
        this.maxAgeSeconds = maxAgeSeconds;
    }
}
