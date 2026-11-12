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

    private void insertTag(UUID diaryId, UUID pageId, String name) {
        jdbc.update("insert into diary_page_tag (id, diary_id, page_id, created_at, updated_at, version, name, created_by, updated_by) "
                + "values (?, ?, ?, now(), now(), 0, ?, 'test', 'test')", UUID.randomUUID(), diaryId, pageId, name);
    }

    private void insertTwoTagsOnSamePage(String first, String second) {
        transactions.executeWithoutResult(status -> {
            UUID diaryId = insertDiary();
            UUID pageId = insertPage(diaryId, 1);
            insertTag(diaryId, pageId, first);
            insertTag(diaryId, pageId, second);
            status.setRollbackOnly();
        });
    }

    private void insertSameTagOnTwoPages(String first, String second) {
        transactions.executeWithoutResult(status -> {
            UUID diaryId = insertDiary();
            insertTag(diaryId, insertPage(diaryId, 1), first);
            insertTag(diaryId, insertPage(diaryId, 2), second);
            status.setRollbackOnly();
        });
    }

    private void insertTagWithDiaryOfAnotherDiary() {
        transactions.executeWithoutResult(status -> {
            UUID diaryId = insertDiary();
            UUID otherDiaryId = insertDiary();
            insertTag(otherDiaryId, insertPage(diaryId, 1), "combat");
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
    void diaryPageTagDiaryForeignKey_schema_restrictsDelete() {
        assertThat(deleteRuleOf("fk_diary_page_tag_diary_id")).isEqualTo("r");
    }

    @Test
    void diaryPageTagPageForeignKey_schema_restrictsDelete() {
        assertThat(deleteRuleOf("fk_diary_page_tag_page_id_diary_id")).isEqualTo("r");
    }

    @Test
    void diaryPageTagNameIndex_schema_isTrigramGin() {
        String definition = jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'ix_diary_page_tag_name_trgm'", String.class);

        assertThat(definition).contains("gin_trgm_ops");
    }

    @Test
    void diaryPageTag_twoTagsOnSamePage_isRejectedByUniqueIndex() {
        assertThatThrownBy(() -> insertTwoTagsOnSamePage("combat", "clues")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void diaryPageTag_sameNameOnTwoPages_isAccepted() {
        assertThatCode(() -> insertSameTagOnTwoPages("combat", "combat")).doesNotThrowAnyException();
    }

    @Test
    void diaryPageTag_diaryDifferentFromTheDiaryOfThePage_isRejectedByCompositeForeignKey() {
        assertThatThrownBy(this::insertTagWithDiaryOfAnotherDiary).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void diaryPageTagName_longerThanThirtyCharacters_isRejected() {
        assertThatThrownBy(() -> insertSameTagOnTwoPages("a".repeat(31), "b")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertFeedback(String type, String status, String email, boolean privacyAccepted, String message) {
        transactions.executeWithoutResult(transaction -> {
            jdbc.update("insert into feedback (id, type, status, email, privacy_accepted, message, created_at, updated_at, version, created_by, updated_by) "
                    + "values (?, ?, ?, ?, ?, ?, now(), now(), 0, 'N/A', 'N/A')", UUID.randomUUID(), type, status, email, privacyAccepted, message);
            transaction.setRollbackOnly();
        });
    }

    private void insertValidFeedbackWithMessage(String message) {
        insertFeedback("IDEA", "NEW", "alira@example.com", true, message);
    }

    private void insertTwoFeedbackLinksWithSameDocument() {
        transactions.executeWithoutResult(transaction -> {
            UUID feedbackId = UUID.randomUUID();
            UUID documentId = UUID.randomUUID();
            jdbc.update("insert into feedback (id, type, status, email, privacy_accepted, message, created_at, updated_at, version, created_by, updated_by) "
                    + "values (?, 'BUG', 'NEW', 'alira@example.com', true, 'Broken', now(), now(), 0, 'N/A', 'N/A')", feedbackId);
            jdbc.update("insert into document (id, size, created_at, updated_at, version, name, mime_type, bucket, object_key, created_by, updated_by) "
                    + "values (?, 1, now(), now(), 0, 'a.png', 'image/png', 'feedback-documents', 'key', 'N/A', 'N/A')", documentId);
            jdbc.update("insert into feedback_document (id, feedback_id, document_id, created_at, updated_at, version, created_by, updated_by) "
                    + "values (?, ?, ?, now(), now(), 0, 'N/A', 'N/A')", UUID.randomUUID(), feedbackId, documentId);
            jdbc.update("insert into feedback_document (id, feedback_id, document_id, created_at, updated_at, version, created_by, updated_by) "
                    + "values (?, ?, ?, now(), now(), 0, 'N/A', 'N/A')", UUID.randomUUID(), feedbackId, documentId);
            transaction.setRollbackOnly();
        });
    }

    @Test
    void feedback_validRow_isAccepted() {
        assertThatCode(() -> insertValidFeedbackWithMessage("Great tags")).doesNotThrowAnyException();
    }

    @Test
    void feedbackType_unknownValue_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertFeedback("PRAISE", "NEW", "alira@example.com", true, "Hi")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void feedbackStatus_unknownValue_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertFeedback("IDEA", "DONE", "alira@example.com", true, "Hi")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void feedbackPrivacyAccepted_false_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertFeedback("IDEA", "NEW", "alira@example.com", false, "Hi")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void feedbackEmail_missing_isRejected() {
        assertThatThrownBy(() -> insertFeedback("IDEA", "NEW", null, true, "Hi")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void feedbackMessage_empty_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertValidFeedbackWithMessage("")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void feedbackMessage_2000Chars_isAccepted() {
        assertThatCode(() -> insertValidFeedbackWithMessage("a".repeat(2000))).doesNotThrowAnyException();
    }

    @Test
    void feedbackMessage_2001Chars_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertValidFeedbackWithMessage("a".repeat(2001))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void feedbackMessage_2000EmojisCountedAsCharacters_isAccepted() {
        assertThatCode(() -> insertValidFeedbackWithMessage("🎲".repeat(2000))).doesNotThrowAnyException();
    }

    @Test
    void feedbackDocumentFeedbackForeignKey_schema_restrictsDelete() {
        assertThat(deleteRuleOf("fk_feedback_document_feedback_id")).isEqualTo("r");
    }

    @Test
    void feedbackDocumentDocumentForeignKey_schema_restrictsDelete() {
        assertThat(deleteRuleOf("fk_feedback_document_document_id")).isEqualTo("r");
    }

    @Test
    void feedbackDocument_sameDocumentLinkedTwice_isRejectedByUniqueConstraint() {
        assertThatThrownBy(this::insertTwoFeedbackLinksWithSameDocument).isInstanceOf(DataIntegrityViolationException.class);
    }
}
