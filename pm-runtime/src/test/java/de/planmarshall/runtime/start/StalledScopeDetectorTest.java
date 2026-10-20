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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatus.JobEntry;
import de.planmarshall.core.store.WorkflowStatus.PendingCondition;
import de.planmarshall.core.store.WorkflowStatus.TaskEntry;
import de.planmarshall.runtime.start.StalledScopeDetector.Activity;
import de.planmarshall.runtime.start.StalledScopeDetector.Cause;
import de.planmarshall.runtime.start.StalledScopeDetector.OperatorPort;
import de.planmarshall.runtime.start.StalledScopeDetector.StalledHold;
import de.planmarshall.runtime.start.StalledScopeDetector.View;

/**
 * The stalled-scope detection (PM-ARCH-8; Runtime Lifecycle Specification, Stalled Plans; Timeouts Specification,
 * Stalled Threshold): a plan or epic with no transition and no task progress for the compiled threshold, awaiting no
 * event, is held {@code stalled} with the cause {@code no_progress} and the idle span.
 * <p>
 * The detection is derived on read and kept nowhere: the stored status gains no member, the one
 * {@code plan_stalled} record of an episode carries the stall, and a hold that clears by itself announces the report
 * {@code plan.hold_released}. The runtime module holds no model-facing representation yet; the stored status is the
 * only persisted representation the runtime serves, and it stays untouched (PM-ARCH-8, Hypermedia Format
 * Specification, Model-Facing Minimality).
 */
@DisplayName("Stalled scope detector")
class StalledScopeDetectorTest {

    private static final Instant LAST_PROGRESS = Instant.parse("2026-10-20T10:00:00Z");
    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN_ID = "a-plan";
    private static final String EPIC_ID = "an-epic";

    private final List<AuditEvent> records = new ArrayList<>();
    private final List<StalledHold> opened = new ArrayList<>();
    private final List<StalledHold> released = new ArrayList<>();
    private final List<String> reports = new ArrayList<>();

    private StalledScopeDetector detector() {
        return new StalledScopeDetector(records::add, new OperatorPort() {

            @Override
            public void holdOpened(StalledHold hold) {
                opened.add(hold);
            }

            @Override
            public void holdReleased(StalledHold hold, String report) {
                released.add(hold);
                reports.add(report);
            }
        });
    }

    /** @return the status of a plan that rests in a state and made no progress since {@link #LAST_PROGRESS} */
    private static WorkflowStatus.WorkflowStatusBuilder plan() {
        return status(JournalScopeType.PLAN, PLAN_ID);
    }

    private static WorkflowStatus.WorkflowStatusBuilder epic() {
        return status(JournalScopeType.EPIC, EPIC_ID);
    }

    private static WorkflowStatus.WorkflowStatusBuilder status(JournalScopeType scopeType, String scopeId) {
        return WorkflowStatus.builder()
                .scopeType(scopeType)
                .scopeId(scopeId)
                .phase("phase-5-execute")
                .state("execute.review")
                .callStack(List.of())
                .policyCounters(Map.of())
                .waiverRequests(List.of())
                .retryCounters(RetryCounters.empty())
                .activeJobs(Map.of())
                .activeTasks(Map.of())
                .updatedAt(LAST_PROGRESS)
                .retained(Map.of());
    }

    static Stream<Arguments> idleSpans() {
        return Stream.of(
                Arguments.of(-1L, false),
                Arguments.of(0L, false),
                Arguments.of(599L, false),
                Arguments.of(600L, true),
                Arguments.of(601L, true),
                Arguments.of(3_600L, true));
    }

    @ParameterizedTest(name = "{0} seconds idle")
    @MethodSource("idleSpans")
    @DisplayName("a scope is held stalled at the threshold and past it, never one second below it")
    void shouldHoldFromTheThresholdOn(long idleSeconds, boolean stalled) {
        var activity = detector().detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(idleSeconds));

