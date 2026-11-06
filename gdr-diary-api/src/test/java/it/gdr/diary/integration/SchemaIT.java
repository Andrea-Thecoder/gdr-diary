package it.gdr.diary.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class SchemaIT extends AbstractIntegrationTest {

    @Autowired
    private TransactionTemplate transactions;

    private String constraintFlag(String constraint, String column) {
        return jdbc.queryForObject("select " + column + "::text from pg_constraint where conname = ?", String.class, constraint);
    }

    private String deleteRuleOf(String constraint) {
        return jdbc.queryForObject("select confdeltype::text from pg_constraint where conname = ?", String.class, constraint);
    }

    private void insertCharacterWithColor(String color) {
        transactions.executeWithoutResult(status -> {
            UUID userId = UUID.randomUUID();
            jdbc.update("insert into app_user (id, is_premium, created_at, updated_at, version, keycloak_id, created_by, updated_by) "
                    + "values (?, false, now(), now(), 0, 'kc-schema-test', 'test', 'test')", userId);
            jdbc.update("insert into game_character (id, owner_id, created_at, updated_at, version, name, kind, background_color, created_by, updated_by) "
                    + "values (?, ?, now(), now(), 0, 'Aragorn', 'PLAYER', ?, 'test', 'test')", UUID.randomUUID(), userId, color);
            status.setRollbackOnly();
        });
    }

    private void insertDiaryWithColor(String color) {
        transactions.executeWithoutResult(status -> {
            UUID userId = UUID.randomUUID();
            UUID characterId = UUID.randomUUID();
            jdbc.update("insert into app_user (id, is_premium, created_at, updated_at, version, keycloak_id, created_by, updated_by) "
                    + "values (?, false, now(), now(), 0, 'kc-schema-test', 'test', 'test')", userId);
            jdbc.update("insert into game_character (id, owner_id, created_at, updated_at, version, name, kind, created_by, updated_by) "
                    + "values (?, ?, now(), now(), 0, 'Aragorn', 'PLAYER', 'test', 'test')", characterId, userId);
            jdbc.update("insert into diary (id, character_id, created_at, updated_at, version, title, cover_color, created_by, updated_by) "
                    + "values (?, ?, now(), now(), 0, 'Chapter', ?, 'test', 'test')", UUID.randomUUID(), characterId, color);
            status.setRollbackOnly();
        });
    }

    private void insertTwoSheetsWithSameDocument() {
        transactions.executeWithoutResult(status -> {
            UUID userId = UUID.randomUUID();
            UUID characterId = UUID.randomUUID();
            UUID documentId = UUID.randomUUID();
            jdbc.update("insert into app_user (id, is_premium, created_at, updated_at, version, keycloak_id, created_by, updated_by) "
                    + "values (?, false, now(), now(), 0, 'kc-schema-test', 'test', 'test')", userId);
            jdbc.update("insert into game_character (id, owner_id, created_at, updated_at, version, name, kind, created_by, updated_by) "
                    + "values (?, ?, now(), now(), 0, 'Aragorn', 'PLAYER', 'test', 'test')", characterId, userId);
            jdbc.update("insert into document (id, size, created_at, updated_at, version, name, mime_type, bucket, object_key, created_by, updated_by) "
                    + "values (?, 10, now(), now(), 0, 'sheet.pdf', 'application/pdf', 'bucket', 'key', 'test', 'test')", documentId);
            jdbc.update("insert into character_sheet (id, character_id, document_id, created_at, updated_at, version, title, created_by, updated_by) "
                    + "values (?, ?, ?, now(), now(), 0, 'First', 'test', 'test')", UUID.randomUUID(), characterId, documentId);
            jdbc.update("insert into character_sheet (id, character_id, document_id, created_at, updated_at, version, title, created_by, updated_by) "
                    + "values (?, ?, ?, now(), now(), 0, 'Second', 'test', 'test')", UUID.randomUUID(), characterId, documentId);
            status.setRollbackOnly();
        });
    }

    private UUID insertDiary() {
        UUID userId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        UUID diaryId = UUID.randomUUID();
        jdbc.update("insert into app_user (id, is_premium, created_at, updated_at, version, keycloak_id, created_by, updated_by) "
                + "values (?, false, now(), now(), 0, 'kc-schema-test', 'test', 'test')", userId);
        jdbc.update("insert into game_character (id, owner_id, created_at, updated_at, version, name, kind, created_by, updated_by) "
                + "values (?, ?, now(), now(), 0, 'Aragorn', 'PLAYER', 'test', 'test')", characterId, userId);
        jdbc.update("insert into diary (id, character_id, created_at, updated_at, version, title, created_by, updated_by) "
                + "values (?, ?, now(), now(), 0, 'Chapter', 'test', 'test')", diaryId, characterId);
        return diaryId;
    }

    private UUID insertPage(UUID diaryId, int pageNumber) {
        UUID pageId = UUID.randomUUID();
        jdbc.update("insert into diary_page (id, page_number, diary_id, created_at, updated_at, version, content, created_by, updated_by) "
                + "values (?, ?, ?, now(), now(), 0, '{}'::jsonb, 'test', 'test')", pageId, pageNumber, diaryId);
        return pageId;
    }

    private void insertTag(UUID pageId, String name) {
        jdbc.update("insert into diary_page_tag (id, page_id, created_at, updated_at, version, name, created_by, updated_by) "
                + "values (?, ?, now(), now(), 0, ?, 'test', 'test')", UUID.randomUUID(), pageId, name);
    }

    private void insertTwoTagsOnSamePage(String first, String second) {
        transactions.executeWithoutResult(status -> {
            UUID pageId = insertPage(insertDiary(), 1);
            insertTag(pageId, first);
            insertTag(pageId, second);
            status.setRollbackOnly();
        });
    }

    private void insertSameTagOnTwoPages(String name) {
        transactions.executeWithoutResult(status -> {
            UUID diaryId = insertDiary();
            insertTag(insertPage(diaryId, 1), name);
            insertTag(insertPage(diaryId, 2), name);
            status.setRollbackOnly();
        });
    }

    @Test
    void pageNumberUniqueConstraint_schema_isDeferrable() {
        assertThat(constraintFlag("uq_diary_page_diary_id_page_number", "condeferrable")).isEqualTo("true");
    }

    @Test
    void pageNumberUniqueConstraint_schema_isInitiallyDeferred() {
        assertThat(constraintFlag("uq_diary_page_diary_id_page_number", "condeferred")).isEqualTo("true");
    }

    @Test
    void appUserAvatarForeignKey_schema_setsNullOnDelete() {
        assertThat(deleteRuleOf("fk_app_user_avatar_document_id")).isEqualTo("n");
    }

    @Test
    void gameCharacterAvatarForeignKey_schema_setsNullOnDelete() {
        assertThat(deleteRuleOf("fk_game_character_avatar_document_id")).isEqualTo("n");
    }

    @Test
    void diaryCoverForeignKey_schema_setsNullOnDelete() {
        assertThat(deleteRuleOf("fk_diary_cover_document_id")).isEqualTo("n");
    }

    @Test
    void gameCharacterOwnerForeignKey_schema_restrictsDelete() {
        assertThat(deleteRuleOf("fk_game_character_owner_id")).isEqualTo("r");
    }

    @Test
    void gameCharacterBackgroundColor_invalidHex_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertCharacterWithColor("#GGGGGG")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void diaryCoverColor_invalidHex_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertDiaryWithColor("#GGGGGG")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void diaryBookmarkForeignKey_schema_setsNullOnDelete() {
        assertThat(deleteRuleOf("fk_diary_bookmark_page_id")).isEqualTo("n");
    }

    @Test
    void characterSheetCharacterForeignKey_schema_restrictsDelete() {
        assertThat(deleteRuleOf("fk_character_sheet_character_id")).isEqualTo("r");
    }

    @Test
    void characterSheetDocumentForeignKey_schema_restrictsDelete() {
        assertThat(deleteRuleOf("fk_character_sheet_document_id")).isEqualTo("r");
    }

    @Test
    void characterSheetDocument_sharedByTwoSheets_isRejectedByUniqueConstraint() {
        assertThatThrownBy(this::insertTwoSheetsWithSameDocument).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void diaryPageTagPageForeignKey_schema_restrictsDelete() {
        assertThat(deleteRuleOf("fk_diary_page_tag_page_id")).isEqualTo("r");
    }

    @Test
    void diaryPageTagNameIndex_schema_isTrigramGin() {
        String definition = jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'ix_diary_page_tag_name_trgm'", String.class);

        assertThat(definition).contains("gin_trgm_ops");
    }

    @Test
    void diaryPageTagName_sameNameWithOtherCaseOnSamePage_isRejectedByUniqueIndex() {
        assertThatThrownBy(() -> insertTwoTagsOnSamePage("Combat", "combat")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void diaryPageTagName_sameNameOnTwoPages_isAccepted() {
        assertThatCode(() -> insertSameTagOnTwoPages("combat")).doesNotThrowAnyException();
    }

    @Test
    void diaryPageTagName_longerThanThirtyCharacters_isRejected() {
        assertThatThrownBy(() -> insertTwoTagsOnSamePage("a".repeat(31), "b")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
