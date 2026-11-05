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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The operator's qualification settings (Store Schemas § Operator Local Configuration): the block is read with its
 * defaults and the document's revision, and a write replaces it alone — every other member of the operator's document
 * survives, the revision of the document is incremented, and a write against an older revision is refused. The member
 * names of the operator's document and its file name are wire contracts; levels, revisions and roles are generated.
 */
@EnableGeneratorController
@DisplayName("Qualification settings")
class QualificationSettingsStoreTest {

    // Wire document of the operator's local configuration; the level and the revision are filled in per test.
    private static final String DOCUMENT_TEMPLATE = """
            {"format_version":1,"revision":%d,
             "settings":{"commit_identity":{"name":"Oliver","email":"oliver@example.org"},
                         "qualification":{"agreement_level":%d}},
             "integrations":{"git":{"variant":"cli"}}}""";

    @TempDir
    Path base;

    private Path file;
    private QualificationSettingsStore store;
    private int level;
    private long revision;
    private String document;

    @BeforeEach
    void createStore() throws Exception {
        file = Files.createDirectories(base.resolve("projects").resolve("pm-mcp-core"))
                .resolve("local-config.json");
        store = new QualificationSettingsStore(
                new AtomicStoreFile(file, LockKey.localConfiguration(base, "pm-mcp-core")),
                new RecordingLockManager());
        level = Generators.integers(0, 100).next();
        revision = Generators.longs(1, 1_000_000L).next();
        document = DOCUMENT_TEMPLATE.formatted(revision, level);
    }

    private static String role() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static QualificationSettings generatedSettings() {
        return new QualificationSettings(Generators.integers(0, 100).next(), Generators.integers(0, 100).next(),
                List.of(role()), Generators.booleans().next());
    }

    @Test
    @DisplayName("reads the block, the revision and the compiled defaults of the members it leaves out")
    void readDefaults() throws Exception {
        Files.writeString(file, document, UTF_8);

        var read = store.read();

        assertAll(
                () -> assertEquals(level, read.settings().agreementLevel(), "the agreement level should be read"),
                () -> assertEquals(QualificationSettings.DEFAULT_ALLOWED_GAP, read.settings().allowedGap(),
                        "the allowed gap should default"),
                () -> assertEquals(List.of(), read.settings().skipRoles(), "the skipped roles should default"),
                () -> assertFalse(read.settings().skipDecisions(), "the skipped decisions should default"),
                () -> assertEquals(revision, read.revision(), "the revision should be read"));
    }

    @Test
    @DisplayName("a document without the block reads as the compiled defaults")
    void absentBlock() throws Exception {
        Files.writeString(file, "{\"format_version\":1,\"revision\":%d}".formatted(revision), UTF_8);

        var read = store.read();

        assertAll(
                () -> assertEquals(QualificationSettings.defaults(), read.settings(),
                        "a missing block should read as defaults"),
                () -> assertEquals(revision, read.revision(), "the revision should be read"));
    }

    @Test
    @DisplayName("a document that does not exist reads the defaults at revision 0")
    void absentDocument() {
        var read = store.read();

        assertAll(
                () -> assertEquals(QualificationSettings.defaults(), read.settings(),
                        "a missing document should read as defaults"),
                () -> assertEquals(0L, read.revision(), "a missing document should be revision 0"));
    }

    @Test
    @DisplayName("writes the block alone, keeps every other member and increments the revision")
    void writeReplacesOnlyTheBlock() throws Exception {
        Files.writeString(file, document, UTF_8);
        var current = store.read().revision();
        var settings = generatedSettings();

        store.write(settings, current);

        var written = Files.readString(file, UTF_8);
        var read = store.read();
        assertAll(
                () -> assertEquals(settings, read.settings(), "the written block should be read back"),
                () -> assertEquals(current + 1, read.revision(), "the revision should be incremented"),
                () -> assertTrue(written.contains("\"revision\":" + (current + 1)),
                        "the document should carry the new revision"),
                () -> assertTrue(written.contains("\"commit_identity\""), "the commit identity should survive"),
                () -> assertTrue(written.contains("\"integrations\""), "the integrations should survive"),
                () -> assertTrue(written.contains("\"variant\":\"cli\""), "the git variant should survive"));
    }

    @Test
    @DisplayName("a write against an older revision is refused and leaves the document unchanged")
    void staleRevision() throws Exception {
        Files.writeString(file, document, UTF_8);
        var current = store.read();
        var replacement = generatedSettings();
        var staleRevision = current.revision() - Generators.longs(1, current.revision()).next();

        var refusal = assertThrows(StoreRefusal.class, () -> store.write(replacement, staleRevision),
                "a stale revision should be refused");

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.CONFLICT, refusal.outcome(),
                        "the refusal should be a conflict"),
                () -> assertEquals(file, refusal.file(), "the refusal should name the file"),
                () -> assertEquals(document, Files.readString(file, UTF_8), "the document should stay unchanged"),
                () -> assertEquals(current, store.read(), "the settings should stay unchanged"));
    }

    @Test
    @DisplayName("a document that does not exist is not created by a write")
    void absentDocumentWrite() {
        var defaults = QualificationSettings.defaults();

        assertAll(
                () -> assertThrows(StoreRefusal.class, () -> store.write(defaults, 0L),
                        "a write without a document should fail"),
                () -> assertFalse(Files.exists(file), "no document should have been created"));
    }

    @Test
    @DisplayName("a level outside 0 to 100, a broken role or a role skipped twice is refused (bounds and grammar)")
    void bounds() {
        var noRoles = List.<String>of();
        var badRole = List.of("Not A Role");
        var role = role();
        var skippedTwice = List.of(role, role);
        var over = Generators.integers(101, 1_000).next();
        var valid = Generators.integers(0, 100).next();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(over, 0, noRoles, false),
                        "a level over 100 should be refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(0, over, noRoles, false),
                        "a gap over 100 should be refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(valid, valid, badRole, false),
                        "a broken role should be refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(valid, valid, skippedTwice, false),
                        "a role skipped twice should be refused"));
    }

    @Test
    @DisplayName("a document whose revision cannot be incremented is refused as a StoreRefusal (Long.MAX_VALUE)")
    void exhaustedRevision() throws Exception {
        Files.writeString(file, "{\"format_version\":1,\"revision\":%d}".formatted(Long.MAX_VALUE), UTF_8);
        var defaults = QualificationSettings.defaults();
        var exhausted = store.read().revision();

        var refusal = assertThrows(StoreRefusal.class, () -> store.write(defaults, exhausted),
                "an exhausted revision should be refused");
        assertEquals(file, refusal.file(), "the refusal should name the file");
    }

    @Test
    @DisplayName("a document that is not readable is refused as a StoreRefusal naming it (malformed wire document)")
    void unreadableDocument() throws Exception {
        Files.writeString(file, "{\"format_version\":1,\"revision\":1,\"settings\":42}", UTF_8);

        var refusal = assertThrows(StoreRefusal.class, store::read, "an unreadable document should be refused");
        assertEquals(file, refusal.file(), "the refusal should name the file");
    }
}
