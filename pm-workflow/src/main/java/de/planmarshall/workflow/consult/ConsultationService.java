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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskId;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.Attempt;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Consultation;
import de.planmarshall.core.queue.QueueTaskRecord.ConsultationStatus;
import de.planmarshall.core.queue.QueueTaskRecord.DeliveredTo;
import de.planmarshall.core.queue.QueueTaskRecord.Kind;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.queue.QueueTaskRecord.TaskClass;
import de.planmarshall.core.role.RoleClass;

/**
 * The consultation of one role by another (Model Work § Consultation; Timeouts &amp; Budgets § Consultation Bound).
 * <p>
 * A task of a state that declares {@code consult role <role>} offers the link {@code consult:<role>} while it executes
 * ({@link #links(QueueTaskRecord, List)}). Following it opens a queue task of the consulted role — kind
 * {@code consultation}, its {@code queue_task_id} the {@code consultation_id} — records the consultation on the asking
 * task and moves that task to {@code consulting} ({@link #open(ConsultationRequest)}). The consulted role is served by
 * the task protocol like any other task, on its own effective harness and model, and its worker answers through the one
 * link it offers, {@code submit:answer} ({@link #answer}).
 * <p>
 * The rules of the link are decided from the asking task's own record, so they survive a restart and no second store
 * repeats them:
 * <ul>
 * <li><em>One at a time</em>: while a consultation is open, no {@code consult} link is offered.</li>
 * <li><em>Bound</em>: a task follows at most the compiled {@value #MAX_CONSULT_LINKS} consultation links over all its
 * attempts; after that no {@code consult} link is offered and {@link #open} refuses.</li>
 * <li><em>Depth one</em>: a consultation task offers only its {@code submit:answer} and no consultation link.</li>
 * </ul>
 * <p>
 * Delivery is decided when the consultation closes, from the asking task's current lease: while the asking generation
 * still holds the task, the answer is delivered to its next wait (the task returns to {@code executing} and the wait
 * re-offers it); once the generation was fenced, the answer is recorded in the task's facts and the successor's offer
 * carries it ({@code delivered_to: facts}). A delivery recorded for a wait whose generation no longer holds the task is
 * treated as a delivery to the successor when the task is represented: the answer is in its facts, and it is offered no
 * {@code consult} link for that role. A consultation that closes {@code lost} is delivered the same way with the status
 * {@code unavailable} and no answer.
 * <p>
 * The submit of a task that declares consultations lists their {@code consultation_ids} ({@link #validateSubmit}): once
 * an answer was delivered, the submit lists every delivered id and no other, else it is refused
 * {@code invalid_link_args} with a reason that names the missing or unknown reference.
 * <p>
 * The asking task records the consultation before the consultation task is created (ADR 003), so no task is ever
 * offered that no asking task lists. A consultation the server closed outside {@link #answer} or {@link #lost} — for
 * example one whose attempts were spent — is repaired by {@link #reconcile(Instant)}, which the server calls on its
 * queue reconciliation tick.
 *
 * @since 0.1
 */
public final class ConsultationService {

    /** The compiled number of {@code consult} links a task may follow over all its attempts. */
    public static final int MAX_CONSULT_LINKS = 3;

    /** The compiled length of a question and of an answer, in characters. */
    public static final int MAX_TEXT_LENGTH = 4_000;

    private static final String NO_TASK = "the queue holds no task";

    private final QueueStore queue;

    /**
     * @param queue the queue of the project the consultations belong to
     */
    public ConsultationService(QueueStore queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    /**
     * The {@code consult:<role>} links the task's entity offers: one per declared role, while the task executes, while no
     * consultation is open and while the task followed fewer than {@value #MAX_CONSULT_LINKS} consultations over all
     * its attempts. A role whose answer was delivered to a successor is offered no link, whether the delivery was
     * recorded to the facts or to a wait the asking generation no longer holds.
     *
     * @param task          the task whose entity is represented
     * @param declaredRoles the roles its state declares with {@code consult role}
     * @return the links, in the order the roles are declared; empty for a consultation task
     */
    public List<ConsultationLink> links(QueueTaskRecord task, List<String> declaredRoles) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(declaredRoles, "declaredRoles");
        if (task.kind() != Kind.WORK || task.status() != Status.EXECUTING || hasOpenConsultation(task)
                || task.consultations().size() >= MAX_CONSULT_LINKS) {
            return List.of();
        }
        var delivered = deliveredRoles(task);
        return declaredRoles.stream()
                .distinct()
                .filter(role -> !delivered.contains(role))
                .map(ConsultationLink::new)
                .toList();
    }

