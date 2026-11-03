package it.gdr.diary.exception;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class SqlStates {

    public static final String UNIQUE_VIOLATION = "23505";
    public static final String CHECK_VIOLATION = "23514";
    public static final String FOREIGN_KEY_VIOLATION = "23503";
    public static final String DEADLOCK = "40P01";

    private SqlStates() {
    }

    public static boolean hasSqlState(Throwable e, Set<String> states) {
        return ExceptionUtils.getThrowableList(e).stream()
                .filter(SQLException.class::isInstance)
                .flatMap(cause -> Stream.iterate((SQLException) cause, Objects::nonNull, SQLException::getNextException))
                .map(SQLException::getSQLState)
                .filter(Objects::nonNull)
                .anyMatch(states::contains);
    }

    public static boolean isForeignKeyViolation(Throwable e) {
        return hasSqlState(e, Set.of(FOREIGN_KEY_VIOLATION));
    }
}
