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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.ConsultationStatus;
import de.planmarshall.core.queue.QueueTaskRecord.DeliveredTo;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.role.RoleClass;
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
@DisplayName("Consultation through the server")
class ConsultationServiceTest {

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
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT));
        var second = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, "again",
                        ConsultationFixtures.AT.plusSeconds(1)));

        var fact = answered.facts().getFirst();
        var consultation = queue.read(opened.consultationId()).orElseThrow();
        var links = service.links(answered.askingTask(), List.of(ConsultationFixtures.REVIEWER));
        var reconciled = service.reconcile(ConsultationFixtures.AT.plusSeconds(1), Duration.ofSeconds(60));
        assertAll(
                () -> assertEquals(ConsultationStatus.ANSWERED, fact.status()),
                () -> assertEquals(ConsultationFixtures.ANSWER, fact.answer()),
                () -> assertEquals(Status.EXECUTING, answered.askingTask().status()),
                () -> assertEquals(DeliveredTo.WAIT, answered.askingTask().consultations().getFirst().deliveredTo()),
                () -> assertNotNull(answered.askingTask().consultations().getFirst().deliveredAt()),
                () -> assertEquals(List.of(ConsultationFixtures.REVIEWER),
                        links.stream().map(ConsultationLink::role).toList()),
                () -> assertEquals(0, reconciled),
                () -> assertEquals(DeliveredTo.WAIT, queue.read(ConsultationFixtures.ASKER).orElseThrow()
                        .consultations().getFirst().deliveredTo()),
                () -> assertEquals(ConsultationRefusal.STALE_ENTITY, second.refusal()),
                () -> assertEquals(Status.CLOSED, consultation.status()),
                () -> assertEquals(Outcome.SUBMITTED, consultation.outcome()));
    }

    @Test
    @DisplayName("a fenced asker delivers the answer to the successor's offer once and is offered no consult for it")
    void fencedAskerDeliversToTheSuccessorOfferOnce() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        protocol.fence(ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.AT);

        var answered = assertInstanceOf(ConsultationOutcome.Answered.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT.plusSeconds(1)));
        var successor = assertInstanceOf(WaitAnswer.Offered.class, protocol.wait(
                new WaitRequest(ConsultationFixtures.SUCCESSOR_GENERATION, ConsultationFixtures.ASKER_ROLE,
                        ConsultationFixtures.DIGEST, ConsultationFixtures.DIGEST, 3),
                ConsultationFixtures.AT.plusSeconds(2)));
        var acknowledged = assertInstanceOf(CallResult.Accepted.class, protocol.acknowledge(
                new TaskCall(ConsultationFixtures.ASKER, ConsultationFixtures.SUCCESSOR_GENERATION),
                ConsultationFixtures.AT.plusSeconds(3)));
        var links = service.links(acknowledged.task(),
                List.of(ConsultationFixtures.REVIEWER, ConsultationFixtures.SECOND_REVIEWER));

        var delivered = answered.askingTask().consultations().getFirst();
        var offered = successor.task();
        assertAll(
                () -> assertEquals(DeliveredTo.FACTS, delivered.deliveredTo()),
                () -> assertEquals(ConsultationStatus.ANSWERED, delivered.status()),
                () -> assertEquals(Status.QUEUED, answered.askingTask().status()),
                () -> assertEquals(1, offered.consultations().size()),
                () -> assertEquals(ConsultationStatus.ANSWERED, offered.consultations().getFirst().status()),
                () -> assertEquals(ConsultationFixtures.ANSWER, offered.consultations().getFirst().answer()),
                () -> assertEquals(1, service.facts(ConsultationFixtures.ASKER).size()),
                () -> assertEquals(List.of(ConsultationFixtures.SECOND_REVIEWER),
                        links.stream().map(ConsultationLink::role).toList()));
    }

    @Test
    @DisplayName("a fourth consult link of a task is not offered and a fourth consultation is refused")
    void fourthConsultLinkNotOffered() {
        for (var role : List.of(ConsultationFixtures.REVIEWER, ConsultationFixtures.SECOND_REVIEWER,
                "third-reviewer")) {
            var opened = opened(role);
            serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, role);
            assertInstanceOf(ConsultationOutcome.Answered.class,
                    service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                            ConsultationFixtures.ANSWER, ConsultationFixtures.AT));
        }

        var asker = queue.read(ConsultationFixtures.ASKER).orElseThrow();
        var links = service.links(asker, List.of(ConsultationFixtures.REVIEWER, ConsultationFixtures.SECOND_REVIEWER,
                "third-reviewer", "fourth-reviewer"));
        var fourth = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.open(ConsultationFixtures.request("fourth-reviewer", RoleClass.READ_ONLY)));

        assertAll(
                () -> assertEquals(3, asker.consultations().size()),
                () -> assertTrue(links.isEmpty()),
                () -> assertEquals(ConsultationRefusal.CONSULTATION_BOUND_SPENT, fourth.refusal()));
    }

    @Test
    @DisplayName("a lost consultation is delivered with the status unavailable and no answer")
    void lostConsultationDeliveredUnavailable() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);

        var lost = assertInstanceOf(ConsultationOutcome.Answered.class,
                service.lost(opened.consultationId(), LostReason.ROLE_UNAVAILABLE, ConsultationFixtures.AT));
        var second = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.lost(opened.consultationId(), LostReason.ROLE_UNAVAILABLE,
                        ConsultationFixtures.AT.plusSeconds(1)));

        var fact = lost.facts().getFirst();
        var consultation = queue.read(opened.consultationId()).orElseThrow();
        assertAll(
                () -> assertEquals(ConsultationStatus.UNAVAILABLE, fact.status()),
                () -> assertEquals("unavailable", fact.wireStatus()),
                () -> assertNull(fact.answer()),
                () -> assertEquals(DeliveredTo.WAIT, lost.askingTask().consultations().getFirst().deliveredTo()),
                () -> assertEquals(Status.EXECUTING, lost.askingTask().status()),
                () -> assertEquals(Outcome.LOST, consultation.outcome()),
                () -> assertEquals(LostReason.ROLE_UNAVAILABLE, consultation.reason()),
                () -> assertEquals(ConsultationRefusal.STALE_ENTITY, second.refusal()),
                () -> assertInstanceOf(SubmitValidation.Accepted.class,
                        service.validateSubmit(ConsultationFixtures.ASKER, List.of(opened.consultationId()))));
    }

    @Test
    @DisplayName("a submit that omits a delivered consultation_id or names one not delivered is refused")
    void submitValidatesConsultationIds() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        assertInstanceOf(ConsultationOutcome.Answered.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT));

        var missing = assertInstanceOf(SubmitValidation.Refused.class,
                service.validateSubmit(ConsultationFixtures.ASKER, List.of()));
        var unknown = assertInstanceOf(SubmitValidation.Refused.class, service.validateSubmit(
                ConsultationFixtures.ASKER, List.of(opened.consultationId(), UNKNOWN_CONSULTATION)));
        var accepted = assertInstanceOf(SubmitValidation.Accepted.class,
                service.validateSubmit(ConsultationFixtures.ASKER, List.of(opened.consultationId())));

        var second = opened(ConsultationFixtures.SECOND_REVIEWER);
        serve(second.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.SECOND_REVIEWER);
        assertInstanceOf(ConsultationOutcome.Answered.class,
                service.answer(second.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT));
        var omitsSecond = assertInstanceOf(SubmitValidation.Refused.class,
                service.validateSubmit(ConsultationFixtures.ASKER, List.of(opened.consultationId())));

        assertAll(
                () -> assertTrue(missing.detail().contains(opened.consultationId())),
                () -> assertTrue(unknown.detail().contains(UNKNOWN_CONSULTATION)),
                () -> assertNotNull(accepted),
                () -> assertTrue(omitsSecond.detail().contains(second.consultationId())));
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
                        ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.SECOND_REVIEWER)));

        assertAll(
                () -> assertTrue(links.isEmpty()),
                () -> assertEquals(Status.CONSULTING, asker.status()),
                () -> assertEquals(ConsultationRefusal.CONSULTATION_OPEN, second.refusal()),
                () -> assertEquals(opened.consultationId(), asker.consultations().getFirst().consultationId()));
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
                        ConsultationFixtures.AT)));

        assertAll(
                () -> assertEquals(Status.EXECUTING, consultation.status()),
                () -> assertTrue(links.isEmpty()),
                () -> assertEquals(ConsultationRefusal.CONSULTATION_DEPTH, nested.refusal()));
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

        var empty = assertInstanceOf(ConsultationOutcome.Refused.class, service.open(emptyQuestion));
        var tooLong = assertInstanceOf(ConsultationOutcome.Refused.class, service.open(longQuestion));
        var noProtocol = assertInstanceOf(ConsultationOutcome.Refused.class, service.open(zeroTool));

        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        var emptyAnswer = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, "",
                        ConsultationFixtures.AT));

        assertAll(
                () -> assertEquals(ConsultationRefusal.INVALID_LINK_ARGS, empty.refusal()),
                () -> assertEquals(ConsultationRefusal.INVALID_LINK_ARGS, tooLong.refusal()),
                () -> assertEquals(ConsultationRefusal.CLASS_MISMATCH, noProtocol.refusal()),
                () -> assertEquals(ConsultationRefusal.INVALID_LINK_ARGS, emptyAnswer.refusal()),
                () -> assertEquals(2, queue.records().size()));
    }

    @Test
    @DisplayName("an answer is refused while the asking task does not carry the consultation")
    void answerRefusedWhenAskerDoesNotCarryTheConsultation() {
        queue.create(ConsultationFixtures.consultationTask("qt-00000000000000aa", ConsultationFixtures.ASKER,
                ConsultationFixtures.REVIEWER));
        serve("qt-00000000000000aa", ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);

        var refused = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.answer("qt-00000000000000aa", ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT));

        assertAll(
                () -> assertEquals(ConsultationRefusal.JOB_SCOPE_MISMATCH, refused.refusal()),
                () -> assertEquals(Status.EXECUTING,
                        queue.read("qt-00000000000000aa").orElseThrow().status()),
                () -> assertTrue(service.facts(ConsultationFixtures.ASKER).isEmpty()));
    }

    @Test
    @DisplayName("an answer recorded for a wait the asking generation lost reaches the successor once")
    void answerThenFenceDeliversToTheSuccessorOnce() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        assertInstanceOf(ConsultationOutcome.Answered.class,
                service.answer(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION,
                        ConsultationFixtures.ANSWER, ConsultationFixtures.AT));
        protocol.fence(ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.AT.plusSeconds(1));
        var repaired = service.reconcile(ConsultationFixtures.AT.plusSeconds(2), Duration.ofSeconds(60));

        var successor = assertInstanceOf(WaitAnswer.Offered.class, protocol.wait(
                new WaitRequest(ConsultationFixtures.SUCCESSOR_GENERATION, ConsultationFixtures.ASKER_ROLE,
                        ConsultationFixtures.DIGEST, ConsultationFixtures.DIGEST, 3),
                ConsultationFixtures.AT.plusSeconds(2)));
        var acknowledged = assertInstanceOf(CallResult.Accepted.class, protocol.acknowledge(
                new TaskCall(ConsultationFixtures.ASKER, ConsultationFixtures.SUCCESSOR_GENERATION),
                ConsultationFixtures.AT.plusSeconds(3)));
        var links = service.links(acknowledged.task(),
                List.of(ConsultationFixtures.REVIEWER, ConsultationFixtures.SECOND_REVIEWER));
        var recorded = queue.read(ConsultationFixtures.ASKER).orElseThrow().consultations().getFirst();

        assertAll(
                () -> assertEquals(1, repaired),
                () -> assertEquals(DeliveredTo.FACTS, recorded.deliveredTo()),
                () -> assertEquals(1, successor.task().consultations().size()),
                () -> assertEquals(ConsultationFixtures.ANSWER,
                        successor.task().consultations().getFirst().answer()),
                () -> assertEquals(List.of(ConsultationFixtures.SECOND_REVIEWER),
                        links.stream().map(ConsultationLink::role).toList()));
    }

    @Test
    @DisplayName("an unavailable delivery to a successor leaves the role open to a fresh consultation")
    void unavailableDeliveredToSuccessorAllowsReconsult() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        protocol.fence(ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.AT);
        assertInstanceOf(ConsultationOutcome.Answered.class,
                service.lost(opened.consultationId(), LostReason.ROLE_UNAVAILABLE, ConsultationFixtures.AT));

        var successor = assertInstanceOf(WaitAnswer.Offered.class, protocol.wait(
                new WaitRequest(ConsultationFixtures.SUCCESSOR_GENERATION, ConsultationFixtures.ASKER_ROLE,
                        ConsultationFixtures.DIGEST, ConsultationFixtures.DIGEST, 3),
                ConsultationFixtures.AT.plusSeconds(1)));
        var acknowledged = assertInstanceOf(CallResult.Accepted.class, protocol.acknowledge(
                new TaskCall(ConsultationFixtures.ASKER, ConsultationFixtures.SUCCESSOR_GENERATION),
                ConsultationFixtures.AT.plusSeconds(2)));
        var links = service.links(acknowledged.task(), List.of(ConsultationFixtures.REVIEWER));

        assertAll(
                () -> assertEquals(ConsultationStatus.UNAVAILABLE,
                        successor.task().consultations().getFirst().status()),
                () -> assertEquals(List.of(ConsultationFixtures.REVIEWER),
                        links.stream().map(ConsultationLink::role).toList()));
    }

    @Test
    @DisplayName("a consultation task refuses submit and infeasible: it offers only submit:answer")
    void consultationOffersOnlyItsAnswer() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        var call = new TaskCall(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION);

        var submit = assertInstanceOf(CallResult.Refused.class, protocol.submit(call, ConsultationFixtures.AT));
        var infeasible = assertInstanceOf(CallResult.Refused.class, protocol.infeasible(call, ConsultationFixtures.AT));

        assertAll(
                () -> assertEquals(TaskRefusal.LINK_NOT_AVAILABLE, submit.refusal()),
                () -> assertEquals(TaskRefusal.LINK_NOT_AVAILABLE, infeasible.refusal()));
    }

    @Test
    @DisplayName("a role the state does not declare is refused")
    void undeclaredRoleRefused() {
        var refused = assertInstanceOf(ConsultationOutcome.Refused.class,
                service.open(ConsultationFixtures.request(ConsultationFixtures.REVIEWER,
                        List.of(ConsultationFixtures.SECOND_REVIEWER))));

        assertAll(
                () -> assertEquals(ConsultationRefusal.LINK_NOT_AVAILABLE, refused.refusal()),
                () -> assertEquals(1, queue.records().size()));
    }

    @Test
    @DisplayName("a consultation closed without the service is repaired to unavailable")
    void reconcileSettlesAConsultationClosedWithoutTheService() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        assertInstanceOf(CallResult.Accepted.class, protocol.report(
                new TaskCall(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION),
                AttemptEnd.WITHDRAWN, 3, ConsultationFixtures.AT));

        var repaired = service.reconcile(ConsultationFixtures.AT.plusSeconds(1), Duration.ofSeconds(60));
        var fact = service.facts(ConsultationFixtures.ASKER).getFirst();

        assertAll(
                () -> assertEquals(1, repaired),
                () -> assertEquals(ConsultationStatus.UNAVAILABLE, fact.status()),
                () -> assertEquals(0, service.reconcile(ConsultationFixtures.AT.plusSeconds(2),
                        Duration.ofSeconds(60))));
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
                () -> assertEquals(0, early),
                () -> assertEquals(1, repaired),
                () -> assertEquals(ConsultationStatus.UNAVAILABLE, asker.consultations().getFirst().status()),
                () -> assertEquals(DeliveredTo.WAIT, asker.consultations().getFirst().deliveredTo()),
                () -> assertEquals(Status.EXECUTING, asker.status()),
                () -> assertEquals(0, service.reconcile(ConsultationFixtures.AT.plusSeconds(62),
                        Duration.ofSeconds(60))));
    }

    @Test
    @DisplayName("a lost consultation recorded for a wait is repaired to the successor's facts")
    void lostThenFenceRepairsTheDurableDeliveryToFacts() {
        var opened = opened(ConsultationFixtures.REVIEWER);
        serve(opened.consultationId(), ConsultationFixtures.CONSULTED_GENERATION, ConsultationFixtures.REVIEWER);
        assertInstanceOf(ConsultationOutcome.Answered.class,
                service.lost(opened.consultationId(), LostReason.ROLE_UNAVAILABLE, ConsultationFixtures.AT));
        protocol.fence(ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.AT.plusSeconds(1));

        var repaired = service.reconcile(ConsultationFixtures.AT.plusSeconds(2), Duration.ofSeconds(60));
        var entry = queue.read(ConsultationFixtures.ASKER).orElseThrow().consultations().getFirst();

        assertAll(
                () -> assertEquals(1, repaired),
                () -> assertEquals(ConsultationStatus.UNAVAILABLE, entry.status()),
                () -> assertEquals(DeliveredTo.FACTS, entry.deliveredTo()),
                () -> assertNull(entry.answer()));
    }

    @Test
    @DisplayName("the consultation task carries the caller's form downgrade")
    void consultationTaskCarriesTheCallersFormDowngrade() {
        var opened = assertInstanceOf(ConsultationOutcome.Opened.class,
                service.open(ConsultationFixtures.requestWithDowngrade(ConsultationFixtures.REVIEWER)));

        assertTrue(queue.read(opened.consultationId()).orElseThrow().formDowngraded());
    }

    @Test
    @DisplayName("a creation failure propagates and leaves the entry for the reconciliation")
    void createFailurePropagatesAndLeavesTheEntryForReconcile() {
        var failing = new ConsultationService(QueueStore.ofProject(base, "project-a", new FailingCreateLocks()));
        var request = ConsultationFixtures.request(ConsultationFixtures.ASKER,
                ConsultationFixtures.ASKING_GENERATION, ConsultationFixtures.REVIEWER);

        assertThrows(UncheckedIOException.class, () -> failing.open(request));
        var asker = queue.read(ConsultationFixtures.ASKER).orElseThrow();
        var repaired = failing.reconcile(ConsultationFixtures.AT.plusSeconds(61), Duration.ofSeconds(60));

        assertAll(
                () -> assertEquals(Status.CONSULTING, asker.status()),
                () -> assertEquals(ConsultationStatus.OPEN, asker.consultations().getFirst().status()),
                () -> assertEquals(1, repaired),
                () -> assertEquals(ConsultationStatus.UNAVAILABLE,
                        queue.read(ConsultationFixtures.ASKER).orElseThrow().consultations().getFirst().status()));
    }

    private ConsultationOutcome.Opened opened(String role) {
        var request = ConsultationFixtures.request(ConsultationFixtures.ASKER,
                ConsultationFixtures.ASKING_GENERATION, role);
        return assertInstanceOf(ConsultationOutcome.Opened.class, service.open(request));
    }

    private void serve(String consultationId, String generation, String role) {
        var answer = protocol.wait(new WaitRequest(generation, role, ConsultationFixtures.DIGEST,
                ConsultationFixtures.DIGEST, 3), ConsultationFixtures.AT);
        var offered = assertInstanceOf(WaitAnswer.Offered.class, answer);
        assertEquals(consultationId, offered.task().queueTaskId());
        assertInstanceOf(CallResult.Accepted.class,
                protocol.acknowledge(new TaskCall(consultationId, generation), ConsultationFixtures.AT));
    }
}
