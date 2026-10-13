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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.spi.WorkflowClock;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.ast.GuardExpr;
import de.planmarshall.workflow.ast.GuardResult;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.ResultGuard;

/**
 * The evaluation of guards (PM-IMPL-5; PM-WF-3: a link whose guard does not hold is omitted; PM-WF-16: a result guard
 * decides between the routes of a returned outcome).
 * <p>
 * A guard expression has three values: {@link GuardResult.Held}, {@link GuardResult.NotHeld} and
 * {@link GuardResult.CouldNotEvaluate}. {@code and}, {@code or} and {@code not} propagate the third value unless the
 * other operand decides the result: {@code NOT_HELD and anything} is {@code NOT_HELD}, {@code HELD or anything} is
 * {@code HELD}. A guard that could not be evaluated is never folded into held or not held: it raises the condition
 * {@value EngineStep#GUARD_INDETERMINATE} on the current state, naming the guard, the link and the reason
 * ({@link #raiseUndecided(WorkflowStatus, List, GuardFacts)}).
 * <p>
 * A result guard reads the result record of the returning frame, which the same transaction bound, and always decides:
 * a boolean field holds when it is {@code true}, any other field when it is present ({@link #holds(ResultGuard, Map)}).
 *
 * @since 0.1
 */
public final class GuardEvaluator {

    private final WorkflowClock clock;

    /**
     * @param clock the clock the {@code raised_at} of a raised condition is read from
     * @throws NullPointerException if the clock is {@code null}
     */
    public GuardEvaluator(WorkflowClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The values the atoms of a guard read. Each read is three-valued: an input that cannot be read is
     * {@link GuardResult.CouldNotEvaluate}, never a value.
     *
     * @since 0.1
     */
    public interface GuardFacts {

        /** @return whether the scope works in its worktree */
        GuardResult inWorktree();

        /** @return whether the scope has open findings */
        GuardResult hasOpenFindings();

        /** @return whether every task of the scope is completed */
        GuardResult allTasksCompleted();

        /**
         * @param primitiveId the read-only primitive of a {@code predicate:} atom
         * @return what the primitive decided
         */
        GuardResult predicate(String primitiveId);
    }

    /**
     * Evaluates a guard expression.
     *
     * @param expr  the expression
     * @param facts the values its atoms read
     * @return held, not held, or could not evaluate
     * @throws NullPointerException if an argument or a value read is {@code null}
     */
    public static GuardResult evaluate(GuardExpr expr, GuardFacts facts) {
        Objects.requireNonNull(facts, "facts");
        return switch (Objects.requireNonNull(expr, "expr")) {
            case GuardExpr.InWorktree _ -> Objects.requireNonNull(facts.inWorktree(), "in_worktree");
            case GuardExpr.HasOpenFindings _ -> Objects.requireNonNull(facts.hasOpenFindings(), "has_open_findings");
            case GuardExpr.AllTasksCompleted _ ->
                Objects.requireNonNull(facts.allTasksCompleted(), "all_tasks_completed");
            case GuardExpr.CustomPredicate(var primitiveId) ->
                Objects.requireNonNull(facts.predicate(primitiveId), primitiveId);
            case GuardExpr.And(var left, var right) -> and(evaluate(left, facts), evaluate(right, facts));
            case GuardExpr.Or(var left, var right) -> or(evaluate(left, facts), evaluate(right, facts));
            case GuardExpr.Not(var operand) -> not(evaluate(operand, facts));
        };
    }

    /**
     * Decides a result guard over the result record of a returning frame.
     *
     * @param guard  the result guard of a call-site transition
     * @param result the result record, by field
     * @return whether the guard holds: a boolean field when it is {@code true}, any other field when it is present,
     *         inverted for {@code not}
     * @throws NullPointerException if an argument is {@code null}
     */
    public static boolean holds(ResultGuard guard, Map<String, Object> result) {
        Objects.requireNonNull(guard, "guard");
        var value = Objects.requireNonNull(result, "result").get(guard.field());
        var held = value instanceof Boolean flag ? flag : value != null;
        return held != guard.negated();
    }

    /**
     * Evaluates the guard of every guarded link of the current state, in declaration order, and raises the condition
     * {@value EngineStep#GUARD_INDETERMINATE} for the first that could not be evaluated. The condition's facts name
     * the guard, the link and the reason; state, phase and call stack stay as they are.
     *
     * @param status the status of the scope, without a condition
     * @param links  the declared links of the current state
     * @param facts  the values the atoms read
     * @return the raised condition; empty when every guard decided
     * @throws NullPointerException  if an argument is {@code null}
     * @throws IllegalStateException if the status carries a condition already and a guard could not be evaluated
     */
    public Optional<EngineStep.ConditionRaised> raiseUndecided(WorkflowStatus status, List<LinkSpec> links,
            GuardFacts facts) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(facts, "facts");
        for (var link : links) {
            var guard = link.guard();
            if (guard.isPresent()
                    && evaluate(guard.get().expr(), facts) instanceof GuardResult.CouldNotEvaluate(var reason)) {
                var conditionFacts = new LinkedHashMap<String, String>();
                conditionFacts.put("guard", render(guard.get().expr()));
                conditionFacts.put("link", link.idPattern());
                conditionFacts.put("reason", reason);
                return Optional.of(EngineStep.ConditionRaised.raise(status, EngineStep.GUARD_INDETERMINATE,
                        conditionFacts, clock.now()));
            }
        }
        return Optional.empty();
    }

    private static GuardResult and(GuardResult left, GuardResult right) {
        if (left instanceof GuardResult.NotHeld || right instanceof GuardResult.NotHeld) {
            return new GuardResult.NotHeld();
        }
        return undecided(left, right).orElseGet(GuardResult.Held::new);
    }

    private static GuardResult or(GuardResult left, GuardResult right) {
        if (left instanceof GuardResult.Held || right instanceof GuardResult.Held) {
            return new GuardResult.Held();
        }
        return undecided(left, right).orElseGet(GuardResult.NotHeld::new);
    }

    private static GuardResult not(GuardResult operand) {
        return switch (operand) {
            case GuardResult.Held _ -> new GuardResult.NotHeld();
            case GuardResult.NotHeld _ -> new GuardResult.Held();
            case GuardResult.CouldNotEvaluate undecided -> undecided;
        };
    }

    private static Optional<GuardResult> undecided(GuardResult left, GuardResult right) {
        if (left instanceof GuardResult.CouldNotEvaluate) {
            return Optional.of(left);
        }
        if (right instanceof GuardResult.CouldNotEvaluate) {
            return Optional.of(right);
        }
        return Optional.empty();
    }

    /**
     * Writes a guard expression in the form of the DSL, with parentheses around every combination below the top.
     */
    private static String render(GuardExpr expr) {
        return switch (expr) {
            case GuardExpr.InWorktree _ -> "in_worktree";
            case GuardExpr.HasOpenFindings _ -> "has_open_findings";
            case GuardExpr.AllTasksCompleted _ -> "all_tasks_completed";
            case GuardExpr.CustomPredicate(var primitiveId) -> "predicate:" + primitiveId;
            case GuardExpr.And(var left, var right) -> operand(left) + " and " + operand(right);
            case GuardExpr.Or(var left, var right) -> operand(left) + " or " + operand(right);
            case GuardExpr.Not(var operand) -> "not " + operand(operand);
        };
    }

    private static String operand(GuardExpr expr) {
        return expr instanceof GuardExpr.And || expr instanceof GuardExpr.Or ? "(" + render(expr) + ")" : render(expr);
    }
}
