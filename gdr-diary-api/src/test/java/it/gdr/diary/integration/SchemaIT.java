package it.gdr.diary.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
}
