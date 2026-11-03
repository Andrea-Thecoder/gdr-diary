package it.gdr.diary.integration.support;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public final class RealHttp {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final String baseUrl;
    private final String token;

    public RealHttp(int port) {
        this.baseUrl = "http://localhost:" + port + "/api/v1/gdr-diary-api";
        this.token = accessToken("user1");
    }

    public static String accessToken(String username) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", TestContainers.FRONTEND_CLIENT);
        form.add("username", username);
        form.add("password", "secret");
        String response = RestClient.create().post()
                .uri(TestContainers.issuerUri() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        return JsonPath.read(response, "$.access_token");
    }

    public HttpResponse<String> send(String method, String path, String contentType, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (contentType != null) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }
        try {
            return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public HttpResponse<String> send(String method, String path) {
        return send(method, path, null, null);
    }

    public static String field(HttpResponse<String> response, String path) {
        return String.valueOf((Object) JsonPath.read(response.body(), path));
    }
}
