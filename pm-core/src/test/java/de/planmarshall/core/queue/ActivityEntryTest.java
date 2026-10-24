/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;

/**
 * An activity {@code signal} entry round-trips, and an {@code agent_started} entry with any action but {@code restart}
 * is refused (Store Schemas § Activity Journal).
 */
@SuppressWarnings("java:S5778")
// the assertion table names one throwing builder call per case, by design
@DisplayName("Activity entry")
class ActivityEntryTest {

    @Test
    @DisplayName("an agent_started signal entry round-trips")
    void agentStarted() {
        var entry = ActivityEntry.builder()
                .seq(4)
                .attempt(2)
                .source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.SIGNAL)
                .signal(new ActivityEntry.Signal(SignalName.AGENT_STARTED, SignalAction.RESTART))
                .at(QueueFixtures.AT)
                .build();

        var read = ActivityEntryCodec.read(Path.of("activity.jsonl"), ActivityEntryCodec.write(entry));

        assertEquals(entry, read);
    }

    @Test
    @DisplayName("an agent_started entry with any other action is refused")
    void agentStartedWithAnotherAction() {
        var refusal = assertThrows(IllegalArgumentException.class,
                () -> new ActivityEntry.Signal(SignalName.AGENT_STARTED, SignalAction.SHOW));

        assertEquals("An 'agent_started' signal always takes the action 'restart', not 'show'",
                refusal.getMessage());
    }

    @Test
    @DisplayName("a kind is recorded by its own source")
    void sourceOfKind() {
        assertThrows(IllegalArgumentException.class, () -> ActivityEntry.builder()
                .seq(1)
                .attempt(1)
                .source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.TEXT)
                .text("text")
                .at(QueueFixtures.AT)
                .build());
    }
}
