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
import static de.planmarshall.runtime.start.RecoveryStepFixtures.PROJECT_B;
import static de.planmarshall.runtime.start.RecoveryStepFixtures.claim;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LeaseStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;

/**
 * The lease step of the per-project recovery over a real build-slot lease store on the lock manager of the runtime.
 */
@DisplayName("Lease recovery step")
class LeaseRecoveryStepTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path base;

    private FileLockManager lockManager;
    private LeaseStore store;

    @BeforeEach
    void createStore() throws IOException {
        lockManager = new FileLockManager(new RecordingAuditSink());
        store = RecoveryStepFixtures.buildSlots(base, lockManager);
    }

    private LeaseRecoveryStep step(LeaseRecoveryStep.AbandonmentEvidence evidence) {
        return new LeaseRecoveryStep(List.of(store), evidence, CLOCK);
    }

    private static String report(Outcome outcome) {
        return assertInstanceOf(Outcome.Done.class, outcome).report();
    }

    @Test
    @DisplayName("is the leases step")
    void shouldBeLeasesStep() {
        assertEquals(RecoveryStep.Name.LEASES, step(LeaseRecoveryStep.AbandonmentEvidence.NONE).name());
    }

    @Test
    @DisplayName("orphans only the leases of the recovered project; those of another project stay as they were")
    void shouldOrphanOnlyLeasesOfProject() {
        var own = claim(store, "slot-a", PROJECT_A, RecoveryStepFixtures.dead());
        var foreign = claim(store, "slot-b", PROJECT_B, RecoveryStepFixtures.dead());

        var outcome = step(LeaseRecoveryStep.AbandonmentEvidence.NONE).run(PROJECT_A);

        assertAll(
                () -> assertEquals(List.of(own.withOwner(own.owner().orphaned(NOW)), foreign), store.snapshot()),
                () -> assertTrue(report(outcome).contains("0 kept, 1 orphaned, 0 removed (all_kept)"),
                        report(outcome)));
    }

    @Test
    @DisplayName("without evidence nothing is removed")
    void shouldRemoveNothingWithoutEvidence() {
        claim(store, "slot-a1", PROJECT_A, RecoveryStepFixtures.dead());
        claim(store, "slot-a2", PROJECT_A, RecoveryStepFixtures.dead());

        step(LeaseRecoveryStep.AbandonmentEvidence.NONE).run(PROJECT_A);

        assertAll(
                () -> assertEquals(2, store.snapshot().size()),
                () -> assertTrue(store.snapshot().stream().allMatch(lease -> lease.owner().isOrphaned())));
    }

    @Test
    @DisplayName("with evidence of abandonment the lease of the project is removed, that of another project not")
    void shouldRemoveAbandonedLeaseOfProject() {
        claim(store, "slot-a", PROJECT_A, RecoveryStepFixtures.dead());
        var foreign = claim(store, "slot-b", PROJECT_B, RecoveryStepFixtures.dead());
        var askedFor = new ArrayList<String>();

        var outcome = step((projectId, lease) -> {
            askedFor.add(projectId + " " + lease.key());
            return true;
        }).run(PROJECT_A);

        assertAll(
                () -> assertEquals(List.of(foreign), store.snapshot()),
                () -> assertEquals(List.of(PROJECT_A + " slot-a"), askedFor),
                () -> assertTrue(report(outcome).contains("0 kept, 0 orphaned, 1 removed"), report(outcome)));
    }

    @Test
    @DisplayName("a lease whose holder is alive is kept")
    void shouldKeepLiveHolder() {
        var live = claim(store, "slot-a", PROJECT_A, RecoveryStepFixtures.self());

        var outcome = step((_, _) -> true).run(PROJECT_A);

        assertAll(
                () -> assertEquals(List.of(live), store.snapshot()),
                () -> assertTrue(report(outcome).contains("1 kept, 0 orphaned, 0 removed (all_kept)"),
                        report(outcome)));
    }

    @Test
    @DisplayName("a store that does not exist is reported as root_absent and the step is done")
    void shouldReportAbsentStore() {
        var absent = report(step(LeaseRecoveryStep.AbandonmentEvidence.NONE).run(PROJECT_A));

        assertTrue(absent.endsWith("0 kept, 0 orphaned, 0 removed (root_absent)"), absent);
    }

    @Test
    @DisplayName("an empty store is reported with its counts and the step is done")
    void shouldReportEmptyStore() {
        var own = claim(store, "slot-a", PROJECT_A, RecoveryStepFixtures.self());
        store.release(own.key(), own.owner());

        var empty = report(step(LeaseRecoveryStep.AbandonmentEvidence.NONE).run(PROJECT_A));

        assertTrue(empty.contains("0 kept, 0 orphaned, 0 removed"), empty);
    }

    @Test
    @DisplayName("a refused store fails the step with its name and refusal, and the following stores are not swept")
    void shouldFailOnRefusedStore() throws Exception {
        var following = new LeaseStore(new AtomicStoreFile(base.resolve("state").resolve("model-slots.json"),
                LockKey.modelSlots(base)), lockManager);
        var untouched = claim(following, "model-a", PROJECT_A, RecoveryStepFixtures.dead());
        Files.writeString(store.store(), "{\"format_version\":1,\"leases\":[", StandardCharsets.UTF_8);

        var outcome = new LeaseRecoveryStep(List.of(store, following), LeaseRecoveryStep.AbandonmentEvidence.NONE,
                CLOCK).run(PROJECT_A);

        assertAll(
                () -> assertEquals(new Outcome.Failed("lease store " + store.store() + " refused: UNREADABLE"),
                        outcome),
                () -> assertEquals(List.of(untouched), following.snapshot()));
    }

    @Test
    @DisplayName("without a lease store there is nothing to sweep")
    void shouldReportNoStore() {
        var outcome = new LeaseRecoveryStep(List.of(), LeaseRecoveryStep.AbandonmentEvidence.NONE, CLOCK)
                .run(PROJECT_A);

        assertEquals(new Outcome.Done("no lease store"), outcome);
    }
}
