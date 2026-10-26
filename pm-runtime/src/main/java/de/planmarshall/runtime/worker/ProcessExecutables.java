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

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where the executable of a process is read (PM-WORK-7, {@code agent_started}): the absolute path of the file the
 * process runs and the digest of that file, for one PID.
 * <p>
 * The read answers a decision, never an exception: a process that is gone by the time its tree is examined, or one
 * whose executable cannot be read, is {@link Reading.Absent}, and a process whose executable path names something that
 * is not a regular file — a binary deleted after it was started, for example — is {@link Reading.NotRegular}. The
 * supervisor's detection pass walks a whole tree on every pass, where both are ordinary facts about a process table
 * that changes under it.
 * <p>
 * {@link #system()} is the binding of the operating system this runtime runs on; the specification fixes how it reads
 * there (see {@link SystemProcessExecutables}).
 *
 * @since 0.1
 */
public interface ProcessExecutables {

    /**
     * What reading the executable of one process decided.
     *
     * @since 0.1
     */
    sealed interface Reading permits Found, Absent, NotRegular {
    }

    /**
     * The process runs a regular file, read as its executable path and digest.
     *
     * @param executable the executable of the process
     * @since 0.1
     */
    record Found(ProcessExecutable executable) implements Reading {

        /** @throws NullPointerException if the executable is {@code null} */
        public Found {
            Objects.requireNonNull(executable, "executable");
        }
    }

    /**
     * The process is gone, or the operating system answered no executable for it.
     *
     * @since 0.1
     */
    record Absent() implements Reading {
    }

    /**
     * The process answered an executable path that is absent or is not a regular file, so nothing can be digested.
     *
     * @param path the path the operating system reported
     * @since 0.1
     */
    record NotRegular(Path path) implements Reading {

        /** @throws NullPointerException if the path is {@code null} */
        public NotRegular {
            Objects.requireNonNull(path, "path");
        }
    }

    /**
     * @param pid the PID of the process, from 1
     * @return the executable of the process, or why there is none
     * @throws IllegalArgumentException if the PID is below 1
     */
    Reading read(long pid);

    /**
     * @return the reader of the operating system this runtime runs on
     */
    static ProcessExecutables system() {
        return new SystemProcessExecutables();
    }
}
