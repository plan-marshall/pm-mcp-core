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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * The counts and reason codes in the reports the tests read are the specified outcome format of the step.
 */
@EnableGeneratorController
@DisplayName("Lease recovery step")
class LeaseRecoveryStepTest {

    @TempDir
    Path base;

    private final Instant now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private FileLockManager lockManager;
    private LeaseStore store;

    @BeforeEach
    void createStore() throws IOException {
        lockManager = new FileLockManager(new RecordingAuditSink());
        store = RecoveryStepFixtures.buildSlots(base, lockManager);
    }

    private LeaseRecoveryStep step(LeaseRecoveryStep.AbandonmentEvidence evidence) {
        return new LeaseRecoveryStep(List.of(store), evidence, clock);
    }

    private static String report(Outcome outcome) {
        return assertInstanceOf(Outcome.Done.class, outcome, "the step must be done").report();
    }

    /** @return a generated slot key that sorts by its prefix */
    private static String slot(String prefix) {
        return "slot-" + prefix + "-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("is the leases step")
    void shouldBeLeasesStep() {
        assertEquals(RecoveryStep.Name.LEASES, step(LeaseRecoveryStep.AbandonmentEvidence.NONE).name(),
                "the step must carry its specified name");
    }

    @Test
    @DisplayName("orphans only the leases of the recovered project; those of another project stay as they were")
    void shouldOrphanOnlyLeasesOfProject() {
        var own = claim(store, slot("a"), PROJECT_A, RecoveryStepFixtures.dead());
        var foreign = claim(store, slot("b"), PROJECT_B, RecoveryStepFixtures.dead());

        var outcome = step(LeaseRecoveryStep.AbandonmentEvidence.NONE).run(PROJECT_A);

        assertAll(
                () -> assertEquals(List.of(own.withOwner(own.owner().orphaned(now)), foreign), store.snapshot(),
                        "only the lease of the project must be orphaned"),
                () -> assertTrue(report(outcome).contains("0 kept, 1 orphaned, 0 removed (all_kept)"),
                        "the report must count the orphaned lease"));
    }

    @Test
    @DisplayName("without evidence nothing is removed")
    void shouldRemoveNothingWithoutEvidence() {
        claim(store, slot("a"), PROJECT_A, RecoveryStepFixtures.dead());
        claim(store, slot("b"), PROJECT_A, RecoveryStepFixtures.dead());

        step(LeaseRecoveryStep.AbandonmentEvidence.NONE).run(PROJECT_A);

        assertAll(
                () -> assertEquals(2, store.snapshot().size(), "without evidence no lease is removed"),
                () -> assertTrue(store.snapshot().stream().allMatch(lease -> lease.owner().isOrphaned()),
                        "every lease of the project must be orphaned"));
    }

    @Test
    @DisplayName("with evidence of abandonment the lease of the project is removed, that of another project not")
    void shouldRemoveAbandonedLeaseOfProject() {
        var ownKey = slot("a");
        claim(store, ownKey, PROJECT_A, RecoveryStepFixtures.dead());
        var foreign = claim(store, slot("b"), PROJECT_B, RecoveryStepFixtures.dead());
        var askedFor = new ArrayList<String>();

        var outcome = step((projectId, lease) -> {
            askedFor.add(projectId + " " + lease.key());
            return true;
        }).run(PROJECT_A);

        assertAll(
                () -> assertEquals(List.of(foreign), store.snapshot(), "only the lease of another project stays"),
                () -> assertEquals(List.of(PROJECT_A + " " + ownKey), askedFor,
                        "the evidence must be asked for the own lease"),
                () -> assertTrue(report(outcome).contains("0 kept, 0 orphaned, 1 removed"),
                        "the report must count the removed lease"));
    }

    @Test
    @DisplayName("a lease whose holder is alive is kept")
    void shouldKeepLiveHolder() {
        var live = claim(store, slot("a"), PROJECT_A, RecoveryStepFixtures.self());

        var outcome = step((_, _) -> true).run(PROJECT_A);

        assertAll(
                () -> assertEquals(List.of(live), store.snapshot(), "the lease of a live holder must stay"),
                () -> assertTrue(report(outcome).contains("1 kept, 0 orphaned, 0 removed (all_kept)"),
                        "the report must count the kept lease"));
    }

    @Test
    @DisplayName("a store that does not exist is reported as root_absent and the step is done")
    void shouldReportAbsentStore() {
        var absent = report(step(LeaseRecoveryStep.AbandonmentEvidence.NONE).run(PROJECT_A));

        assertTrue(absent.endsWith("0 kept, 0 orphaned, 0 removed (root_absent)"),
                "the report must name the absent store");
    }

    @Test
    @DisplayName("an empty store is reported with its counts and the step is done")
    void shouldReportEmptyStore() {
        var own = claim(store, slot("a"), PROJECT_A, RecoveryStepFixtures.self());
        store.release(own.key(), own.owner());

        var empty = report(step(LeaseRecoveryStep.AbandonmentEvidence.NONE).run(PROJECT_A));

        assertTrue(empty.contains("0 kept, 0 orphaned, 0 removed"), "the report must count an empty store");
    }

    @Test
    @DisplayName("a refused store fails the step with its name and refusal, and the following stores are not swept")
    void shouldFailOnRefusedStore() throws Exception {
        // Contract literals: the directory and the file name of the model-slot lease store the runtime prescribes
        var following = new LeaseStore(new AtomicStoreFile(base.resolve("state").resolve("model-slots.json"),
                LockKey.modelSlots(base)), lockManager);
        var untouched = claim(following, "model-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT),
                PROJECT_A, RecoveryStepFixtures.dead());
        // Contract literal: a torn lease store, cut inside its lease array
        Files.writeString(store.store(), "{\"format_version\":1,\"leases\":[", StandardCharsets.UTF_8);

        var outcome = new LeaseRecoveryStep(List.of(store, following), LeaseRecoveryStep.AbandonmentEvidence.NONE,
                clock).run(PROJECT_A);

        assertAll(
                () -> assertEquals(new Outcome.Failed("lease store " + store.store() + " refused: UNREADABLE"),
                        outcome, "the failure must name the refused store"),
                () -> assertEquals(List.of(untouched), following.snapshot(),
                        "the following store must not be swept"));
    }

    @Test
    @DisplayName("without a lease store there is nothing to sweep")
    void shouldReportNoStore() {
        var outcome = new LeaseRecoveryStep(List.of(), LeaseRecoveryStep.AbandonmentEvidence.NONE, clock)
                .run(PROJECT_A);

        assertEquals(new Outcome.Done("no lease store"), outcome, "the step must report no lease store");
    }
}
