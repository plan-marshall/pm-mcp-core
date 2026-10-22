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

import static de.planmarshall.core.store.QuestionRecordCodecTest.asked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fixtures of the question-log tests (PM-WF-12, PM-IMPL-2): a plan scope with its question log, a transaction per
 * write, the three questions of the set {@code Q-001}, and the bytes of the log. Each test class of the log holds one
 * group of its cases and shares these fixtures.
 */
abstract class QuestionLogFixture {

    static final String PROJECT = "pm-mcp-core";
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    JournalScope scope;
    QuestionLog log;
    RecordingLockManager locks;
    RecordingAuditSink audit;

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, "a-plan");
        Files.createDirectories(scope.journalFile().getParent());
        log = new QuestionLog(scope);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    /**
     * Runs one write of the log in a transaction of its own, and commits it unless the log refused it; a refused
     * write must have added no target.
     */
    Optional<QuestionRefusal> write(Function<ScopeTransaction, Optional<QuestionRefusal>> action) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            var refusal = action.apply(write);
            if (refusal.isEmpty()) {
                write.commit();
            } else {
                assertFalse(write.isTarget(log.store()), "a refused write adds nothing to the transaction");
            }
            return refusal;
        }
    }

    /** Asks the set Q-001 of three questions: the decision on Q-001, the parts Q-002 and Q-003. */
    void askSet() {
        assertEquals(Optional.empty(), write(t -> log.ask(t, List.of(asked("Q-001", "Q-001"),
                asked("Q-002", "Q-001"), asked("Q-003", "Q-001")))));
    }

    byte[] content() throws IOException {
        return Files.readAllBytes(log.store());
    }
}
