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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.RoleClass;

/**
 * The supervisor scenario harness (PM-WORK-3, Done When): the real supervisor environment over the test fixture, a
 * {@link VirtualClock} in place of the wall clock and the host's timers, the loss signals a scenario observes, and the
 * scripted stream a consultation or resume scenario reads its answer from.
 * <p>
 * A scenario drives real supervisor and detector values — no wall-clock sleep and no timer thread — so every assertion
 * is made at a clock position the scenario chose: before a deadline, at it, and after it. The scripted stream stands in
 * for the harness's output on a consultation or a resume: the scenario scripts the answer and the scenario reads it
 * through {@link #nextScriptedLine()}, so the harness's stream is observed rather than assumed.
 */
final class FixtureHarness {

    private static final SupervisorEnvironment.StartGuard OPEN_GUARD = SupervisorEnvironment.StartGuard.open();

    private static final SupervisorEnvironment.EligibilityGate OPEN_GATE = SupervisorEnvironment.EligibilityGate.open();

    private static final String GENERATION = "l-0123456789abcdef";

    private final SupervisorTestFixture fixture;

    private final VirtualClock clock;

    private final List<LossSignal> signals = new CopyOnWriteArrayList<>();

    private final List<String> script = new CopyOnWriteArrayList<>();

    private FixtureHarness(SupervisorTestFixture fixture, VirtualClock clock) {
        this.fixture = fixture;
        this.clock = clock;
    }

    /**
     * @param base the temporary base of the fixture
     * @return the harness over a fresh fixture
     * @throws IOException if the fixture cannot create its directories
     */
    static FixtureHarness create(Path base) throws IOException {
        var fixture = new SupervisorTestFixture(base);
        var clock = new VirtualClock(SupervisorTestFixture.NOW);
        fixture.timers = clock;
        return new FixtureHarness(fixture, clock);
    }

    /** @return the fixture the harness is built on */
    SupervisorTestFixture fixture() {
        return fixture;
    }

    /** @return the virtual clock and timers */
    VirtualClock clock() {
        return clock;
    }

    /** @return a supervisor over the harness's environment */
    WorkerSupervisor supervisor() {
        return new WorkerSupervisor(fixture.environment(OPEN_GUARD, OPEN_GATE));
    }

    /** @return the loss signals the scenarios observed, in the order they fired */
    List<LossSignal> signals() {
        return List.copyOf(signals);
    }

    /**
     * @param roleClass  the class of the role
     * @param parameters the harness's worker values
     * @return a loss detector over the virtual clock and the harness's signal sink
     */
    LossDetection detection(RoleClass roleClass, WorkerParameters parameters) {
        return new LossDetection(GENERATION, roleClass, parameters, clock, clock, signals::add);
    }

    /**
     * @param duration the time to move the clock forward
     */
    void advance(Duration duration) {
        clock.advance(duration);
    }

    /**
     * @param line one line the scripted harness stream yields
     */
    void script(String line) {
        script.add(line);
    }

    /**
     * @return the next line of the scripted stream, or {@code null} when it is empty
     */
    String nextScriptedLine() {
        return script.isEmpty() ? null : script.removeFirst();
    }
}
