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

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * A component that joins the {@link RuntimeDrain}: jobs, workers, the LSP pool and the web listener
 * (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Drain, Stop &amp; Upgrade). The drain calls every
 * participant in the order of the specification:
 * <ol>
 * <li>{@link #stopAdmitting()} and {@link #endIdle()} on every participant;</li>
 * <li>{@link #awaitQuiet(Instant)} up to the drain timeout;</li>
 * <li>{@link #terminateRemaining()} on every participant that did not get quiet in time;</li>
 * <li>{@link #close()} on every participant, in the order they were given to the drain, so the web listener, given
 * last, closes after everything else has ended.</li>
 * </ol>
 * <p>
 * Admission contract: stopping the admission is a check-then-act against a concurrent admission. The drain sets its
 * draining flag atomically before it calls any participant, and a participant takes every admission decision under
 * its own lock and re-reads {@link RuntimeDrain#isDraining()} there, so nothing admitted between the check and the
 * flag outlives the drain unsupervised.
 * <p>
 * A participant that holds build or model slots or task leases releases them in {@link #terminateRemaining()}.
 * Merge-queue and review-bot leases are never released by a drain: they are left in place, become orphaned at the
 * next start and are handled there like those of a crash.
 *
 * @since 0.1
 */
public interface DrainParticipant {

    /**
     * @return the name of the participant in the log and in the drain outcome
     */
    String name();

    /**
     * Stops admitting new work: no job is admitted and no task offered any more.
     *
     * @throws IOException if the participant cannot record the stop
     */
    void stopAdmitting() throws IOException;

    /**
     * Ends what is idle, for example warm workers without a task.
     *
     * @throws IOException if the participant cannot end what is idle
     */
    void endIdle() throws IOException;

    /**
     * Waits until nothing of the participant runs any more, at most until the deadline.
     *
     * @param deadline the end of the drain timeout
     * @return {@code true} when nothing runs any more, {@code false} when the deadline passed first
     * @throws InterruptedException if the wait is interrupted
     * @throws IOException          if the participant cannot observe what runs
     */
    boolean awaitQuiet(Instant deadline) throws InterruptedException, IOException;

    /**
     * Terminates what still runs after the drain timeout, records it {@code killed} and releases its build and model
     * slots and task leases.
     *
     * @return what was terminated and recorded {@code killed}, for the drain outcome
     * @throws IOException if the participant cannot terminate or record what still runs
     */
    List<String> terminateRemaining() throws IOException;

    /**
     * Closes the participant after everything has ended: the LSP pool shuts down its servers, the web listener closes.
     *
     * @throws IOException if the participant cannot close
     */
    void close() throws IOException;
}
