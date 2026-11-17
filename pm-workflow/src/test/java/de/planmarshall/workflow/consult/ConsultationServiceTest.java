/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.consult;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.ConsultationStatus;
import de.planmarshall.core.queue.QueueTaskRecord.DeliveredTo;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.workflow.task.CallResult;
import de.planmarshall.workflow.task.TaskCall;
import de.planmarshall.workflow.task.TaskProtocol;
import de.planmarshall.workflow.task.TaskRefusal;
import de.planmarshall.workflow.task.WaitAnswer;
import de.planmarshall.workflow.task.WaitRequest;

/**
 * The consultation of one role by another (Model Work § Consultation; Timeouts &amp; Budgets § Consultation Bound): the
 * link rules, the serving by the task protocol, the answer through the next wait, the delivery to a successor after a
 * fence, the {@code unavailable} delivery of a lost consultation and the {@code consultation_ids} of a submit.
 */
@EnableGeneratorController
@DisplayName("Consultation through the server")
class ConsultationServiceTest {

    // A queue task id in the wire format, deliberately absent from the store.
    private static final String UNKNOWN_CONSULTATION = "qt-00000000000000ff";

    @TempDir
    Path base;

    private QueueStore queue;
    private ConsultationService service;
    private TaskProtocol protocol;

    @BeforeEach
    void setUp() {
        var locks = new ConsultTestLocks();
        queue = QueueStore.ofProject(base, "project-a", locks);
        service = new ConsultationService(queue);
        protocol = TaskProtocol.builder(queue, locks).build();
        queue.create(ConsultationFixtures.executing(ConsultationFixtures.ASKER, ConsultationFixtures.ASKER_ROLE,
                ConsultationFixtures.ASKING_GENERATION));
    }

