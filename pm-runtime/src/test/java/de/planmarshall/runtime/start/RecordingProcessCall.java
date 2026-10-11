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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * A {@link ProcessCall} that starts no process: it records every command in order and answers from a scripted queue
 * of results or failures, and with exit code {@code 0} once the queue is empty.
 * <p>
 * When it watches a unit file, every recorded call also notes whether that file was present at the moment of the
 * call, so a test can prove the order of the file work and the commands of the service manager.
 */
final class RecordingProcessCall implements ProcessCall {

    /**
     * One recorded command.
     *
     * @param command     the program and its arguments
     * @param unitPresent whether the watched unit file was present when the command was issued; {@code false} when
     *                    no file is watched
     */
    record Call(List<String> command, boolean unitPresent) {
    }

    private final Path watched;
    private final List<Call> calls = new ArrayList<>();
    private final Deque<Supplier<Result>> answers = new ArrayDeque<>();

    /** A call that watches no unit file. */
    RecordingProcessCall() {
        this(null);
    }

    /**
     * @param watched the unit file whose presence is noted with every call, or {@code null} for none
     */
    RecordingProcessCall(Path watched) {
        this.watched = watched;
    }

    /**
     * Queues the result of the next unanswered command.
     *
     * @param exitCode the exit code
     * @param output   the merged output
     * @return this call
     */
    RecordingProcessCall thenAnswer(int exitCode, String output) {
        var result = new Result(exitCode, output);
        answers.add(() -> result);
        return this;
    }

    /**
     * Queues a failure of the next unanswered command, as if it could not be started.
     *
     * @param failure the failure to raise
     * @return this call
     */
    RecordingProcessCall thenFail(ServiceUnitException failure) {
        answers.add(() -> {
            throw failure;
        });
        return this;
    }

    @Override
    public Result run(List<String> command) {
        calls.add(new Call(List.copyOf(command), watched != null && Files.exists(watched)));
        var next = answers.poll();
        return next == null ? new Result(0, "") : next.get();
    }

    /**
     * @return the recorded calls in order
     */
    List<Call> calls() {
        return List.copyOf(calls);
    }

    /**
     * @return the recorded commands in order
     */
    List<List<String>> commands() {
        return calls.stream().map(Call::command).toList();
    }
}
