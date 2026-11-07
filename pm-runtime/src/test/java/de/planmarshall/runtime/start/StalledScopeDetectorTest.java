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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * <p>
 * The threshold boundary, the record name, its detail keys, the cause and the report are the specified contract; the
 * project, the scope ids, the instant of the last progress, the idle spans past the threshold and the failure
 * messages are generated.
 */
@EnableGeneratorController
@DisplayName("Stalled scope detector")
class StalledScopeDetectorTest {

    /** The specified stalled threshold in seconds; the boundary cases of the idle span are counted from it. */
    private static final long THRESHOLD = 600;
    private static final Instant LAST_PROGRESS =
            Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private static final String PROJECT = "project-" + name();
    private static final String PLAN_ID = "plan-" + name();
    private static final String EPIC_ID = "epic-" + name();
    /** Fixture: the state a plan rests in; any state that awaits no event serves. */
    private static final String STATE = "execute.review";

    private final List<AuditEvent> records = new ArrayList<>();
    private final List<StalledHold> opened = new ArrayList<>();
    private final List<StalledHold> released = new ArrayList<>();
    private final List<String> reports = new ArrayList<>();
    private final String failure = Generators.letterStrings(3, 40).next();

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return a generated id with the given prefix and 16 hexadecimal digits */
    private static String hexId(String prefix) {
        return prefix + Generators.strings("0123456789abcdef", 16, 16).next();
    }

    /** @return a generated idle span of at least the threshold, in seconds */
    private static long overThreshold() {
        return THRESHOLD + Generators.longs(0, 3_600).next();
    }

