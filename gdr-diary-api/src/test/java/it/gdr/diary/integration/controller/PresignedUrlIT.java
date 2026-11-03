package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import it.gdr.diary.config.StorageConfig;
import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

@WithMockJwt
class PresignedUrlIT extends AbstractIntegrationTest {

    @Autowired
    private StorageConfig storageConfig;

    private static String objectKeyOf(String url) {
        String path = URI.create(url).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String expiresOf(String url) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("X-Amz-Expires");
    }

    private static String stringAt(MvcTestResult result, String path) {
        return JsonPath.read(responseBody(result), path);
    }

    private static List<String> listAt(MvcTestResult result, String path) {
        return JsonPath.read(responseBody(result), path);
    }

    private String characterAvatarKey(UUID characterId) {
        return jdbc.queryForObject("select d.object_key from document d join game_character c on c.avatar_document_id = d.id where c.id = ?",
                String.class, characterId);
    }

    private String diaryCoverKey(UUID diaryId) {
        return jdbc.queryForObject("select d.object_key from document d join diary y on y.cover_document_id = d.id where y.id = ?",
                String.class, diaryId);
    }

    private String userAvatarKey() {
        return jdbc.queryForObject("select d.object_key from document d join app_user u on u.avatar_document_id = d.id", String.class);
    }

    private UUID characterWithAvatar(String name, RequestPostProcessor user) {
        UUID characterId = createGameCharacter(name, user);
        mvc.put().uri("/characters/{id}/avatar", characterId).with(user).multipart().file(pngFile("a.png")).exchange();
        return characterId;
    }

    @Test
    void getGameCharacterById_withAvatar_returnsUrlOfStoredObjectKey() {
        createAppUser();
        UUID characterId = characterWithAvatar("Aragorn", CURRENT_USER);

        String url = stringAt(mvc.get().uri("/characters/{id}", characterId).exchange(), "$.avatarUrl");

        assertThat(objectKeyOf(url)).isEqualTo(characterAvatarKey(characterId));
    }

    @Test
    void getGameCharacterById_withAvatar_returnsUrlExpiringAfterConfiguredSeconds() {
        createAppUser();
        UUID characterId = characterWithAvatar("Aragorn", CURRENT_USER);

        String url = stringAt(mvc.get().uri("/characters/{id}", characterId).exchange(), "$.avatarUrl");

        assertThat(expiresOf(url)).isEqualTo(String.valueOf(storageConfig.getUrlExpiry().toSeconds()));
    }

    @Test
    void findAllGameCharacters_withAvatar_returnsUrlOfStoredObjectKey() {
        createAppUser();
        UUID characterId = characterWithAvatar("Aragorn", CURRENT_USER);

        List<String> urls = listAt(mvc.get().uri("/characters").exchange(), "$.list[*].avatarUrl");

        assertThat(urls).extracting(PresignedUrlIT::objectKeyOf).containsExactly(characterAvatarKey(characterId));
    }

    @Test
    void findAllGameCharacters_withAvatar_returnsUrlExpiringAfterConfiguredSeconds() {
        createAppUser();
        characterWithAvatar("Aragorn", CURRENT_USER);

        List<String> urls = listAt(mvc.get().uri("/characters").exchange(), "$.list[*].avatarUrl");

        assertThat(urls).extracting(PresignedUrlIT::expiresOf).containsExactly(String.valueOf(storageConfig.getUrlExpiry().toSeconds()));
    }

    @Test
    void findAllGameCharacters_twoUsersWithAvatars_returnsOnlyOwnObjectKeys() {
        createAppUser();
        createAppUser(TestJwt.otherUser());
        UUID own = characterWithAvatar("Aragorn", CURRENT_USER);
        characterWithAvatar("Samwise", TestJwt.otherUser());

        List<String> urls = listAt(mvc.get().uri("/characters").exchange(), "$.list[*].avatarUrl");

        assertThat(urls).extracting(PresignedUrlIT::objectKeyOf).containsExactly(characterAvatarKey(own));
    }

    @Test
    void getDiaryById_withCover_returnsUrlOfStoredObjectKey() {
        UUID diaryId = createDiaryWithOwner();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("c.png")).exchange();

        String url = stringAt(mvc.get().uri("/diaries/{id}", diaryId).exchange(), "$.coverUrl");

        assertThat(objectKeyOf(url)).isEqualTo(diaryCoverKey(diaryId));
    }

    @Test
    void getDiaryById_withCover_returnsUrlExpiringAfterConfiguredSeconds() {
        UUID diaryId = createDiaryWithOwner();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("c.png")).exchange();

        String url = stringAt(mvc.get().uri("/diaries/{id}", diaryId).exchange(), "$.coverUrl");

        assertThat(expiresOf(url)).isEqualTo(String.valueOf(storageConfig.getUrlExpiry().toSeconds()));
    }

    @Test
    void findAllDiariesByCharacter_withCover_returnsUrlOfStoredObjectKey() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID diaryId = createDiary(characterId, "Chapter one");
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("c.png")).exchange();

        List<String> urls = listAt(mvc.get().uri("/characters/{id}/diaries", characterId).exchange(), "$.list[*].coverUrl");

        assertThat(urls).extracting(PresignedUrlIT::objectKeyOf).containsExactly(diaryCoverKey(diaryId));
    }

    @Test
    void getAppUserBySubject_withAvatar_returnsUrlOfStoredObjectKey() {
        createAppUser();
        mvc.put().uri("/me/avatar").multipart().file(pngFile("me.png")).exchange();

        String url = stringAt(mvc.get().uri("/me").exchange(), "$.avatarUrl");

        assertThat(objectKeyOf(url)).isEqualTo(userAvatarKey());
    }

    @Test
    void getAppUserBySubject_withAvatar_returnsUrlExpiringAfterConfiguredSeconds() {
        createAppUser();
        mvc.put().uri("/me/avatar").multipart().file(pngFile("me.png")).exchange();

        String url = stringAt(mvc.get().uri("/me").exchange(), "$.avatarUrl");

        assertThat(expiresOf(url)).isEqualTo(String.valueOf(storageConfig.getUrlExpiry().toSeconds()));
    }

    @Test
    void getAppUserBySubject_afterAvatarDeleted_returnsNullAvatarUrl() {
        createAppUser();
        mvc.put().uri("/me/avatar").multipart().file(pngFile("me.png")).exchange();
        mvc.delete().uri("/me/avatar").exchange();

        assertThat(mvc.get().uri("/me")).bodyJson().extractingPath("$.avatarUrl").isNull();
    }

    @Test
    void getGameCharacterById_afterAvatarDeleted_returnsNullAvatarUrl() {
        createAppUser();
        UUID characterId = characterWithAvatar("Aragorn", CURRENT_USER);
        mvc.delete().uri("/characters/{id}/avatar", characterId).exchange();

        assertThat(mvc.get().uri("/characters/{id}", characterId)).bodyJson().extractingPath("$.avatarUrl").isNull();
    }

    @Test
    void getGameCharacterById_afterAvatarDeleted_returnsOk() {
        createAppUser();
        UUID characterId = characterWithAvatar("Aragorn", CURRENT_USER);
        mvc.delete().uri("/characters/{id}/avatar", characterId).exchange();

        assertThat(mvc.get().uri("/characters/{id}", characterId)).hasStatus(HttpStatus.OK);
    }
}
