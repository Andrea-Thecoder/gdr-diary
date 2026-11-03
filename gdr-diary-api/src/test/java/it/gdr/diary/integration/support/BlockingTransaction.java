package it.gdr.diary.integration.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class BlockingTransaction implements AutoCloseable {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(15);
    private static final String LOCK_WAITERS = "select count(*) from pg_stat_activity "
            + "where datname = current_database() and wait_event_type = 'Lock'";

    private final Connection connection;
    private final JdbcTemplate jdbc;

    private BlockingTransaction(Connection connection, JdbcTemplate jdbc) {
        this.connection = connection;
        this.jdbc = jdbc;
    }

    public static BlockingTransaction begin(JdbcTemplate jdbc) {
        DataSource dataSource = jdbc.getDataSource();
        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            return new BlockingTransaction(connection, jdbc);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public BlockingTransaction execute(String sql, Object... parameters) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.execute();
            return this;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public <T> CompletableFuture<T> startBlockedCall(Supplier<T> call) {
        int waiting = lockWaiters();
        CompletableFuture<T> result = CompletableFuture.supplyAsync(call);
        awaitLockWaiters(waiting + 1, result);
        return result;
    }

    public void commit() {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        try (connection) {
            connection.rollback();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private int lockWaiters() {
        return jdbc.queryForObject(LOCK_WAITERS, Integer.class);
    }

    private void awaitLockWaiters(int expected, CompletableFuture<?> call) {
        Instant deadline = Instant.now().plus(WAIT_TIMEOUT);
        while (lockWaiters() < expected) {
            if (call.isDone()) {
                throw new IllegalStateException("The call finished without waiting for the lock: " + call.join());
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("The call did not wait for the lock within " + WAIT_TIMEOUT);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
