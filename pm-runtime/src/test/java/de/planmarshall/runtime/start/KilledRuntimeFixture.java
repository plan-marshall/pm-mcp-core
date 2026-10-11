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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The killed-runtime fixture of the lifecycle tests: it starts {@link RuntimeChildProcess} in a JVM of its own, with
 * the java and the class path of the test JVM, waits for a named marker line on its standard output, and kills it
 * with {@code SIGKILL} ({@link Process#destroyForcibly()}) in the middle of a lifecycle step, the way a crash or the
 * OS ends a runtime.
 * <p>
 * Every wait is bounded. The fixture is closed after every test, also after a failed one; closing kills a child that
 * is still alive.
 */
final class KilledRuntimeFixture implements AutoCloseable {

    /** Covers the start of a JVM on a loaded build machine. */
    static final Duration PROCESS_BOUND = Duration.ofSeconds(30);

    private Process child;
    private BufferedReader output;

    /**
     * Starts the child.
     *
     * @param mode the lifecycle step the child runs, a mode of {@link RuntimeChildProcess}
     * @param base the {@code PM_MCP_BASE} of the child
     * @throws IOException if the child cannot be started
     */
    void start(String mode, Path base) throws IOException {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                RuntimeChildProcess.class.getName(), mode, base.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        output = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Waits until the child has written the marker line, at most {@link #PROCESS_BOUND}.
     *
     * @param marker the line to wait for
     * @throws InterruptedException if the wait is interrupted
     * @throws ExecutionException   if the output cannot be read
     * @throws TimeoutException     if the child did not write the marker in time
     */
    void awaitMarker(String marker) throws InterruptedException, ExecutionException, TimeoutException {
        assertNotNull(child, "no child started");
        // Reading a line cannot be interrupted, so it is waited for from outside; killing the child ends the read
        var seen = CompletableFuture.supplyAsync(() -> readUntil(marker))
                .get(PROCESS_BOUND.toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(marker, seen.getLast(), () -> "the child ended before the marker, it wrote " + seen);
    }

    private List<String> readUntil(String marker) {
        var lines = new ArrayList<String>();
        try {
            for (var line = output.readLine(); line != null; line = output.readLine()) {
                lines.add(line);
                if (line.equals(marker)) {
                    return lines;
                }
            }
            lines.add("<end of output>");
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Kills the child with {@code SIGKILL} and waits for its end, at most {@link #PROCESS_BOUND}.
     *
     * @throws InterruptedException if the wait is interrupted
     */
    void kill() throws InterruptedException {
        assertNotNull(child, "no child started");
        child.destroyForcibly();
        assertTrue(child.waitFor(PROCESS_BOUND.toMillis(), TimeUnit.MILLISECONDS),
                "the child did not end after SIGKILL");
    }

    /**
     * Kills a child that is still alive and waits for its end, at most {@link #PROCESS_BOUND}.
     *
     * @throws InterruptedException if the wait is interrupted
     */
    @Override
    public void close() throws InterruptedException {
        if (child != null && child.isAlive()) {
            child.destroyForcibly();
            child.waitFor(PROCESS_BOUND.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
