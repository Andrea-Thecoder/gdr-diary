package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.RealHttp;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;

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

    private HttpResponse<String> postFeedbackFrom(String localAddress, String contentType, byte[] body) {
        try {
            HttpClient client = HttpClient.newBuilder().localAddress(InetAddress.getByName(localAddress)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/gdr-diary-api/feedback"))
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] feedbackForm(byte[] image) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        Map.of("type", "idea", "message", "Great tags", "email", "alira@example.com", "consent", "true")
                .forEach((name, value) -> body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + name
                        + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8)));
        body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"shot.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(image);
        body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private static byte[] imageOfSize(int size) {
        return Arrays.copyOf(PNG, size);
    }

    private HttpResponse<String> postFeedbackFrom(String localAddress, byte[] body) {
        return postFeedbackFrom(localAddress, "multipart/form-data; boundary=" + BOUNDARY, body);
    }

    @Test
    void createFeedback_validFormWithoutTokenOverTomcat_returnsCreated() {
        HttpResponse<String> response = postFeedbackFrom("127.0.0.2", feedbackForm(PNG));

        assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void createFeedback_sixthRequestFromSameAddressOverTomcat_returnsTooManyRequests() {
        IntStream.range(0, 5).forEach(index -> postFeedbackFrom("127.0.0.3", new byte[0]));

        HttpResponse<String> response = postFeedbackFrom("127.0.0.3", new byte[0]);

        assertThat(response.statusCode()).isEqualTo(429);
    }

    @Test
    void createFeedback_limitReachedByOtherAddressOverTomcat_returnsCreated() {
        IntStream.range(0, 6).forEach(index -> postFeedbackFrom("127.0.0.4", new byte[0]));

        HttpResponse<String> response = postFeedbackFrom("127.0.0.5", feedbackForm(PNG));

        assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void createFeedback_fileOverMultipartMaxFileSizeOverTomcat_returnsPayloadTooLarge() {
        HttpResponse<String> response = postFeedbackFrom("127.0.0.6", feedbackForm(imageOfSize(10 * 1024 * 1024 + 1)));

        assertThat(response.statusCode()).isEqualTo(413);
    }

    @Test
    void createFeedback_fileOverMultipartMaxFileSizeOverTomcat_savesNothing() throws Exception {
        postFeedbackFrom("127.0.0.7", feedbackForm(imageOfSize(10 * 1024 * 1024 + 1)));

        assertThat(countRows("feedback") + storedObjectKeys(FEEDBACK_BUCKET).size()).isZero();
    }

    @Test
    void createFeedback_imageOverImageLimitButUnderMultipartLimitOverTomcat_returnsBadRequest() {
        HttpResponse<String> response = postFeedbackFrom("127.0.0.8", feedbackForm(imageOfSize(6 * 1024 * 1024)));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void createFeedback_truncatedMultipartOverTomcat_returnsBadRequest() {
        byte[] form = feedbackForm(PNG);

        HttpResponse<String> response = postFeedbackFrom("127.0.0.9", Arrays.copyOf(form, form.length / 2));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void createFeedback_multipartWithoutBoundaryOverTomcat_returnsExceptionResponseBody() {
        HttpResponse<String> response = postFeedbackFrom("127.0.0.10", "multipart/form-data", "garbage".getBytes(StandardCharsets.UTF_8));

        assertThat(RealHttp.field(response, "$.title")).isEqualTo("BAD_REQUEST");
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