    /**
     * Opens a consultation: records the consultation on the asking task and moves it to {@code consulting}, then
     * creates the queue task of the consulted role. When the task cannot be created the asking task is put back as it
     * was, so no consultation is left open without a task.
     *
     * @param request the ask
     * @return the opened consultation, or the refusal
     */
    public ConsultationOutcome open(ConsultationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!validText(request.question())) {
            return refused(ConsultationRefusal.INVALID_LINK_ARGS,
                    "the question has 1 to %d characters".formatted(MAX_TEXT_LENGTH));
        }
        if (!request.declaredRoles().contains(request.role())) {
            return refused(ConsultationRefusal.LINK_NOT_AVAILABLE,
                    "the state declares no '%s' consultation".formatted(request.role()));
        }
        if (request.roleClass() == RoleClass.ZERO_TOOL) {
            return refused(ConsultationRefusal.CLASS_MISMATCH,
                    "a consulted role has a protocol to answer through, not '%s'".formatted(request.role()));
        }
        var known = queue.read(request.askingTaskId());
        if (known.isEmpty()) {
            return refused(ConsultationRefusal.JOB_SCOPE_MISMATCH, noTask(request.askingTaskId()));
        }
        var early = openRefusal(known.get(), request);
        if (early != null) {
            return early;
        }
        var consultationId = freeId();
        var recorded = new AtomicReference<QueueTaskRecord>();
        var outcome = new AtomicReference<ConsultationOutcome>();
        queue.updateAll(records -> {
            var asker = find(records, request.askingTaskId());
            if (asker == null) {
                outcome.set(refused(ConsultationRefusal.JOB_SCOPE_MISMATCH, noTask(request.askingTaskId())));
                return records;
            }
            var refusal = openRefusal(asker, request);
            if (refusal != null) {
                outcome.set(refusal);
                return records;
            }
            var consultations = new ArrayList<>(asker.consultations());
            consultations.add(new Consultation(consultationId, request.role(), request.generationId(),
                    request.at(), ConsultationStatus.OPEN, null, null, null, null));
            var updated = asker.withConsultations(consultations).withStatus(Status.CONSULTING);
            recorded.set(updated);
            outcome.set(new ConsultationOutcome.Opened(consultationId, updated));
            return replace(records, updated);
        });
        if (outcome.get() instanceof ConsultationOutcome.Refused) {
            return outcome.get();
        }
        try {
            queue.create(consultationTask(recorded.get(), request, consultationId));
        }
        /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
        catch (RuntimeException failure) {
            rollback(recorded.get(), consultationId);
            return refused(ConsultationRefusal.JOB_SCOPE_MISMATCH,
                    "the consultation task '%s' could not be created".formatted(consultationId));
        }
        return outcome.get();
    }

    /**
     * Records the answer of a consultation and delivers it. The consultation task must be executing and leased to the
     * caller; its first answer closes it, and a second answer is refused {@code stale_entity}.
     *
     * @param consultationId the consultation task
     * @param generationId   the generation of the consulted role that answers
     * @param answer         the answer, 1 to 4000 characters
     * @param at             the instant of the answer
     * @return the answering task with its consultation facts, or the refusal
     */
    public ConsultationOutcome answer(String consultationId, String generationId, String answer, Instant at) {
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(at, "at");
        if (!validText(answer)) {
            return refused(ConsultationRefusal.INVALID_LINK_ARGS,
                    "the answer has 1 to %d characters".formatted(MAX_TEXT_LENGTH));
        }
        return close(consultationId, generationId, ConsultationStatus.ANSWERED, answer, null, at);
    }

    /**
     * Closes a consultation {@code lost} — its re-offers spent, or its role undefined, unqualified or unavailable — and
     * delivers it with the status {@code unavailable} and no answer. This is the model caller's path, so the call needs
     * no generation of the consulted role.
     *
     * @param consultationId the consultation task
     * @param reason         why the consultation closed lost
     * @param at             the instant of the close
     * @return the asking task with its consultation facts, or the refusal
     */
    public ConsultationOutcome lost(String consultationId, LostReason reason, Instant at) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        return close(consultationId, null, ConsultationStatus.UNAVAILABLE, null, reason, at);
    }

    /**
     * Repairs every consultation that closed outside {@link #answer} or {@link #lost} — a consultation task the task
     * protocol closed without an answer, for example with its attempts spent — and whose asking task still lists it
     * {@code open}: the entry is settled {@code unavailable}, with the consultation's own closing instant. A
     * consultation that is still open is never touched.
     *
     * @param at the instant of the repair, used for a consultation task that closed without a closing instant
     * @return the number of asking tasks repaired
     */
    public int reconcile(Instant at) {
        Objects.requireNonNull(at, "at");
        var repaired = new AtomicInteger();
        queue.updateAll(records -> {
            var askers = new LinkedHashMap<String, QueueTaskRecord>();
            for (var task : records) {
                if (task.kind() == Kind.WORK) {
                    askers.put(task.queueTaskId(), task);
                }
            }
            for (var task : records) {
                if (task.kind() != Kind.CONSULTATION || !task.isClosed()) {
                    continue;
                }
                var asker = askers.get(task.consultationOf());
                if (asker == null || !hasOpenEntry(asker, task.queueTaskId())) {
                    continue;
                }
                var closedAt = task.closedAt() == null ? at : task.closedAt();
                askers.put(asker.queueTaskId(), settleAsker(asker, task.queueTaskId(),
                        ConsultationStatus.UNAVAILABLE, null, closedAt));
                repaired.incrementAndGet();
            }
            var after = new ArrayList<QueueTaskRecord>(records.size());
            for (var task : records) {
                after.add(askers.getOrDefault(task.queueTaskId(), task));
            }
            return after;
        });
        return repaired.get();
    }

    /**
     * @param askingTaskId the asking task
     * @return the consultations the task carries, in the order they were asked; empty when the task is unknown
     */
    public List<ConsultationFact> facts(String askingTaskId) {
        Objects.requireNonNull(askingTaskId, "askingTaskId");
        return queue.read(askingTaskId).map(ConsultationService::factsOf).orElse(List.of());
    }

    /**
     * Validates the {@code consultation_ids} of a submit against the consultations delivered to the task: every
     * delivered id must be listed, and no other.
     *
     * @param askingTaskId    the submitting task
     * @param consultationIds the ids the submit lists
     * @return the accepted validation, or the refusal naming the missing or unknown reference
     */
    public SubmitValidation validateSubmit(String askingTaskId, List<String> consultationIds) {
        Objects.requireNonNull(askingTaskId, "askingTaskId");
        Objects.requireNonNull(consultationIds, "consultationIds");
        var task = queue.read(askingTaskId).orElse(null);
        if (task == null) {
            return new SubmitValidation.Refused(noTask(askingTaskId));
        }
        var delivered = task.consultations().stream()
                .filter(consultation -> consultation.deliveredAt() != null)
                .map(Consultation::consultationId)
                .toList();
        var submitted = new LinkedHashSet<>(consultationIds);
        var missing = delivered.stream().filter(id -> !submitted.contains(id)).findFirst();
        if (missing.isPresent()) {
            return new SubmitValidation.Refused(
                    "the submit omits the delivered consultation_id '%s'".formatted(missing.get()));
        }
        var unknown = submitted.stream().filter(id -> !delivered.contains(id)).findFirst();
        if (unknown.isPresent()) {
            return new SubmitValidation.Refused(
                    "the submit names the consultation_id '%s', which was not delivered to the task"
                            .formatted(unknown.get()));
        }
        return new SubmitValidation.Accepted();
    }

    private ConsultationOutcome close(String consultationId, String generationId, ConsultationStatus status,
            String answer, LostReason reason, Instant at) {
        var outcome = new AtomicReference<ConsultationOutcome>();
        queue.updateAll(records -> {
            var consultation = find(records, consultationId);
            if (consultation == null) {
                outcome.set(refused(ConsultationRefusal.JOB_SCOPE_MISMATCH, noTask(consultationId)));
                return records;
            }
            if (consultation.kind() != Kind.CONSULTATION) {
                outcome.set(refused(ConsultationRefusal.LINK_NOT_AVAILABLE,
                        "the task '%s' is no consultation".formatted(consultationId)));
                return records;
            }
            if (consultation.isClosed()) {
                outcome.set(refused(ConsultationRefusal.STALE_ENTITY,
                        "the consultation '%s' already closed".formatted(consultationId)));
                return records;
            }
            if (generationId != null && !leasedTo(consultation, generationId)) {
                outcome.set(refused(ConsultationRefusal.LINK_NOT_AVAILABLE,
                        "the consultation '%s' is not leased to '%s'".formatted(consultationId, generationId)));
                return records;
            }
            var asker = find(records, consultation.consultationOf());
            if (asker == null || !hasOpenEntry(asker, consultationId)) {
                outcome.set(refused(ConsultationRefusal.JOB_SCOPE_MISMATCH,
                        "the asking task '%s' does not carry the consultation".formatted(consultation.consultationOf())));
                return records;
            }
            var closed = closeTask(consultation, reason, at);
            var settled = settleAsker(asker, consultationId, status, answer, at);
            outcome.set(new ConsultationOutcome.Answered(settled, factsOf(settled)));
            return replace(replace(records, closed), settled);
        });
        return outcome.get();
    }

    private static QueueTaskRecord settleAsker(QueueTaskRecord asker, String consultationId,
            ConsultationStatus status, String answer, Instant at) {
        var entry = asker.consultations().stream()
                .filter(consultation -> consultation.consultationId().equals(consultationId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the asking task '%s' does not carry the consultation '%s'"
                                .formatted(asker.queueTaskId(), consultationId)));
        var deliveredTo = asker.lease() != null && asker.lease().generationId().equals(entry.askedBy())
                ? DeliveredTo.WAIT : DeliveredTo.FACTS;
        var consultations = new ArrayList<>(asker.consultations());
        for (var index = 0; index < consultations.size(); index++) {
            if (consultations.get(index).consultationId().equals(consultationId)) {
                consultations.set(index, new Consultation(entry.consultationId(), entry.role(), entry.askedBy(),
                        entry.askedAt(), status, answer, at, at, deliveredTo));
            }
        }
        var updated = asker.withConsultations(consultations);
        if (asker.status() != Status.CONSULTING) {
            return updated;
        }
        if (deliveredTo == DeliveredTo.WAIT) {
            return updated.withStatus(Status.EXECUTING);
        }
        return asker.lease() == null ? updated.withStatus(Status.QUEUED) : updated;
    }

    private static QueueTaskRecord closeTask(QueueTaskRecord task, LostReason reason, Instant at) {
        var attempts = task.attempts();
        if (!attempts.isEmpty() && attempts.getLast().end() == null) {
            var last = attempts.getLast();
            var updated = new ArrayList<>(attempts);
            updated.set(updated.size() - 1, new Attempt(last.attempt(), last.generationId(), last.jobId(),
                    last.configurationDigest(), last.offeredAt(), last.acknowledgedAt(), at, AttemptEnd.CLOSED,
                    false));
            attempts = updated;
        }
        return task.withAttempts(attempts).withLease(null).withStatus(Status.CLOSED)
                .withOutcome(reason == null ? Outcome.SUBMITTED : Outcome.LOST).withReason(reason).withClosedAt(at);
    }

    private void rollback(QueueTaskRecord recorded, String consultationId) {
        queue.updateAll(records -> {
            var current = find(records, recorded.queueTaskId());
            if (current == null) {
                return records;
            }
            var consultations = current.consultations().stream()
                    .filter(consultation -> !consultation.consultationId().equals(consultationId))
                    .toList();
            var updated = current.withConsultations(consultations);
            if (current.status() == Status.CONSULTING) {
                updated = updated.withStatus(current.lease() == null ? Status.QUEUED : Status.EXECUTING);
            }
            return replace(records, updated);
        });
    }

    private ConsultationOutcome openRefusal(QueueTaskRecord asker, ConsultationRequest request) {
        if (asker.kind() != Kind.WORK) {
            return refused(ConsultationRefusal.CONSULTATION_DEPTH,
                    "the consultation task '%s' offers no consultation".formatted(asker.queueTaskId()));
        }
        if (hasOpenConsultation(asker)) {
            return refused(ConsultationRefusal.CONSULTATION_OPEN,
                    "the task '%s' has an open consultation".formatted(asker.queueTaskId()));
        }
        if (asker.status() != Status.EXECUTING || asker.lease() == null
                || !asker.lease().generationId().equals(request.generationId())) {
            return refused(ConsultationRefusal.LINK_NOT_AVAILABLE,
                    "the task '%s' is not executing for '%s'".formatted(asker.queueTaskId(), request.generationId()));
        }
        if (asker.consultations().size() >= MAX_CONSULT_LINKS) {
            return refused(ConsultationRefusal.CONSULTATION_BOUND_SPENT,
                    "the task '%s' followed %d consultation links".formatted(asker.queueTaskId(),
                            MAX_CONSULT_LINKS));
        }
        if (deliveredRoles(asker).contains(request.role())) {
            return refused(ConsultationRefusal.LINK_NOT_AVAILABLE,
                    "the answer of '%s' was already delivered to the successor".formatted(request.role()));
        }
        return null;
    }

    private QueueTaskRecord consultationTask(QueueTaskRecord asker, ConsultationRequest request,
            String consultationId) {
        return QueueTaskRecord.builder()
                .queueTaskId(consultationId)
                .kind(Kind.CONSULTATION)
                .role(request.role())
                .taskClass(taskClass(request.roleClass()))
                .scope(asker.scope())
                .entity(consultationId)
                .stateId(asker.stateId())
                .formDowngraded(false)
                .status(Status.QUEUED)
                .continuation(null)
                .lease(null)
                .attempts(List.of())
                .signals(List.of())
                .consultations(List.of())
                .consultationOf(asker.queueTaskId())
                .outcome(null)
                .reason(null)
                .questionId(null)
                .createdAt(request.at())
                .closedAt(null)
                .build();
    }

    private String freeId() {
        while (true) {
            var candidate = QueueTaskId.random().value();
            if (queue.read(candidate).isEmpty()) {
                return candidate;
            }
        }
    }

    /**
     * The roles whose answered consultation is delivered to a generation other than the one that asked.
     */
    private static Set<String> deliveredRoles(QueueTaskRecord task) {
        var leaseGeneration = task.lease() == null ? null : task.lease().generationId();
        return task.consultations().stream()
                .filter(consultation -> consultation.status() == ConsultationStatus.ANSWERED)
                .filter(consultation -> consultation.deliveredTo() == DeliveredTo.FACTS
                        || leaseGeneration == null || !leaseGeneration.equals(consultation.askedBy()))
                .map(Consultation::role)
                .collect(Collectors.toSet());
    }

    private static boolean hasOpenEntry(QueueTaskRecord task, String consultationId) {
        return task.consultations().stream().anyMatch(consultation -> consultation.consultationId()
                .equals(consultationId) && consultation.status() == ConsultationStatus.OPEN);
    }

    private static boolean leasedTo(QueueTaskRecord task, String generationId) {
        return task.status() == Status.EXECUTING && task.lease() != null
                && task.lease().generationId().equals(generationId);
    }

    private static boolean hasOpenConsultation(QueueTaskRecord task) {
        return task.consultations().stream()
                .anyMatch(consultation -> consultation.status() == ConsultationStatus.OPEN);
    }

    private static boolean validText(String text) {
        return text != null && !text.isEmpty() && text.codePointCount(0, text.length()) <= MAX_TEXT_LENGTH;
    }

    private static List<ConsultationFact> factsOf(QueueTaskRecord task) {
        return task.consultations().stream().map(ConsultationFact::of).toList();
    }

    private static TaskClass taskClass(RoleClass roleClass) {
        return switch (roleClass) {
            case WRITER -> TaskClass.WRITER;
            case READ_ONLY -> TaskClass.READ_ONLY;
            case ZERO_TOOL -> TaskClass.ZERO_TOOL;
        };
    }

    private static ConsultationOutcome refused(ConsultationRefusal refusal, String detail) {
        return new ConsultationOutcome.Refused(refusal, detail);
    }

    private static String noTask(String queueTaskId) {
        return NO_TASK + " '%s'".formatted(queueTaskId);
    }

    private static QueueTaskRecord find(List<QueueTaskRecord> records, String queueTaskId) {
        return records.stream().filter(task -> task.queueTaskId().equals(queueTaskId)).findFirst().orElse(null);
    }

    private static List<QueueTaskRecord> replace(List<QueueTaskRecord> records, QueueTaskRecord replacement) {
        var after = new ArrayList<QueueTaskRecord>(records.size());
        for (var task : records) {
            after.add(task.queueTaskId().equals(replacement.queueTaskId()) ? replacement : task);
        }
        return after;
    }
}
