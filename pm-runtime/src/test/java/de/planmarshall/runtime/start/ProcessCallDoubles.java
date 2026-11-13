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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * EasyMock process calls for the tests of the service managers and of the installer: the answers a test scripts, one
 * expectation per answer, and the recorded calls in order.
 */
final class ProcessCallDoubles {

    /**
     * One recorded command.
     *
     * @param command     the program and its arguments
     * @param unitPresent whether the watched unit file was present when the command was issued; {@code false} when
     *                    no file is watched
     */
    record Call(List<String> command, boolean unitPresent) {
    }

    /**
     * One answer of the scripted call.
     *
     * @param exitCode the exit code of a success answer
     * @param output   the merged output of a success answer
     * @param failure  the failure the call raises instead of answering; {@code null} for a success answer
     */
    record Answer(int exitCode, String output, ServiceUnitException failure) {
    }

    private ProcessCallDoubles() {
    }

    /**
     * @param exitCode the exit code
     * @param output   the merged output
     * @return an answer with that exit code and output
     */
    static Answer answer(int exitCode, String output) {
        return new Answer(exitCode, output, null);
    }

    /**
     * @param failure the failure to raise
     * @return an answer that raises the failure
     */
    static Answer failure(ServiceUnitException failure) {
        return new Answer(0, null, failure);
    }

    /**
     * @param watched the unit file whose presence every call notes, or {@code null} for none
     * @param calls   the recorded calls, in order
     * @param answers the answers, in the order of the calls
     * @return an EasyMock process call that answers from the answers and then succeeds with an empty output
     */
    static ProcessCall create(Path watched, List<Call> calls, Answer... answers) {
        ProcessCall mock = createMock(ProcessCall.class);
        for (var answer : answers) {
            expect(mock.run(anyObject(List.class))).andAnswer(() -> {
                calls.add(recorded(watched));
                if (answer.failure != null) {
                    throw answer.failure;
                }
                return new ProcessCall.Result(answer.exitCode, answer.output);
            });
        }
        expect(mock.run(anyObject(List.class))).andAnswer(() -> {
            calls.add(recorded(watched));
            // Contract literal: a command answered by default succeeds with an empty output
            return new ProcessCall.Result(0, "");
        }).anyTimes();
        replay(mock);
        return mock;
    }

    @SuppressWarnings("unchecked")
    private static Call recorded(Path watched) {
        var command = (List<String>) getCurrentArguments()[0];
        return new Call(List.copyOf(command), watched != null && Files.exists(watched));
    }
}
