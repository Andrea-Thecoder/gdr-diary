package it.gdr.diary.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.config.AppUserConfig;
import it.gdr.diary.config.CharacterSheetConfig;
import it.gdr.diary.config.DiaryPageConfig;
import it.gdr.diary.config.StorageConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConfigValidationTest {

    private static final String[] VALID_STORAGE = {
            "app.storage.endpoint=http://localhost:8333",
            "app.storage.access-key=admin",
            "app.storage.secret-key=secret",
            "app.storage.bucket=diary-documents"};

    @Configuration
    @EnableConfigurationProperties(StorageConfig.class)
    static class StorageProperties {
    }

    @Configuration
    @EnableConfigurationProperties(DiaryPageConfig.class)
    static class DiaryProperties {
    }

    @Configuration
    @EnableConfigurationProperties(AppUserConfig.class)
    static class UserProperties {
    }

    @Configuration
    @EnableConfigurationProperties(CharacterSheetConfig.class)
    static class CharacterSheetProperties {
    }

    private ApplicationContextRunner userRunner(String... values) {
        return new ApplicationContextRunner().withUserConfiguration(UserProperties.class).withPropertyValues(values);
    }

    private ApplicationContextRunner storageRunner(String... extra) {
        return new ApplicationContextRunner().withUserConfiguration(StorageProperties.class)
                .withPropertyValues(VALID_STORAGE).withPropertyValues(extra);
    }

    private ApplicationContextRunner diaryRunner(String... values) {
        return new ApplicationContextRunner().withUserConfiguration(DiaryProperties.class).withPropertyValues(values);
    }

    private ApplicationContextRunner characterSheetRunner(String... values) {
        return new ApplicationContextRunner().withUserConfiguration(CharacterSheetProperties.class).withPropertyValues(values);
    }

    @Test
    void startup_validStorageConfig_succeeds() {
        storageRunner("app.storage.url-expiry=5m", "app.storage.max-file-size=5MB")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startup_validDiaryConfig_succeeds() {
        diaryRunner("app.diary.max-page-content-depth=50").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startup_negativeMaxFileSize_fails() {
        storageRunner("app.storage.max-file-size=-1MB").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_zeroUrlExpiry_fails() {
        storageRunner("app.storage.url-expiry=0s").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_urlExpiryOverSevenDays_fails() {
        storageRunner("app.storage.url-expiry=8d").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_zeroPageContentDepth_fails() {
        diaryRunner("app.diary.max-page-content-depth=0").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_missingBucket_fails() {
        new ApplicationContextRunner().withUserConfiguration(StorageProperties.class)
                .withPropertyValues("app.storage.endpoint=http://localhost:8333", "app.storage.access-key=admin", "app.storage.secret-key=secret")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_validUserConfig_succeeds() {
        userRunner("app.user.max-settings-size=10KB", "app.user.delete-max-auth-age=5m").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startup_negativeDeleteMaxAuthAge_fails() {
        userRunner("app.user.delete-max-auth-age=-1m").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_zeroMaxSettingsSize_fails() {
        userRunner("app.user.max-settings-size=0B").run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"endpoint", "access-key", "secret-key"})
    void startup_blankStorageCredential_fails(String key) {
        storageRunner("app.storage." + key + "=").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_zeroMaxFileSize_fails() {
        storageRunner("app.storage.max-file-size=0B").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_urlExpiryExactlySevenDays_succeeds() {
        storageRunner("app.storage.url-expiry=7d").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startup_urlExpirySevenDaysAndOneSecond_fails() {
        storageRunner("app.storage.url-expiry=604801s").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_zeroMaxPageContentSize_fails() {
        diaryRunner("app.diary.max-page-content-size=0B").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_zeroDeleteMaxAuthAge_fails() {
        userRunner("app.user.delete-max-auth-age=0s").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_zeroMaxPdfFileSize_fails() {
        storageRunner("app.storage.max-pdf-file-size=0B").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_negativeMaxPdfFileSize_fails() {
        storageRunner("app.storage.max-pdf-file-size=-1MB").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_validMaxPdfFileSize_succeeds() {
        storageRunner("app.storage.max-pdf-file-size=10MB").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startup_validCharacterSheetConfig_succeeds() {
        characterSheetRunner("app.character-sheet.max-sheets-per-character=10").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startup_noCharacterSheetConfig_usesDefaultOfTen() {
        characterSheetRunner().run(context -> assertThat(context.getBean(CharacterSheetConfig.class).getMaxSheetsPerCharacter()).isEqualTo(10));
    }

    @Test
    void startup_configuredMaxSheetsPerCharacter_overridesDefault() {
        characterSheetRunner("app.character-sheet.max-sheets-per-character=3")
                .run(context -> assertThat(context.getBean(CharacterSheetConfig.class).getMaxSheetsPerCharacter()).isEqualTo(3));
    }

    @Test
    void startup_zeroMaxSheetsPerCharacter_fails() {
        characterSheetRunner("app.character-sheet.max-sheets-per-character=0").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startup_negativeMaxSheetsPerCharacter_fails() {
        characterSheetRunner("app.character-sheet.max-sheets-per-character=-1").run(context -> assertThat(context).hasFailed());
    }
}
