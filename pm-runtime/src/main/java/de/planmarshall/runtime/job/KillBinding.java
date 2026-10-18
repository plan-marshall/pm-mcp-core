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
 * The FFM binding of {@code kill(2)}, capturing {@code errno} of each call. It is the implementation behind
 * {@link ProcessGroupSignals#system()}; the foreign downcall descriptor, {@code kill(jint, jint) -> jint} with the
 * linker option {@code captureCallState}, is the one registered in the reachability metadata of this module.
 *
 * @since 0.1
 */
final class KillBinding implements ProcessGroupSignals {

    /** {@code ESRCH} on Linux and macOS. */
    static final int ESRCH = 3;

    /** The largest PID: {@code pid_t} is a 32-bit signed integer. */
    private static final long MAX_PID = Integer.MAX_VALUE;

    private static final String ERRNO_MEMBER = "errno";

    private final MethodHandle killHandle;
    private final StructLayout callState;
    private final VarHandle errno;

    /** @throws IllegalStateException if the C library offers no {@code kill} */
    KillBinding() {
        var linker = Linker.nativeLinker();
        var symbol = linker.defaultLookup().find("kill")
                .orElseThrow(() -> new IllegalStateException("The C library offers no kill"));
        killHandle = linker.downcallHandle(symbol, FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT),
                Linker.Option.captureCallState(ERRNO_MEMBER));
        callState = Linker.Option.captureStateLayout();
        errno = callState.varHandle(MemoryLayout.PathElement.groupElement(ERRNO_MEMBER));
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
                result = (int) killHandle.invokeExact(state, target, signal.number());
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
     * A PID of {@code 1} or less would address every process, the caller's own group or {@code init}; none is a job.
     */
    private static int checkedPid(long pid) {
        if (pid <= 1 || pid > MAX_PID) {
            throw new IllegalArgumentException("Not the PID of a job: " + pid);
        }
        return (int) pid;
    }
}
