package com.wisekoin.backend.evaluation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class NotificationPilotMigrationTest {
    @Test
    void notificationPilotMigrationAppliesToExistingSchemas() throws Exception {
        String url =
                "jdbc:h2:mem:wisekoin-notification-pilot;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE transactions (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, "
                            + "FOREIGN KEY (user_id) REFERENCES users(id))");
            statement.execute(
                    "CREATE TABLE ai_extractions (id UUID PRIMARY KEY, user_id BIGINT NOT NULL, "
                            + "source VARCHAR(20) NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id))");

            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource(
                            "db/migration/V12__add_notification_pilot_and_transaction_idempotency.sql"));

            assertTrue(tableExists(statement, "notification_pilot_metrics"));
            assertTrue(columnExists(statement, "transactions", "idempotency_key_hash"));
            assertTrue(columnExists(statement, "transactions", "idempotency_request_hash"));
            assertTrue(columnExists(statement, "ai_extractions", "source_reference_hash"));
            assertTrue(columnExists(statement, "ai_extractions", "parser_version"));
        }
    }

    private boolean tableExists(Statement statement, String table) throws Exception {
        try (ResultSet result =
                statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '"
                                + table
                                + "'")) {
            result.next();
            return result.getInt(1) == 1;
        }
    }

    private boolean columnExists(Statement statement, String table, String column)
            throws Exception {
        try (ResultSet result =
                statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = '"
                                + table
                                + "' AND column_name = '"
                                + column
                                + "'")) {
            result.next();
            return result.getInt(1) == 1;
        }
    }
}
