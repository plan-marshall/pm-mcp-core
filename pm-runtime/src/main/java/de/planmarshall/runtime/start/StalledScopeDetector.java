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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;

/**
 * The detection of a stalled plan or epic (PM-ARCH-8): a scope is stalled when it has had no transition and no task
 * progress for longer than the compiled stalled threshold and awaits no event
 * (doc/specification/runtime-lifecycle/02-plan-pause-and-stalled-plans.adoc, Stalled Plans).
 *
 * @since 0.1
 */
public final class StalledScopeDetector {

    /** The compiled stalled threshold (doc/specification/timeouts/02-runtime-leases-operator-web.adoc). */
    public static final Duration THRESHOLD = Duration.ofSeconds(600);

    /** The report and event name of a hold that ended by itself. */
    public static final String HOLD_RELEASED = "plan.hold_released";

    private final AuditSink auditSink;
    private final OperatorPort operator;
    private final Map<String, StalledHold> stalled = new HashMap<>();

    /** The phases a scope ends in; a scope that ended can never be stalled. */
    private static final Set<String> TERMINAL_PHASES = Set.of("completed", "aborted");

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
     * waits for the operator, or is held for another reason. Stalled is derived on read and never stored.
     *
     * @param projectId    the project the scope belongs to
     * @param status       the stored status of the scope
     * @param paused       whether the operator paused the scope
     * @param operatorWait whether the scope rests in an operator wait
     * @param otherHold    whether a hold of another kind is shown for the scope
     */
    public record View(String projectId, WorkflowStatus status, boolean paused, boolean operatorWait,
    boolean otherHold) {

        /** @throws NullPointerException if a component is {@code null} */
        public View {
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(status, "status");
        }

        /**
         * @param projectId the project the scope belongs to
         * @param status    a scope with no pause, no operator wait and no other hold
         * @return the view of that scope
         */
        public static View of(String projectId, WorkflowStatus status) {
            return new View(projectId, status, false, false, false);
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

        /** @return how many items of {@code needs_operator} the hold is: one */
        public int needsOperator() {
            return 1;
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
     * The port that shows the hold to the operator and announces it: the event streams of the scope and the report
     * {@value #HOLD_RELEASED} when the hold ends by itself (the assembly wires the local API).
     */
    public interface OperatorPort {

        /** @return a port that announces nothing, for a caller that keeps no clients */
        static OperatorPort none() {
            return new OperatorPort() {
                @Override
                public void holdOpened(StalledHold hold) {
                }

                @Override
                public void holdReleased(StalledHold hold) {
                }
            };
        }

        /**
         * @param hold the hold that was found stalled
         */
        void holdOpened(StalledHold hold);

        /**
         * @param hold the hold that ended by itself because the scope transitioned or made task progress
         */
        void holdReleased(StalledHold hold);
    }

    /**
     * Detects whether the scope is stalled and keeps the episode.
     *
     * @param view what the caller knows about the scope
     * @param now  the instant of the read
     * @return the hold, when the scope is stalled
     */
    public Activity detect(View view, Instant now) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(now, "now");
        var status = view.status();
        var key = key(view.projectId(), status);
        if (stallable(status) && !view.paused() && !view.operatorWait() && !view.otherHold()
                && awaitsNoEvent(status)) {
            var idleSeconds = Duration.between(status.updatedAt(), now).toSeconds();
            if (idleSeconds >= THRESHOLD.toSeconds()) {
                var hold = new StalledHold(scopeType(status.scopeType()), status.scopeId(), status.state(),
                        Cause.NO_PROGRESS, status.updatedAt(), idleSeconds);
                if (stalled.put(key, hold) == null) {
                    auditSink.append(AuditEvent.planStalled(hold.scopeType().wireName(), hold.scopeId(),
                            hold.state(), hold.cause().wireName(), hold.idleSince(), hold.idleSeconds()));
                    operator.holdOpened(hold);
                }
                return new Activity(Optional.of(hold));
            }
        }
        release(key);
        return new Activity(Optional.empty());
    }

    /** Ends the episode of the scope, if one is open, and announces that the hold cleared by itself. */
    private void release(String key) {
        var held = stalled.remove(key);
        if (held != null) {
            operator.holdReleased(held);
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
        return projectId + "/" + status.scopeType().wireName() + "/" + Objects.toString(status.scopeId(), "");
    }
}
