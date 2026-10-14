/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static de.planmarshall.runtime.start.RecoveryStepFixtures.PROJECT_A;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.queue.GenerationCheck;
import de.planmarshall.core.queue.SessionGeneration;
import de.planmarshall.core.queue.SessionGenerationStore;
import de.planmarshall.core.store.ScopeSessionRow.Client;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fenced-generation step of the periodic sweep over the session generation record of a project in a test base,
 * on the lock manager of the runtime: a generation fenced before the instant is removed, one fenced later and a
 * current one are kept.
 */
@EnableTestLogger
@DisplayName("Fenced session generations sweep")
class FencedSessionGenerationsSweepTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private static final Instant OLDER_THAN = NOW.minus(PeriodicSweep.PENDING_ENTRY_MAX_AGE);
    private static final SessionGenerationStore.Request SESSION = new SessionGenerationStore.Request("a".repeat(64),
            Client.CLAUDE, "session-1", true);
    private static final SessionGenerationStore.Request OTHER_SESSION = new SessionGenerationStore.Request(
            "b".repeat(64), Client.CODEX, null, false);

    @TempDir
    Path base;

    private FileLockManager lockManager;
    private FencedSessionGenerationsSweep sweep;

    @BeforeEach
    void createSweep() {
        lockManager = new FileLockManager(new RecordingAuditSink());
        sweep = new FencedSessionGenerationsSweep(base, lockManager, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private SessionGenerationStore storeAt(Instant instant) {
        return new SessionGenerationStore(base, PROJECT_A, lockManager, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("is the fenced-generation step of the periodic sweep")
    void name() {
        assertEquals(SweepStep.Name.FENCED_SESSION_GENERATIONS, sweep.name());
    }

    @Test
    @DisplayName("a generation fenced before the instant is removed; one fenced later and the current ones are kept")
    void removesOldFencedKeepsTheRest() {
        var oldFenced = storeAt(OLDER_THAN.minusSeconds(7200)).relayStart(SESSION);
        var youngFenced = storeAt(OLDER_THAN.minusSeconds(60)).newSession(SESSION);
        var current = storeAt(OLDER_THAN.plusSeconds(60)).enter(SESSION);
        var otherCurrent = storeAt(OLDER_THAN.minusSeconds(86_400)).relayStart(OTHER_SESSION);
        var store = storeAt(NOW);

        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        assertAll(
                () -> assertEquals(new Outcome.Done("removed 1 fenced session generations"), outcome),
                () -> assertEquals(List.of(youngFenced.id(), current.id(), otherCurrent.id()),
                        store.snapshot().stream().map(SessionGeneration::id).toList()),
                () -> assertEquals(new GenerationCheck.Unknown(oldFenced.id()), store.check(oldFenced.id())),
                () -> assertInstanceOf(GenerationCheck.Superseded.class, store.check(youngFenced.id())),
                () -> assertInstanceOf(GenerationCheck.Current.class, store.check(current.id())),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Periodic sweep removed 1 fenced session generations of project '" + PROJECT_A + "'"));
    }

    @Test
    @DisplayName("an old current generation is never removed, and nothing is logged")
    void oldCurrentKept() {
        var current = storeAt(OLDER_THAN.minusSeconds(86_400)).relayStart(SESSION);

        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        assertAll(
                () -> assertEquals(new Outcome.Done("removed 0 fenced session generations"), outcome),
                () -> assertInstanceOf(GenerationCheck.Current.class, storeAt(NOW).check(current.id())),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, "Periodic sweep removed"));
    }

    @Test
    @DisplayName("a project without a session generation record has nothing to remove and gets none")
    void noRecord() {
        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        assertAll(
                () -> assertEquals(new Outcome.Done("removed 0 fenced session generations"), outcome),
                () -> assertFalse(Files.exists(storeAt(NOW).store())));
    }

    @Test
    @DisplayName("a record in another format version is reported as a failure and left as it is")
    void refusedRecordFails() throws Exception {
        var file = storeAt(NOW).store();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"format_version\":2,\"generations\":{}}", StandardCharsets.UTF_8);

        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome).reason();
        assertAll(
                () -> assertTrue(reason.startsWith("session generation record refused (format_newer)"), reason),
                () -> assertEquals("{\"format_version\":2,\"generations\":{}}",
                        Files.readString(file, StandardCharsets.UTF_8)));
    }
}
