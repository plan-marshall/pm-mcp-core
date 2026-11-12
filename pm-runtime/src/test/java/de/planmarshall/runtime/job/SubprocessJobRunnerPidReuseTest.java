/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.function.LongFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.JobId;

/**
 * The PID-reuse decision of the runner and the termination it decides (termination step 3a, ADR 010): a PID whose start
 * instant cannot be read is treated as reused, whether the unreadable instant is the launched process's or the one the
 * PID answers now, and the only safe end of such a process is the one through its handle — never a numeric signal, which
 * could reach a process that took the PID. The decision is taken over a seam for the handle lookup because a platform
 * that reports a start instant for every listed process cannot produce an unreadable one with a real handle; the
 * production path of the seam is {@link java.lang.ProcessHandle#of(long)}.
 * <p>
 * The data are generated: the PIDs and the start instants. The literals that stay are the contract — the signals the
 * termination protocol would send and the handle calls it replaced.
 * <p>
 * The class builds the runner it needs itself; it takes no helper from {@link SubprocessJobRunnerTest}.
 */
@EnableGeneratorController
@DisplayName("PID reuse of a started process")
class SubprocessJobRunnerPidReuseTest {

    private JobEnrolmentFixture fixture;
    private SubprocessJobRunner runner;
    private ProcessGroupSignals signalling;
    private long pid;
    private Instant start;
    private Instant later;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new JobEnrolmentFixture();
        pid = Generators.longs(2, 4_000_000).next();
        start = Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());
        later = start.plusSeconds(Generators.longs(1, 86_400).next());
        signalling = createMock(ProcessGroupSignals.class);
        replay(signalling);
        runner = runner(fixture.base);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private SubprocessJobRunner runner(Path base) {
        return SubprocessJobRunner.builder().base(base).lockManager(fixture.locks)
                .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(base.resolve("home")))
                .termination(new TerminationProtocol(signalling))
                .parser((kind, output) -> {
                    throw new AssertionError("the PID reuse decision parses no job log");
                })
                .primitives(request -> Optional.empty()).build();
    }

    /** @return a process whose own start instant the test answers, behind the handle it reports */
    private static Process process(Optional<Instant> ownStartInstant) {
        ProcessHandle.Info info = createMock(ProcessHandle.Info.class);
        expect(info.startInstant()).andReturn(ownStartInstant);
        ProcessHandle handle = createMock(ProcessHandle.class);
        expect(handle.info()).andReturn(info);
        Process process = createMock(Process.class);
        expect(process.toHandle()).andReturn(handle);
        replay(info, handle, process);
        return process;
    }

    /** @return the handle a PID lookup answers, with the start instant the test answers for it */
    private static ProcessHandle handle(Optional<Instant> startInstant) {
        ProcessHandle.Info info = createMock(ProcessHandle.Info.class);
        expect(info.startInstant()).andReturn(startInstant);
        ProcessHandle handle = createMock(ProcessHandle.class);
        expect(handle.info()).andReturn(info);
        replay(info, handle);
        return handle;
    }

    /** @return the decision over the launched process and the handle the PID lookup answers */
    private boolean reused(Process process, Optional<Instant> currentStartInstant) {
        LongFunction<Optional<ProcessHandle>> handles = ignored -> Optional.of(handle(currentStartInstant));
        return SubprocessJobRunner.pidReused(new PmExecLauncher.Launched(process, pid), handles);
    }

    @Test
    @DisplayName("an unreadable instant of the launched process answers reused")
    void ownInstantUnreadable() {
        var reused = reused(process(Optional.empty()), Optional.of(start));

        assertTrue(reused, "an unreadable own start instant is a reused PID");
    }

    @Test
    @DisplayName("an unreadable instant of the PID's current process answers reused")
    void currentInstantUnreadable() {
        var reused = reused(process(Optional.of(start)), Optional.empty());

        assertTrue(reused, "an unreadable current start instant is a reused PID");
    }

    @Test
    @DisplayName("both instants readable and equal answer not reused")
    void equalInstants() {
        var reused = reused(process(Optional.of(start)), Optional.of(start));

        assertFalse(reused, "the same start instant twice is the started process");
    }

    @Test
    @DisplayName("readable instants that differ answer reused")
    void differingInstants() {
        var reused = reused(process(Optional.of(later)), Optional.of(start));

        assertTrue(reused, "another start instant is a reused PID");
    }

    @Test
    @DisplayName("an unreadable instant signals nothing and destroys the process through its handle")
    void unreadableInstantEndsThroughTheHandle() {
        ProcessHandle.Info info = createMock(ProcessHandle.Info.class);
        expect(info.startInstant()).andReturn(Optional.empty());
        ProcessHandle handle = createMock(ProcessHandle.class);
        expect(handle.info()).andReturn(info);
        Process process = createMock(Process.class);
        expect(process.toHandle()).andReturn(handle);
        expect(process.destroyForcibly()).andReturn(process);
        replay(info, handle, process);
        var launched = new PmExecLauncher.Launched(process, ProcessHandle.current().pid());

        var cancelBeforeGroup = runner.terminateByHandle(JobId.random(JobId.Kind.GIT), launched);

        assertAll(
                () -> assertFalse(cancelBeforeGroup, "an unreadable instant reaches no group"),
                () -> assertDoesNotThrow(() -> verify(process), "the process is destroyed through its handle"),
                () -> assertDoesNotThrow(() -> verify(signalling), "no numeric signal is sent by the termination"));
    }
}
