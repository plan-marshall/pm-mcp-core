/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;
import de.planmarshall.runtime.worker.EndCause;

/**
 * The terminal worker block the settlement writes with the job's fate (PM-WORK-3, Job Record; PM-EXEC-4, Write order at
 * job end).
 * <p>
 * The fate and the worker's own connection decide the end reason, and the terminal protocol state follows it: a loss is
 * {@code lost}, every other end {@code ended}. The literals that stay are the contract values of the protocol — the
 * states and the end reasons; the digest, the model, the role name and the job id are generated.
 */
@EnableGeneratorController
@DisplayName("Worker termination")
class WorkerTerminationTest {

    private static final Instant CONNECTED_AT = Instant.parse("2026-10-26T10:00:00Z");

    private static final Instant ENDED_AT = CONNECTED_AT.plusSeconds(60);

    private final JobId jobId = new JobId(RuntimeGenerators.jobIds(JobId.Kind.LLM).next());

    @Test
    @DisplayName("a succeeded exit of a connected worker is a served task")
    void aSucceededExitIsAServedTask() {
        var terminal = WorkerTermination.terminal(worker(WorkerState.RoleClass.WRITER, CONNECTED_AT),
                settled(JobRecord.Fate.SUCCEEDED));

        assertAll(
                () -> assertEquals(WorkerState.EndReason.TASK_DONE, terminal.endReason(),
                        "a succeeded exit ends the worker by its served task"),
                () -> assertEquals(WorkerState.State.ENDED, terminal.state(), "the served worker has ended"),
                () -> assertEquals(ENDED_AT, terminal.stateAt(), "the terminal instant is the settlement's"),
                () -> assertNull(terminal.task(), "no task is leased in a terminal state"),
                () -> assertFalse(terminal.startFailure(), "a served task is no start failure"));
    }

    @Test
    @DisplayName("an exit before the first call is a start failure")
    void anExitBeforeTheFirstCallIsAStartFailure() {
        var terminal = WorkerTermination.terminal(worker(WorkerState.RoleClass.WRITER, null),
                settled(JobRecord.Fate.FAILED));

        assertAll(
                () -> assertEquals(WorkerState.EndReason.START_FAILURE, terminal.endReason(),
                        "an exit before the first call is a start failure"),
                () -> assertEquals(WorkerState.State.ENDED, terminal.state(), "a start failure has ended"),
                () -> assertTrue(terminal.startFailure(), "the flag and the end reason agree"));
    }

    @Test
    @DisplayName("a zero-tool worker makes no call by design and is no start failure")
    void aZeroToolWorkerIsNoStartFailure() {
        var terminal = WorkerTermination.terminal(worker(WorkerState.RoleClass.ZERO_TOOL, null),
                settled(JobRecord.Fate.SUCCEEDED));

        assertAll(
                () -> assertEquals(WorkerState.EndReason.TASK_DONE, terminal.endReason(),
                        "a zero-tool worker's valid result serves its task"),
                () -> assertFalse(terminal.startFailure(), "a zero-tool worker never fails to start"));
    }

    @ParameterizedTest
    @EnumSource(JobRecord.Fate.class)
    @DisplayName("every fate maps onto the end reason and the terminal state")
    void everyFateMapsOntoItsEnd(JobRecord.Fate fate) {
        var terminal = WorkerTermination.terminal(worker(WorkerState.RoleClass.WRITER, CONNECTED_AT), settled(fate));

        assertAll(
                () -> assertTrue(terminal.state().isTerminal(), "the state of a settled job is terminal"),
                () -> assertEquals(expected(fate), terminal.endReason(), "the fate names the end reason"),
                () -> assertEquals(expectedState(fate), terminal.state(), "the end reason names the state"));
    }

