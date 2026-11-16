/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.config.SettingsConflict;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The machine's settings this store owns (Store Schemas § Machine Configuration Store, State Catalogue § The Machine's
 * Settings): how a start takes its documents ({@code sources.documents_mode}) and the durations of the one cleanup
 * ({@code cleanup}). Each write replaces its own member and keeps the operator's other members; a change against the
 * revision another change already advanced the document past is refused with {@code conflict} and the current value,
 * so the first change wins and the second is refused.
 * <p>
 * The member names, the format version and the wire values are the specified document format. The revisions, the days
 * and the free text of the members the store copies through are generated.
 */
@EnableGeneratorController
@DisplayName("Machine settings writers")
class MachineSettingsWritersTest {

    @TempDir
    Path base;

    private Path file;
    private MachineConfigurationStore store;

    private final long revision = Generators.longs(1, 1_000_000).next();
    private final int maxBuildSlots = Generators.integers(1, 16).next();
    private final int lspIdleTimeout = Generators.integers(60, 86_400).next();
    private final int plansAfterDays = Generators.integers(CleanupDuration.MIN_DAYS, 365).next();
    private final int epicsAfterDays = Generators.integers(CleanupDuration.MIN_DAYS, 365).next();
    private final int remindAfterDays = Generators.integers(CleanupDurations.MIN_REMIND_DAYS, 30).next();

    @BeforeEach
    void createStore() {
        store = new MachineConfigurationStore(MachineConfigurationStore.file(base), new TestLocks());
        file = base.resolve(MachineConfigurationStore.FILE_NAME);
    }

    /** @return the operator's document at the generated revision, with members the store copies through */
    private String document() {
        return """
                {"format_version":1,"revision":%d,"max_build_slots":%d,"lsp_idle_timeout_seconds":%d,
                 "credentials":{"store":"os"},
                 "harnesses":{"default":"claude"}}""".formatted(revision, maxBuildSlots, lspIdleTimeout);
    }

    private CleanupDurations durations() {
        return CleanupDurations.builder()
                .plansAfterDays(CleanupDuration.ofDays(plansAfterDays))
                .epicsAfterDays(CleanupDuration.ofDays(epicsAfterDays))
                .logsAfterDays(CleanupDuration.never())
                .reportsAfterDays(CleanupDuration.ofDays(CleanupDuration.DEFAULT_DAYS))
                .unenrolledAfterDays(CleanupDuration.never())
                .remindAfterDays(remindAfterDays)
                .build();
    }

    @Test
    @DisplayName("the documents mode is written and the other members survive")
    void documentsMode() throws Exception {
        Files.writeString(file, document(), UTF_8);

        store.writeDocumentsMode(DocumentsMode.MOVE, revision);
        var read = store.read();

        assertAll(
                () -> assertEquals(DocumentsMode.MOVE, read.documentsMode(), "The written mode must be read back"),
                () -> assertEquals(revision + 1, read.revision(), "A write increments the document revision"),
                () -> assertTrue(Files.readString(file, UTF_8).contains("\"credentials\":{\"store\":\"os\"}"),
                        "The operator's credential store survives the write"),
                () -> assertTrue(Files.readString(file, UTF_8).contains("\"harnesses\":{\"default\":\"claude\"}"),
                        "The operator's harness default survives the write"));
    }

    @Test
    @DisplayName("the cleanup durations are written and read back, never included")
    void cleanupDurations() throws Exception {
        Files.writeString(file, document(), UTF_8);

        store.writeCleanupDurations(durations(), revision);
        var read = store.read();

        assertAll(
                () -> assertEquals(durations(), read.cleanup(), "The written durations must be read back"),
                () -> assertEquals(revision + 1, read.revision(), "A write increments the document revision"),
                () -> assertTrue(Files.readString(file, UTF_8).contains("\"logs_after_days\":\"never\""),
                        "A never duration is written as the schema's string"),
                () -> assertTrue(Files.readString(file, UTF_8).contains("\"credentials\":"),
                        "The operator's credential store survives the write"));
    }

    @Test
    @DisplayName("of two documents-mode changes against one revision the second is refused")
    void documentsModeFirstChangeWins() throws Exception {
        Files.writeString(file, document(), UTF_8);

        store.writeDocumentsMode(DocumentsMode.COPY, revision);
        var conflict = assertThrows(SettingsConflict.class,
                () -> store.writeDocumentsMode(DocumentsMode.MOVE, revision),
                "A change against the consumed revision is refused");

        assertAll(
                () -> assertEquals(SettingsConflict.ERROR_CODE, conflict.code(), "The refusal carries the conflict code"),
                () -> assertEquals(SettingsConflict.HTTP_STATUS, conflict.status(), "The refusal carries status 409"),
                () -> assertEquals(DocumentsMode.COPY, conflict.currentValue(), "The refusal names the current mode"),
                () -> assertEquals(DocumentsMode.COPY, store.read().documentsMode(),
                        "The refused change must not overwrite the first one"));
    }

    @Test
    @DisplayName("of two cleanup changes against one revision the second is refused")
    void cleanupFirstChangeWins() throws Exception {
        Files.writeString(file, document(), UTF_8);
        var accepted = durations();
        var refused = CleanupDurations.builder()
                .plansAfterDays(CleanupDuration.never())
                .epicsAfterDays(CleanupDuration.never())
                .logsAfterDays(CleanupDuration.never())
                .reportsAfterDays(CleanupDuration.never())
                .unenrolledAfterDays(CleanupDuration.never())
                .remindAfterDays(CleanupDurations.DEFAULT_REMIND_DAYS)
                .build();

        store.writeCleanupDurations(accepted, revision);
        var conflict = assertThrows(SettingsConflict.class,
                () -> store.writeCleanupDurations(refused, revision),
                "A change against the consumed revision is refused");

        assertAll(
                () -> assertEquals(accepted, conflict.currentValue(), "The refusal names the current durations"),
                () -> assertEquals(accepted, store.read().cleanup(), "The refused change must not overwrite"),
                () -> assertEquals(revision + 1, store.read().revision(), "Only one write advances the revision"));
    }

    @Test
    @DisplayName("an absent document is created with the setting and the required members")
    void absentDocumentIsCreated() {
        store.writeDocumentsMode(DocumentsMode.ASK, 0L);
        store.writeCleanupDurations(durations(), 1L);

        var read = store.read();

        assertAll(
                () -> assertEquals(DocumentsMode.ASK, read.documentsMode(), "The created document holds the mode"),
                () -> assertEquals(durations(), read.cleanup(), "The created document holds the durations"),
                () -> assertEquals(2L, read.revision(), "Each creation write increments the revision"));
    }

    @Test
    @DisplayName("the codec refuses a duration below the schema minimum and an unknown mode")
    void refusesInvalidValues() {
        var belowMinimum = CleanupDuration.MIN_DAYS - 1;
        var written = document().replace("\"credentials\"", "\"sources\":{\"documents_mode\":\"keep\"},"
                + "\"credentials\"").getBytes(UTF_8);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CleanupDuration.ofDays(belowMinimum), "A duration below seven days is refused"),
                () -> assertThrows(StoreRefusal.class,
                        () -> MachineConfigurationCodec.read(file, written),
                        "An unknown documents mode is refused"));
    }
}
