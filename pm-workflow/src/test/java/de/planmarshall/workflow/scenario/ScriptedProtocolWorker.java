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

import de.planmarshall.core.queue.QueueTaskRecord;
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
 * role of one replay, for one generation.
 * <p>
 * A worker the harness loses ({@code kill_worker}, {@code end_turn}, {@code kill_relay}, {@code stall_worker}) is
 * {@link #replaced()}: its generation is fenced, and the role's next {@code worker_wait} starts the successor
 * {@link #successor()} with the next generation of the counter. A runtime restart rebuilds the instance over the same
 * generation, with its leased task re-read from the queue, because nothing in memory survives the restart and the
 * lease does.
 */
// Every datum in this class is the replay's contract: the generation and the leased task entity.
final class ScriptedProtocolWorker {

    private final String role;
    private final String generation;
    private final TaskProtocol protocol;
    private String queueTaskId;
    private boolean replaced;
    private Instant offeredAt;
    private Instant lastCallAt;

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

    /** @return the instant of the offer the worker holds; empty before its first offer */
    Optional<Instant> offeredAt() {
        return Optional.ofNullable(offeredAt);
    }

    /** @return the instant of the worker's latest call; empty before its first */
    Optional<Instant> lastCallAt() {
        return Optional.ofNullable(lastCallAt);
    }

    /** @return whether the harness lost this generation, so the role's next wait starts a successor */
    boolean replaced() {
        return replaced;
    }

    /** Records that the harness lost this generation: its lease was released and its generation fenced. */
    void replaced(boolean value) {
        replaced = value;
    }

    /**
     * Records the task a rebuilt instance holds after a runtime restart: the queue record is durable, the in-memory
     * handle is not, so the offer's instant and the worker's last call are read back from its attempt.
     *
     * @param task the task the queue's lease names for this generation
     */
    void leased(QueueTaskRecord task) {
        queueTaskId = task.queueTaskId();
        var attempt = task.currentAttempt().orElse(null);
        offeredAt = attempt == null ? null : attempt.offeredAt();
        if (attempt != null && attempt.acknowledgedAt() != null) {
            lastCallAt = attempt.acknowledgedAt();
        } else {
            lastCallAt = offeredAt;
        }
    }

    /** @return the successor of this lost generation, over the same protocol, with the next generation of the counter */
    ScriptedProtocolWorker successor(String nextGeneration) {
        return new ScriptedProtocolWorker(role, nextGeneration, protocol);
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
        called(at);
        if (answer instanceof WaitAnswer.Offered offered) {
            queueTaskId = offered.task().queueTaskId();
            offeredAt = at;
        }
        return answer;
    }

    /**
     * @param at the instant of the acknowledgement
     * @return the task in {@code executing}, or the refusal
     */
    CallResult acknowledge(Instant at) {
        called(at);
        return protocol.acknowledge(call(), at);
    }

    /**
     * @param at the instant of the submit
     * @return the closed task, or the refusal
     */
    CallResult submit(Instant at) {
        called(at);
        return protocol.submit(call(), at);
    }

    /**
     * @param at the instant of the report
     * @return the closed task, or the refusal
     */
    CallResult infeasible(Instant at) {
        called(at);
        return protocol.infeasible(call(), at);
    }

    /**
     * Records a consultation the worker opened or answered: a consultation is one of the worker's calls, so the silence
     * limit counts from it.
     *
     * @param at the instant of the consultation
     */
    void consulted(Instant at) {
        called(at);
    }

    private void called(Instant at) {
        lastCallAt = at;
    }

    private TaskCall call() {
        if (queueTaskId == null) {
            throw new IllegalStateException("the worker of role %s holds no leased task".formatted(role));
        }
        return new TaskCall(queueTaskId, generation);
    }
}
