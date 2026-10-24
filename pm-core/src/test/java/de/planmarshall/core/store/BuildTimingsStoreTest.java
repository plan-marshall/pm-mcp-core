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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.BuildTimings.Baseline;
import de.planmarshall.core.store.BuildTimings.KeyTimings;
import de.planmarshall.core.store.BuildTimings.Residue;

/**
 * The learned budgets of a project (Store Schemas § Build Timings Store): the max-biased blend of a finished run, the
 * one-shot escalation of a timed-out one, and the store that keeps them.
 */
@DisplayName("Build timings")
class BuildTimingsStoreTest {

    private static final String KEY = "llm:writer";
    private static final Instant OBSERVED_AT = Instant.parse("2026-10-22T10:00:00Z");

    @TempDir
    Path base;

    private Path file;
    private BuildTimingsStore store;

    @BeforeEach
    void createStore() {
        file = base.resolve("build-timings.json");
        store = new BuildTimingsStore(
                new AtomicStoreFile(file, LockKey.buildTimings(base, "pm-mcp-core")), new RecordingLockManager());
    }

    private KeyTimings observe(long seconds) {
        return store.observe(KEY, Duration.ofSeconds(seconds), Residue.CLEAN, OBSERVED_AT);
    }

    @Nested
    @DisplayName("a finished run")
    class FinishedRun {

        @Test
        @DisplayName("is stored raw as the first observation of its baseline")
        void firstObservation() {
            var timings = observe(120);

            assertAll(
                    () -> assertEquals(new Baseline(120L, 1), timings.clean()),
                    () -> assertEquals(Baseline.empty(), timings.poisoned()),
                    () -> assertEquals(Baseline.empty(), timings.cold()),
                    () -> assertEquals(OBSERVED_AT, timings.lastObservedAt()),
                    () -> assertNull(timings.escalationSeconds()));
        }

        @Test
        @DisplayName("feeds the max-biased blend floor(0.8 * max + 0.2 * min) into its own baseline only")
        void blend() {
            observe(120);
            var timings = observe(200);

            assertAll(
                    () -> assertEquals(new Baseline(184L, 2), timings.clean()),
                    () -> assertEquals(Baseline.empty(), timings.poisoned()));

            var poisoned = store.observe(KEY, Duration.ofSeconds(900), Residue.POISONED, OBSERVED_AT);

            assertAll(
                    () -> assertEquals(new Baseline(184L, 2), poisoned.clean()),
                    () -> assertEquals(new Baseline(900L, 1), poisoned.poisoned()));
        }

        @Test
        @DisplayName("clears the one-shot escalation")
        void clearsEscalation() {
            var escalated = store.escalate(KEY, 300, 1800);
            assertAll(
                    () -> assertEquals(600L, escalated.escalationSeconds()),
                    () -> assertEquals(Baseline.empty(), escalated.clean()));

            var timings = observe(120);

            assertAll(
                    () -> assertNull(timings.escalationSeconds()),
                    () -> assertEquals(new Baseline(120L, 1), timings.clean()));
        }
    }

    @Nested
    @DisplayName("a timed-out run")
    class TimedOutRun {

        @Test
        @DisplayName("sets E = max(t, min(2 * t, C)) and never feeds a baseline")
        void escalation() {
            observe(100);

            var capped = store.escalate(KEY, 1200, 1800);

            assertAll(
                    () -> assertEquals(1800L, capped.escalationSeconds(), "2t is capped at C"),
                    () -> assertEquals(new Baseline(100L, 1), capped.clean(), "a timeout never feeds L"));

            var belowTheCap = store.escalate(KEY, 300, 1800);
            assertEquals(600L, belowTheCap.escalationSeconds());
        }
    }

    @Nested
    @DisplayName("the store")
    class Store {

        @Test
        @DisplayName("reads back what it wrote, and a second instance sees it")
        void roundTrip() {
            observe(120);

            var restarted = new BuildTimingsStore(
                    new AtomicStoreFile(file, LockKey.buildTimings(base, "pm-mcp-core")),
                    new RecordingLockManager());

            assertAll(
                    () -> assertEquals(store.snapshot(), restarted.snapshot()),
                    () -> assertTrue(restarted.timings(KEY).isPresent()),
                    () -> assertEquals(new Baseline(120L, 1), restarted.timings(KEY).orElseThrow().clean()));
        }

        @Test
        @DisplayName("refuses a document in another format version and leaves it byte-identical")
        void newerFormat() throws Exception {
            Files.writeString(file, "{\"format_version\":2,\"keys\":{}}");

            assertThrows(StoreRefusal.class, () -> store.snapshot());
            assertEquals("{\"format_version\":2,\"keys\":{}}", Files.readString(file));
        }

        @Test
        @DisplayName("refuses a baseline whose observations and value disagree")
        void inconsistentBaseline() throws Exception {
            Files.writeString(file, """
                    {"format_version":1,"keys":{"llm:writer":{"clean":{"learned_seconds":null,"observations":3},
                     "poisoned":{"learned_seconds":null,"observations":0},
                     "cold":{"learned_seconds":null,"observations":0},"escalation_seconds":null,
                     "last_observed_at":null}}}""");

            assertThrows(StoreRefusal.class, () -> store.snapshot());
        }

        @Test
        @DisplayName("refuses a baseline with a negative count")
        void negativeObservations() throws Exception {
            Files.writeString(file, """
                    {"format_version":1,"keys":{"llm:writer":{"clean":{"learned_seconds":5,"observations":-1},
                     "poisoned":{"learned_seconds":null,"observations":0},
                     "cold":{"learned_seconds":null,"observations":0},"escalation_seconds":null,
                     "last_observed_at":null}}}""");

            assertThrows(StoreRefusal.class, () -> store.snapshot());
        }
    }
}
