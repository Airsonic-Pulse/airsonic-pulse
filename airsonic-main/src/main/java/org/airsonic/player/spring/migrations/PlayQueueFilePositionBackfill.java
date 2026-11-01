package org.airsonic.player.spring.migrations;

import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.UpdateStatement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Backfill for #214: assigns {@code play_queue_file.position} 0..n-1 per queue in ascending id
 * order — the order rows were returned in before the explicit position column existed — so
 * existing saved queues keep their order through the upgrade. Only rows whose position is NULL
 * are touched, which makes a re-run a no-op. Plain JDBC reads plus {@link UpdateStatement}s keep
 * the implementation identical on HSQLDB, PostgreSQL, MySQL and MariaDB, following the pattern
 * of the other migrations in this package.
 */
public class PlayQueueFilePositionBackfill implements CustomSqlChange {

    private int backfilledRows;

    @Override
    public String getConfirmationMessage() {
        return "Column play_queue_file.position backfilled per queue from id order (" + backfilledRows + " rows)";
    }

    @Override
    public void setUp() throws SetupException {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }

    @Override
    public SqlStatement[] generateStatements(Database database) throws CustomChangeException {
        List<SqlStatement> updates = new ArrayList<>();
        JdbcConnection conn = null;
        if (database.getConnection() instanceof JdbcConnection) {
            conn = (JdbcConnection) database.getConnection();
        }

        if (conn != null) {
            try (Statement st = conn.createStatement();
                    ResultSet result = st.executeQuery(
                            "SELECT id, play_queue_id FROM play_queue_file WHERE position IS NULL ORDER BY play_queue_id, id")) {

                int lastQueueId = Integer.MIN_VALUE;
                int position = 0;
                while (result.next()) {
                    int queueId = result.getInt("play_queue_id");
                    if (queueId != lastQueueId) {
                        lastQueueId = queueId;
                        position = 0;
                    }
                    updates.add(new UpdateStatement(database.getDefaultCatalogName(), database.getDefaultSchemaName(),
                            "play_queue_file")
                                    .addNewColumnValue("position", position++)
                                    .setWhereClause("id=?")
                                    .addWhereParameter(result.getInt("id")));
                }
            } catch (DatabaseException | SQLException e) {
                throw new CustomChangeException(e);
            }
        }

        this.backfilledRows = updates.size();
        return updates.toArray(SqlStatement[]::new);
    }
}