    /** @return a generated span of at least one second */
    private static long span() {
        return Generators.longs(1, 3_600).next();
    }

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
        // Fixture: the phase of the state the scope rests in
        return WorkflowStatus.builder()
                .scopeType(scopeType)
                .scopeId(scopeId)
                .phase("phase-5-execute")
                .state(STATE)
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
                Arguments.of(THRESHOLD - 1, false),
                Arguments.of(THRESHOLD, true),
                Arguments.of(THRESHOLD + 1, true),
                Arguments.of(THRESHOLD + 1 + span(), true));
    }

    @ParameterizedTest(name = "{0} seconds idle")
    @MethodSource("idleSpans")
    @DisplayName("a scope is held stalled at the threshold and past it, never one second below it (the boundary cases)")
    void shouldHoldFromTheThresholdOn(long idleSeconds, boolean stalled) {
        var activity = detector().detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(idleSeconds));

        assertAll(
                () -> assertEquals(stalled, activity.hold().isPresent(), "the hold must follow the threshold"),
                () -> assertEquals(stalled ? 1 : 0, activity.needsOperator(),
                        "a stall must need the operator once"));
        if (stalled) {
            var hold = activity.hold().orElseThrow();
            assertAll(
                    () -> assertEquals(THRESHOLD, StalledScopeDetector.THRESHOLD.toSeconds(),
                            "the threshold must be 600 seconds"),
                    () -> assertEquals("stalled", StalledHold.KIND, "the hold kind must be stalled"),
                    () -> assertEquals(Cause.NO_PROGRESS, hold.cause(), "the cause must be no progress"),
                    () -> assertEquals("no_progress", hold.cause().wireName(), "the wire name must be no_progress"),
                    () -> assertEquals(AuditRecord.Scope.PLAN, hold.scopeType(), "the hold must name the plan scope"),
                    () -> assertEquals(PLAN_ID, hold.scopeId(), "the hold must name the plan id"),
                    () -> assertEquals(STATE, hold.state(), "the hold must name the resting state"),
                    () -> assertEquals(LAST_PROGRESS, hold.idleSince(), "the hold must name the last progress"),
                    () -> assertEquals(idleSeconds, hold.idleSeconds(), "the hold must carry the idle span"));
            assertEquals(1, records.size(), "a stall must record one plan_stalled record");
        } else {
            assertEquals(List.of(), records, "no record must be appended below the threshold");
        }
    }

    static Stream<Arguments> awaitedEvents() {
        var jobId = hexId("b-");
        var taskId = hexId("qt-");
        // Contract literals: the declared job kind and task role, the job and task states, the pending condition kind
        // and the ended phases and states
        var kind = "build";
        var role = "reviewer";
        return Stream.of(
                Arguments.of("an active job", plan().activeJobs(Map.of(kind,
                                new JobEntry(jobId, kind, "running", LAST_PROGRESS))).build(), false,
                        false, false, false),
                Arguments.of("an active job binding", plan().activeJob(
                                new WorkflowStatus.ActiveJob(STATE, jobId)).build(), false,
                        false, false, false),
                Arguments.of("a CI or review wait", plan().build(), false, false, false, true),
                Arguments.of("a queued task", plan().activeTasks(Map.of(role,
                                new TaskEntry(taskId, role, "queued", null, null))).build(), false, false,
                        false, false),
                Arguments.of("a leased task", plan().activeTasks(Map.of(role,
                                new TaskEntry(taskId, role, "leased", null, null))).build(), false, false,
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
                LAST_PROGRESS.plusSeconds(overThreshold()));

        assertAll(
                () -> assertTrue(activity.hold().isEmpty(), "a scope awaiting an event must not be held"),
                () -> assertEquals(0, activity.needsOperator(), "a scope awaiting an event needs no operator"),
                () -> assertEquals(List.of(), records, "no plan_stalled record must be appended"),
                () -> assertEquals(List.of(), released, "no hold must be released"));
    }

    @Test
    @DisplayName("an epic is stalled by the same definition, and names its scope type")
    void shouldHoldAnEpic() {
        var activity = detector().detect(View.of(PROJECT, epic().build()), LAST_PROGRESS.plusSeconds(overThreshold()));

        assertEquals(AuditRecord.Scope.EPIC, activity.hold().orElseThrow().scopeType(),
                "the hold of an epic must name the epic scope");
    }

    @Test
    @DisplayName("the record of an episode is one plan_stalled transition with the warning, the cause and the span")
    void shouldRecordTheStall() {
        var idle = overThreshold();

        detector().detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(idle));

        var stalled = records.getFirst();
        assertAll(
                () -> assertEquals(1, records.size(), "the episode must append one record"),
                () -> assertEquals("plan_stalled", stalled.event(), "the record must be named plan_stalled"),
                () -> assertEquals(AuditEvent.PLAN_STALLED, stalled.event(), "the record must be the PLAN_STALLED one"),
                () -> assertEquals("transition", stalled.category(), "the record must be a transition"),
                () -> assertEquals("warning", stalled.outcome(), "the record must carry the warning outcome"),
                // Contract literals: the detail keys and values of plan_stalled
                () -> assertEquals(Map.of("scope_type", "plan", "scope_id", PLAN_ID, "state", STATE,
                                "cause", "no_progress", "idle_since", LAST_PROGRESS.toString(), "idle_seconds", idle),
                        stalled.details(), "the record must carry the cause and the span"));
    }

    @Test
    @DisplayName("two scopes of one project, and the same plan id of another project, keep their own episodes")
    void shouldKeepEpisodesPerScope() {
        var detector = detector();
        var idle = overThreshold();

        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(idle));
        detector.detect(View.of(PROJECT, epic().build()), LAST_PROGRESS.plusSeconds(idle + span()));
        detector.detect(View.of("other-" + name(), plan().build()), LAST_PROGRESS.plusSeconds(idle + 2 * span()));

        assertEquals(3, records.size(), "each scope must keep its own episode");
    }

    @Test
    @DisplayName("a progress no read observed ends the episode and starts a new one")
    void shouldStartANewEpisodeAfterUnobservedProgress() {
        var detector = detector();
        var firstRead = overThreshold();
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(firstRead));
        var progressAt = LAST_PROGRESS.plusSeconds(firstRead + span());
        var idle = overThreshold();

        var progressed = plan().updatedAt(progressAt).build();
        var activity = detector.detect(View.of(PROJECT, progressed), progressAt.plusSeconds(idle));

        assertAll(
                () -> assertEquals(2, records.size(), "the new episode must append its own record"),
                () -> assertEquals(1, released.size(), "the old episode must be released"),
                () -> assertEquals(LAST_PROGRESS, released.getFirst().idleSince(),
                        "the released hold is the old episode"),
                () -> assertEquals(progressAt, activity.hold().orElseThrow().idleSince(),
                        "the new hold must count from the progress"),
                () -> assertEquals(idle, activity.hold().orElseThrow().idleSeconds(),
                        "the new hold must carry its own idle span"));
    }

    @Test
    @DisplayName("a further detection of one episode reports the idle span of that read")
    void shouldReportTheSpanOfTheRead() {
        var detector = detector();
        var firstIdle = overThreshold();
        var secondIdle = firstIdle + span();

        var first = detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(firstIdle));
        var second = detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(secondIdle));

        assertAll(
                () -> assertEquals(firstIdle, first.hold().orElseThrow().idleSeconds(),
                        "the first read must report its span"),
                () -> assertEquals(secondIdle, second.hold().orElseThrow().idleSeconds(),
                        "the second read must report its span"),
                () -> assertEquals(1, records.size(), "one episode must append one record"));
    }

    @Test
    @DisplayName("a second detection in one episode appends no second record, and announces the hold once")
    void shouldKeepOneRecordPerEpisode() {
        var detector = detector();
        var idle = overThreshold();

        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(idle));
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(idle + span()));
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(idle + 3_600 + span()));

        assertAll(
                () -> assertEquals(1, records.size(), "one episode must append one record"),
                () -> assertEquals(1, opened.size(), "the hold must be announced once"),
                () -> assertEquals(List.of(), released, "no hold must be released"));
    }

    @Test
    @DisplayName("a hold that clears by itself announces plan.hold_released, and a new episode records again")
    void shouldReleaseByItselfAndStartANewEpisode() {
        var detector = detector();
        var firstRead = overThreshold();
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(firstRead));
        var progressAt = LAST_PROGRESS.plusSeconds(firstRead + span());

        var progressed = plan().updatedAt(progressAt).build();
        var cleared = detector.detect(View.of(PROJECT, progressed),
                progressAt.plusSeconds(Generators.longs(0, THRESHOLD - 1).next()));
        var again = detector.detect(View.of(PROJECT, progressed), progressAt.plusSeconds(overThreshold()));

        assertAll(
                () -> assertEquals("plan.hold_released", StalledScopeDetector.HOLD_RELEASED,
                        "the report must be plan.hold_released"),
                () -> assertTrue(cleared.hold().isEmpty(), "the hold must clear after the progress"),
                () -> assertEquals(1, released.size(), "the cleared hold must be released once"),
                () -> assertEquals(List.of("plan.hold_released"), reports, "the release must announce the report"),
                () -> assertEquals(LAST_PROGRESS, released.getFirst().idleSince(),
                        "the released hold is the old episode"),
                () -> assertTrue(again.hold().isPresent(), "a new stall must be held again"),
                () -> assertEquals(2, records.size(), "the new episode must append a record"));
    }

    @Test
    @DisplayName("an epic's hold clears by itself with the same report")
    void shouldReleaseAnEpic() {
        var detector = detector();
        var firstRead = overThreshold();
        detector.detect(View.of(PROJECT, epic().build()), LAST_PROGRESS.plusSeconds(firstRead));
        var progressAt = LAST_PROGRESS.plusSeconds(firstRead + span());

        detector.detect(View.of(PROJECT, epic().updatedAt(progressAt).build()), progressAt.plusSeconds(span() % 60));

        assertAll(
                () -> assertEquals(1, released.size(), "the epic's hold must be released once"),
                () -> assertEquals(EPIC_ID, released.getFirst().scopeId(), "the released hold must name the epic"),
                () -> assertEquals(List.of("plan.hold_released"), reports, "the release must announce the report"));
    }

    @Test
    @DisplayName("two detections racing on one episode append one record and announce one hold")
    void shouldKeepOneRecordUnderConcurrentDetections() {
        var shared = detector();
        var view = View.of(PROJECT, plan().build());
        var now = LAST_PROGRESS.plusSeconds(overThreshold());
        var start = new CyclicBarrier(2);

        var first = new Thread(() -> await(start, shared, view, now));
        var second = new Thread(() -> await(start, shared, view, now));
        first.start();
        second.start();
        join(first);
        join(second);

        assertAll(
                () -> assertEquals(1, records.size(), "the race must append one record"),
                () -> assertEquals(1, opened.size(), "the race must announce one hold"));
    }

    private static void await(CyclicBarrier start, StalledScopeDetector detector, View view, Instant now) {
        try {
            start.await();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return;
        } catch (BrokenBarrierException _) {
            return;
        }
        detector.detect(view, now);
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("a refused audit record leaves the episode open, and the next detection records it once")
    void shouldRetryARefusedRecord() {
        var fail = new AtomicBoolean(true);
        var detector = new StalledScopeDetector(event -> {
            if (fail.getAndSet(false)) {
                throw new UncheckedIOException(new IOException(failure));
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

        var view = View.of(PROJECT, plan().build());
        var idle = overThreshold();
        var failedAt = LAST_PROGRESS.plusSeconds(idle);

        assertThrows(UncheckedIOException.class, () -> detector.detect(view, failedAt),
                "the refused record must fail the detection");
        detector.detect(view, LAST_PROGRESS.plusSeconds(idle + span()));

        assertAll(
                () -> assertEquals(1, records.size(), "the next detection must record once"),
                () -> assertEquals(1, opened.size(), "the hold must be announced once"));
    }

    @Test
    @DisplayName("a release announcement that fails still announces the hold of the new episode")
    void shouldAnnounceTheNewHoldWhenTheReleaseFails() {
        var fail = new AtomicBoolean(true);
        var detector = new StalledScopeDetector(records::add, new OperatorPort() {

            @Override
            public void holdOpened(StalledHold hold) {
                opened.add(hold);
            }

            @Override
            public void holdReleased(StalledHold hold, String report) {
                if (fail.getAndSet(false)) {
                    throw new IllegalStateException(failure);
                }
                released.add(hold);
            }
        });
        var firstRead = overThreshold();
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(firstRead));
        var progressAt = LAST_PROGRESS.plusSeconds(firstRead + span());
        var progressed = plan().updatedAt(progressAt).build();
        var view = View.of(PROJECT, progressed);
        var idle = overThreshold();
        var failedAt = progressAt.plusSeconds(idle);

        var thrown = assertThrows(IllegalStateException.class, () -> detector.detect(view, failedAt),
                "the failing release must fail the detection");

        detector.detect(view, progressAt.plusSeconds(idle + span()));

        assertAll(
                () -> assertEquals(failure, thrown.getMessage(), "the failure of the release must be passed on"),
                () -> assertEquals(2, records.size(), "both episodes must append their record"),
                () -> assertEquals(2, opened.size(), "both holds must be announced"),
                () -> assertEquals(1, released.size(), "the release must be announced once"));
    }

    @Test
    @DisplayName("an opening announcement that fails is retried by the next read, without a second record")
    void shouldRetryAFailedOpeningAnnouncement() {
        var fail = new AtomicBoolean(true);
        var detector = new StalledScopeDetector(records::add, new OperatorPort() {

            @Override
            public void holdOpened(StalledHold hold) {
                if (fail.getAndSet(false)) {
                    throw new IllegalStateException(failure);
                }
                opened.add(hold);
            }

            @Override
            public void holdReleased(StalledHold hold, String report) {
                released.add(hold);
            }
        });

        var view = View.of(PROJECT, plan().build());
        var idle = overThreshold();
        var failedAt = LAST_PROGRESS.plusSeconds(idle);

        assertThrows(IllegalStateException.class, () -> detector.detect(view, failedAt),
                "the failing announcement must fail the detection");
        detector.detect(view, LAST_PROGRESS.plusSeconds(idle + span()));

        assertAll(
                () -> assertEquals(1, records.size(), "the retry must not append a second record"),
                () -> assertEquals(1, opened.size(), "the retry must announce the hold once"));
    }

    @Test
    @DisplayName("a refused replacement record queues no release until the append succeeds")
    void shouldNotQueueAReleaseForARefusedReplacementRecord() {
        var fail = new AtomicBoolean(false);
        var detector = new StalledScopeDetector(event -> {
            if (fail.getAndSet(false)) {
                throw new UncheckedIOException(new IOException(failure));
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
        var firstRead = overThreshold();
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(firstRead));
        var progressAt = LAST_PROGRESS.plusSeconds(firstRead + span());
        var progressed = plan().updatedAt(progressAt).build();
        fail.set(true);
        var view = View.of(PROJECT, progressed);
        var idle = overThreshold();
        var failedAt = progressAt.plusSeconds(idle);

        assertThrows(UncheckedIOException.class, () -> detector.detect(view, failedAt),
                "the refused replacement must fail the detection");
        detector.detect(view, progressAt.plusSeconds(idle + span()));

        assertAll(
                () -> assertEquals(2, records.size(), "both episodes must append their record"),
                () -> assertEquals(1, released.size(), "the release must be announced once"),
                () -> assertEquals(LAST_PROGRESS, released.getFirst().idleSince(),
                        "the released hold is the old episode"));
    }

    @Test
    @DisplayName("a paused scope that has a stall episode open releases it by itself as well")
    void shouldReleaseWhenTheScopeIsPaused() {
        var detector = detector();
        var idle = overThreshold();
        detector.detect(View.of(PROJECT, plan().build()), LAST_PROGRESS.plusSeconds(idle));

        var activity = detector.detect(new View(PROJECT, plan().build(), true, false, false, false),
                LAST_PROGRESS.plusSeconds(idle + span()));

        assertAll(
                () -> assertTrue(activity.hold().isEmpty(), "a paused scope must not be held"),
                () -> assertEquals(1, released.size(), "the open episode must be released"));
    }

    @Test
    @DisplayName("the detection keeps the stall out of the stored status: no member is added or changed")
    void shouldKeepTheStallOutOfTheStoredStatus() {
        var status = plan().build();

        detector().detect(View.of(PROJECT, status), LAST_PROGRESS.plusSeconds(overThreshold()));

        var components = Stream.of(WorkflowStatus.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertAll(
                () -> assertEquals(plan().build(), status, "the stored status must stay unchanged"),
                () -> assertTrue(status.retained().isEmpty(), "nothing must be retained in the status"),
                () -> assertFalse(components.contains("hold"), "the status must have no hold member"),
                () -> assertFalse(components.contains("stalled"), "the status must have no stalled member"),
                () -> assertFalse(components.contains(AuditEvent.PLAN_STALLED),
                        "the status must have no plan_stalled member"));
    }

    @Test
    @DisplayName("the cause is the one closed value of the specification")
    void shouldNameOneCause() {
        var causes = Stream.of(Cause.values()).map(Cause::wireName).toList();

        assertEquals(List.of("no_progress"), causes, "no_progress must be the only cause");
    }

    @Test
    @DisplayName("a missing project, status, view, instant or hold is refused")
    void shouldRefuseMissingValues() {
        var status = plan().build();
        var target = detector();
        var view = View.of(PROJECT, status);
        var idle = overThreshold();

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new View(null, status, false, false, false, false),
                        "a view without a project must be refused"),
                () -> assertThrows(NullPointerException.class, () -> new View(PROJECT, null, false, false, false, false),
                        "a view without a status must be refused"),
                () -> assertThrows(NullPointerException.class, () -> target.detect(view, null),
                        "a detection without an instant is refused"),
                () -> assertThrows(NullPointerException.class,
                        () -> new StalledHold(null, PLAN_ID, STATE, Cause.NO_PROGRESS, LAST_PROGRESS, idle),
                        "a hold without a scope type must be refused"),
                () -> assertThrows(NullPointerException.class,
                        () -> new StalledHold(AuditRecord.Scope.PLAN, null, STATE, Cause.NO_PROGRESS,
                                LAST_PROGRESS, idle),
                        "a hold without a scope id must be refused"),
                () -> assertThrows(NullPointerException.class,
                        () -> new StalledHold(AuditRecord.Scope.PLAN, PLAN_ID, null, Cause.NO_PROGRESS, LAST_PROGRESS,
                                idle),
                        "a hold without a state must be refused"),
                () -> assertThrows(NullPointerException.class,
                        () -> new StalledHold(AuditRecord.Scope.PLAN, PLAN_ID, STATE, null, LAST_PROGRESS, idle),
                        "a hold without a cause must be refused"),
                () -> assertThrows(NullPointerException.class,
                        () -> new StalledHold(AuditRecord.Scope.PLAN, PLAN_ID, STATE, Cause.NO_PROGRESS, null, idle),
                        "a hold without an idle start must be refused"),
                () -> assertThrows(NullPointerException.class, () -> new Activity(null),
                        "an activity without a hold must be refused"));
    }

    @Test
    @DisplayName("the threshold is read from the module's limits resource")
    void thresholdIsReadFromTheLimitsResource() {
        var value = LimitProperties.read(StalledScopeDetector.LIMITS, StalledScopeDetector.THRESHOLD_KEY);

        assertEquals(value, StalledScopeDetector.THRESHOLD.toSeconds(), "the threshold must come from the resource");
    }

    @Test
    @DisplayName("an absent limits resource fails the load instead of defaulting")
    void anAbsentLimitsResourceFailsTheLoad() {
        var absent = "/de/planmarshall/runtime/start/absent-limits.properties";

        assertThrows(IllegalStateException.class,
                () -> LimitProperties.read(absent, StalledScopeDetector.THRESHOLD_KEY),
                "an absent limits resource must fail the load");
    }

    @Test
    @DisplayName("an absent key fails the load instead of defaulting")
    void anAbsentKeyFailsTheLoad() {
        assertThrows(IllegalStateException.class,
                () -> LimitProperties.value(new Properties(), StalledScopeDetector.THRESHOLD_KEY, "limits"),
                "an absent limit key must fail the load");
    }

    @Test
    @DisplayName("a value that is not a number fails the load instead of defaulting")
    void aNonNumericValueFailsTheLoad() {
        var properties = new Properties();
        properties.setProperty(StalledScopeDetector.THRESHOLD_KEY, "not-a-number");

        assertThrows(IllegalStateException.class,
                () -> LimitProperties.value(properties, StalledScopeDetector.THRESHOLD_KEY, "limits"),
                "a non-numeric limit must fail the load");
    }
}
