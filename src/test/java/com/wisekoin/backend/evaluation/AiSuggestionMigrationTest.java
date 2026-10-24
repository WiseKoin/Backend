package com.wisekoin.backend.evaluation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class AiSuggestionMigrationTest {
    @Test
    void suggestionMigrationAppliesToExistingAdaptiveSchema() throws Exception {
        String url =
                "jdbc:h2:mem:wisekoin-suggestion-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE wallets (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE transactions (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE ai_extractions (id UUID PRIMARY KEY, user_id BIGINT NOT NULL, "
                            + "status VARCHAR(20) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                            + "FOREIGN KEY (user_id) REFERENCES users(id))");

            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V11__add_ai_suggestion_lifecycle.sql"));

            assertTrue(tableExists(statement, "ai_suggestion_actions"));
            assertTrue(columnExists(statement, "ai_extractions", "ambiguity_reasons"));
            assertTrue(columnExists(statement, "ai_extractions", "transaction_id"));
            assertTrue(columnExists(statement, "ai_extractions", "updated_at"));
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
