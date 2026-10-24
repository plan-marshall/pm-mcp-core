/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The operator's qualification settings (Store Schemas § Operator Local Configuration): the block is read with its
 * defaults, and a write replaces it alone — every other member of the operator's document survives, and the revision
 * of the document is incremented.
 */
@DisplayName("Qualification settings")
class QualificationSettingsStoreTest {

    private static final String DOCUMENT = """
            {"format_version":1,"revision":3,
             "settings":{"commit_identity":{"name":"Oliver","email":"oliver@example.org"},
                         "qualification":{"agreement_level":90}},
             "integrations":{"git":{"variant":"cli"}}}""";

    @TempDir
    Path base;

    private Path file;
    private QualificationSettingsStore store;

    @BeforeEach
    void createStore() throws Exception {
        file = Files.createDirectories(base.resolve("projects").resolve("pm-mcp-core"))
                .resolve("local-config.json");
        store = new QualificationSettingsStore(
                new AtomicStoreFile(file, LockKey.localConfiguration(base, "pm-mcp-core")),
                new RecordingLockManager());
    }

    @Test
    @DisplayName("reads the block and the compiled defaults of the members it leaves out")
    void readDefaults() throws Exception {
        Files.writeString(file, DOCUMENT, UTF_8);

        var settings = store.read();

        assertAll(
                () -> assertEquals(90, settings.agreementLevel()),
                () -> assertEquals(QualificationSettings.DEFAULT_ALLOWED_GAP, settings.allowedGap()),
                () -> assertEquals(List.of(), settings.skipRoles()),
                () -> assertFalse(settings.skipDecisions()));
    }

    @Test
    @DisplayName("a document without the block reads as the compiled defaults")
    void absentBlock() throws Exception {
        Files.writeString(file, "{\"format_version\":1,\"revision\":1}", UTF_8);

        assertEquals(QualificationSettings.defaults(), store.read());
    }

    @Test
    @DisplayName("writes the block alone, keeps every other member and increments the revision")
    void writeReplacesOnlyTheBlock() throws Exception {
        Files.writeString(file, DOCUMENT, UTF_8);

        store.write(new QualificationSettings(80, 10, List.of("writer"), true));

        var written = Files.readString(file, UTF_8);
        var read = store.read();
        assertAll(
                () -> assertEquals(new QualificationSettings(80, 10, List.of("writer"), true), read),
                () -> assertTrue(written.contains("\"revision\":4"), written),
                () -> assertTrue(written.contains("\"commit_identity\""), written),
                () -> assertTrue(written.contains("\"integrations\""), written),
                () -> assertTrue(written.contains("\"variant\":\"cli\""), written));
    }

    @Test
    @DisplayName("a document that does not exist is not created by a write")
    void absentDocument() {
        assertAll(
                () -> assertThrows(StoreRefusal.class,
                        () -> store.write(QualificationSettings.defaults())),
                () -> assertFalse(Files.exists(file)));
    }

    @Test
    @DisplayName("a level outside 0 to 100 is refused")
    void bounds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(101, 0, List.of(), false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(0, 101, List.of(), false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(80, 5, List.of("Not A Role"), false)));
    }
}
