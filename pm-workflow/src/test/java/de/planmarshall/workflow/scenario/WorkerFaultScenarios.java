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

/**
 * The scripts of the supervision scenarios of {@code pm-scenario} (PM-TEST-4): the fault matrix of the specification's
 * regression scenario 12 and the runtime-crash scenarios of the work package {@code supervision-scenarios}.
 * <p>
 * They are contract text, shared by {@link ScenarioReplayRunnerTest}, which replays them and asserts the properties of
 * the task record, and {@link ScenarioDeterminismTest}, which replays each of them twice to byte-identical stores. Each
 * script names only the fixture plan unit's task state of the role {@code reviewer} and the directives the harness
 * runs; the limits it compares a stall with come from {@link ScenarioFixtures#WORKER_PARAMETERS} (an acknowledgement
 * deadline of {@value ScenarioFixtures#ACK_DEADLINE_SECONDS} seconds and a silence limit of
 * {@value ScenarioFixtures#SILENCE_LIMIT_SECONDS} seconds).
 */
// Every datum in this class is a contract: the scenario scripts the supervision scenarios replay.
final class WorkerFaultScenarios {

    /** The silence limit of the fixture worker values, in seconds. */
    private static final int SILENCE_LIMIT = ScenarioFixtures.SILENCE_LIMIT_SECONDS;

    /**
     * The time the acknowledged worker of {@link #KILL_RELAY} holds its wait before its relay is killed: shorter than
     * the silence limit, so an unsuppressed silence would not have fired yet.
     */
    static final int HELD_WAIT_SECONDS = SILENCE_LIMIT / 6;

    /**
     * A worker lost between its offer and its acknowledgement: the acknowledgement deadline detects it, the replacing
     * generation takes the released task, and the replaced generation's late call is refused at the fence.
     */
    static final String KILL_BEFORE_ACK = """
            scenario kill-before-ack
            start plan scenario-plan
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            assert_state execute.review
            worker_wait reviewer
            assert_worker_state reviewer offered
            kill_worker reviewer
            assert_worker_state reviewer fenced
            worker_do reviewer submit:change {}
            assert_refused generation_fenced
            worker_wait reviewer
            worker_do reviewer ack {}
            worker_do reviewer submit:change {}
            assert_task_outcome reviewer submitted
            assert_state execute.ask
            """;

    /**
     * A worker lost between its acknowledgement and its submit: the silence limit detects it, not the acknowledgement
     * deadline.
     */
    static final String KILL_AFTER_ACK = """
            scenario kill-after-ack
            start plan scenario-plan
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            worker_wait reviewer
            worker_do reviewer ack {}
            assert_worker_state reviewer acknowledged
            kill_worker reviewer
            assert_worker_state reviewer fenced
            worker_do reviewer submit:change {}
            assert_refused generation_fenced
            worker_wait reviewer
            worker_do reviewer ack {}
            worker_do reviewer submit:change {}
            assert_task_outcome reviewer submitted
            assert_state execute.ask
            """;

    /**
     * A worker that stalls for less than its silence limit: nothing changes, the same generation submits, and no
     * re-offer is spent.
     */
    static final String STALL_SHORT = """
            scenario stall-short
            start plan scenario-plan
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            worker_wait reviewer
            worker_do reviewer ack {}
            stall_worker reviewer %d
            assert_worker_state reviewer acknowledged
            worker_do reviewer submit:change {}
            assert_task_outcome reviewer submitted
            assert_state execute.ask
            """.formatted(SILENCE_LIMIT / 3);

    /**
     * A worker that stalls up to its silence limit: it is replaced exactly at the limit, never before, and the
     * replacing generation submits the released task once.
     */
    static final String STALL_LONG = """
            scenario stall-long
            start plan scenario-plan
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            worker_wait reviewer
            worker_do reviewer ack {}
            stall_worker reviewer %d
            assert_worker_state reviewer fenced
            worker_do reviewer ack {}
            assert_refused generation_fenced
            worker_wait reviewer
            worker_do reviewer ack {}
            worker_do reviewer submit:change {}
            assert_task_outcome reviewer submitted
            assert_state execute.ask
            """.formatted(SILENCE_LIMIT);

