package it.gdr.diary.unit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.gdr.diary.config.FeedbackConfig;
import it.gdr.diary.security.FeedbackRateLimitFilter;
import it.gdr.diary.services.FeedbackRateLimiter;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class FeedbackRateLimitFilterTest {

    private static final String CLIENT = "203.0.113.7";

    @Mock
    private FeedbackRateLimiter rateLimiter;
    @Mock
    private HandlerExceptionResolver exceptionResolver;
    @Mock
    private FilterChain chain;

    private final FeedbackConfig config = new FeedbackConfig();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private FeedbackRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        config.setRateLimitWindow(Duration.ofMinutes(30));
        filter = new FeedbackRateLimitFilter(rateLimiter, config, exceptionResolver);
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(CLIENT);
        return request;
    }

    @Test
    void doFilter_feedbackPostAllowed_continuesTheChain() throws Exception {
        MockHttpServletRequest request = request("POST", "/feedback");
        when(rateLimiter.tryAcquire(CLIENT)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_feedbackPostRefused_stopsTheChain() throws Exception {
        when(rateLimiter.tryAcquire(CLIENT)).thenReturn(false);

        filter.doFilter(request("POST", "/feedback"), response, chain);

        verifyNoInteractions(chain);
    }

    @Test
    void doFilter_feedbackPostRefused_setsRetryAfterToWindowSeconds() throws Exception {
        when(rateLimiter.tryAcquire(CLIENT)).thenReturn(false);

        filter.doFilter(request("POST", "/feedback"), response, chain);

        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1800");
    }

    @Test
    void doFilter_feedbackPostRefused_resolvesTooManyRequests() throws Exception {
        MockHttpServletRequest request = request("POST", "/feedback");
        when(rateLimiter.tryAcquire(CLIENT)).thenReturn(false);
        ArgumentCaptor<Exception> error = ArgumentCaptor.forClass(Exception.class);

        filter.doFilter(request, response, chain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(), error.capture());
        assertThat(error.getValue()).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void doFilter_feedbackPost_usesTheClientAddress() throws Exception {
        when(rateLimiter.tryAcquire(any())).thenReturn(true);

        filter.doFilter(request("POST", "/feedback"), response, chain);

        verify(rateLimiter).tryAcquire(CLIENT);
    }

    @ParameterizedTest
    @CsvSource({"GET,/feedback", "DELETE,/feedback/7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10", "POST,/feedback/other", "POST,/characters"})
    void doFilter_otherRequest_doesNotCountIt(String method, String path) throws Exception {
        filter.doFilter(request(method, path), response, chain);

        verifyNoInteractions(rateLimiter);
    }

    @ParameterizedTest
    @CsvSource({"GET,/feedback", "POST,/characters"})
    void doFilter_otherRequest_continuesTheChain(String method, String path) throws Exception {
        MockHttpServletRequest request = request(method, path);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
