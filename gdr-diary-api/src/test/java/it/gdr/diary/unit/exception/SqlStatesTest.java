package it.gdr.diary.unit.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.ebean.DataIntegrityException;
import it.gdr.diary.exception.SqlStates;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SqlStatesTest {

    private static BatchUpdateException batchFailureWithNext(String nextState) {
        BatchUpdateException batch = new BatchUpdateException("batch failed", null, 0, new int[0], null);
        batch.setNextException(new SQLException("next", nextState));
        return batch;
    }

    @Test
    void hasSqlState_directSqlCause_returnsTrue() {
        PersistenceException failure = new PersistenceException("failed", new SQLException("duplicate", "23505"));

        boolean result = SqlStates.hasSqlState(failure, Set.of(SqlStates.UNIQUE_VIOLATION));

        assertThat(result).isTrue();
    }

    @Test
    void hasSqlState_sqlCauseDeepInChain_returnsTrue() {
        RollbackException failure = new RollbackException("commit failed",
                new IllegalStateException("wrapped", new SQLException("deadlock detected", "40P01")));

        boolean result = SqlStates.hasSqlState(failure, Set.of(SqlStates.DEADLOCK));

        assertThat(result).isTrue();
    }

    @Test
    void hasSqlState_stateInNextExceptionOfBatch_returnsTrue() {
        PersistenceException failure = new PersistenceException("failed", batchFailureWithNext("23503"));

        boolean result = SqlStates.hasSqlState(failure, Set.of(SqlStates.FOREIGN_KEY_VIOLATION));

        assertThat(result).isTrue();
    }

    @Test
    void hasSqlState_otherState_returnsFalse() {
        PersistenceException failure = new PersistenceException("failed", new SQLException("connection lost", "08006"));

        boolean result = SqlStates.hasSqlState(failure, Set.of(SqlStates.UNIQUE_VIOLATION, SqlStates.FOREIGN_KEY_VIOLATION));

        assertThat(result).isFalse();
    }

    @Test
    void hasSqlState_noSqlCause_returnsFalse() {
        PersistenceException failure = new PersistenceException("failed", new IllegalStateException("boom"));

        boolean result = SqlStates.hasSqlState(failure, Set.of(SqlStates.UNIQUE_VIOLATION));

        assertThat(result).isFalse();
    }

    @Test
    void isForeignKeyViolation_dataIntegrityWithForeignKeyState_returnsTrue() {
        DataIntegrityException failure = new DataIntegrityException("still referenced",
                new SQLException("violates foreign key constraint", "23503"));

        boolean result = SqlStates.isForeignKeyViolation(failure);

        assertThat(result).isTrue();
    }

    @Test
    void isForeignKeyViolation_uniqueViolation_returnsFalse() {
        DataIntegrityException failure = new DataIntegrityException("duplicate",
                new SQLException("duplicate key", "23505"));

        boolean result = SqlStates.isForeignKeyViolation(failure);

        assertThat(result).isFalse();
    }

    @Test
    void hasSqlState_sqlCauseWithoutState_returnsFalse() {
        PersistenceException failure = new PersistenceException("failed", new SQLException("no state"));

        boolean result = SqlStates.hasSqlState(failure, Set.of(SqlStates.FOREIGN_KEY_VIOLATION));

        assertThat(result).isFalse();
    }
}
