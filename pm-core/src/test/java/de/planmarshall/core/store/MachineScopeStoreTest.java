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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one machine scope of the filesystem specification (Workflow DSL, Machine Scope;
 * {@code filesystem-layout/02-machine-runtime.adoc}): its journal scope lies in {@code machine/}, its workflow state in
 * {@code machine/mcp/status.json} under {@code machine/locks/machine.lock}, and the generic stores of the scope resolve
 * directly below {@code machine/}. The store file names below {@code machine/} are the layout contract.
 */
@EnableGeneratorController
@DisplayName("Machine scope stores")
class MachineScopeStoreTest {

    private Path base;

    private JournalScope machine;

    @BeforeEach
    void createScope() {
        base = Path.of("/tmp", "pm-" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT))
                .toAbsolutePath().normalize();
        machine = JournalScope.machine(base);
    }

    @Test
    @DisplayName("Should resolve the machine scope to machine/ with no scope id and the machine lock")
    void journalScope() {
        assertAll(
                () -> assertEquals(JournalScopeType.MACHINE, machine.type(), "the scope should be the machine scope"),
                () -> assertNull(machine.scopeId(), "the machine scope should carry no scope id"),
                () -> assertEquals(base.resolve("machine"), machine.pathBase(),
                        "the path base should be machine/ below the base"),
                () -> assertEquals(base.resolve("machine/mcp/transaction-journal.json"), machine.journalFile(),
                        "the journal should lie in machine/mcp/"),
                () -> assertEquals(base.resolve("machine/mcp/digests.json"), machine.sidecarFile(),
                        "the digest sidecar should lie in machine/mcp/"),
                () -> assertEquals(LockLevel.WORKSPACE, machine.lockKey().level(),
                        "the machine lock should be of the workspace level"),
                () -> assertEquals(base.resolve("machine/locks/machine.lock"), machine.lockKey().lockFile(),
                        "the lock file should be machine/locks/machine.lock"));
    }

    @Test
    @DisplayName("Should write the workflow state to machine/mcp/status.json")
    void statusStore() {
        assertEquals(base.resolve("machine/mcp/status.json"), new WorkflowStatusStore(machine).store(),
                "the workflow state should lie in machine/mcp/");
    }

    @Test
    @DisplayName("Should resolve the ledger, metrics and question stores directly below machine/")
    void genericStores() {
        assertAll(
                () -> assertEquals(base.resolve("machine/change-ledger.jsonl"), new ChangeLedger(machine).store(),
                        "the change ledger should lie below machine/"),
                () -> assertEquals(base.resolve("machine/metrics.json"), new MetricsStore(machine).store(),
                        "the metrics store should lie below machine/"),
                () -> assertEquals(base.resolve("machine/questions.jsonl"), new QuestionLog(machine).store(),
                        "the question log should lie below machine/"));
    }

    @Test
    @DisplayName("Should write and read the machine status with its onboarding members, whose names are wire names")
    void statusDocument() {
        var run = "\"" + Generators.letterStrings(1, 20).next() + "\"";
        // JSON wire document of the onboarding steps, kept as the retained member stores it.
        var steps = "[{\"step\": \"language\", \"state\": \"done\"}]";
        var status = WorkflowStatus.builder()
                .scopeType(JournalScopeType.MACHINE)
                .phase("machine")
                .state("onboard.completed")
                .callStack(List.of())
                .policyCounters(Map.of())
                .waiverRequests(List.of())
                .retryCounters(RetryCounters.empty())
                .activeJobs(Map.of())
                .activeTasks(Map.of())
                .updatedAt(Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next()))
                .retained(Map.of("onboard_run", run, "onboard_steps", steps))
                .build();

        var read = WorkflowStatusCodec.read(Path.of("status.json"), WorkflowStatusCodec.write(status));

        assertAll(
                () -> assertEquals(JournalScopeType.MACHINE, read.scopeType(), "the scope type should round-trip"),
                () -> assertEquals("machine", read.phase(), "the machine phase should round-trip"),
                () -> assertEquals(run, read.retained().get("onboard_run"),
                        "the onboarding run should round-trip verbatim"),
                () -> assertEquals(steps, read.retained().get("onboard_steps"),
                        "the onboarding steps should round-trip verbatim"));
    }

    @Test
    @DisplayName("Should keep the machine scope out of the project stores")
    void notInAProject() {
        assertAll(
                () -> assertEquals("machine", JournalScopeType.MACHINE.wireName(),
                        "the wire name of the scope should be machine"),
                () -> assertFalse(JournalScopeType.MACHINE.scopeIdRequired(),
                        "the machine scope should need no scope id"),
                () -> assertEquals(base.resolve("machine"), JournalScope.machine(base).pathBase(),
                        "the machine scope should lie outside a project"));
    }
}
