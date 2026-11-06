package it.gdr.diary.integration.support;

import com.jayway.jsonpath.JsonPath;
import io.ebean.Database;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    protected static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected Database database;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MinioClient minioClient;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", TestContainers.POSTGRES::getPassword);
        registry.add("app.storage.endpoint", TestContainers::storageEndpoint);
        registry.add("app.storage.access-key", () -> TestContainers.STORAGE_ACCESS_KEY);
        registry.add("app.storage.secret-key", () -> TestContainers.STORAGE_SECRET_KEY);
        registry.add("app.storage.bucket", () -> TestContainers.BUCKET);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", TestContainers::issuerUri);
    }

    @AfterEach
    void cleanUp() throws Exception {
        jdbc.execute("truncate table character_sheet, diary_page_tag, diary_page, diary, game_character, app_user, document cascade");
        for (String key : storedObjectKeys()) {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(TestContainers.BUCKET).object(key).build());
        }
    }

    protected List<String> storedObjectKeys() throws Exception {
        List<String> keys = new ArrayList<>();
        for (Result<Item> item : minioClient.listObjects(ListObjectsArgs.builder().bucket(TestContainers.BUCKET).recursive(true).build())) {
            keys.add(item.get().objectName());
        }
        return keys;
    }

    protected int countRows(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    protected static String json(MvcTestResult result, String path) {
        return String.valueOf((Object) JsonPath.read(responseBody(result), path));
    }

    protected static String responseBody(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static MockMultipartFile pngFile(String name) {
        return new MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, PNG);
    }

    protected static MockMultipartFile pdfFile(String name) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_PDF_VALUE, PDF);
    }

    protected static String pageContent(String text) {
        return "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]}]}";
    }

    protected static final RequestPostProcessor CURRENT_USER = request -> request;

    protected UUID createAppUser() {
        return createAppUser(CURRENT_USER);
    }

    protected UUID createGameCharacter(String name) {
        return createGameCharacter(name, CURRENT_USER);
    }

    protected UUID createDiary(UUID characterId, String title) {
        return createDiary(characterId, title, CURRENT_USER);
    }

    protected UUID createDiaryPage(UUID diaryId, String text) {
        return createDiaryPage(diaryId, text, CURRENT_USER);
    }

    protected UUID createDiaryWithOwner() {
        return createDiaryWithOwner(CURRENT_USER);
    }

    protected UUID createAppUser(RequestPostProcessor user) {
        return UUID.fromString(json(mvc.post().uri("/me").with(user).exchange(), "$.payload"));
    }

    protected UUID createGameCharacter(String name, RequestPostProcessor user) {
        MvcTestResult result = mvc.post().uri("/characters").with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"kind\":\"PLAYER\",\"campaignName\":\"Lost Mine\",\"backgroundColor\":\"#1A2B3C\"}")
                .exchange();
        return UUID.fromString(json(result, "$.id"));
    }

    protected UUID createDiary(UUID characterId, String title, RequestPostProcessor user) {
        MvcTestResult result = mvc.post().uri("/characters/{characterId}/diaries", characterId).with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"coverColor\":\"#000000\",\"font\":\"Lora\",\"fontSize\":16}")
                .exchange();
        return UUID.fromString(json(result, "$.id"));
    }

    protected UUID createDiaryPage(UUID diaryId, String text, RequestPostProcessor user) {
        MvcTestResult result = mvc.post().uri("/diaries/{diaryId}/pages", diaryId).with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + pageContent(text) + "}")
                .exchange();
        return UUID.fromString(json(result, "$.id"));
    }

    protected List<UUID> createDiaryPages(UUID diaryId, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(number -> createDiaryPage(diaryId, "Page " + number)).toList();
    }

    protected UUID createDiaryWithOwner(RequestPostProcessor user) {
        createAppUser(user);
        return createDiary(createGameCharacter("Aragorn", user), "Chapter one", user);
    }
}
