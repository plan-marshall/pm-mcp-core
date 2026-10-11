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
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Runs one short-lived external command to its end, the seam through which the service managers call
 * {@code launchctl} and {@code systemctl}.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface ProcessCall {

    /**
     * The end of a command.
     *
     * @param exitCode the exit code
     * @param output   standard output and standard error, merged
     * @since 0.1
     */
    record Result(int exitCode, String output) {
    }

    /**
     * Runs a command and waits for its end.
     *
     * @param command the program and its arguments
     * @return the exit code and the output
     * @throws ServiceUnitException if the command cannot be started or the wait is interrupted
     */
    Result run(List<String> command);

    /**
     * Answers the call that starts the command as a child process of the runtime. Every command gets an empty
     * standard input: the pipe to it is closed right after the start, so a command that reads its input sees the
     * end at once instead of waiting.
     *
     * @return the call that runs commands as child processes
     */
    static ProcessCall system() {
        return ProcessCall::runProcess;
    }

    private static Result runProcess(List<String> command) {
        var name = String.join(" ", command);
        try {
            var process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            process.getOutputStream().close();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Result(process.waitFor(), output);
        } catch (IOException e) {
            throw new ServiceUnitException("Command '" + name + "' could not run", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnitException("Command '" + name + "' was interrupted", e);
        }
    }
}
