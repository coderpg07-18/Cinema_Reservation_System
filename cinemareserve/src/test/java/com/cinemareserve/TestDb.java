package com.cinemareserve;

import com.cinemareserve.db.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Points the Database singleton at a fresh, disposable SQLite file for each test,
 *  so tests never see leftover state from a previous test or a developer's local run. */
public final class TestDb {

    private TestDb() {
    }

    public static void freshDatabase() {
        try {
            Path tempFile = Files.createTempFile("cinemareserve-test-", ".db");
            Files.deleteIfExists(tempFile);
            Database.configure(tempFile.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
