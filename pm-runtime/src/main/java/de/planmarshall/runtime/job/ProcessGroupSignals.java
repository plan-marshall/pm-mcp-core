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

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

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
 * {@code errno} captured) is not registered in this module. It is registered in the reachability metadata of the
 * assembly in the repository {@code plan-marshall-mcp}, where the descriptors of the other FFM bindings of this module
 * are registered as well, and it is verified by the native build of {@code pm-mcpd} against the deployed
 * {@code SNAPSHOT}.
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
        return new Kill();
    }

    /**
     * The FFM binding of {@code kill(2)}, capturing {@code errno} of each call.
     *
     * @since 0.1
     */
    final class Kill implements ProcessGroupSignals {

        /** {@code ESRCH} on Linux and macOS. */
        static final int ESRCH = 3;

        /** The largest PID: {@code pid_t} is a 32-bit signed integer. */
        private static final long MAX_PID = Integer.MAX_VALUE;

        private static final String ERRNO = "errno";

        private final MethodHandle kill;
        private final StructLayout callState;
        private final VarHandle errno;

        private Kill() {
            var linker = Linker.nativeLinker();
            var symbol = linker.defaultLookup().find("kill")
                    .orElseThrow(() -> new IllegalStateException("The C library offers no kill"));
            kill = linker.downcallHandle(symbol, FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT),
                    Linker.Option.captureCallState(ERRNO));
            callState = Linker.Option.captureStateLayout();
            errno = callState.varHandle(MemoryLayout.PathElement.groupElement(ERRNO));
        }

        @Override
        public Answer signalGroup(long pgid, Signal signal) {
            return kill(-checkedPid(pgid), signal);
        }

        @Override
        public Answer signalProcess(long pid, Signal signal) {
            return kill(checkedPid(pid), signal);
        }

        @SuppressWarnings("java:S1181") // MethodHandle.invokeExact declares Throwable; Errors are rethrown
        private Answer kill(int target, Signal signal) {
            try (var arena = Arena.ofConfined()) {
                var state = arena.allocate(callState);
                int result;
                try {
                    result = (int) kill.invokeExact(state, target, signal.number());
                    // cui-rewrite:disable InvalidExceptionUsageRecipe
                } catch (Throwable t) {
                    if (t instanceof Error e) {
                        throw e;
                    }
                    throw new IllegalStateException("kill(%d, %d) failed".formatted(target, signal.number()), t);
                }
                if (result == 0) {
                    return new Delivered();
                }
                var code = (int) errno.get(state, 0L);
                return code == ESRCH ? new NoSuchProcess() : new Refused(code);
            }
        }

        /**
         * A PID of {@code 1} or less would address every process, the caller's own group or {@code init}; none is a
         * job.
         */
        private static int checkedPid(long pid) {
            if (pid <= 1 || pid > MAX_PID) {
                throw new IllegalArgumentException("Not the PID of a job: " + pid);
            }
            return (int) pid;
        }
    }
}
