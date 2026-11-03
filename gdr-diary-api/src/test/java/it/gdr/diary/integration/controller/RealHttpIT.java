package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.RealHttp;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class RealHttpIT extends AbstractIntegrationTest {

    private static final String BOUNDARY = "gdr-diary-boundary";

    @LocalServerPort
    private int port;

    private RealHttp http;

    @BeforeEach
    void createUser() {
        http = new RealHttp(port);
        http.send("POST", "/me");
    }

    private HttpResponse<String> putAvatar(String contentType, String body) {
        return http.send("PUT", "/me/avatar", contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static String validMultipartHead() {
        return "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"me.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n";
    }

    @Test
    void updateAppUserAvatar_multipartWithoutBoundary_returnsBadRequest() {
        HttpResponse<String> response = putAvatar("multipart/form-data", "garbage");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void updateAppUserAvatar_multipartWithoutBoundary_returnsExceptionResponseBody() {
        HttpResponse<String> response = putAvatar("multipart/form-data", "garbage");

        assertThat(RealHttp.field(response, "$.title")).isEqualTo("BAD_REQUEST");
    }

    @Test
    void updateAppUserAvatar_truncatedMultipartBody_returnsBadRequest() {
        HttpResponse<String> response = putAvatar("multipart/form-data; boundary=" + BOUNDARY, validMultipartHead() + "\u0089PNG");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void updateAppUserAvatar_truncatedMultipartBody_returnsExceptionResponseBody() {
        HttpResponse<String> response = putAvatar("multipart/form-data; boundary=" + BOUNDARY, validMultipartHead() + "\u0089PNG");

        assertThat(RealHttp.field(response, "$.title")).isEqualTo("BAD_REQUEST");
    }

    @Test
    void updateAppUserAvatar_truncatedMultipartBody_storesNoFile() throws Exception {
        putAvatar("multipart/form-data; boundary=" + BOUNDARY, validMultipartHead() + "\u0089PNG");

        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void unknownRoute_validUserToken_returnsNotFound() {
        HttpResponse<String> response = http.send("GET", "/nope");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void unknownRoute_validUserToken_returnsExceptionResponseBody() {
        HttpResponse<String> response = http.send("GET", "/nope");

        assertThat(RealHttp.field(response, "$.status")).isEqualTo("404");
    }

    @Test
    void deleteCharacters_methodNotAllowed_returnsMethodNotAllowed() {
        HttpResponse<String> response = http.send("DELETE", "/characters");

        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void deleteCharacters_methodNotAllowed_returnsExceptionResponseBody() {
        HttpResponse<String> response = http.send("DELETE", "/characters");

        assertThat(RealHttp.field(response, "$.status")).isEqualTo("405");
    }

    @Test
    void createGameCharacter_plainTextBody_returnsUnsupportedMediaType() {
        HttpResponse<String> response = http.send("POST", "/characters", "text/plain", "Aragorn".getBytes(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(415);
    }

    @Test
    void createGameCharacter_plainTextBody_returnsExceptionResponseBody() {
        HttpResponse<String> response = http.send("POST", "/characters", "text/plain", "Aragorn".getBytes(StandardCharsets.UTF_8));

        assertThat(RealHttp.field(response, "$.status")).isEqualTo("415");
    }
}
