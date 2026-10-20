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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;

/**
 * The detection of a stalled plan or epic (PM-ARCH-8): a scope is stalled when it has had no transition and no task
 * progress for at least the compiled stalled threshold and awaits no event
 * (doc/specification/runtime-lifecycle/02-plan-pause-and-stalled-plans.adoc, Stalled Plans).
 * <p>
 * The hold is derived on read and kept nowhere: the detector writes no store and adds no member to the status
 * document. The open episodes are kept in memory, keyed by project, scope type and scope id; the first detection of
 * an episode hands one {@code plan_stalled} record to the {@link AuditSink} and announces the hold on the
 * {@link OperatorPort}, every further detection of the episode appends nothing, and a detection that finds the scope
 * moved on ends the episode and announces the report {@link #HOLD_RELEASED}. The acts the hold offers are
 * {@code plan-and-epic-control}'s.
 *
 * @since 0.1
 */
public final class StalledScopeDetector {

    /** The compiled stalled threshold (doc/specification/timeouts/02-runtime-leases-operator-web.adoc). */
    public static final Duration THRESHOLD = Duration.ofSeconds(600);

    /** The report and event name of a hold that ended by itself. */
    public static final String HOLD_RELEASED = "plan.hold_released";

    /** The phases a scope ends in; a scope that ended can never be stalled. */
    private static final Set<String> TERMINAL_PHASES = Set.of("completed", "aborted");

    private final AuditSink auditSink;
    private final OperatorPort operator;
    private final Map<String, StalledHold> stalled = new ConcurrentHashMap<>();

    /**
     * @param auditSink where the one {@code plan_stalled} record of an episode is handed over
     * @param operator  the port that announces the hold to the operator's clients
     */
    public StalledScopeDetector(AuditSink auditSink, OperatorPort operator) {
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        this.operator = Objects.requireNonNull(operator, "operator");
    }

    /**
     * What the caller knows about a scope beside its stored status: the project it belongs to, whether it is paused,
     * waits for the operator, is held for another reason, or awaits an event the status does not carry (an open CI,
     * merge-queue or review-bot wait, a prerequisite wait). Stalled is derived on read and never stored.
     *
     * @param projectId         the project the scope belongs to
     * @param status            the stored status of the scope
     * @param paused            whether the operator paused the scope
     * @param operatorWait      whether the scope rests in an operator wait
     * @param otherHold         whether a hold of another kind is shown for the scope
     * @param awaitsExternalEvent whether the scope awaits an event outside the store: an open CI, merge-queue or
     *                          review-bot wait, or a prerequisite wait
     */
    public record View(String projectId, WorkflowStatus status, boolean paused, boolean operatorWait, boolean otherHold,
    boolean awaitsExternalEvent) {

        /** @throws NullPointerException if a component is {@code null} */
        public View {
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(status, "status");
        }

        /**
         * @param projectId the project the scope belongs to
         * @param status    a scope with no pause, no operator wait, no other hold and no external wait
         * @return the view of that scope
         */
        public static View of(String projectId, WorkflowStatus status) {
            return new View(projectId, status, false, false, false, false);
        }
    }

    /**
     * Why a scope is stalled, a closed set.
     *
     * @since 0.1
     */
    public enum Cause {
        /** No transition and no task progress within the threshold. */
        NO_PROGRESS("no_progress");

        private final String wireName;

