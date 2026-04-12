/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service;

import liquibase.CatalogAndSchema;
import liquibase.Scope;
import liquibase.Scope.ScopedRunner;
import liquibase.changelog.ChangeLogParameters;
import liquibase.command.CommandScope;
import liquibase.command.core.DiffChangelogCommandStep;
import liquibase.command.core.DiffCommandStep;
import liquibase.command.core.ExecuteSqlCommandStep;
import liquibase.command.core.GenerateChangelogCommandStep;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DatabaseChangelogCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.command.core.helpers.DiffOutputControlCommandStep;
import liquibase.command.core.helpers.PreCompareCommandStep;
import liquibase.command.core.helpers.ReferenceDbUrlConnectionCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.StandardObjectChangeFilter;
import liquibase.exception.CommandExecutionException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.dao.DatabaseDao;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.LambdaUtils;
import org.airsonic.player.util.LambdaUtils.ThrowingBiFunction;
import org.airsonic.player.util.LegacyHsqlMigrationUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.joining;

@Service
public class DatabaseService {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseService.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    SettingsService settingsService;
    @Autowired
    DatabaseDao databaseDao;
    @Autowired
    private SimpMessagingTemplate brokerTemplate;
    @Autowired
    private TaskSchedulingService taskService;
    @Autowired
    private AirsonicHomeConfig homeConfig;

    private final ReentrantLock dbOperationLock = new ReentrantLock();

    @PostConstruct
    public void init() {
        try {
            schedule();
        } catch (Throwable x) {
            LOG.error("Failed to initialize DatabaseService", x);
        }
    }

    private synchronized void schedule() {
        int hoursBetween = settingsService.getDbBackupInterval();

        if (hoursBetween == -1) {
            LOG.info("Automatic DB backup disabled");
            unschedule();
            return;
        }

        long initialDelayMillis = 5L * 60L * 1000L;
        Instant firstTime = Instant.now().plusMillis(initialDelayMillis);

        taskService.scheduleAtFixedRate("db-backup", backupTask, firstTime, Duration.ofHours(hoursBetween), true);

        LOG.info("Automatic DB backup scheduled to run every {} hour(s), starting at {}", hoursBetween, firstTime);
    }

    public void unschedule() {
        taskService.unscheduleTask("db-backup");
    }

    private Runnable backupTask = () -> {
        LOG.info("Starting scheduled DB backup");
        backup();
        LOG.info("Completed scheduled DB backup");
    };

    public void backup() {
        this.dbOperationLock.lock();
        try {
            brokerTemplate.convertAndSend("/topic/backupStatus", "started");

            if (backuppable()) {
                try {
                    String dbPath = StringUtils.substringBetween(settingsService.getDatabaseUrl(), "jdbc:hsqldb:file:",
                            ";");
                    Path backupLocation = LegacyHsqlMigrationUtil.performHsqlDbBackup(dbPath);
                    LOG.info("Backed up DB to location: {}", backupLocation);
                    brokerTemplate.convertAndSend("/topic/backupStatus", "location: " + backupLocation);
                    deleteObsoleteBackups(backupLocation);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to backup HSQLDB database", e);
                }
            } else {
                LOG.info("DB unable to be backed up via these means");
            }
            brokerTemplate.convertAndSend("/topic/backupStatus", "ended");
        } finally {
            this.dbOperationLock.unlock();
        }
    }

    private void deleteObsoleteBackups(Path backupLocation) {
        this.dbOperationLock.lock();
        try {
            AtomicInteger backupCount = new AtomicInteger(settingsService.getDbBackupRetentionCount());
            if (backupCount.get() == -1) {
                return;
            }

            String backupNamePattern = StringUtils.substringBeforeLast(backupLocation.getFileName().toString(), ".");
            try (Stream<Path> backups = Files.list(backupLocation.getParent());) {
                backups.filter(p -> p.getFileName().toString().startsWith(backupNamePattern))
                        .sorted(Comparator.comparing(
                                LambdaUtils.<Path, FileTime, Exception>uncheckFunction(
                                        p -> Files.readAttributes(p, BasicFileAttributes.class).creationTime()),
                                Comparator.reverseOrder()))
                        .forEach(p -> {
                            if (backupCount.getAndDecrement() <= 0) {
                                FileUtil.delete(p);
                            }
                        });
            } catch (Exception e) {
                LOG.warn("Could not clean up DB backups", e);
            }
        } finally {
            this.dbOperationLock.unlock();
        }
    }

    public boolean backuppable() {
        return settingsService.getDatabaseJNDIName() == null
                && Strings.CS.startsWith(settingsService.getDatabaseUrl(), "jdbc:hsqldb:file:");
    }

