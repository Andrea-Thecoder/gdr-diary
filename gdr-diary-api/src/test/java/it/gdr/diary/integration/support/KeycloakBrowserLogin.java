package it.gdr.diary.integration.support;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class KeycloakBrowserLogin {

    private static final String REDIRECT_URI = "http://localhost:5173/callback";
    private static final Pattern FORM_ACTION = Pattern.compile("<form[^>]*id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"");
    private static final Pattern CODE = Pattern.compile("[?&]code=([^&]+)");

    private KeycloakBrowserLogin() {
    }

    public static String accessToken(String username, String password) {
        HttpClient browser = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        String verifier = randomVerifier();
        try {
            HttpResponse<String> loginPage = browser.send(HttpRequest.newBuilder(URI.create(authorizationUrl(challenge(verifier)))).build(),
                    HttpResponse.BodyHandlers.ofString());
            Matcher action = FORM_ACTION.matcher(loginPage.body());
            if (!action.find()) {
                throw new IllegalStateException("Keycloak login form not found");
            }
            HttpResponse<String> afterLogin = browser.send(HttpRequest.newBuilder(URI.create(action.group(1).replace("&amp;", "&")))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Cookie", cookies(loginPage))
                            .POST(form(Map.of("username", username, "password", password, "credentialId", "")))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            Matcher code = CODE.matcher(afterLogin.headers().firstValue("Location").orElseThrow(() ->
                    new IllegalStateException("Keycloak did not redirect after login: HTTP " + afterLogin.statusCode())));
            if (!code.find()) {
                throw new IllegalStateException("No authorization code in the redirect");
            }
            Map<String, String> exchange = new LinkedHashMap<>();
            exchange.put("grant_type", "authorization_code");
            exchange.put("client_id", TestContainers.FRONTEND_CLIENT);
            exchange.put("code", code.group(1));
            exchange.put("redirect_uri", REDIRECT_URI);
            exchange.put("code_verifier", verifier);
            String tokens = browser.send(HttpRequest.newBuilder(URI.create(TestContainers.issuerUri() + "/protocol/openid-connect/token"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(form(exchange))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            return JsonPath.read(tokens, "$.access_token");
        } catch (IOException e) {
            throw new IllegalStateException("Browser login against Keycloak failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String authorizationUrl(String challenge) {
        return TestContainers.issuerUri() + "/protocol/openid-connect/auth?" + Map.of(
                        "client_id", TestContainers.FRONTEND_CLIENT,
                        "response_type", "code",
                        "scope", "openid",
                        "redirect_uri", REDIRECT_URI,
                        "state", UUID.randomUUID().toString(),
                        "code_challenge", challenge,
                        "code_challenge_method", "S256")
                .entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String cookies(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .map(cookie -> cookie.split(";", 2)[0])
                .collect(Collectors.joining("; "));
    }

    private static HttpRequest.BodyPublisher form(Map<String, String> fields) {
        return HttpRequest.BodyPublishers.ofString(fields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")));
    }

    private static String randomVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
