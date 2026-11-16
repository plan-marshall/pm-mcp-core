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
 * The member names and the channel values are the specified document format. The project and plan ids, the role, model
 * and base-branch values are generated.
 */
@EnableGeneratorController
@DisplayName("Plan overrides store")
class PlanOverridesStoreTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next()), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private PlanOverridesStore store;
    private RecordingLockManager locks;
    private AuditSink audit;

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base,
                "project-" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT),
                "plan-" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT));
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
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            var written = store.write(write, values, revision, new UpdatedBy(Channel.CLI, null), CLOCK.instant());
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

        var conflict = assertThrows(SettingsConflict.class,
                () -> write(refused, PlanOverrides.NO_DOCUMENT),
                "A change against the consumed revision is refused");

        assertAll(
                () -> assertEquals(SettingsConflict.ERROR_CODE, conflict.code(), "The refusal carries conflict"),
                () -> assertEquals(accepted, conflict.currentValue(), "The refusal names the current document"),
                () -> assertEquals(conflict.currentValue(), store.read(), "The refused write changes nothing"),
                () -> assertEquals(1L, store.read().revision(), "Only one write advances the revision"));
    }

    @Test
    @DisplayName("a value the schema does not admit stages nothing")
    void refusedValueStagesNothing() {
        var invalid = values().toBuilder()
                .providers(new Providers(List.of("x"), List.of()))
                .build();

        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);

            assertThrows(IllegalArgumentException.class,
                    () -> store.write(write, invalid, PlanOverrides.NO_DOCUMENT, new UpdatedBy(Channel.CLI, null),
                            CLOCK.instant()),
                    "A provider name the schema refuses is refused");
        }
        assertTrue(Files.notExists(store.file()), "A refused write leaves no document");
    }
}