    ThrowingBiFunction<Path, Connection, Boolean, Exception> exportFunction = (tmpPath,
            connection) -> generateChangeLog(tmpPath, connection, "data", "airsonic-data", makeDiffOutputControl());

    Function<Path, Consumer<Connection>> importFunction = p -> LambdaUtils.uncheckConsumer(
            connection -> runLiquibaseUpdate(connection, p));

    public Path exportDB() throws Exception {
        this.dbOperationLock.lock();
        try {
            brokerTemplate.convertAndSend("/topic/exportStatus", "started");
            Path fPath = getChangeLogFolder();
            Path zPath = null;
            try {
                databaseDao.exportDB(fPath, exportFunction);
                zPath = zip(fPath);
                brokerTemplate.convertAndSend("/topic/exportStatus", "Local DB extraction complete, compressing...");
            } catch (Exception e) {
                LOG.info("DB Export failed!", e);
                brokerTemplate.convertAndSend("/topic/exportStatus", "Error with local DB extraction, check logs...");
                cleanup(fPath);
            }

            brokerTemplate.convertAndSend("/topic/exportStatus", "ended");
            return zPath;
        } finally {
            this.dbOperationLock.unlock();
        }
    }

    public void cleanup(Path p) {
        if (Files.isDirectory(p)) {
            FileUtil.delete(p);
        } else {
            FileUtil.delete(p.getParent());
        }
    }

    private Path zip(Path folder) throws Exception {
        Path zipName = folder.resolve(folder.getFileName().toString() + ".zip");
        try (OutputStream fos = Files.newOutputStream(zipName);
                ZipOutputStream zipOut = new ZipOutputStream(fos);
                Stream<Path> files = Files.list(folder);) {
            files.filter(f -> !f.equals(zipName)).forEach(LambdaUtils.uncheckConsumer(f -> {
                ZipEntry zipEntry = new ZipEntry(f.getFileName().toString());
                zipOut.putNextEntry(zipEntry);
                Files.copy(f, zipOut);
                zipOut.closeEntry();
            }));
        }

        return zipName;
    }

    public void importDB(Path p) {
        this.dbOperationLock.lock();
        try {
            brokerTemplate.convertAndSend("/topic/importStatus", "started");
            if (Files.notExists(p) || !Files.isDirectory(p) || p.toFile().list().length == 0) {
                brokerTemplate.convertAndSend("/topic/importStatus", "Nothing imported");
            } else {
                backup();
                brokerTemplate.convertAndSend("/topic/importStatus", "Importing XML");
                databaseDao.importDB(importFunction.apply(p));
                brokerTemplate.convertAndSend("/topic/importStatus", "Import complete. Cleaning up...");
                cleanup(p);
            }
            brokerTemplate.convertAndSend("/topic/importStatus", "ended");
        } finally {
            this.dbOperationLock.unlock();
        }
    }

