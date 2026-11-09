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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The protocol words of the wait-answer reasons: the wire name of each enum constant is the word the transport hands to a
 * worker, so a typo in one changes the protocol silently. The guide no longer lists the reasons; it links these enums, so
 * this test is where the words stay pinned.
 */
// Every datum in this class is a contract: the protocol words a worker reads.
@DisplayName("WaitAnswer reasons")
class WaitAnswerReasonsTest {

    @Test
    @DisplayName("the wait-again reasons carry their protocol words")
    void waitAgainReasonNames() {
        assertAll(
                () -> assertEquals("no_task", WaitAnswer.WaitAgainReason.NO_TASK.wireName(),
                        "NO_TASK answers no_task"),
                () -> assertEquals("paused", WaitAnswer.WaitAgainReason.PAUSED.wireName(),
                        "PAUSED answers paused"),
                () -> assertEquals("consulting", WaitAnswer.WaitAgainReason.CONSULTING.wireName(),
                        "CONSULTING answers consulting"),
                () -> assertEquals("awaiting_answer", WaitAnswer.WaitAgainReason.AWAITING_ANSWER.wireName(),
                        "AWAITING_ANSWER answers awaiting_answer"),
                () -> assertEquals("attempts_spent", WaitAnswer.WaitAgainReason.ATTEMPTS_SPENT.wireName(),
                        "ATTEMPTS_SPENT answers attempts_spent"));
    }

    @Test
    @DisplayName("the end reasons carry their protocol words")
    void endReasonNames() {
        assertAll(
                () -> assertEquals("job_ended", WaitAnswer.EndReason.JOB_ENDED.wireName(),
                        "JOB_ENDED answers job_ended"),
                () -> assertEquals("fenced", WaitAnswer.EndReason.FENCED.wireName(),
                        "FENCED answers fenced"),
                () -> assertEquals("recycled", WaitAnswer.EndReason.RECYCLED.wireName(),
                        "RECYCLED answers recycled"));
    }
}