    @Test
    @DisplayName("a consultation is answered once and a second answer is refused")
    void answersOnceAndRefusesSecond() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);

        var answered = assertInstanceOf(ConsultationOutcome.Answered.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT),
                "the first answer is accepted");
        var second = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        Generators.letterStrings(3, 20).next(), ConsultationFixtures.AT.plusSeconds(1)),
                "a second answer is refused");

        var fact = answered.facts().getFirst();
        var consultation = queue.read(opened.consultationId()).orElseThrow();
        var links = service.links(answered.askingTask(), List.of(ConsultationFixtures.REVIEWER));
        var reconciled = service.reconcile(ConsultationFixtures.AT.plusSeconds(1), Duration.ofSeconds(60));
        assertAll(
                () -> assertEquals(ConsultationStatus.ANSWERED, fact.status(),
                        "the fact reports the answered status"),
                () -> assertEquals(ConsultationFixtures.ANSWER, fact.answer(), "the fact carries the answer"),
                () -> assertEquals(Status.EXECUTING, answered.askingTask().status(), "the asker keeps executing"),
                () -> assertEquals(DeliveredTo.WAIT, answered.askingTask().consultations().getFirst().deliveredTo(),
                        "the consultation is delivered to the wait"),
                () -> assertNotNull(answered.askingTask().consultations().getFirst().deliveredAt(),
                        "the delivery carries its timestamp"),
                () -> assertEquals(List.of(ConsultationFixtures.REVIEWER),
                        links.stream().map(ConsultationLink::role).toList(),
                        "the consulted role is the only link"),
                () -> assertEquals(0, reconciled, "nothing is reconciled while executing"),
                () -> assertEquals(DeliveredTo.WAIT, queue.read(ConsultationFixtures.ASKER).orElseThrow()
                                .consultations().getFirst().deliveredTo(),
                        "the stored asker keeps the wait delivery"),
                () -> assertEquals(ConsultationRefusal.STALE_ENTITY, second.refusal(),
                        "the second answer reports a stale entity"),
                () -> assertEquals(Status.CLOSED, consultation.status(), "the consultation is closed"),
                () -> assertEquals(Outcome.SUBMITTED, consultation.outcome(),
                        "the consultation records the submit"));
    }

    @Test
    @DisplayName("a fenced asker delivers the answer to the successor's offer once and is offered no consult for it")
    void fencedAskerDeliversToTheSuccessorOfferOnce() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        protocol.fence(ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.AT);

        var answered = assertInstanceOf(ConsultationOutcome.Answered.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT.plusSeconds(1)),
                "the answer to the fenced asker is accepted");
        var successor = assertInstanceOf(WaitAnswer.Offered.class, protocol.wait(
                        new WaitRequest(ConsultationFixtures.SUCCESSOR_GENERATION, ConsultationFixtures.ASKER_ROLE,
                                ConsultationFixtures.DIGEST, ConsultationFixtures.DIGEST, 3),
                        ConsultationFixtures.AT.plusSeconds(2)),
                "the successor is offered the answer");
        var acknowledged = assertInstanceOf(CallResult.Accepted.class, protocol.acknowledge(
                        new TaskCall(ConsultationFixtures.ASKER, ConsultationFixtures.SUCCESSOR_GENERATION),
                        ConsultationFixtures.AT.plusSeconds(3)),
                "the successor acknowledges the answer");
        var links = service.links(acknowledged.task(),
                List.of(ConsultationFixtures.REVIEWER, ConsultationFixtures.SECOND_REVIEWER));

        var delivered = answered.askingTask().consultations().getFirst();
        var offered = successor.task();
        assertAll(
                () -> assertEquals(DeliveredTo.FACTS, delivered.deliveredTo(), "the answer is delivered as facts"),
                () -> assertEquals(ConsultationStatus.ANSWERED, delivered.status(),
                        "the delivered consultation is answered"),
                () -> assertEquals(Status.QUEUED, answered.askingTask().status(),
                        "the fenced asker is queued again"),
                () -> assertEquals(1, offered.consultations().size(), "the successor holds one consultation"),
                () -> assertEquals(ConsultationStatus.ANSWERED, offered.consultations().getFirst().status(),
                        "the successor's consultation is answered"),
                () -> assertEquals(ConsultationFixtures.ANSWER, offered.consultations().getFirst().answer(),
                        "the successor sees the answer"),
                () -> assertEquals(1, service.facts(ConsultationFixtures.ASKER).size(),
                        "the fenced asker has one fact"),
                () -> assertEquals(List.of(ConsultationFixtures.SECOND_REVIEWER),
                        links.stream().map(ConsultationLink::role).toList(),
                        "only the second reviewer is linked"));
    }

    @Test
    @DisplayName("a fourth consult link of a task is not offered and a fourth consultation is refused")
    void fourthConsultLinkNotOffered() {
        for (var role : List.of(ConsultationFixtures.REVIEWER, ConsultationFixtures.SECOND_REVIEWER,
                ConsultationFixtures.THIRD_REVIEWER)) {
            var opened = opened(role);
            serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, role);
            assertInstanceOf(ConsultationOutcome.Answered.class,
                    service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                            ConsultationFixtures.ANSWER, ConsultationFixtures.AT),
                    "each of the three answers is accepted");
        }

        var fourthRole = "fourth-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var asker = queue.read(ConsultationFixtures.ASKER).orElseThrow();
        var links = service.links(asker, List.of(ConsultationFixtures.REVIEWER, ConsultationFixtures.SECOND_REVIEWER,
                ConsultationFixtures.THIRD_REVIEWER, fourthRole));
        var fourth = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.open(ConsultationFixtures.request(fourthRole, RoleClass.READ_ONLY)),
                "a fourth consultation is refused");

        assertAll(
                () -> assertEquals(3, asker.consultations().size(), "the asker keeps three consultations"),
                () -> assertTrue(links.isEmpty(), "a fourth link is not offered"),
                () -> assertEquals(ConsultationRefusal.CONSULTATION_BOUND_SPENT, fourth.refusal(),
                        "the bound is reported spent"));
    }

    @Test
    @DisplayName("a lost consultation is delivered with the status unavailable and no answer")
    void lostConsultationDeliveredUnavailable() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);

        var lost = assertInstanceOf(ConsultationOutcome.Answered.class,
                service.lost(opened.consultationId(), LostReason.ROLE_UNAVAILABLE, ConsultationFixtures.AT),
                "a lost consultation is delivered");
        var second = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.lost(opened.consultationId(), LostReason.ROLE_UNAVAILABLE,
                        ConsultationFixtures.AT.plusSeconds(1)),
                "a second lost report is refused");

        var fact = lost.facts().getFirst();
        var consultation = queue.read(opened.consultationId()).orElseThrow();
        assertAll(
                () -> assertEquals(ConsultationStatus.UNAVAILABLE, fact.status(),
                        "the fact reports the unavailable status"),
                () -> assertEquals("unavailable", fact.wireStatus(), "the wire status is unavailable"),
                () -> assertNull(fact.answer(), "an unavailable fact carries no answer"),
                () -> assertEquals(DeliveredTo.WAIT, lost.askingTask().consultations().getFirst().deliveredTo(),
                        "the lost consultation is delivered to the wait"),
                () -> assertEquals(Status.EXECUTING, lost.askingTask().status(), "the asker keeps executing"),
                () -> assertEquals(Outcome.LOST, consultation.outcome(), "the consultation records the loss"),
                () -> assertEquals(LostReason.ROLE_UNAVAILABLE, consultation.reason(), "the loss keeps its reason"),
                () -> assertEquals(ConsultationRefusal.STALE_ENTITY, second.refusal(),
                        "the second loss reports a stale entity"),
                () -> assertInstanceOf(SubmitValidation.Accepted.class,
                        service.validateSubmit(ConsultationFixtures.ASKER, List.of(opened.consultationId())),
                        "the delivered consultation validates the submit"));
    }

    @Test
    @DisplayName("a submit that omits a delivered consultation_id or names one not delivered is refused")
    void submitValidatesConsultationIds() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        assertInstanceOf(ConsultationOutcome.Answered.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT),
                "the consultation is answered before the submit");

        var missing = assertInstanceOf(SubmitValidation.Refused.class,
                service.validateSubmit(ConsultationFixtures.ASKER, List.of()),
                "a submit without the id is refused");
        var unknown = assertInstanceOf(SubmitValidation.Refused.class, service.validateSubmit(
                        ConsultationFixtures.ASKER, List.of(opened.consultationId(), UNKNOWN_CONSULTATION)),
                "a submit naming an unknown id is refused");
        var accepted = assertInstanceOf(SubmitValidation.Accepted.class,
                service.validateSubmit(ConsultationFixtures.ASKER, List.of(opened.consultationId())),
                "a submit naming the delivered id is accepted");

        var second = opened(ConsultationFixtures.SECOND_REVIEWER);
        serve(second.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.SECOND_REVIEWER);
        assertInstanceOf(ConsultationOutcome.Answered.class,
                service.answer(second.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT),
                "the second consultation is answered");
        var omitsSecond = assertInstanceOf(SubmitValidation.Refused.class,
                service.validateSubmit(ConsultationFixtures.ASKER, List.of(opened.consultationId())),
                "a submit omitting the second id is refused");

        assertAll(
                () -> assertTrue(missing.detail().contains(opened.consultationId()),
                        "the refusal names the missing id"),
                () -> assertTrue(unknown.detail().contains(UNKNOWN_CONSULTATION),
                        "the refusal names the unknown id"),
                () -> assertNotNull(accepted, "the accepted submit is not null"),
                () -> assertTrue(omitsSecond.detail().contains(second.consultationId()),
                        "the refusal names the omitted id"));
    }

    @Test
    @DisplayName("while a consultation is open, no consult link is offered and a second one is refused")
    void oneOpenConsultationAtATime() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        var asker = queue.read(ConsultationFixtures.ASKER).orElseThrow();

        var links = service.links(asker,
                List.of(ConsultationFixtures.REVIEWER, ConsultationFixtures.SECOND_REVIEWER));
        var second = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.open(ConsultationFixtures.request(ConsultationFixtures.ASKER,
                        ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.SECOND_REVIEWER)),
                "a second consultation while one is open is refused");

        assertAll(
                () -> assertTrue(links.isEmpty(), "no link is offered while consulting"),
                () -> assertEquals(Status.CONSULTING, asker.status(), "the asker is consulting"),
                () -> assertEquals(ConsultationRefusal.CONSULTATION_OPEN, second.refusal(),
                        "the refusal names the open consultation"),
                () -> assertEquals(opened.consultationId(), asker.consultations().getFirst().consultationId(),
                        "the asker carries the opened consultation"));
    }

    @Test
    @DisplayName("a consultation task offers no consult link: depth one")
    void depthOne() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        var consultation = queue.read(opened.consultationId()).orElseThrow();

        var links = service.links(consultation, List.of(ConsultationFixtures.SECOND_REVIEWER));
        var nested = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.open(new ConsultationRequest(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.SECOND_REVIEWER, RoleClass.READ_ONLY, false,
                        List.of(ConsultationFixtures.SECOND_REVIEWER), ConsultationFixtures.QUESTION,
                        ConsultationFixtures.AT)),
                "a nested consultation is refused");

        assertAll(
                () -> assertEquals(Status.EXECUTING, consultation.status(),
                        "the consultation task keeps executing"),
                () -> assertTrue(links.isEmpty(), "a consultation task offers no link"),
                () -> assertEquals(ConsultationRefusal.CONSULTATION_DEPTH, nested.refusal(),
                        "the refusal names the depth bound"));
    }

    @Test
    @DisplayName("a question and an answer have 1 to 4000 characters, and a zero_tool role is refused")
    void textBoundsAndRoleClass() {
        var emptyQuestion = new ConsultationRequest(ConsultationFixtures.ASKER, ConsultationFixtures.ASKING_GENERATION,
                ConsultationFixtures.REVIEWER, RoleClass.READ_ONLY, false, List.of(ConsultationFixtures.REVIEWER), "",
                ConsultationFixtures.AT);
        var longQuestion = new ConsultationRequest(ConsultationFixtures.ASKER,
                ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.REVIEWER, RoleClass.READ_ONLY, false,
                List.of(ConsultationFixtures.REVIEWER), "x".repeat(4_001), ConsultationFixtures.AT);
        var zeroTool = ConsultationFixtures.request(ConsultationFixtures.REVIEWER, RoleClass.ZERO_TOOL);

        var empty = assertInstanceOf(ConsultationOutcome.Refused.class, service.open(emptyQuestion),
                "an empty question is refused");
        var tooLong = assertInstanceOf(ConsultationOutcome.Refused.class, service.open(longQuestion),
                "a question over 4000 characters is refused");
        var noProtocol = assertInstanceOf(ConsultationOutcome.Refused.class, service.open(zeroTool),
                "a zero tool role is refused");

        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        var emptyAnswer = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, "",
                        ConsultationFixtures.AT),
                "an empty answer is refused");

        assertAll(
                () -> assertEquals(ConsultationRefusal.INVALID_LINK_ARGS, empty.refusal(),
                        "the empty question reports invalid arguments"),
                () -> assertEquals(ConsultationRefusal.INVALID_LINK_ARGS, tooLong.refusal(),
                        "the long question reports invalid arguments"),
                () -> assertEquals(ConsultationRefusal.CLASS_MISMATCH, noProtocol.refusal(),
                        "the zero tool role reports a class mismatch"),
                () -> assertEquals(ConsultationRefusal.INVALID_LINK_ARGS, emptyAnswer.refusal(),
                        "the empty answer reports invalid arguments"),
                () -> assertEquals(2, queue.records().size(), "the queue holds the asker and the opened task"));
    }

    @Test
    @DisplayName("an answer is refused while the asking task does not carry the consultation")
    void answerRefusedWhenAskerDoesNotCarryTheConsultation() {
        queue.create(ConsultationFixtures.consultationTask("qt-00000000000000aa", ConsultationFixtures.ASKER,
                ConsultationFixtures.REVIEWER));
        serve("qt-00000000000000aa", ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);

        var refused = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.answer("qt-00000000000000aa", ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT),
                "an answer for a task without the consultation is refused");

        assertAll(
                () -> assertEquals(ConsultationRefusal.JOB_SCOPE_MISMATCH, refused.refusal(),
                        "the refusal names the job scope mismatch"),
                () -> assertEquals(Status.EXECUTING,
                        queue.read("qt-00000000000000aa").orElseThrow().status(),
                        "the consultation task keeps executing"),
                () -> assertTrue(service.facts(ConsultationFixtures.ASKER).isEmpty(),
                        "the asker receives no fact"));
    }

    @Test
    @DisplayName("an answer recorded for a wait the asking generation lost reaches the successor once")
    void answerThenFenceDeliversToTheSuccessorOnce() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        assertInstanceOf(ConsultationOutcome.Answered.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT),
                "the answer is recorded before the fence");
        protocol.fence(ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.AT.plusSeconds(1));
        var repaired = service.reconcile(ConsultationFixtures.AT.plusSeconds(2), Duration.ofSeconds(60));

        var successor = assertInstanceOf(WaitAnswer.Offered.class, protocol.wait(
                        new WaitRequest(ConsultationFixtures.SUCCESSOR_GENERATION, ConsultationFixtures.ASKER_ROLE,
                                ConsultationFixtures.DIGEST, ConsultationFixtures.DIGEST, 3),
                        ConsultationFixtures.AT.plusSeconds(2)),
                "the successor is offered the recorded answer");
        var acknowledged = assertInstanceOf(CallResult.Accepted.class, protocol.acknowledge(
                        new TaskCall(ConsultationFixtures.ASKER, ConsultationFixtures.SUCCESSOR_GENERATION),
                        ConsultationFixtures.AT.plusSeconds(3)),
                "the successor acknowledges the recorded answer");
        var links = service.links(acknowledged.task(),
                List.of(ConsultationFixtures.REVIEWER, ConsultationFixtures.SECOND_REVIEWER));
        var recorded = queue.read(ConsultationFixtures.ASKER).orElseThrow().consultations().getFirst();

        assertAll(
                () -> assertEquals(1, repaired, "one delivery is repaired"),
                () -> assertEquals(DeliveredTo.FACTS, recorded.deliveredTo(), "the answer is delivered as facts"),
                () -> assertEquals(1, successor.task().consultations().size(),
                        "the successor holds one consultation"),
                () -> assertEquals(ConsultationFixtures.ANSWER,
                        successor.task().consultations().getFirst().answer(),
                        "the successor sees the recorded answer"),
                () -> assertEquals(List.of(ConsultationFixtures.SECOND_REVIEWER),
                        links.stream().map(ConsultationLink::role).toList(),
                        "only the second reviewer is linked"));
    }

    @Test
    @DisplayName("an unavailable delivery to a successor leaves the role open to a fresh consultation")
    void unavailableDeliveredToSuccessorAllowsReconsult() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        protocol.fence(ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.AT);
        assertInstanceOf(ConsultationOutcome.Answered.class,
                service.lost(opened.consultationId(), LostReason.ROLE_UNAVAILABLE, ConsultationFixtures.AT),
                "A lost consultation is delivered as answered");

        var successor = assertInstanceOf(WaitAnswer.Offered.class, protocol.wait(
                        new WaitRequest(ConsultationFixtures.SUCCESSOR_GENERATION, ConsultationFixtures.ASKER_ROLE,
                                ConsultationFixtures.DIGEST, ConsultationFixtures.DIGEST, 3),
                        ConsultationFixtures.AT.plusSeconds(1)),
                "the successor is offered the unavailable consultation");
        var acknowledged = assertInstanceOf(CallResult.Accepted.class, protocol.acknowledge(
                        new TaskCall(ConsultationFixtures.ASKER, ConsultationFixtures.SUCCESSOR_GENERATION),
                        ConsultationFixtures.AT.plusSeconds(2)),
                "the successor acknowledges the consultation");
        var links = service.links(acknowledged.task(), List.of(ConsultationFixtures.REVIEWER));

        assertAll(
                () -> assertEquals(ConsultationStatus.UNAVAILABLE,
                        successor.task().consultations().getFirst().status(),
                        "the successor sees the unavailable consultation"),
                () -> assertEquals(List.of(ConsultationFixtures.REVIEWER),
                        links.stream().map(ConsultationLink::role).toList(),
                        "the role stays open for a fresh consultation"));
    }

    @Test
    @DisplayName("a consultation task refuses submit and infeasible: it offers only submit:answer")
    void consultationOffersOnlyItsAnswer() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        var call = new TaskCall(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION);

        var submit = assertInstanceOf(CallResult.Refused.class, protocol.submit(call, ConsultationFixtures.AT),
                "a consultation task refuses submit");
        var infeasible = assertInstanceOf(CallResult.Refused.class, protocol.infeasible(call, ConsultationFixtures.AT),
                "a consultation task refuses infeasible");

        assertAll(
                () -> assertEquals(TaskRefusal.LINK_NOT_AVAILABLE, submit.refusal(),
                        "the submit names the missing link"),
                () -> assertEquals(TaskRefusal.LINK_NOT_AVAILABLE, infeasible.refusal(),
                        "the infeasible names the missing link"));
    }

    @Test
    @DisplayName("a role the state does not declare is refused")
    void undeclaredRoleRefused() {
        var refused = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.open(ConsultationFixtures.request(ConsultationFixtures.REVIEWER,
                        List.of(ConsultationFixtures.SECOND_REVIEWER))),
                "a role the state does not declare is refused");

        assertAll(
                () -> assertEquals(ConsultationRefusal.LINK_NOT_AVAILABLE, refused.refusal(),
                        "the refusal names the missing link"),
                () -> assertEquals(1, queue.records().size(), "only the asker is recorded"));
    }

    @Test
    @DisplayName("a consultation closed without the service is repaired to unavailable")
    void reconcileSettlesAConsultationClosedWithoutTheService() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        assertInstanceOf(CallResult.Accepted.class, protocol.report(
                        new TaskCall(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION),
                        AttemptEnd.WITHDRAWN, 3, ConsultationFixtures.AT),
                "the consultation is withdrawn outside the service");

        var repaired = service.reconcile(ConsultationFixtures.AT.plusSeconds(1), Duration.ofSeconds(60));
        var fact = service.facts(ConsultationFixtures.ASKER).getFirst();

        assertAll(
                () -> assertEquals(1, repaired, "one consultation is repaired"),
                () -> assertEquals(ConsultationStatus.UNAVAILABLE, fact.status(),
                        "the repaired consultation is unavailable"),
                () -> assertEquals(0, service.reconcile(ConsultationFixtures.AT.plusSeconds(2),
                        Duration.ofSeconds(60)), "a second reconcile repairs nothing"));
    }

    @Test
    @DisplayName("an open consultation whose task was never created is repaired after the grace")
    void reconcileRepairsAnOpenEntryWithoutAConsultationTask() {
        queue.updateAll(records -> records.stream()
                .map(task -> ConsultationFixtures.ASKER.equals(task.queueTaskId())
                        ? ConsultationFixtures.askingWithOrphan("qt-00000000000000bb") : task)
                .toList());

        var early = service.reconcile(ConsultationFixtures.AT.plusSeconds(30), Duration.ofSeconds(60));
        var repaired = service.reconcile(ConsultationFixtures.AT.plusSeconds(61), Duration.ofSeconds(60));
        var asker = queue.read(ConsultationFixtures.ASKER).orElseThrow();

        assertAll(
                () -> assertEquals(0, early, "the grace period repairs nothing"),
                () -> assertEquals(1, repaired, "the consultation is repaired after the grace"),
                () -> assertEquals(ConsultationStatus.UNAVAILABLE, asker.consultations().getFirst().status(),
                        "the orphaned consultation is unavailable"),
                () -> assertEquals(DeliveredTo.WAIT, asker.consultations().getFirst().deliveredTo(),
                        "the orphaned consultation stays at the wait"),
                () -> assertEquals(Status.EXECUTING, asker.status(), "the asker keeps executing"),
                () -> assertEquals(0, service.reconcile(ConsultationFixtures.AT.plusSeconds(62),
                        Duration.ofSeconds(60)), "a second reconcile repairs nothing"));
    }

    @Test
    @DisplayName("a lost consultation recorded for a wait is repaired to the successor's facts")
    void lostThenFenceRepairsTheDurableDeliveryToFacts() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        assertInstanceOf(ConsultationOutcome.Answered.class,
                service.lost(opened.consultationId(), LostReason.ROLE_UNAVAILABLE, ConsultationFixtures.AT),
                "the lost consultation is recorded for the wait");
        protocol.fence(ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.AT.plusSeconds(1));

        var repaired = service.reconcile(ConsultationFixtures.AT.plusSeconds(2), Duration.ofSeconds(60));
        var entry = queue.read(ConsultationFixtures.ASKER).orElseThrow().consultations().getFirst();

        assertAll(
                () -> assertEquals(1, repaired, "one delivery is repaired"),
                () -> assertEquals(ConsultationStatus.UNAVAILABLE, entry.status(),
                        "the repaired entry is unavailable"),
                () -> assertEquals(DeliveredTo.FACTS, entry.deliveredTo(), "the entry is delivered as facts"),
                () -> assertNull(entry.answer(), "the lost entry carries no answer"));
    }

    @Test
    @DisplayName("the consultation task carries the caller's form downgrade")
    void consultationTaskCarriesTheCallersFormDowngrade() {
        var opened = assertInstanceOf(ConsultationOutcome.Opened.class,
                service.open(ConsultationFixtures.requestWithDowngrade(ConsultationFixtures.REVIEWER)),
                "the consultation opens with the downgrade");

        assertTrue(queue.read(opened.consultationId()).orElseThrow().formDowngraded(),
                "the consultation task carries the downgrade");
    }

    @Test
    @DisplayName("a creation failure propagates and leaves the entry for the reconciliation")
    void createFailurePropagatesAndLeavesTheEntryForReconcile() {
        var delegate = new ConsultTestLocks();
        LockManager locks = createMock(LockManager.class);
        expect(locks.openTransaction()).andAnswer(delegate::openTransaction).once();
        expect(locks.openTransaction())
                .andThrow(new UncheckedIOException(new IOException(Generators.letterStrings(10, 40).next()))).once();
        expect(locks.openTransaction()).andAnswer(delegate::openTransaction).anyTimes();
        replay(locks);
        var failing = new ConsultationService(QueueStore.ofProject(base, "project-a", locks));
        var request = ConsultationFixtures.request(ConsultationFixtures.ASKER,
                ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.REVIEWER);

        assertThrows(UncheckedIOException.class, () -> failing.open(request),
                "the creation failure propagates");
        var asker = queue.read(ConsultationFixtures.ASKER).orElseThrow();
        var repaired = failing.reconcile(ConsultationFixtures.AT.plusSeconds(61), Duration.ofSeconds(60));

        assertAll(
                () -> assertEquals(Status.CONSULTING, asker.status(), "the asker keeps consulting"),
                () -> assertEquals(ConsultationStatus.OPEN, asker.consultations().getFirst().status(),
                        "the consultation entry stays open"),
                () -> assertEquals(1, repaired, "the reconciliation repairs the entry"),
                () -> assertEquals(ConsultationStatus.UNAVAILABLE,
                        queue.read(ConsultationFixtures.ASKER).orElseThrow().consultations().getFirst().status(),
                        "the repaired entry is unavailable"));
    }

    private ConsultationOutcome.Opened opened(String role) {
        var request = ConsultationFixtures.request(ConsultationFixtures.ASKER,
                ConsultationFixtures.ASKING_GENERATION, role);
        return assertInstanceOf(ConsultationOutcome.Opened.class, service.open(request),
                "a refusal-free task opens a consultation");
    }

    private void serve(String consultationId, String generation, String role) {
        var answer = protocol.wait(new WaitRequest(generation, role, ConsultationFixtures.DIGEST,
                ConsultationFixtures.DIGEST, 3), ConsultationFixtures.AT);
        var offered = assertInstanceOf(WaitAnswer.Offered.class, answer,
                "the consulted generation is offered the answer");
        assertEquals(consultationId, offered.task().queueTaskId(),
                "the offered task carries the consultation id");
        assertInstanceOf(CallResult.Accepted.class,
                protocol.acknowledge(new TaskCall(consultationId, generation), ConsultationFixtures.AT),
                "the consulted generation acknowledges the task");
    }
}
