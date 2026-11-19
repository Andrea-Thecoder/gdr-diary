package it.liberheroum.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import it.liberheroum.controller.UserExportController;
import it.liberheroum.exception.ExceptionMapper;
import it.liberheroum.exception.RecentAuthenticationRequiredException;
import it.liberheroum.services.UserExport;
import it.liberheroum.services.UserExportService;
import java.io.OutputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserExportControllerTest {

    @Mock
    private UserExportService service;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new UserExportController(service))
                .setControllerAdvice(new ExceptionMapper())
                .build());
    }

    @Test
    void getUserExport_recentLogin_streamsTheZipAsAttachment() {
        UserExport export = new UserExport("liber-heroum-2026-11-19.zip", List.of());
        when(service.getUserExport()).thenReturn(export);
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write(new byte[]{80, 75, 3, 4});
            return null;
        }).when(service).writeUserExport(eq(export), any(OutputStream.class));

        MvcTestResult result = mvc.get().uri("/me/export").exchange();

        assertThat(result).hasStatusOk().hasContentType("application/zip")
                .headers().hasValue(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"liber-heroum-2026-11-19.zip\"; filename*=UTF-8''liber-heroum-2026-11-19.zip");
        assertThat(result.getResponse().getContentAsByteArray()).containsExactly(80, 75, 3, 4);
    }

    @Test
    void getUserExport_oldLogin_returnsUnauthorizedWithoutWriting() {
        when(service.getUserExport()).thenThrow(new RecentAuthenticationRequiredException(300));

        assertThat(mvc.get().uri("/me/export")).hasStatus(HttpStatus.UNAUTHORIZED)
                .headers().containsHeader(HttpHeaders.WWW_AUTHENTICATE);
    }
}
