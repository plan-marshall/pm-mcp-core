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

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one machine scope of the filesystem specification (Workflow DSL, Machine Scope;
 * {@code filesystem-layout/02-machine-runtime.adoc}): its journal scope lies in {@code machine/}, its workflow state in
 * {@code machine/mcp/status.json} under {@code machine/locks/machine.lock}, and the generic stores of the scope resolve
 * directly below {@code machine/}.
 */
@DisplayName("Machine scope stores")
class MachineScopeStoreTest {

    private static final Path BASE = Path.of("/tmp/pm-mcp-core-test/machine-scope").toAbsolutePath().normalize();

    private final JournalScope machine = JournalScope.machine(BASE);

    @Test
    @DisplayName("Should resolve the machine scope to machine/ with no scope id and the machine lock")
    void journalScope() {
        assertAll(
                () -> assertEquals(JournalScopeType.MACHINE, machine.type()),
                () -> assertNull(machine.scopeId()),
                () -> assertEquals(BASE.resolve("machine"), machine.pathBase()),
                () -> assertEquals(BASE.resolve("machine/mcp/transaction-journal.json"), machine.journalFile()),
                () -> assertEquals(BASE.resolve("machine/mcp/digests.json"), machine.sidecarFile()),
                () -> assertEquals(LockLevel.WORKSPACE, machine.lockKey().level()),
                () -> assertEquals(BASE.resolve("machine/locks/machine.lock"), machine.lockKey().lockFile()));
    }

    @Test
    @DisplayName("Should write the workflow state to machine/mcp/status.json")
    void statusStore() {
        assertEquals(BASE.resolve("machine/mcp/status.json"), new WorkflowStatusStore(machine).store());
    }

    @Test
    @DisplayName("Should resolve the ledger, metrics and question stores directly below machine/")
    void genericStores() {
        assertAll(
                () -> assertEquals(BASE.resolve("machine/change-ledger.jsonl"), new ChangeLedger(machine).store()),
                () -> assertEquals(BASE.resolve("machine/metrics.json"), new MetricsStore(machine).store()),
                () -> assertEquals(BASE.resolve("machine/questions.jsonl"), new QuestionLog(machine).store()));
    }

    @Test
    @DisplayName("Should keep the machine scope out of the project stores")
    void notInAProject() {
        assertAll(
                () -> assertEquals("machine", JournalScopeType.MACHINE.wireName()),
                () -> assertFalse(JournalScopeType.MACHINE.scopeIdRequired()),
                () -> assertEquals(BASE.resolve("machine"), JournalScope.machine(BASE).pathBase()));
    }
}
