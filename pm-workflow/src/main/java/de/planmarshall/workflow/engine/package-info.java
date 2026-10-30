/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The deterministic interpreter of the state machine (PM-IMPL-5; PM-WF-1: only verified units run; PM-WF-16:
 * sub-workflow calls with a persisted call stack and a small exit contract).
 * <p>
 * {@link de.planmarshall.workflow.engine.WorkflowEngine} runs one unit that
 * {@link de.planmarshall.workflow.verify.WorkflowValidator} passed, together with every sub-workflow it calls. One
 * advance chains in one transaction: it runs {@code auto} states through their primitives, pushes a
 * {@link de.planmarshall.workflow.engine.CallStack} frame on a {@code call} and enters the sub-workflow's start state,
 * pops exactly one frame on a sub-workflow's terminal and routes the returned outcome at the calling state, and stops at
 * the first state that rests or at a terminal of the top-level unit. The result is an
 * {@link de.planmarshall.workflow.engine.EngineStep}: the scope rests and waits for an event, the unit ended, or a
 * condition was raised on the current state. Each carries the new status, which the caller writes in the transaction of
 * the advance, so the engine keeps no state outside the status store (PM-ARCH-4).
 * <p>
 * A resting state is parked in the same write: a job state binds its job, a task state its queue task, through the
 * ports of {@code core.spi}. {@link de.planmarshall.workflow.engine.RestingWait} derives from the store what the state
 * waits for, and only the matching {@link de.planmarshall.workflow.engine.EngineEvent} (a settled job, settled tasks, a
 * resolved question, the passing of time) resumes it (PM-WF-2: waiting on a job is an engine event, never a link).
 * <p>
 * {@link de.planmarshall.workflow.engine.GuardEvaluator} evaluates guards with three values and raises the condition
 * {@code guard_indeterminate} for a guard that could not be evaluated, and decides the result guards of a returned
 * outcome. {@link de.planmarshall.workflow.engine.LoopBackBudget} keeps the loop-back and remediation budgets of a plan,
 * which are raised only and yield {@code exhausted} when spent (PM-WF-7).
 * <p>
 * The engine counts no retry itself: a retrying primitive counts through
 * {@link de.planmarshall.core.store.RetryCounters}, and the engine drops the counts of every state of a sub-workflow
 * when it pops or unwinds that sub-workflow's frame.
 * <p>
 * Besides the engine's own waits, the package holds the two waits a model holds (PM-WF-8, PM-IMPL-4): a
 * {@link de.planmarshall.workflow.engine.ModelWait} is a bounded long-poll or a progress-reset wait over the instant it
 * started at and the window it may hold, and {@link de.planmarshall.workflow.engine.WaitSelection} selects either from
 * the calling connection's {@link de.planmarshall.workflow.engine.ConnectionSettings} and the request's progress token.
 * <p>
 * Entity addressing (PM-ARCH-4, PM-WF-1): an {@link de.planmarshall.workflow.engine.EntityAddress} names an entity
 * within a plan, an epic, or the project's workspace scope, which a tool call names with {@code plan_id: NO_PLAN} and
 * which carries no scope id. {@link de.planmarshall.workflow.engine.EntityAddressResolver} checks the entity
 * identifier and {@code enrich-<module_id>} against their grammars (held by {@code EntityIdGrammar}, their only place)
 * and the {@code entry_id} against the grammar of {@link de.planmarshall.core.store.EntryId}, which owns it, and
 * resolves the identifier only within the addressed scope: a pending plan or epic bootstrap by its typed
 * {@code entry_id} through the port
 * {@link de.planmarshall.core.spi.PendingEntryReader}, a task entity from the {@code active_tasks} of the scope's own
 * status. The result, an {@link de.planmarshall.workflow.engine.AddressResolution}, is the entity with its kind or a
 * refusal with a closed reason; a refusal is a value, never an exception. The resolver is no member of
 * {@link de.planmarshall.workflow.engine.WorkflowEngine.Ports}.
 */
@NullMarked
package de.planmarshall.workflow.engine;

import org.jspecify.annotations.NullMarked;
