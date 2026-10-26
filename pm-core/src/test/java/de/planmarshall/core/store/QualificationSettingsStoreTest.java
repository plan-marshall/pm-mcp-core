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
 * defaults and the document's revision, and a write replaces it alone — every other member of the operator's document
 * survives, the revision of the document is incremented, and a write against an older revision is refused.
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
    @DisplayName("reads the block, the revision and the compiled defaults of the members it leaves out")
    void readDefaults() throws Exception {
        Files.writeString(file, DOCUMENT, UTF_8);

        var read = store.read();

        assertAll(
                () -> assertEquals(90, read.settings().agreementLevel()),
                () -> assertEquals(QualificationSettings.DEFAULT_ALLOWED_GAP, read.settings().allowedGap()),
                () -> assertEquals(List.of(), read.settings().skipRoles()),
                () -> assertFalse(read.settings().skipDecisions()),
                () -> assertEquals(3L, read.revision()));
    }

    @Test
    @DisplayName("a document without the block reads as the compiled defaults")
    void absentBlock() throws Exception {
        Files.writeString(file, "{\"format_version\":1,\"revision\":1}", UTF_8);

        var read = store.read();

        assertAll(
                () -> assertEquals(QualificationSettings.defaults(), read.settings()),
                () -> assertEquals(1L, read.revision()));
    }

    @Test
    @DisplayName("a document that does not exist reads the defaults at revision 0")
    void absentDocument() {
        var read = store.read();

        assertAll(
                () -> assertEquals(QualificationSettings.defaults(), read.settings()),
                () -> assertEquals(0L, read.revision()));
    }

    @Test
    @DisplayName("writes the block alone, keeps every other member and increments the revision")
    void writeReplacesOnlyTheBlock() throws Exception {
        Files.writeString(file, DOCUMENT, UTF_8);
        var revision = store.read().revision();

        store.write(new QualificationSettings(80, 10, List.of("writer"), true), revision);

        var written = Files.readString(file, UTF_8);
        var read = store.read();
        assertAll(
                () -> assertEquals(new QualificationSettings(80, 10, List.of("writer"), true), read.settings()),
                () -> assertEquals(revision + 1, read.revision()),
                () -> assertTrue(written.contains("\"revision\":4"), written),
                () -> assertTrue(written.contains("\"commit_identity\""), written),
                () -> assertTrue(written.contains("\"integrations\""), written),
                () -> assertTrue(written.contains("\"variant\":\"cli\""), written));
    }

    @Test
    @DisplayName("a write against an older revision is refused and leaves the document unchanged")
    void staleRevision() throws Exception {
        Files.writeString(file, DOCUMENT, UTF_8);
        var current = store.read();
        var replacement = new QualificationSettings(70, 1, List.of(), false);
        var staleRevision = current.revision() - 1;

        var refusal = assertThrows(StoreRefusal.class, () -> store.write(replacement, staleRevision));

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.CONFLICT, refusal.outcome()),
                () -> assertEquals(file, refusal.file()),
                () -> assertEquals(DOCUMENT, Files.readString(file, UTF_8)),
                () -> assertEquals(current, store.read()));
    }

    @Test
    @DisplayName("a document that does not exist is not created by a write")
    void absentDocumentWrite() {
        var defaults = QualificationSettings.defaults();

        assertAll(
                () -> assertThrows(StoreRefusal.class, () -> store.write(defaults, 0L)),
                () -> assertFalse(Files.exists(file)));
    }

    @Test
    @DisplayName("a level outside 0 to 100, a broken role or a role skipped twice is refused")
    void bounds() {
        var noRoles = List.<String>of();
        var badRole = List.of("Not A Role");
        var skippedTwice = List.of("writer", "writer");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(101, 0, noRoles, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(0, 101, noRoles, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(80, 5, badRole, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(80, 5, skippedTwice, false)));
    }

    @Test
    @DisplayName("a document whose revision cannot be incremented is refused as a StoreRefusal")
    void exhaustedRevision() throws Exception {
        Files.writeString(file, "{\"format_version\":1,\"revision\":9223372036854775807}", UTF_8);
        var defaults = QualificationSettings.defaults();
        var revision = store.read().revision();

        var refusal = assertThrows(StoreRefusal.class, () -> store.write(defaults, revision));
        assertEquals(file, refusal.file());
    }

    @Test
    @DisplayName("a document that is not readable is refused as a StoreRefusal naming it")
    void unreadableDocument() throws Exception {
        Files.writeString(file, "{\"format_version\":1,\"revision\":1,\"settings\":42}", UTF_8);

        var refusal = assertThrows(StoreRefusal.class, store::read);
        assertEquals(file, refusal.file());
    }
}
