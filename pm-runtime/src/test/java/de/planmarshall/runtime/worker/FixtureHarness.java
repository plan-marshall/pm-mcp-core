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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The fixture harness of the supervisor scenarios (PM-WORK-3, Done When): a test-scope program started as a real
 * process, so a scenario reads a real output stream, a real first-call delay, a real held call, the process's answer to
 * a termination request and a real child process instead of a table the test supplied.
 * <p>
 * The program prints the lines it is given, after the scripted first-call delay; it can hold its call (stay alive until
 * it is terminated); it delays its own exit for a scripted grace after {@code SIGTERM}, which is how a scenario observes
 * signal behaviour without a second harness; and it can start a child with a named executable, which is the member the
 * {@code agent_started} detector examines in the harness process tree.
 * <p>
 * A scenario starts it through {@link #start(Path, List)} and reads its stream through {@link Handle#awaitLine(Duration)},
 * so the harness's output is observed rather than assumed.
 *
 * @since 0.1
 */
final class FixtureHarness {

    /** The marker that precedes the PID of the child the harness started. */
    static final String CHILD_MARKER = "child:";

    private static final String OUTPUT = "--output";
    private static final String FIRST_CALL_DELAY = "--first-call-delay-ms";
    private static final String TERM_GRACE = "--term-grace-ms";
    private static final String CHILD = "--child";
    private static final String HOLD = "--hold";

    private FixtureHarness() {
    }

    /**
     * Runs the fixture harness: the program a scenario starts as a real process.
     *
     * @param arguments the scripted stream and behaviour
     * @throws Exception if the scripted delay is interrupted or the child cannot start
     */
    public static void main(String[] arguments) throws Exception {
        var output = new ArrayList<String>();
        var firstCallDelayMillis = 0L;
        var termGraceMillis = 0L;
        Path child = null;
        var hold = false;
        for (var index = 0; index < arguments.length; index++) {
            switch (arguments[index]) {
                case OUTPUT -> output.add(arguments[++index]);
                case FIRST_CALL_DELAY -> firstCallDelayMillis = Long.parseLong(arguments[++index]);
                case TERM_GRACE -> termGraceMillis = Long.parseLong(arguments[++index]);
                case CHILD -> child = Path.of(arguments[++index]);
                case HOLD -> hold = true;
                default -> throw new IllegalArgumentException("Unknown fixture argument " + arguments[index]);
            }
        }
        if (termGraceMillis > 0) {
            var grace = termGraceMillis;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> ignoreTermination(grace)));
        }
        if (firstCallDelayMillis > 0) {
            awaitScriptedDelay(firstCallDelayMillis);
        }
        for (var line : output) {
            print(line);
        }
        if (child != null) {
            var started = new ProcessBuilder(child.toString(), "60").start();
            print(CHILD_MARKER + started.pid());
        }
        if (hold) {
            new CountDownLatch(1).await();
        }
    }

    /**
     * @param workingDirectory the directory the program runs in
     * @param arguments        the scripted stream and behaviour
     * @return the started harness
     * @throws IOException if the program cannot be started
     */
    static Handle start(Path workingDirectory, List<String> arguments) throws IOException {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(arguments, "arguments");
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(classpath());
        command.add(FixtureHarness.class.getName());
        command.addAll(arguments);
        var process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        var lines = new LinkedBlockingQueue<String>();
        var reader = new Thread(() -> read(process, lines), "fixture-harness-reader");
        reader.setDaemon(true);
        reader.start();
        return new Handle(process, lines);
    }

    /**
     * The running fixture harness: its process, its scripted stream and the one termination request a scenario sends.
     *
     * @since 0.1
     */
    static final class Handle implements AutoCloseable {

        private final Process process;

        private final BlockingQueue<String> lines;

        private Handle(Process process, BlockingQueue<String> lines) {
            this.process = process;
            this.lines = lines;
        }

        /** @return the PID of the harness process */
        long pid() {
            return process.pid();
        }

        /**
         * @param timeout how long to wait for the line
         * @return the next line of the scripted stream
         * @throws InterruptedException if the wait is interrupted
         */
        String awaitLine(Duration timeout) throws InterruptedException {
            var line = lines.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (line == null) {
                throw new IllegalStateException("The fixture harness printed no line within " + timeout);
            }
            return line;
        }

        /**
         * @param timeout how long to wait for the exit
         * @return the process's exit code
         * @throws InterruptedException if the wait is interrupted
         * @throws IllegalStateException if the process did not exit within the timeout
         */
        int awaitExit(Duration timeout) throws InterruptedException {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("The fixture harness did not exit within " + timeout);
            }
            return process.exitValue();
        }

        /**
         * @param timeout how long to observe the process staying alive
         * @return whether the process was still alive for the whole observation
         * @throws InterruptedException if the wait is interrupted
         */
        boolean survives(Duration timeout) throws InterruptedException {
            return !process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        /** Sends the termination request the operating system maps to {@code SIGTERM}. */
        void terminate() {
            process.destroy();
        }

        /** Kills the process without a grace. */
        @Override
        public void close() {
            process.destroyForcibly();
        }
    }

    private static String classpath() {
        return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    }

    private static void print(String line) {
        // Deliberately System.out: the scenario reads the fixture's stdout as its scripted stream, which CuiLogger's
        // level and format would alter. Owner: runtime worker test fixtures.
        // cui-rewrite:disable CuiLoggerStandardsRecipe
        System.out.println(line);
        System.out.flush();
    }

    /**
     * Waits the scripted delay of the fixture: the delay is the behaviour a scenario asks the harness to simulate, not
     * a test waiting for a result, so it is a bounded latch wait instead of a raw sleep.
     *
     * @param millis how long the fixture delays
     * @throws InterruptedException if the wait is interrupted
     */
    private static void awaitScriptedDelay(long millis) throws InterruptedException {
        if (millis > 0) {
            new CountDownLatch(1).await(millis, TimeUnit.MILLISECONDS);
        }
    }

    private static void ignoreTermination(long graceMillis) {
        try {
            awaitScriptedDelay(graceMillis);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static void read(Process process, BlockingQueue<String> lines) {
        try (var reader = new BufferedReader(
                     new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the fixture harness stream", e);
        }
    }
}
