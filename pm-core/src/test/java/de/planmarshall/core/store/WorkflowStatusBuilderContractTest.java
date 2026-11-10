/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.WorkflowStatus.BoundArg;
import de.planmarshall.core.store.WorkflowStatus.CallFrame;
import de.planmarshall.core.store.WorkflowStatus.Integrity;
import de.planmarshall.core.store.WorkflowStatus.JobEntry;
import de.planmarshall.core.store.WorkflowStatus.PendingCondition;
import de.planmarshall.core.store.WorkflowStatus.PolicyCounter;
import de.planmarshall.core.store.WorkflowStatus.TaskEntry;

@DisplayName("Workflow status builder contract")
class WorkflowStatusBuilderContractTest {

    private static final String SHA_256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant AT = Instant.parse("2026-10-22T10:00:00Z");

    @Test
    @DisplayName("the builder builds the constructor's workflow status")
    void builderBuildsWorkflowStatus() {
        var canonical = new WorkflowStatus(JournalScopeType.PLAN, "plan-core", "phase-5-execute",
                "execute.step", List.of(), 0, 0, Map.of(), List.of(), RetryCounters.empty(), null, Map.of(),
                Map.of(), null, AT, Map.of());

        var built = WorkflowStatus.builder().scopeType(canonical.scopeType()).scopeId(canonical.scopeId())
                .phase(canonical.phase()).state(canonical.state()).callStack(canonical.callStack())
                .loopBackIteration(canonical.loopBackIteration())
                .outlineLoopBacks(canonical.outlineLoopBacks())
                .policyCounters(canonical.policyCounters())
                .waiverRequests(canonical.waiverRequests()).retryCounters(canonical.retryCounters())
                .activeJob(canonical.activeJob()).activeJobs(canonical.activeJobs())
                .activeTasks(canonical.activeTasks()).pendingCondition(canonical.pendingCondition())
                .updatedAt(canonical.updatedAt()).retained(canonical.retained()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new WorkflowStatus(null, canonical.scopeId(), canonical.phase(), canonical.state(),
                        canonical.callStack(), canonical.loopBackIteration(),
                        canonical.outlineLoopBacks(), canonical.policyCounters(),
                        canonical.waiverRequests(), canonical.retryCounters(), canonical.activeJob(),
                        canonical.activeJobs(), canonical.activeTasks(), canonical.pendingCondition(),
                        canonical.updatedAt(), canonical.retained()),
                "the constructor refuses an absent scope type");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> WorkflowStatus.builder().scopeId(canonical.scopeId()).phase(canonical.phase())
                        .state(canonical.state()).callStack(canonical.callStack())
                        .loopBackIteration(canonical.loopBackIteration())
                        .outlineLoopBacks(canonical.outlineLoopBacks())
                        .policyCounters(canonical.policyCounters())
                        .waiverRequests(canonical.waiverRequests())
                        .retryCounters(canonical.retryCounters()).activeJob(canonical.activeJob())
                        .activeJobs(canonical.activeJobs()).activeTasks(canonical.activeTasks())
                        .pendingCondition(canonical.pendingCondition()).updatedAt(canonical.updatedAt())
                        .retained(canonical.retained()).build(),
                "the builder refuses an absent scope type");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent scope type alike"));
    }

    @Test
    @DisplayName("the builder builds the constructor's call frame")
    void builderBuildsCallFrame() {
        var canonical = new CallFrame("finalize", "finalize.settle", "git.commit_and_push", "commit",
                Map.<String, BoundArg>of("message_mode", new BoundArg.StringArg("step_or_fallback")), AT);

        var built = CallFrame.builder().callerWorkflowId(canonical.callerWorkflowId())
                .callerStateId(canonical.callerStateId()).subworkflowId(canonical.subworkflowId())
                .entry(canonical.entry()).boundArgs(canonical.boundArgs()).pushedAt(canonical.pushedAt())
                .build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new CallFrame(null, canonical.callerStateId(), canonical.subworkflowId(),
                        canonical.entry(), canonical.boundArgs(), canonical.pushedAt()),
                "the constructor refuses an absent caller workflow");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> CallFrame.builder().callerStateId(canonical.callerStateId())
                        .subworkflowId(canonical.subworkflowId()).entry(canonical.entry())
                        .boundArgs(canonical.boundArgs()).pushedAt(canonical.pushedAt()).build(),
                "the builder refuses an absent caller workflow");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent workflow alike"));
    }

    @Test
    @DisplayName("the builder builds the constructor's job entry")
    void builderBuildsJobEntry() {
        var canonical = new JobEntry("g-0123456789abcdef", "git", "running", AT);

        var built = JobEntry.builder().jobId(canonical.jobId()).kind(canonical.kind())
                .status(canonical.status()).startedAt(canonical.startedAt()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new JobEntry(null, canonical.kind(), canonical.status(), canonical.startedAt()),
                "the constructor refuses an absent job id");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> JobEntry.builder().kind(canonical.kind()).status(canonical.status())
                        .startedAt(canonical.startedAt()).build(),
                "the builder refuses an absent job id");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent job id alike"));
    }

    @Test
    @DisplayName("the builder builds the constructor's task entry")
    void builderBuildsTaskEntry() {
        var canonical = new TaskEntry("qt-0123456789abcdef", "implement", "queued", null, null);

        var built = TaskEntry.builder().queueTaskId(canonical.queueTaskId()).role(canonical.role())
                .state(canonical.state()).outcome(canonical.outcome()).reason(canonical.reason()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new TaskEntry(null, canonical.role(), canonical.state(), canonical.outcome(),
                        canonical.reason()),
                "the constructor refuses an absent queue task id");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> TaskEntry.builder().role(canonical.role()).state(canonical.state())
                        .outcome(canonical.outcome()).reason(canonical.reason()).build(),
                "the builder refuses an absent queue task id");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent task id alike"));
    }

    @Test
    @DisplayName("the builder builds the constructor's pending condition")
    void builderBuildsPendingCondition() {
        var canonical = new PendingCondition("guard_indeterminate", 1, null, "{}", AT);

        var built = PendingCondition.builder().kind(canonical.kind())
                .remedyAttempts(canonical.remedyAttempts()).integrity(canonical.integrity())
                .facts(canonical.facts()).raisedAt(canonical.raisedAt()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new PendingCondition(null, canonical.remedyAttempts(), canonical.integrity(),
                        canonical.facts(), canonical.raisedAt()),
                "the constructor refuses an absent condition kind");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> PendingCondition.builder().remedyAttempts(canonical.remedyAttempts())
                        .integrity(canonical.integrity()).facts(canonical.facts())
                        .raisedAt(canonical.raisedAt()).build(),
                "the builder refuses an absent condition kind");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent condition alike"));
    }

    @Test
    @DisplayName("the builder builds the constructor's integrity")
    void builderBuildsIntegrity() {
        var canonical = new Integrity("store_tampered", "mcp/status.json", SHA_256, null);

        var built = Integrity.builder().kind(canonical.kind()).store(canonical.store())
                .recordedDigest(canonical.recordedDigest()).currentDigest(canonical.currentDigest()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Integrity(null, canonical.store(), canonical.recordedDigest(),
                        canonical.currentDigest()),
                "the constructor refuses an absent integrity kind");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> Integrity.builder().store(canonical.store())
                        .recordedDigest(canonical.recordedDigest())
                        .currentDigest(canonical.currentDigest()).build(),
                "the builder refuses an absent integrity kind");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent kind alike"));
    }

    @Test
    @DisplayName("the builder builds the constructor's policy counter")
    void builderBuildsPolicyCounter() {
        var canonical = new PolicyCounter(1, 2, null, null);

        var built = PolicyCounter.builder().retryUsed(canonical.retryUsed()).fixUsed(canonical.fixUsed())
                .renewedFrom(canonical.renewedFrom()).lastRetry(canonical.lastRetry()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
