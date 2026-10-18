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

/**
 * The lowest signalling primitive of the job runtime (PM-EXEC-4, PM-TECH-4): {@code kill(2)} against a job's process
 * group ({@code kill(-pgid, sig)}) or its PID ({@code kill(pid, sig)}), answering what the call answered as a value:
 * {@link Delivered}, {@link NoSuchProcess} for {@code ESRCH}, or {@link Refused} with any other {@code errno}.
 * <p>
 * Everything that signals a job goes through this interface, so a test replaces it with a recording fake and asserts
 * at the lowest signalling call, also that none was made. {@link #system()} is the binding of the C library's
 * {@code kill} through FFM (no JNI library).
 * <p>
 * Native image: the foreign downcall descriptor of {@code kill} (two {@code int} parameters, an {@code int} result,
 * {@code errno} captured, so the entry carries the linker option {@code captureCallState}) is registered in the
 * reachability metadata of this module,
 * {@code META-INF/native-image/de.planmarshall/pm-runtime-credentials/reachability-metadata.json}, next to the
 * descriptors of the FFM bindings of the credentials package. It is verified by the native build of {@code pm-mcpd} in
 * the repository {@code plan-marshall-mcp} against the deployed {@code SNAPSHOT}.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Process Termination &
 * Cancellation Protocol).
 *
 * @since 0.1
 */
public interface ProcessGroupSignals {

    /**
     * The signals the runtime sends to a job. The numbers are those of Linux and macOS.
     *
     * @since 0.1
     */
    enum Signal {
        /** No signal: only whether the target exists is checked. */
        PROBE(0),
        /** {@code SIGKILL}. */
        SIGKILL(9),
        /** {@code SIGTERM}. */
        SIGTERM(15);

        private final int number;

        Signal(int number) {
            this.number = number;
        }

        /** @return the number of the signal */
        public int number() {
            return number;
        }
    }

    /**
     * What a {@code kill} call answered.
     *
     * @since 0.1
     */
    sealed interface Answer permits Delivered, NoSuchProcess, Refused {
    }

    /**
     * The call succeeded: the signal was delivered, or for {@link Signal#PROBE} the target exists.
     *
     * @since 0.1
     */
    record Delivered() implements Answer {
    }

    /**
     * The call failed with {@code ESRCH}: no process or process group of that id exists.
     *
     * @since 0.1
     */
    record NoSuchProcess() implements Answer {
    }

    /**
     * The call failed with another {@code errno}, for example {@code EPERM}.
     *
     * @param errno the {@code errno} of the call
     * @since 0.1
     */
    record Refused(int errno) implements Answer {
    }

    /**
     * Sends a signal to the process group of a job: {@code kill(-pgid, signal)}.
     *
     * @param pgid   the process-group id, which is the job PID
     * @param signal the signal
     * @return what the call answered
     * @throws IllegalArgumentException if the id is no PID of a process group
     */
    Answer signalGroup(long pgid, Signal signal);

    /**
     * Sends a signal to one process: {@code kill(pid, signal)}.
     *
     * @param pid    the PID
     * @param signal the signal
     * @return what the call answered
     * @throws IllegalArgumentException if the id is no PID of a process
     */
    Answer signalProcess(long pid, Signal signal);

    /**
     * @return the binding of the C library's {@code kill} through FFM
     * @throws IllegalStateException if the C library offers no {@code kill}
     */
    static ProcessGroupSignals system() {
        return new KillBinding();
    }
}
