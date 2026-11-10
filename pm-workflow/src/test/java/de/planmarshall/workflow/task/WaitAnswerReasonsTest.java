/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The protocol words of the task vocabulary: the wire name of each enum constant is the word the transport hands to a
 * worker, so a typo in one changes the protocol silently. The developer guide no longer lists the refusals or the reasons;
 * it links these types, so this test is where the words stay pinned.
 */
// Every datum in this class is a contract: the protocol words a worker reads.
@DisplayName("Task protocol vocabulary")
class WaitAnswerReasonsTest {

    @Test
    @DisplayName("the wait-again reasons carry their protocol words")
    void waitAgainReasonNames() {
        var words = Arrays.stream(WaitAnswer.WaitAgainReason.values())
                .map(WaitAnswer.WaitAgainReason::wireName).toList();

        assertEquals(List.of("no_task", "paused", "consulting", "awaiting_answer", "attempts_spent"), words,
                "the wait-again reasons carry their protocol words");
    }

    @Test
    @DisplayName("the end reasons carry their protocol words")
    void endReasonNames() {
        var words = Arrays.stream(WaitAnswer.EndReason.values()).map(WaitAnswer.EndReason::wireName).toList();

        assertEquals(List.of("job_ended", "fenced", "recycled"), words, "the end reasons carry their protocol words");
    }

    @Test
    @DisplayName("the task refusals carry their protocol words, in check order")
    void refusalNames() {
        var words = Arrays.stream(TaskRefusal.values()).map(TaskRefusal::wireName).toList();

        assertEquals(List.of("job_ended", "generation_fenced", "stale_entity", "job_scope_mismatch",
                        "link_not_available", "invalid_link_args", "skills_not_loaded"), words,
                "the task refusals carry their protocol words");
    }
}