    /**
     * A worker that ends its turn without a submit: the supervisor observes the exit after the first call, and the
     * replacing generation takes the released task.
     */
    static final String END_TURN = """
            scenario end-turn
            start plan scenario-plan
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            worker_wait reviewer
            worker_do reviewer ack {}
            end_turn reviewer
            assert_worker_state reviewer fenced
            worker_do reviewer submit:change {}
            assert_refused generation_fenced
            worker_wait reviewer
            worker_do reviewer ack {}
            worker_do reviewer submit:change {}
            assert_task_outcome reviewer submitted
            assert_state execute.ask
            """;

    /**
     * A relay killed under the held wait of an acknowledged worker: the wait dies with the relay, the lost worker is
     * detected a silence limit later, and the replacing generation takes the released task.
     */
    static final String KILL_RELAY = """
            scenario kill-relay
            start plan scenario-plan
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            worker_wait reviewer
            worker_do reviewer ack {}
            stall_worker reviewer %d
            kill_relay reviewer
            assert_worker_state reviewer fenced
            worker_do reviewer submit:change {}
            assert_refused generation_fenced
            worker_wait reviewer
            worker_do reviewer ack {}
            worker_do reviewer submit:change {}
            assert_task_outcome reviewer submitted
            assert_state execute.ask
            """.formatted(HELD_WAIT_SECONDS);

    /**
     * A task whose re-offers are spent: the fourth loss closes it {@code lost} with {@code attempts_spent}, and the
     * task state routes that to the plan's failure terminal.
     */
    static final String BUDGET_EXHAUSTED = """
            scenario budget-exhausted
            start plan scenario-plan
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            worker_wait reviewer
            kill_worker reviewer
            worker_wait reviewer
            kill_worker reviewer
            worker_wait reviewer
            kill_worker reviewer
            worker_wait reviewer
            kill_worker reviewer
            assert_worker_state reviewer closed
            assert_task_outcome reviewer lost
            assert_state handed_back
            assert_outcome handed_back
            """;

    /**
     * A runtime crash with a leased task: the same generation waits again after the restart, still holds the lease, and
     * its submit closes the task once.
     */
    static final String RESTART_WITH_A_LEASE = """
            scenario restart-with-a-lease
            start plan scenario-plan
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            worker_wait reviewer
            worker_do reviewer ack {}
            assert_worker_state reviewer acknowledged
            restart_runtime
            worker_wait reviewer
            assert_worker_state reviewer offered
            worker_do reviewer ack {}
            worker_do reviewer submit:change {}
            assert_worker_state reviewer closed
            assert_task_outcome reviewer submitted
            assert_state execute.ask
            """;

    /**
     * A runtime crash with a leased task, stopped at the re-offer: the proof that a second generation's wait is
     * answered {@code no_task} while the lease is held is a protocol call of the test, because the harness's
     * {@code worker_wait} requires an offer.
     */
    static final String RESTART_HELD_BY_ONE_GENERATION = """
            scenario restart-held-by-one-generation
            start plan scenario-plan
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            worker_wait reviewer
            worker_do reviewer ack {}
            restart_runtime
            worker_wait reviewer
            assert_worker_state reviewer offered
            """;

    /**
     * A runtime crash after a loss: the successor takes the task from the released queue once the runtime is rebuilt.
     */
    static final String RESTART_AFTER_A_LOSS = """
            scenario restart-after-a-loss
            start plan scenario-plan
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            worker_wait reviewer
            worker_do reviewer ack {}
            kill_worker reviewer
            assert_worker_state reviewer fenced
            restart_runtime
            worker_wait reviewer
            assert_worker_state reviewer offered
            worker_do reviewer ack {}
            worker_do reviewer submit:change {}
            assert_task_outcome reviewer submitted
            assert_state execute.ask
            """;

    private WorkerFaultScenarios() {
    }
}
