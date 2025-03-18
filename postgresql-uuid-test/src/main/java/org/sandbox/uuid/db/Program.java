package org.sandbox.uuid.db;

import com.fasterxml.uuid.Generators;
import lombok.Builder;
import lombok.Getter;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

public class Program {

    private static final int ROW_COUNT = 1_000_000;
    private static final int BATCH_SIZE = 10_000;
    private static final int VALUE_SIZE = 1024;
    private static final Random RANDOM = new Random();
    private static final AtomicLong LONG_GENERATOR = new AtomicLong(0);

    private static final Collection<TestDefinition> UUIDS = List.of(

            // BigSerial
            TestDefinition.builder()
                    .name("bigserial")
                    .keyGenerator(LONG_GENERATOR::incrementAndGet)
                    .keyReader(Long::valueOf)
                    .createTableSql("""
                            CREATE TABLE IF NOT EXISTS uuid_long (
                                id BigSerial NOT NULL,
                                value bytea NULL,
                                CONSTRAINT "uuid_long_pk" PRIMARY KEY (id)
                            )
                            """)
                    .insertIntoTableSql("""
                            INSERT INTO uuid_long(id, value)
                            VALUES (?, ?)
                            """)
                    .selectFromTableSql("""
                            SELECT value
                            FROM uuid_long
                            WHERE id = ?
                            """)
                    .build(),

            // UUID v1
            TestDefinition.builder()
                    .name("uuid1")
                    .keyGenerator(() -> Generators.timeBasedGenerator().generate())
                    .keyReader(UUID::fromString)
                    .createTableSql("""
                            CREATE TABLE IF NOT EXISTS uuid_v1 (
                                id uuid NOT NULL,
                                value bytea NULL,
                                CONSTRAINT "uuid_v1_pk" PRIMARY KEY (id)
                            )
                            """)
                    .insertIntoTableSql("""
                            INSERT INTO uuid_v1(id, value)
                            VALUES (?::uuid, ?)
                            """)
                    .selectFromTableSql("""
                            SELECT value
                            FROM uuid_v1
                            WHERE id = ?
                            """)
                    .build(),

            // UUID v4
            TestDefinition.builder()
                    .name("uuid4")
                    .keyGenerator(() -> Generators.randomBasedGenerator().generate())
                    .keyReader(UUID::fromString)
                    .createTableSql("""
                            CREATE TABLE IF NOT EXISTS uuid_v4 (
                                id uuid NOT NULL,
                                value bytea NULL,
                                CONSTRAINT "uuid_v4_pk" PRIMARY KEY (id)
                            )
                            """)
                    .insertIntoTableSql("""
                            INSERT INTO uuid_v4(id, value)
                            VALUES (?::uuid, ?)
                            """)
                    .selectFromTableSql("""
                            SELECT value
                            FROM uuid_v4
                            WHERE id = ?
                            """)
                    .build(),

            // UUID v6
            TestDefinition.builder()
                    .name("uuid6")
                    .keyGenerator(() -> Generators.timeBasedReorderedGenerator().generate())
                    .keyReader(UUID::fromString)
                    .createTableSql("""
                            CREATE TABLE IF NOT EXISTS uuid_v6 (
                                id uuid NOT NULL,
                                value bytea NULL,
                                CONSTRAINT "uuid_v6_pk" PRIMARY KEY (id)
                            )
                            """)
                    .insertIntoTableSql("""
                            INSERT INTO uuid_v6(id, value)
                            VALUES (?::uuid, ?)
                            """)
                    .selectFromTableSql("""
                            SELECT value
                            FROM uuid_v6
                            WHERE id = ?
                            """)
                    .build(),

            // UUID v7
            TestDefinition.builder()
                    .name("uuid7")
                    .keyGenerator(() -> Generators.timeBasedEpochGenerator().generate())
                    .keyReader(UUID::fromString)
                    .createTableSql("""
                            CREATE TABLE IF NOT EXISTS uuid_v7 (
                                id uuid NOT NULL,
                                value bytea NULL,
                                CONSTRAINT "uuid_v7_pk" PRIMARY KEY (id)
                            )
                            """)
                    .insertIntoTableSql("""
                            INSERT INTO uuid_v7(id, value)
                            VALUES (?::uuid, ?)
                            """)
                    .selectFromTableSql("""
                            SELECT value
                            FROM uuid_v7
                            WHERE id = ?
                            """)
                    .build());