        assertAll(
                () -> assertEquals(stalled, activity.hold().isPresent()),
                () -> assertEquals(stalled ? 1 : 0, activity.needsOperator()));
        if (stalled) {
            var hold = activity.hold().orElseThrow();
            assertAll(
                    () -> assertEquals(600, StalledScopeDetector.THRESHOLD.toSeconds()),
                    () -> assertEquals("stalled", hold.kind()),
                    () -> assertEquals(Cause.NO_PROGRESS, hold.cause()),
                    () -> assertEquals("no_progress", hold.cause().wireName()),
                    () -> assertEquals(AuditRecord.Scope.PLAN, hold.scopeType()),
                    () -> assertEquals(PLAN_ID, hold.scopeId()),
                    () -> assertEquals("execute.review", hold.state()),
                    () -> assertEquals(LAST_PROGRESS, hold.idleSince()),
                    () -> assertEquals(idleSeconds, hold.idleSeconds()));
            assertEquals(1, records.size());
        } else {
            assertEquals(List.of(), records);
        }
    }

    static Stream<Arguments> awaitedEvents() {
        return Stream.of(
                Arguments.of("an active job", plan().activeJobs(Map.of("build",
                                new JobEntry("b-0000000000000001", "build", "running", LAST_PROGRESS))).build(), false,
                        false, false, false),
                Arguments.of("an active job binding", plan().activeJob(
                                new WorkflowStatus.ActiveJob("execute.review", "b-0000000000000001")).build(), false,
                        false, false, false),
                Arguments.of("a CI or review wait", plan().build(), false, false, false, true),
                Arguments.of("a queued task", plan().activeTasks(Map.of("reviewer",
                                new TaskEntry("qt-0000000000000001", "reviewer", "queued", null, null))).build(), false, false,
                        false, false),
                Arguments.of("a leased task", plan().activeTasks(Map.of("reviewer",
                                new TaskEntry("qt-0000000000000001", "reviewer", "leased", null, null))).build(), false, false,
                        false, false),
                Arguments.of("a pending condition", plan().pendingCondition(
                                new PendingCondition("job_incomplete", 0, null, "{}", LAST_PROGRESS)).build(), false, false,
                        false, false),
                Arguments.of("an operator wait", plan().build(), false, true, false, false),
                Arguments.of("a pause", plan().build(), true, false, false, false),
                Arguments.of("another hold", plan().build(), false, false, true, false),
                Arguments.of("a workspace scope", status(JournalScopeType.WORKSPACE, null).build(), false, false,
                        false, false),
                Arguments.of("an ended plan", plan().phase("completed").state("completed").build(), false, false,
                        false, false),
                Arguments.of("an aborted plan", plan().phase("aborted").state("plan.aborted").build(), false, false,
                        false, false));
    }

    @ParameterizedTest(name = "not stalled: {0}")
    @MethodSource("awaitedEvents")
    @DisplayName("a scope that awaits an event, waits, is paused, is held, or ended is never stalled, however idle")
    void shouldNotHoldScopeAwaitingAnEvent(String ignored, WorkflowStatus status, boolean paused, boolean operatorWait,
            boolean otherHold, boolean awaitsExternalEvent) {
        var activity = detector().detect(new View(PROJECT, status, paused, operatorWait, otherHold, awaitsExternalEvent),
                LAST_PROGRESS.plusSeconds(86_400));

        assertAll(
                () -> assertTrue(activity.hold().isEmpty()),
                () -> assertEquals(0, activity.needsOperator()),
                () -> assertEquals(List.of(), records),
                () -> assertEquals(List.of(), released));
    }

    @Test
    @DisplayName("an epic is stalled by the same definition, and names its scope type")
    void shouldHoldAnEpic() {
        var activity = detector().detect(View.of(PROJECT, epic().build()), LAST_PROGRESS.plusSeconds(600));

        assertEquals(AuditRecord.Scope.EPIC, activity.hold().orElseThrow().scopeType());
    }

    @Test
    @DisplayName("the record of an episode is one plan_stalled transition with the warning, the cause and the span")
    void shouldRecordTheStall() {
        detector().detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(610));

        var record = records.getFirst();
        assertAll(
                () -> assertEquals(1, records.size()),
                () -> assertEquals("plan_stalled", record.event()),
                () -> assertEquals(AuditEvent.PLAN_STALLED, record.event()),
                () -> assertEquals("transition", record.category()),
                () -> assertEquals("warning", record.outcome()),
                () -> assertEquals(Map.of("scope_type", "plan", "scope_id", PLAN_ID, "state", "execute.review",
                                "cause", "no_progress", "idle_since", LAST_PROGRESS.toString(), "idle_seconds", 610L),
                        record.details()));
    }

    @Test
    @DisplayName("a further detection of one episode reports the idle span of that read")
    void shouldReportTheSpanOfTheRead() {
        var detector = detector();

        var first = detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(700));
        var second = detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(800));

        assertAll(
                () -> assertEquals(700, first.hold().orElseThrow().idleSeconds()),
                () -> assertEquals(800, second.hold().orElseThrow().idleSeconds()),
                () -> assertEquals(1, records.size()));
    }

    @Test
    @DisplayName("a second detection in one episode appends no second record, and announces the hold once")
    void shouldKeepOneRecordPerEpisode() {
        var detector = detector();

        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(700));
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(800));
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(900));

        assertAll(
                () -> assertEquals(1, records.size()),
                () -> assertEquals(1, opened.size()),
                () -> assertEquals(List.of(), released));
    }

    @Test
    @DisplayName("a hold that clears by itself announces plan.hold_released, and a new episode records again")
    void shouldReleaseByItselfAndStartANewEpisode() {
        var detector = detector();
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(700));

        var progressed = plan().updatedAt(LAST_PROGRESS.plusSeconds(750)).build();
        var cleared = detector.detect(View.of(PROJECT, progressed), LAST_PROGRESS.plusSeconds(760));
        var again = detector.detect(View.of(PROJECT, progressed), LAST_PROGRESS.plusSeconds(1_350));

        assertAll(
                () -> assertEquals("plan.hold_released", StalledScopeDetector.HOLD_RELEASED),
                () -> assertTrue(cleared.hold().isEmpty()),
                () -> assertEquals(1, released.size()),
                () -> assertEquals(List.of("plan.hold_released"), reports),
                () -> assertEquals(LAST_PROGRESS, released.getFirst().idleSince()),
                () -> assertTrue(again.hold().isPresent()),
                () -> assertEquals(2, records.size()));
    }

    @Test
    @DisplayName("an epic's hold clears by itself with the same report")
    void shouldReleaseAnEpic() {
        var detector = detector();
        detector.detect(View.of(PROJECT, epic().build()), LAST_PROGRESS.plusSeconds(700));

        detector.detect(View.of(PROJECT, epic().updatedAt(LAST_PROGRESS.plusSeconds(750)).build()),
                LAST_PROGRESS.plusSeconds(760));

        assertAll(
                () -> assertEquals(1, released.size()),
                () -> assertEquals("an-epic", released.getFirst().scopeId()),
                () -> assertEquals(List.of("plan.hold_released"), reports));
    }

    @Test
    @DisplayName("two detections racing on one episode append one record and announce one hold")
    void shouldKeepOneRecordUnderConcurrentDetections() {
        var shared = detector();
        var view = View.of(PROJECT, plan().build());
        var now = LAST_PROGRESS.plusSeconds(700);
        var start = new CyclicBarrier(2);

        var first = new Thread(() -> await(start, shared, view, now));
        var second = new Thread(() -> await(start, shared, view, now));
        first.start();
        second.start();
        join(first);
        join(second);

        assertAll(
                () -> assertEquals(1, records.size()),
                () -> assertEquals(1, opened.size()));
    }

    private static void await(CyclicBarrier start, StalledScopeDetector detector, View view, Instant now) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (BrokenBarrierException e) {
            return;
        }
        detector.detect(view, now);
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("a refused audit record leaves the episode open, and the next detection records it once")
    void shouldRetryARefusedRecord() {
        var fail = new AtomicBoolean(true);
        var detector = new StalledScopeDetector(event -> {
            if (fail.getAndSet(false)) {
                throw new UncheckedIOException(new IOException("audit log unavailable"));
            }
            records.add(event);
        }, new OperatorPort() {

            @Override
            public void holdOpened(StalledHold hold) {
                opened.add(hold);
            }

            @Override
            public void holdReleased(StalledHold hold, String report) {
                released.add(hold);
            }
        });

        assertThrows(UncheckedIOException.class,
                () -> detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(700)));
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(710));

        assertAll(
                () -> assertEquals(1, records.size()),
                () -> assertEquals(1, opened.size()));
    }

    @Test
    @DisplayName("a paused scope that has a stall episode open releases it by itself as well")
    void shouldReleaseWhenTheScopeIsPaused() {
        var detector = detector();
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(700));

        var activity = detector.detect(new View(PROJECT, plan().build(), true, false, false, false),
                LAST_PROGRESS.plusSeconds(710));

        assertAll(
                () -> assertTrue(activity.hold().isEmpty()),
                () -> assertEquals(1, released.size()));
    }

    @Test
    @DisplayName("the detection keeps the stall out of the stored status: no member is added or changed")
    void shouldKeepTheStallOutOfTheStoredStatus() {
        var status = plan().build();

        detector().detect(View.of(PROJECT, status), LAST_PROGRESS.plusSeconds(700));

        var components = Stream.of(WorkflowStatus.class.getRecordComponents())
                .map(component -> component.getName()).toList();
        assertAll(
                () -> assertEquals(plan().build(), status),
                () -> assertTrue(status.retained().isEmpty()),
                () -> assertFalse(components.contains("hold")),
                () -> assertFalse(components.contains("stalled")),
                () -> assertFalse(components.contains(AuditEvent.PLAN_STALLED)));
    }

    @Test
    @DisplayName("the cause is the one closed value of the specification")
    void shouldNameOneCause() {
        var causes = Stream.of(Cause.values()).map(Cause::wireName).toList();

        assertEquals(List.of("no_progress"), causes);
    }

    @Test
    @DisplayName("a missing project, status, view, instant or hold is refused")
    void shouldRefuseMissingValues() {
        var status = plan().build();

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new View(null, status, false, false, false, false)),
                () -> assertThrows(NullPointerException.class, () -> new View(PROJECT, null, false, false, false, false)),
                () -> assertThrows(NullPointerException.class,
                        () -> detector().detect(View.of(PROJECT, status), null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new StalledHold(null, PLAN_ID, "execute.review", Cause.NO_PROGRESS, LAST_PROGRESS, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new StalledHold(AuditRecord.Scope.PLAN, null, "execute.review", Cause.NO_PROGRESS,
                                LAST_PROGRESS, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new StalledHold(AuditRecord.Scope.PLAN, PLAN_ID, null, Cause.NO_PROGRESS, LAST_PROGRESS,
                                1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new StalledHold(AuditRecord.Scope.PLAN, PLAN_ID, "execute.review", null, LAST_PROGRESS,
                                1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new StalledHold(AuditRecord.Scope.PLAN, PLAN_ID, "execute.review", Cause.NO_PROGRESS, null,
                                1)),
                () -> assertThrows(NullPointerException.class, () -> new Activity(null)));
    }

    @Test
    @DisplayName("the threshold is the value of the timeouts specification, read from it")
    void thresholdIsTheSpecifiedValue() throws Exception {
        var property = System.getProperty("pm.specification.root");
        var root = property == null || property.isBlank() ? Path.of("..") : Path.of(property);
        var specification = Files.readString(root.toAbsolutePath().normalize()
                .resolve("doc/specification/timeouts/02-runtime-leases-operator-web.adoc"), UTF_8);
        var matcher = Pattern.compile("\\[\\[stalled-threshold\\]\\]Stalled threshold\\s*\\n\\s*\\|`(\\d+)`")
                .matcher(specification);

        assertTrue(matcher.find(), "the specification states no stalled threshold");
        assertEquals(Long.parseLong(matcher.group(1)), StalledScopeDetector.THRESHOLD.toSeconds());
    }
}
