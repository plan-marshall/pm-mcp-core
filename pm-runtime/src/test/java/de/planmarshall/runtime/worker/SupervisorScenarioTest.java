/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The supervisor scenarios of the package Done When (PM-WORK-3, Detection and Supervision): every deadline is driven by
 * the virtual clock and asserted at deadline−1, at the deadline and after it, so no scenario sleeps. The consultation
 * and resume scenarios read the fixture harness's scripted stream rather than assuming the answer.
 */
@DisplayName("Supervisor scenarios")
class SupervisorScenarioTest {

    private static final Instant NOW = SupervisorTestFixture.NOW;

    private static final String TASK = "qt-0123456789abcdef";

    private static final int ACK_SECONDS = 10;

    private static final int BOUNDED_WAIT_SECONDS = 30;

    private static final int GRACE_SECONDS = 10;

    private static final int SILENCE_SECONDS = BOUNDED_WAIT_SECONDS + GRACE_SECONDS;

    @TempDir
    Path base;

    @Test
    @DisplayName("the acknowledgement signal fires at its deadline and settles after")
    void acknowledgementAtTheDeadline() throws IOException {
        var harness = FixtureHarness.create(base);
        var detection = harness.detection(RoleClass.WRITER, parameters(OptionalInt.of(ACK_SECONDS), false));
        detection.started(NOW);
        detection.offered(TASK, NOW);
        var deadline = NOW.plusSeconds(ACK_SECONDS);

        harness.clock().moveTo(deadline.minusMillis(1));
        var before = harness.signals().size();
        harness.clock().moveTo(deadline);
        var at = harness.signals().size();
        harness.clock().moveTo(deadline.plusSeconds(1));
        var after = harness.signals().size();

        assertAll(
                () -> assertEquals(0, before, "nothing fires a millisecond before the deadline"),
                () -> assertEquals(1, at, "the signal fires at the deadline"),
                () -> assertEquals(LossReason.ACK_MISSED, harness.signals().getFirst().reason()),
                () -> assertEquals(TASK, harness.signals().getFirst().leasedTask()),
                () -> assertEquals(1, after, "a settled detection fires no second signal"));
    }

    @Test
    @DisplayName("the silence signal fires at its deadline and settles after")
    void silenceAtTheDeadline() throws IOException {
        var harness = FixtureHarness.create(base);
        var detection = harness.detection(RoleClass.WRITER, parameters(OptionalInt.empty(), false));
        detection.started(NOW);
        detection.firstCall(NOW);
        var deadline = NOW.plusSeconds(SILENCE_SECONDS);

        harness.clock().moveTo(deadline.minusMillis(1));
        var before = harness.signals().size();
        harness.clock().moveTo(deadline);
        var at = harness.signals().size();
        harness.clock().moveTo(deadline.plusSeconds(1));
        var after = harness.signals().size();

        assertAll(
                () -> assertEquals(0, before, "nothing fires a millisecond before the deadline"),
                () -> assertEquals(1, at, "the signal fires at the deadline"),
                () -> assertEquals(LossReason.SILENCE, harness.signals().getFirst().reason()),
                () -> assertEquals(1, after, "a settled detection fires no second signal"));
    }

    @Test
    @DisplayName("a held consultation suppresses the silence until the scripted answer closes it")
    void aConsultationSuppressesSilence() throws IOException {
        var harness = FixtureHarness.create(base);
        harness.script("answer: continue");
        var detection = harness.detection(RoleClass.WRITER, parameters(OptionalInt.empty(), false));
        detection.started(NOW);
        detection.firstCall(NOW);
        detection.waitOpened(NOW);

        harness.advance(Duration.ofSeconds(SILENCE_SECONDS * 2L));
        var held = harness.signals().size();
        detection.waitClosed(harness.clock().instant());
        harness.advance(Duration.ofSeconds(SILENCE_SECONDS));

        assertAll(
                () -> assertEquals(0, held, "a held wait is an open call and is never silent"),
                () -> assertEquals(1, harness.signals().size(), "the answer re-arms the silence deadline"),
                () -> assertEquals("answer: continue", harness.nextScriptedLine(),
                        "the consultation reads the harness's scripted answer"));
    }

    @Test
    @DisplayName("a resumed generation reads the scripted stream and is recorded by the harness")
    void aResumedGenerationIsScripted() throws IOException {
        var harness = FixtureHarness.create(base);
        harness.script("resume: session-42");
        var role = harness.fixture().role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var supervisor = harness.supervisor();

        var outcomes = supervisor.start(List.of(harness.fixture().freshRequest(role)));

        var started = assertInstanceOf(StartOutcome.Started.class, outcomes.getFirst());
        harness.fixture().protocol.agentStarted(started.generation().generationId(), TASK, 1, "/usr/bin/claude",
                harness.clock().instant());
        harness.advance(Duration.ofSeconds(1));

        assertAll(
                () -> assertEquals("resume: session-42", harness.nextScriptedLine(),
                        "the resume reads the harness's scripted stream"),
                () -> assertEquals(1, harness.fixture().protocol.startedAgents.size(),
                        "the agent start is recorded"),
                () -> assertEquals(started.generation().generationId(),
                        harness.fixture().protocol.startedAgents.getFirst().generationId(),
                        "the recorded start belongs to the generation"),
                () -> assertEquals(1, harness.fixture().starter.starts.size(), "one worker started"),
                () -> assertAll("no loss fired", () -> assertEquals(0, harness.signals().size())));
    }

    private static WorkerParameters parameters(OptionalInt ackDeadline, boolean informational) {
        return new WorkerParameters(true, ackDeadline, informational, GRACE_SECONDS, 3, BOUNDED_WAIT_SECONDS, true);
    }
}
