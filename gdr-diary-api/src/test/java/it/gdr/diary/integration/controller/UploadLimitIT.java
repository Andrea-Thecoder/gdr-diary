package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.TestContainers;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class UploadLimitIT extends AbstractIntegrationTest {

    private static final String BOUNDARY = "gdr-diary-boundary";

    @LocalServerPort
    private int port;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/gdr-diary-api";
    }

    private String accessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", TestContainers.FRONTEND_CLIENT);
        form.add("username", "user1");
        form.add("password", "secret");
        String response = RestClient.create().post()
                .uri(TestContainers.issuerUri() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        return JsonPath.read(response, "$.access_token");
    }

    private byte[] multipartBody(byte[] content) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"me.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private int uploadAvatar(byte[] content) throws Exception {
        String token = accessToken();
        HttpClient client = HttpClient.newHttpClient();
        client.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/me"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/me/avatar"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(multipartBody(content))).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void updateAppUserAvatar_fileOverMultipartMaxFileSize_returnsPayloadTooLarge() throws Exception {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);

        int status = uploadAvatar(oversized);

        assertThat(status).isEqualTo(413);
    }

    @Test
    void updateAppUserAvatar_fileOverMultipartMaxFileSize_storesNoFile() throws Exception {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);

        uploadAvatar(oversized);

        assertThat(storedObjectKeys()).isEmpty();
    }
}
