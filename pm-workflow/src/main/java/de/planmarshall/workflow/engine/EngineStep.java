/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonFactory;
import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.CommandSettlement;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.store.WorkflowStatus;
import lombok.Builder;

/**
 * What one advance of the {@link WorkflowEngine} ends in (PM-IMPL-5, PM-WF-16): the scope rests in a state that waits
 * for an event, the top-level unit ended in a terminal, or a condition was raised on the current state. Every variant
 * carries the status the advance produced, which the caller writes in the transaction of the advance, and the
 * settlements of the primitives the advance ran, which the caller may write in the same transaction; the engine keeps
 * nothing of its own (PM-ARCH-4).
 * <p>
 * Each variant has four components, so each carries a {@code @Builder} beside its validating canonical constructor.
 *
 * @since 0.1
 */
public sealed interface EngineStep permits EngineStep.Resting, EngineStep.Ended, EngineStep.ConditionRaised {

    /** The condition kind a guard that could not be evaluated raises. */
    String GUARD_INDETERMINATE = "guard_indeterminate";

    /** @return the status after the advance */
    WorkflowStatus status();

    /**
     * @return the settlements of the primitives the advance ran, in run order; empty when it ran none. Unmodifiable,
     *         never {@code null} and without a {@code null} element.
     */
    List<CommandSettlement> settlements();

    /**
     * The scope rests in a state that waits for an event: a link state, a job or composite job state, a task state, or
     * a question state, possibly inside a sub-workflow.
     *
     * @param status       the status after the advance
     * @param stateId      the resting state, the status's current state
     * @param awaitedEvent the event the state waits for
     * @param settlements  the settlements of the advance in run order; the record keeps an unmodifiable copy
     * @since 0.1
     */
    @Builder
    record Resting(WorkflowStatus status, String stateId, AwaitedEvent awaitedEvent,
    List<CommandSettlement> settlements) implements EngineStep {

        /**
         * @throws NullPointerException     if a component or a settlement is {@code null}
         * @throws IllegalArgumentException if the state is not the current state of the status
         */
        public Resting {
            requireStatus(status);
            Objects.requireNonNull(awaitedEvent, "awaitedEvent");
            requireCurrent(status, stateId);
            settlements = copyOf(settlements);
        }

        private static void requireCurrent(WorkflowStatus status, String stateId) {
            Objects.requireNonNull(stateId, "stateId");
            if (!status.state().equals(stateId)) {
                throw new IllegalArgumentException("the state '%s' is not the current state '%s'"
                        .formatted(stateId, status.state()));
            }
        }
    }

    /**
     * The top-level unit reached a terminal state; the call stack is empty.
     *
     * @param status       the status after the advance
     * @param outcomeId    the outcome the terminal reports
     * @param outcomeClass the class of the outcome
     * @param settlements  the settlements of the advance in run order; the record keeps an unmodifiable copy
     * @since 0.1
     */
    @Builder
    record Ended(WorkflowStatus status, String outcomeId, OutcomeClass outcomeClass,
    List<CommandSettlement> settlements) implements EngineStep {

        /**
         * @throws NullPointerException     if a component or a settlement is {@code null}
         * @throws IllegalArgumentException if the status still has a call frame
         */
        public Ended {
            requireStatus(status);
            Objects.requireNonNull(outcomeId, "outcomeId");
            Objects.requireNonNull(outcomeClass, "outcomeClass");
            if (!status.callStack().isEmpty()) {
                throw new IllegalArgumentException("a unit ends only with an empty call stack");
            }
            settlements = copyOf(settlements);
        }
    }