    public static void main(String[] args) {
        var action = (args != null && args.length > 0) ? args[0] : null;
        var uuidVersion = (args != null && args.length > 1) ? args[1] : null;
        var rowCount = (args != null && args.length > 2) ? Integer.parseInt(args[2]) : ROW_COUNT;

        if (action == null || action.isBlank()) {
            System.err.println("Action is undefined!");
            printHelp();
            return;
        }

        if (uuidVersion == null || uuidVersion.isBlank()) {
            System.err.println("UUID version is undefined!");
            printHelp();
            return;
        }

        var uuidDefinition = UUIDS.stream().filter(i -> uuidVersion.equalsIgnoreCase(i.getName())).findFirst().orElse(null);

        if (uuidDefinition == null) {
            System.err.printf("Unknown UUID version '%s'.%n", uuidVersion);
            printHelp();
            return;
        }

        if (rowCount <= 0) {
            System.err.println("The row count must be positive.");
            printHelp();
            return;
        }

        try {
            var dataSource = new PGSimpleDataSource();
            dataSource.setServerNames(new String[]{System.getenv("POSTGRES_HOST")});
            dataSource.setPortNumbers(new int[]{Integer.parseInt(System.getenv("POSTGRES_PORT"))});
            dataSource.setDatabaseName(System.getenv("POSTGRES_DB"));
            dataSource.setUser(System.getenv("POSTGRES_USER"));
            dataSource.setPassword(System.getenv("POSTGRES_PASSWORD"));

            if (action.equalsIgnoreCase("insert")) {
                System.out.println("Inserting UUID keys...");
                insertKeys(dataSource, uuidDefinition, rowCount);
            } else if (action.equalsIgnoreCase("select")) {
                System.out.println("Selecting UUID keys...");
                selectKeys(dataSource, uuidDefinition, rowCount);
            } else {
                System.err.printf("Unknown action '%s'.%n", action);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        System.out.println("Done!");
    }

    private static void printHelp() {
        System.err.println("Usage:");
        System.err.println("  postgresql-uuid-test ACTION UUID_VERSION ITERATION_COUNT");
        System.err.println("where:");
        System.err.println("  ACTION    - insert|select");
        System.err.println("  KEY_TYPE  - BigSerial|uuid1|uuid4|uuid6|uuid7");
        System.err.println("  ROW_COUNT - the number of rows to insert or select");
    }

    private static void insertKeys(DataSource dataSource, TestDefinition testDefinition, int rowCount) throws Exception {
        var elapsedTime = 0L;
        var uuidFilePath = System.getenv("DATA_DIR") + "/" + testDefinition.getName() + ".csv";

        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement()) {
                statement.execute(testDefinition.getCreateTableSql());
            }

            try (var statement = connection.prepareStatement(testDefinition.getInsertIntoTableSql());
                 var uuidFileWriter = new FileWriter(uuidFilePath, StandardCharsets.UTF_8, true);
                 var uuidWriter = new BufferedWriter(uuidFileWriter)) {
                var row = 0;

                for (var i = 1; i <= rowCount; ++i) {
                    var id = testDefinition.getKeyGenerator().get();

                    uuidWriter.write(id.toString());
                    uuidWriter.newLine();

                    statement.setObject(1, id);
                    statement.setBytes(2, randomValue());
                    statement.addBatch();

                    if (++row == BATCH_SIZE) {
                        row = 0;
                        uuidWriter.flush();
                        var startTime = System.nanoTime();
                        try {
                            statement.executeBatch();
                        } finally {
                            var stopTime = System.nanoTime();
                            elapsedTime += (stopTime - startTime);
                        }
                    }
                }

                if (row > 0) {
                    uuidWriter.flush();
                    var startTime = System.nanoTime();
                    try {
                        statement.executeBatch();
                    } finally {
                        var stopTime = System.nanoTime();
                        elapsedTime += (stopTime - startTime);
                    }
                }

            }
        }

        System.out.printf("Total time = %s%n", Duration.ofNanos(elapsedTime));
    }

    private static void selectKeys(DataSource dataSource, TestDefinition testDefinition, int rowCount) throws Exception {
        var uuidFilePath = Path.of(System.getenv("DATA_DIR") + "/" + testDefinition.getName() + ".csv");

        if (!Files.exists(uuidFilePath)) {
            System.out.printf("The data file not found: %s%n", uuidFilePath);
            return;
        }

        long lineCount;

        try (var stream = Files.lines(uuidFilePath, StandardCharsets.UTF_8)) {
            lineCount = stream.count();
        }

        System.out.printf("The number of IDs in the file: %d%n", lineCount);

        var ids = new ArrayList<>(rowCount);
        var lineNumbers = new HashSet<Long>(rowCount);

        while (lineNumbers.size() < rowCount) {
            lineNumbers.add(RANDOM.nextLong(0, lineCount));
        }

        try (var stream = Files.lines(uuidFilePath, StandardCharsets.UTF_8)) {
            var lineNumber = new AtomicLong(0);
            stream.forEach(l -> {
                if (lineNumbers.contains(lineNumber.getAndIncrement())) {
                    ids.add(testDefinition.keyReader.apply(l));
                }
            });
        }

        System.out.printf("The number of IDs to read: %d%n", ids.size());

        var elapsedTime = 0L;

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(testDefinition.getSelectFromTableSql())) {
            for (var id : ids) {
                statement.setObject(1, id);

                var startTime = System.nanoTime();
                try (var resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        resultSet.getBytes(1);
                    }
                } finally {
                    var stopTime = System.nanoTime();
                    elapsedTime += (stopTime - startTime);
                }
            }
        }

        System.out.printf("Total time = %s%n", Duration.ofNanos(elapsedTime));
    }

    private static byte[] randomValue() {
        var value = new byte[VALUE_SIZE];
        RANDOM.nextBytes(value);
        return value;
    }


    @Getter
    @Builder
    private static class TestDefinition {

        private final String name;
        private final Supplier<Object> keyGenerator;
        private final Function<String, Object> keyReader;
        private final String createTableSql;
        private final String insertIntoTableSql;
        private final String selectFromTableSql;

    }

}
