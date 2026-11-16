/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.scenario;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.task.CallResult;
import de.planmarshall.workflow.task.TaskCall;
import de.planmarshall.workflow.task.TaskProtocol;
import de.planmarshall.workflow.task.WaitAnswer;
import de.planmarshall.workflow.task.WaitRequest;

/**
 * The scripted protocol worker of one role in the replay harness {@code pm-scenario} (PM-TEST-4): the generation the
 * script starts with {@code worker_wait}, and the task the shipped {@link TaskProtocol} leased to it. It holds its own
 * generation, waits for its task through the protocol and acts on the leased entity through {@code acknowledge},
 * {@code submit} and {@code infeasible}; the harness never settles the task for it.
 * <p>
 * The generation id comes from the fixtures' counter, so two replays start the same generations; the configuration
 * digest is the replay's one fixed digest, so the protocol offers the task to the generation. One instance serves one
 * role of one replay.
 */
// Every datum in this class is the replay's contract: the generation and the leased task entity.
final class ScriptedProtocolWorker {

    private final String role;
    private final String generation;
    private final TaskProtocol protocol;
    private String queueTaskId;

    /**
     * @param role       the role the worker serves
     * @param generation the worker's generation, {@code l-} and 16 hex digits
     * @param protocol   the task protocol of the replay
     */
    ScriptedProtocolWorker(String role, String generation, TaskProtocol protocol) {
        this.role = Objects.requireNonNull(role, "role");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.protocol = Objects.requireNonNull(protocol, "protocol");
    }

    /** @return the role the worker serves */
    String role() {
        return role;
    }

    /** @return the worker's generation */
    String generation() {
        return generation;
    }

    /** @return the task the protocol leased to the worker; empty before its first offer */
    Optional<String> queueTaskId() {
        return Optional.ofNullable(queueTaskId);
    }

    /**
     * Calls the protocol's wait with the worker's generation and role.
     *
     * @param at the instant of the wait
     * @return the answer of the protocol; an offer is remembered as the worker's leased task
     */
    WaitAnswer wait(Instant at) {
        var answer = protocol.wait(
                new WaitRequest(generation, role, ScenarioFixtures.DIGEST, ScenarioFixtures.DIGEST,
                        ScenarioFixtures.RE_OFFER_BUDGET), at);
        if (answer instanceof WaitAnswer.Offered offered) {
            queueTaskId = offered.task().queueTaskId();
        }
        return answer;
    }

    /**
     * @param at the instant of the acknowledgement
     * @return the task in {@code executing}, or the refusal
     */
    CallResult acknowledge(Instant at) {
        return protocol.acknowledge(call(), at);
    }

    /**
     * @param at the instant of the submit
     * @return the closed task, or the refusal
     */
    CallResult submit(Instant at) {
        return protocol.submit(call(), at);
    }

    /**
     * @param at the instant of the report
     * @return the closed task, or the refusal
     */
    CallResult infeasible(Instant at) {
        return protocol.infeasible(call(), at);
    }

    private TaskCall call() {
        if (queueTaskId == null) {
            throw new IllegalStateException("the worker of role %s holds no leased task".formatted(role));
        }
        return new TaskCall(queueTaskId, generation);
    }
}