    /**
     * The supervisor's reason, its end cause, the fate the forced end settles with and the recorded end reason are the
     * contract values of ADR runtime-worker/021: the record keeps the coarse mapping of the fate, not the reason.
     */
    @ParameterizedTest(name = "{0} is forced as {1}, settles {2} and records {3}")
    @CsvSource({
            "idle_recycle, recycle, cancelled, withdrawn",
            "silence, lost, cancelled, withdrawn",
            "ack_missed, lost, cancelled, withdrawn",
            "off_track, off_track, cancelled, withdrawn"
    })
    @DisplayName("contract values: a forced end records the coarse mapping of its fate, not its reason")
    void aForcedEndRecordsTheCoarseMappingOfItsFate(String reason, String cause, String fate, String endReason) {
        var endCause = Arrays.stream(EndCause.values()).filter(value -> value.wireName().equals(cause)).findFirst()
                .orElseThrow();
        var forced = OutcomeDerivation.derive(JobEvidence.ProcessEnded.builder().kind(JobId.Kind.LLM)
                .termination(JobEvidence.Termination.SUPERVISOR_END).waitStatus(new JobEvidence.Signalled("SIGTERM"))
                .log(JobEvidence.LogState.READABLE).parse(JobEvidence.ParseResult.NOTHING)
                .declaredExitCodes(Set.of()).exitReliable(true).build());

        var terminal = WorkerTermination.terminal(worker(WorkerState.RoleClass.WRITER, CONNECTED_AT),
                settled(forced.fate()));

        assertAll(reason,
                () -> assertEquals(JobRunner.CancelCause.SUPERVISOR_END, endCause.cancelCause(),
                        "the reason's end reaches the runner as its end"),
                () -> assertEquals(fate, forced.fate().wireName(), "the supervisor's forced end settles the fate"),
                () -> assertEquals(endReason, terminal.endReason().wireName(),
                        "the record keeps the fate's end reason"),
                () -> assertNotEquals(reason, terminal.endReason().wireName(),
                        "the record does not carry the reason itself"));
    }

    private static WorkerState.EndReason expected(JobRecord.Fate fate) {
        return switch (fate) {
            case SUCCEEDED -> WorkerState.EndReason.TASK_DONE;
            case TIMED_OUT -> WorkerState.EndReason.BUDGET;
            case KILLED -> WorkerState.EndReason.DRAIN;
            case CANCELLED -> WorkerState.EndReason.WITHDRAWN;
            case FAILED, FAILED_UNPARSED, INDETERMINATE, REFUSED -> WorkerState.EndReason.EXITED;
        };
    }

    private static WorkerState.State expectedState(JobRecord.Fate fate) {
        return expected(fate) == WorkerState.EndReason.EXITED ? WorkerState.State.LOST : WorkerState.State.ENDED;
    }

    private WorkerState worker(WorkerState.RoleClass roleClass, Instant connectedAt) {
        var digest = RuntimeGenerators.sha256Digests().next();
        return WorkerState.builder()
                .role("implementer")
                .configurationDigest(digest)
                .roleClass(roleClass)
                .form(WorkerState.Form.FRESH)
                .harness("claude")
                .harnessVersion("1.5.0")
                .model("claude-sonnet-4")
                .generationId(jobId)
                .workdir(new WorkerState.Workdir(WorkerState.Workdir.Kind.PROJECT_CHECKOUT, null))
                .promptDigest(digest)
                .jobTokenSha256(roleClass == WorkerState.RoleClass.ZERO_TOOL ? null : digest)
                .connectedAt(connectedAt)
                .state(WorkerState.State.EXECUTING)
                .stateAt(CONNECTED_AT)
                .task(null)
                .servedTasks(List.of())
                .idleWakeups(0L)
                .contextTokens(WorkerState.ContextTokens.NOT_MEASURED)
                .deliveredSkills(List.of())
                .startFailure(false)
                .build();
    }

    private JobRecord settled(JobRecord.Fate fate) {
        var refused = fate == JobRecord.Fate.REFUSED;
        return JobRecord.builder().jobId(jobId).execution(JobRecord.Execution.PROCESS)
                .scope(new JobRecord.Scope(JobRecord.Scope.Type.PROJECT, null))
                .worker("{}")
                .egress(JobRecord.Egress.UNENFORCED)
                .gitMetadata(JobRecord.GitMetadata.UNENFORCED)
                .budgetKey("worker:implementer").budgetSeconds(60)
                .budgetDerivation(new JobRecord.BudgetDerivation(JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                .status(refused ? JobRecord.Status.REFUSED : JobRecord.Status.ENDED).fate(fate)
                .outcomeRow(refused ? 1 : 2).refusalReason(refused ? "launch_refused" : null)
                .confinement(JobRecord.Confinement.UNAVAILABLE)
                .createdAt(CONNECTED_AT).spawnedAt(refused ? null : CONNECTED_AT).endedAt(ENDED_AT)
                .childWallMs(refused ? null : 60_000L)
                .clampedValues(List.of()).build();
    }
}
