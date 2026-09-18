package com.cinemareserve.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the JDBC connection lifecycle for the application.
 *
 * SQLite has no real client-server connection pool -- for a single-process console
 * application the simplest and most correct approach is one connection per unit of
 * work, opened and closed around each service-layer transaction. We centralize that
 * here so every DAO/service gets consistent PRAGMA settings instead of repeating them.
 */
public final class Database {

    private static String dbPath = System.getenv().getOrDefault("CINEMARESERVE_DB_PATH", "data/cinemareserve.db");
    private static volatile boolean initialized = false;

    private Database() {
    }

    /** Allows tests to point at an isolated, disposable database file. */
    public static synchronized void configure(String path) {
        dbPath = path;
        initialized = false;
    }

    public static Connection getConnection() {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 5000");
                st.execute("PRAGMA journal_mode = WAL");
            }
            ensureSchema(conn);
            return conn;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found on classpath", e);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open database connection", e);
        }
    }

    /**
     * Starts a write transaction using SQLite's IMMEDIATE mode instead of the JDBC
     * default (DEFERRED).
     *
     * Why this matters: SQLite's default transaction only acquires a SHARED (read) lock
     * when it starts; it only tries to upgrade to a write lock on the first INSERT/UPDATE.
     * When many threads each begin a deferred transaction, run some SELECTs, and then all
     * try to upgrade to a write lock at roughly the same time, they can contend for that
     * upgrade in a way plain busy_timeout retries don't resolve cleanly -- exactly the
     * SQLITE_BUSY-on-upgrade failures this project's own concurrency tests surfaced.
     * BEGIN IMMEDIATE instead grabs the write lock (a RESERVED lock) up front, so the
     * threads simply queue for it one at a time -- respecting busy_timeout properly --
     * rather than racing each other during an upgrade.
     */
    public static void beginWriteTransaction(Connection conn) throws SQLException {
        // The Xerial driver eagerly opens its own DEFERRED transaction the moment
        // setAutoCommit(false) is called, before we get a chance to request IMMEDIATE
        // mode ourselves. That auto-opened transaction has done no work yet, so we close
        // it right back out with a no-op COMMIT and then open the IMMEDIATE transaction
        // we actually want -- conn.commit()/rollback() remain valid afterwards because
        // autoCommit is still false.
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            st.execute("COMMIT");
            st.execute("BEGIN IMMEDIATE");
        }
    }

    /**
     * The bundled Xerial SQLite driver does not implement
     * {@code PreparedStatement.getGeneratedKeys()} reliably across all statement forms,
     * so every DAO insert calls this immediately afterwards instead -- it reads back
     * SQLite's own per-connection {@code last_insert_rowid()}, which is exactly the id
     * the just-completed INSERT on this connection produced.
     */
    public static long lastInsertRowId(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Runs schema.sql exactly once per configured path (idempotent thanks to IF NOT EXISTS). */
    private static synchronized void ensureSchema(Connection conn) {
        if (initialized) {
            return;
        }
        try {
            String schema = stripLineComments(loadSchemaSql());
            try (Statement st = conn.createStatement()) {
                for (String stmt : schema.split(";")) {
                    String trimmed = stmt.trim();
                    if (!trimmed.isEmpty()) {
                        st.execute(trimmed);
                    }
                }
            }
            initialized = true;
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Failed to initialize schema", e);
        }
    }

    /** Removes "-- ..." line comments before the file is split on ';', so a semicolon
     *  or blank statement inside a comment can never be mistaken for SQL. */
    private static String stripLineComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n")) {
            int idx = line.indexOf("--");
            out.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return out.toString();
    }

    private static String loadSchemaSql() throws IOException {
        // Prefer the packaged resource (works from a runnable jar); fall back to the
        // repo-relative path so `java -cp ...` from the project root also works.
        var resource = Database.class.getResourceAsStream("/schema.sql");
        if (resource != null) {
            return new String(resource.readAllBytes());
        }
        Path fallback = Path.of("database/schema.sql");
        if (Files.exists(fallback)) {
            return Files.readString(fallback);
        }
        throw new UncheckedIOException(new IOException("schema.sql not found on classpath or at database/schema.sql"));
    }
}
