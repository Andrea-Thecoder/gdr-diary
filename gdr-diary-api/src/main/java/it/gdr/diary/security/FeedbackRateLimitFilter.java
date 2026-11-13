package it.gdr.diary.security;

import it.gdr.diary.config.FeedbackConfig;
import it.gdr.diary.services.FeedbackRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class FeedbackRateLimitFilter extends OncePerRequestFilter {

    private static final String FEEDBACK_PATH = "/feedback";

    private final FeedbackRateLimiter rateLimiter;
    private final FeedbackConfig config;
    private final HandlerExceptionResolver exceptionResolver;

    public FeedbackRateLimitFilter(FeedbackRateLimiter rateLimiter, FeedbackConfig config,
                                   @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.rateLimiter = rateLimiter;
        this.config = config;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !FEEDBACK_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (rateLimiter.tryAcquire(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(config.getRateLimitWindow().toSeconds()));
        exceptionResolver.resolveException(request, response, null,
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many feedback submissions, try again later"));
    }
}
