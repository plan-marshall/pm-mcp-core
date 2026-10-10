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

import java.io.File;
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

    /** The empty standard input of every command. */
    File NULL_DEVICE = new File("/dev/null");

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
     * @return the call that starts the command as a child process of the runtime, with an empty standard input
     */
    static ProcessCall system() {
        return ProcessCall::runProcess;
    }

    private static Result runProcess(List<String> command) {
        var name = String.join(" ", command);
        try {
            var process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectInput(ProcessBuilder.Redirect.from(NULL_DEVICE))
                    .start();
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