        Cause(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the cause in a record */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * The hold {@code stalled} of a scope, derived on read with its cause and the idle span.
     *
     * @param scopeType   the type of the scope: plan or epic
     * @param scopeId     the id of the plan or epic
     * @param state       the state the scope rests in
     * @param cause       why the scope is stalled
     * @param idleSince   the instant of the last transition or task progress
     * @param idleSeconds the seconds since that instant
     * @since 0.1
     */
    public record StalledHold(AuditRecord.Scope scopeType, String scopeId, String state, Cause cause, Instant idleSince,
    long idleSeconds) {

        /** @throws NullPointerException if a component is {@code null} */
        public StalledHold {
            Objects.requireNonNull(scopeType, "scopeType");
            Objects.requireNonNull(scopeId, "scopeId");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(cause, "cause");
            Objects.requireNonNull(idleSince, "idleSince");
        }

        /** @return the kind of the hold */
        public String kind() {
            return "stalled";
        }
    }

    /**
     * What the detection found: the hold, when the scope is stalled.
     *
     * @param hold the hold of a stalled scope; empty otherwise
     */
    public record Activity(Optional<StalledHold> hold) {

        /** @throws NullPointerException if the hold is {@code null} */
        public Activity {
            Objects.requireNonNull(hold, "hold");
        }

        /** @return how many items of {@code needs_operator} the scope has */
        public int needsOperator() {
            return hold.isPresent() ? 1 : 0;
        }
    }

    /**
     * The port that shows the hold to the operator and announces it: the event streams of the scope and, when the hold
     * ends by itself, the report {@link #HOLD_RELEASED} (the assembly wires the local API).
     */
    public interface OperatorPort {

        /**
         * @param hold the hold that was found stalled
         */
        void holdOpened(StalledHold hold);

        /**
         * @param hold   the hold that ended by itself because the scope transitioned or made task progress
         * @param report the report the release writes: {@link #HOLD_RELEASED}
         */
        void holdReleased(StalledHold hold, String report);
    }

    /**
     * Detects whether the scope is stalled and keeps the episode.
     *
     * @param view what the caller knows about the scope
     * @param now  the instant of the read
     * @return the hold, when the scope is stalled, with the idle span of this read
     */
    public Activity detect(View view, Instant now) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(now, "now");
        var status = view.status();
        var key = key(view.projectId(), status);
        if (!stallable(status) || view.paused() || view.operatorWait() || view.otherHold()
                || view.awaitsExternalEvent() || !awaitsNoEvent(status)) {
            release(key);
            return new Activity(Optional.empty());
        }
        var idleSeconds = Duration.between(status.updatedAt(), now).toSeconds();
        if (idleSeconds < THRESHOLD.toSeconds()) {
            release(key);
            return new Activity(Optional.empty());
        }
        var hold = new StalledHold(scopeType(status.scopeType()), status.scopeId(), status.state(), Cause.NO_PROGRESS,
                status.updatedAt(), idleSeconds);
        var opened = new AtomicBoolean();
        stalled.compute(key, (ignored, previous) -> {
            if (previous != null) {
                return previous;
            }
            auditSink.append(AuditEvent.planStalled(hold.scopeType().wireName(), hold.scopeId(), hold.state(),
                    hold.cause().wireName(), hold.idleSince(), hold.idleSeconds()));
            opened.set(true);
            return hold;
        });
        if (opened.get()) {
            operator.holdOpened(hold);
        }
        return new Activity(Optional.of(hold));
    }

    /** Ends the episode of the scope, if one is open, and announces that the hold cleared by itself. */
    private void release(String key) {
        var held = stalled.remove(key);
        if (held != null) {
            operator.holdReleased(held, HOLD_RELEASED);
        }
    }

    /** @return whether a scope of that type can stall at all: a plan or an epic, never the workspace */
    private static boolean stallable(WorkflowStatus status) {
        return status.scopeType() == JournalScopeType.PLAN || status.scopeType() == JournalScopeType.EPIC;
    }

    /** @return whether the scope awaits no event: no condition, no job, no task, and not ended */
    private static boolean awaitsNoEvent(WorkflowStatus status) {
        return status.condition().isEmpty()
                && status.activeJobBinding().isEmpty()
                && status.activeJobs().isEmpty()
                && status.activeTasks().isEmpty()
                && !TERMINAL_PHASES.contains(status.phase());
    }

    private static AuditRecord.Scope scopeType(JournalScopeType type) {
        return type == JournalScopeType.EPIC ? AuditRecord.Scope.EPIC : AuditRecord.Scope.PLAN;
    }

    private static String key(String projectId, WorkflowStatus status) {
        return projectId + "/" + status.scopeType().wireName() + "/" + status.scopeId();
    }
}