    /**
     * A condition overlay is set on the current state; state, phase and call stack are those before it was raised.
     *
     * @param status      the status carrying the condition
     * @param kind        the condition kind
     * @param facts       the facts of the condition, a JSON object
     * @param settlements the settlements of the advance in run order, the last one that of the primitive whose reason
     *                    raised the condition, if one did; the record keeps an unmodifiable copy
     * @since 0.1
     */
    @Builder
    record ConditionRaised(WorkflowStatus status, String kind, String facts,
    List<CommandSettlement> settlements) implements EngineStep {

        /**
         * @throws NullPointerException     if a component or a settlement is {@code null}
         * @throws IllegalArgumentException if the status carries no condition, or another one
         */
        public ConditionRaised {
            requireStatus(status);
            var condition = status.condition()
                    .orElseThrow(() -> new IllegalArgumentException("the status carries no condition"));
            if (!condition.kind().equals(kind) || !condition.facts().equals(facts)) {
                throw new IllegalArgumentException("the step names another condition than the status carries");
            }
            settlements = copyOf(settlements);
        }

        /**
         * @param status a status that carries a condition
         * @return the step of that condition, without settlements
         * @throws IllegalArgumentException if the status carries no condition
         */
        static ConditionRaised of(WorkflowStatus status) {
            return of(status, List.of());
        }

        private static ConditionRaised of(WorkflowStatus status, List<CommandSettlement> settlements) {
            var condition = status.condition()
                    .orElseThrow(() -> new IllegalArgumentException("the status carries no condition"));
            return new ConditionRaised(status, condition.kind(), condition.facts(), settlements);
        }

        /**
         * Raises a condition on the current state outside a chain of primitives: state, phase and call stack stay as
         * they are, and the step carries no settlements.
         *
         * @param status   the status without a condition
         * @param kind     the condition kind; never {@code integrity_breach}, which names its breached store
         * @param facts    the facts of the condition by name, in their order
         * @param raisedAt the instant of the raise, also the new {@code updated_at}
         * @return the step with the condition set
         * @throws IllegalStateException    if the status carries a condition already
         * @throws IllegalArgumentException if the kind is {@code integrity_breach} or no condition kind
         */
        static ConditionRaised raise(WorkflowStatus status, String kind, Map<String, String> facts,
                Instant raisedAt) {
            return raise(status, kind, facts, raisedAt, List.of());
        }

        /**
         * Raises a condition on the current state inside a chain of primitives: state, phase and call stack stay as
         * they are, and the step carries the settlements of the advance.
         *
         * @param status      the status without a condition
         * @param kind        the condition kind; never {@code integrity_breach}, which names its breached store
         * @param facts       the facts of the condition by name, in their order
         * @param raisedAt    the instant of the raise, also the new {@code updated_at}
         * @param settlements the settlements of the advance in run order
         * @return the step with the condition set
         * @throws IllegalStateException    if the status carries a condition already
         * @throws IllegalArgumentException if the kind is {@code integrity_breach} or no condition kind
         * @throws NullPointerException     if the settlements or one of them is {@code null}
         */
        static ConditionRaised raise(WorkflowStatus status, String kind, Map<String, String> facts,
                Instant raisedAt, List<CommandSettlement> settlements) {
            if (status.condition().isPresent()) {
                throw new IllegalStateException("the state '%s' carries the condition '%s' already"
                        .formatted(status.state(), status.condition().get().kind()));
            }
            var condition = WorkflowStatus.PendingCondition.builder().kind(kind).remedyAttempts(0).integrity(null)
                    .facts(toJson(facts)).raisedAt(raisedAt).build();
            return of(status.toBuilder().pendingCondition(condition).updatedAt(raisedAt).build(), settlements);
        }

        private static String toJson(Map<String, String> facts) {
            var text = new StringWriter();
            try (var json = new JsonFactory().createGenerator(text)) {
                json.writeStartObject();
                for (var fact : facts.entrySet()) {
                    json.writeStringField(fact.getKey(), fact.getValue());
                }
                json.writeEndObject();
            } catch (IOException e) {
                throw new UncheckedIOException("a JSON object cannot be written to a string", e);
            }
            return text.toString();
        }
    }

    private static void requireStatus(WorkflowStatus status) {
        Objects.requireNonNull(status, "status");
    }

    private static List<CommandSettlement> copyOf(List<CommandSettlement> settlements) {
        return List.copyOf(Objects.requireNonNull(settlements, "settlements"));
    }
}