    private void runLiquibaseUpdate(Connection connection, Path p) throws Exception {
        Database database = getDatabase(connection);
        truncateAll(database);
        Map<String, Object> scopeObjects = Map.of(
                Scope.Attr.database.name(), database,
                Scope.Attr.resourceAccessor.name(), new DirectoryResourceAccessor(p.toFile()));
        try (Stream<Path> files = Files.list(p)) {
            files.sorted().forEach(LambdaUtils.uncheckConsumer(f -> {
                Scope.child(scopeObjects, (ScopedRunner<?>) () -> new CommandScope(UpdateCommandStep.COMMAND_NAME)
                        .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                        .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, p.relativize(f).toString())
                        .addArgumentValue(DatabaseChangelogCommandStep.CHANGELOG_PARAMETERS,
                                new ChangeLogParameters(database))
                        .execute());
            }));
        }
    }

    private static void truncateAll(Database db) throws Exception {
        String sql = TABLE_ORDER.stream().flatMap(t -> t.stream())
                .map(t -> "delete from " + t).collect(joining("; "));
        CommandScope commandScope = new CommandScope(ExecuteSqlCommandStep.COMMAND_NAME);
        commandScope.addArgumentValue(ExecuteSqlCommandStep.SQL_ARG, sql);
        commandScope.addArgumentValue(ExecuteSqlCommandStep.DELIMITER_ARG, ";");
        commandScope.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, db);
        commandScope.execute();
    }

    private static List<List<String>> TABLE_ORDER = Arrays.asList(
            Arrays.asList("users", "music_folder", "transcoding", "player"),
            Arrays.asList("music_folder_user", "user_credentials", "user_settings", "player_transcoding"),
            Arrays.asList("media_file", "music_file_info"),
            Arrays.asList("playlist", "play_queue", "internet_radio"),
            Arrays.asList("playlist_file", "playlist_user", "play_queue_file"),
            Arrays.asList("album", "artist", "genre"),
            Arrays.asList("podcast_channel", "share"),
            Arrays.asList("cover_art"),
            Arrays.asList("podcast_channel_rules", "podcast_episode", "bookmark", "share_file", "sonoslink"),
            Arrays.asList("starred_album", "starred_artist", "starred_media_file", "user_rating", "custom_avatar"));

    private boolean generateChangeLog(Path fPath, Connection connection, String snapshotTypes, String author,
            DiffOutputControl diffOutputControl) throws Exception {
        Database database = getDatabase(connection);
        Files.createDirectories(fPath);
        for (int i = 0; i < TABLE_ORDER.size(); i++) {
            setTableFilter(diffOutputControl, TABLE_ORDER.get(i));
            doGenerateChangeLog(fPath.resolve(i + ".xml").toString(), database, snapshotTypes, author,
                    diffOutputControl);
        }

        return true;
    }

    private Database getDatabase(Connection connection) throws Exception {
        DatabaseConnection databaseConnection = new JdbcConnection(connection);
        return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(databaseConnection);
    }

    private Path getChangeLogFolder() {
        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        return homeConfig.getAirsonicHome().resolve("backups")
                .resolve(String.format("airsonic.exportDB.%s", timestamp));
    }

    public Path getImportDBFolder() {
        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        return homeConfig.getAirsonicHome().resolve("backups")
                .resolve(String.format("airsonic.importDB.%s", timestamp));
    }

    private DiffOutputControl makeDiffOutputControl() {
        return new DiffOutputControl(false, false, false, null);
    }

    private void setTableFilter(DiffOutputControl diffOutputControl, List<String> tables) {
        StandardObjectChangeFilter filter = new StandardObjectChangeFilter(
                StandardObjectChangeFilter.FilterType.INCLUDE,
                tables.stream().map(t -> MessageFormat.format("table:(?i){0}", t)).collect(joining(",")));
        diffOutputControl.setObjectChangeFilter(filter);
    }

    /**
     * Generate a change log for the given database.
     * this method is customized to generate a change log for a specific set of
     * tables
     * {@link liquibase.integration.commandline.CommandLineUtils#doGenerateChangeLog(String,
     * Database, String, String, String, String, String, DiffOutputControl)}
     *
     * @param changeLogFile     the file to write the change log to
     * @param originalDatabase  the original database to compare to
     * @param snapshotTypes     the types of snapshots to take
     * @param author            the author to use for the change log
     * @param diffOutputControl the output control to use
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws LiquibaseException
     */
    private void doGenerateChangeLog(String changeLogFile, Database originalDatabase,
            String snapshotTypes, String author, DiffOutputControl diffOutputControl)
            throws IOException, ParserConfigurationException,
            LiquibaseException {
        CatalogAndSchema[] schemas = new CatalogAndSchema[] { new CatalogAndSchema(null, null) };
        CompareControl.SchemaComparison[] comparisons = new CompareControl.SchemaComparison[schemas.length];
        int i = 0;
        for (CatalogAndSchema schema : schemas) {
            comparisons[i++] = new CompareControl.SchemaComparison(schema, schema);
        }
        CompareControl compareControl = new CompareControl(comparisons, snapshotTypes);
        CommandScope command = new CommandScope("generateChangeLog");
        command
                .addArgumentValue(ReferenceDbUrlConnectionCommandStep.REFERENCE_DATABASE_ARG, originalDatabase)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, originalDatabase)
                .addArgumentValue(PreCompareCommandStep.SNAPSHOT_TYPES_ARG,
                        DiffCommandStep.parseSnapshotTypes(snapshotTypes))
                .addArgumentValue(PreCompareCommandStep.COMPARE_CONTROL_ARG, compareControl)
                .addArgumentValue(PreCompareCommandStep.OBJECT_CHANGE_FILTER_ARG,
                        diffOutputControl.getObjectChangeFilter())
                .addArgumentValue(DiffChangelogCommandStep.CHANGELOG_FILE_ARG, changeLogFile)
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_CATALOG_ARG,
                        diffOutputControl.getIncludeCatalog())
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_SCHEMA_ARG, diffOutputControl.getIncludeSchema())
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_TABLESPACE_ARG,
                        diffOutputControl.getIncludeTablespace())
                .addArgumentValue(GenerateChangelogCommandStep.AUTHOR_ARG, author);

        if (diffOutputControl.isReplaceIfExistsSet()) {
            command.addArgumentValue(GenerateChangelogCommandStep.USE_OR_REPLACE_OPTION, true);
        }
        command.setOutput(System.out);
        try {
            command.execute();
        } catch (CommandExecutionException e) {
            throw new LiquibaseException(e);
        }

    }
}
