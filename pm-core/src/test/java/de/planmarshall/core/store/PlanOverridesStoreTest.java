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

import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.config.SettingsConflict;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.PlanOverrides.Axis;
import de.planmarshall.core.store.PlanOverrides.Channel;
import de.planmarshall.core.store.PlanOverrides.Providers;
import de.planmarshall.core.store.PlanOverrides.Role;
import de.planmarshall.core.store.PlanOverrides.UpdatedBy;

/**
 * {@link PlanOverridesStore} writes a plan's {@code overrides.json} through the plan's transaction (Store Schemas § Plan
 * Overrides): the document is absent until the first value, a write raises the revision, and a second write against the
 * revision the first one consumed is refused with the current document, so the first change wins.
 * <p>
 * The member names and the channel values are the specified document format. The project and plan ids and the write
 * instant are generated; the role, model and axis values are the literals the document's grammar admits.
 */
@EnableGeneratorController
@DisplayName("Plan overrides store")
class PlanOverridesStoreTest {

    @TempDir
    Path base;

    /** The fixed clock of one test, drawn under the test's own generator controller. */
    private Clock clock;
    private JournalScope scope;
    private PlanOverridesStore store;
    private RecordingLockManager locks;
    private AuditSink audit;
    private String project;
    private String plan;

    @BeforeEach
    void createScope() throws IOException {
        clock = Clock.fixed(Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next()),
                ZoneOffset.UTC);
        project ="project-" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT);
        plan = "plan-" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT);
        scope = JournalScope.plan(base, project, plan);
        Files.createDirectories(scope.journalFile().getParent());
        store = new PlanOverridesStore(scope);
        locks = new RecordingLockManager();
        audit = AuditSinkDoubles.capturing(newCapture(CaptureType.ALL));
    }

    private PlanOverrides values() {
        var roles = new LinkedHashMap<String, Role>();
        roles.put("implementer", new Role("claude", "deepseek-v4.1-flash", "high"));
        var axes = new LinkedHashMap<Axis, String>();
        axes.put(Axis.LANDING, "landing.main");
        return PlanOverrides.builder()
                .revision(PlanOverrides.NO_DOCUMENT)
                .roles(roles)
                .axes(axes)
                .integrations(Map.of())
                .providers(Providers.none())
                .build();
    }

    /** Runs one write in a transaction of its own and commits it. */
    private PlanOverrides write(PlanOverrides values, long revision) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, clock);
            var written = store.write(write, values, revision, new UpdatedBy(Channel.CLI, null), clock.instant());
            write.commit();
            return written;
        }
    }

    @Test
    @DisplayName("an absent document reads revision 0 and no value")
    void absent() {
        var read = store.read();

        assertAll(
                () -> assertEquals(PlanOverrides.NO_DOCUMENT, read.revision(), "An absent document is at revision 0"),
                () -> assertTrue(read.roles().isEmpty(), "An absent document sets no role"),
                () -> assertTrue(read.axes().isEmpty(), "An absent document sets no axis"));
    }

    @Test
    @DisplayName("a write at revision 0 creates the document at revision 1")
    void firstWrite() {
        var written = write(values(), PlanOverrides.NO_DOCUMENT);

        var read = store.read();

        assertAll(
                () -> assertEquals(1L, written.revision(), "The first write raises the revision to 1"),
                () -> assertEquals(written, read, "The document reads as it was written"),
                () -> assertTrue(Files.exists(expected()), "The document lands in the plan's own directory"),
                () -> assertEquals("claude", read.roles().get("implementer").harness(),
                        "The role's harness must round-trip"),
                () -> assertEquals("landing.main", read.axes().get(Axis.LANDING),
                        "The bound landing variant must round-trip"),
                () -> assertEquals(Channel.CLI, read.updatedBy().channel(), "The write channel is recorded"));
    }

    @Test
    @DisplayName("of two writes against one revision the first wins and the second is refused")
    void firstChangeWins() {
        var accepted = write(values(), PlanOverrides.NO_DOCUMENT);
        var refused = values().toBuilder()
                .axes(Map.of(Axis.LANDING, "landing.files"))
                .build();
        var stamp = new UpdatedBy(Channel.CLI, null);
        var now = clock.instant();
        SettingsConflict conflict;
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, clock);

            conflict = assertThrows(SettingsConflict.class,
                    () -> store.write(write, refused, PlanOverrides.NO_DOCUMENT, stamp, now),
                    "A change against the consumed revision is refused");
        }

        var refusal = conflict;
        assertAll(
                () -> assertEquals(SettingsConflict.ERROR_CODE, refusal.code(), "The refusal carries conflict"),
                () -> assertEquals(accepted, refusal.currentValue(), "The refusal names the current document"),
                () -> assertEquals(refusal.currentValue(), store.read(), "The refused write changes nothing"),
                () -> assertEquals(1L, store.read().revision(), "Only one write advances the revision"));
    }

    @Test
    @DisplayName("a value the schema does not admit stages nothing")
    void refusedValueStagesNothing() {
        var invalid = values().toBuilder()
                .providers(new Providers(List.of("x"), List.of()))
                .build();
        var stamp = new UpdatedBy(Channel.CLI, null);
        var now = clock.instant();

        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, clock);

            assertThrows(IllegalArgumentException.class,
                    () -> store.write(write, invalid, PlanOverrides.NO_DOCUMENT, stamp, now),
                    "A provider name the schema refuses is refused");
        }
        assertTrue(Files.notExists(expected()), "A refused write leaves no document");
    }

    @Test
    @DisplayName("a transaction of another scope is refused")
    void foreignScopeRefused() throws Exception {
        var epic = JournalScope.epic(base, project,
                "epic-" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT));
        Files.createDirectories(epic.journalFile().getParent());
        var stamp = new UpdatedBy(Channel.CLI, null);
        var now = clock.instant();
        var proposed = values();

        try (var transaction = locks.openTransaction()) {
            transaction.acquire(epic.lockKey());
            var write = ScopeTransaction.open(transaction, epic, audit, clock);

            assertThrows(IllegalArgumentException.class,
                    () -> store.write(write, proposed, PlanOverrides.NO_DOCUMENT, stamp, now),
                    "The plan overrides are written by the plan's own transaction");
        }
        assertTrue(Files.notExists(epic.pathBase().resolve("overrides.json")),
                "A foreign transaction writes nothing into its own scope");
    }

    /** @return the document's path, built from the store layout rather than from the object under test */
    private Path expected() {
        return LockKey.projectStore(base, project).resolve("plans").resolve(plan).resolve("overrides.json");
    }

    @Test
    @DisplayName("a second write of the document in one transaction is refused")
    void oneWritePerTransaction() {
        var values = values();
        var stamp = new UpdatedBy(Channel.CLI, null);
        var now = clock.instant();

        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, clock);
            write.replace(PlanOverridesStore.FILE_NAME,
                    PlanOverridesCodec.write(values.toBuilder().revision(1).updatedAt(now).updatedBy(stamp).build()));

            assertThrows(IllegalArgumentException.class,
                    () -> store.write(write, values, PlanOverrides.NO_DOCUMENT, stamp, now),
                    "The plan overrides are written once per transaction");
        }
    }
}
