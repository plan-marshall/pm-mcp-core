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
 * The immutable tree the parser of the workflow language produces (PM-IMPL-5): records and sealed interfaces whose
 * lists and maps are copied into immutable collections, the maps keeping the order in which their entries were
 * declared.
 * <ul>
 * <li><b>Definition</b>: {@link de.planmarshall.workflow.ast.WorkflowDefinition} is one complete definition unit, with
 * its scope ({@link de.planmarshall.workflow.ast.WorkflowScope}), origin
 * ({@link de.planmarshall.workflow.ast.UnitOrigin}), sub-workflow parameters
 * ({@link de.planmarshall.workflow.ast.ParamDecl}), {@code declare} and {@code decide} lines
 * ({@link de.planmarshall.workflow.ast.DecisionDeclaration}) and state migrations
 * ({@link de.planmarshall.workflow.ast.StateMigration}).</li>
 * <li><b>States and behaviours</b>: {@link de.planmarshall.workflow.ast.StateNode} with its terminal outcome
 * ({@link de.planmarshall.workflow.ast.TerminalOutcome}) and its closed behaviour
 * ({@link de.planmarshall.workflow.ast.StateBehavior}): links, {@code auto}, {@code job}, {@code job llm},
 * {@code call}, {@code question} or {@code terminal}.</li>
 * <li><b>Links and guards</b>: {@link de.planmarshall.workflow.ast.LinkSpec} with its relation and form
 * ({@link de.planmarshall.workflow.ast.LinkRelation}, {@link de.planmarshall.workflow.ast.LinkForm}), free parameters
 * ({@link de.planmarshall.workflow.ast.ParamSpec}, {@link de.planmarshall.workflow.ast.ParamType}), action
 * ({@link de.planmarshall.workflow.ast.LinkAction}) and guard ({@link de.planmarshall.workflow.ast.GuardSpec},
 * {@link de.planmarshall.workflow.ast.GuardExpr}).</li>
 * <li><b>Transitions</b>: {@link de.planmarshall.workflow.ast.OutcomeTransition} with its result guard
 * ({@link de.planmarshall.workflow.ast.ResultGuard}) and step completions
 * ({@link de.planmarshall.workflow.ast.StepCompletion}, {@link de.planmarshall.workflow.ast.StepCompletionStatus}).</li>
 * <li><b>Call values</b>: {@link de.planmarshall.workflow.ast.CallValue}, the literals bound at a {@code call} site or
 * declared by a {@code declare} line (PM-WF-16).</li>
 * <li><b>Instance header</b>: {@link de.planmarshall.workflow.ast.InstanceHeader}, the parsed {@code instance} line of
 * a plan instance version.</li>
 * <li><b>Call frame</b>: {@link de.planmarshall.workflow.ast.CallFrame}, one frame of a scope's call stack
 * (PM-WF-16).</li>
 * <li><b>Guard result</b>: {@link de.planmarshall.workflow.ast.GuardResult}, the three-valued result of evaluating a
 * guard.</li>
 * </ul>
 * Every node that the parser reads from a source carries its {@link de.planmarshall.workflow.ast.SourceLocation}.
 */
package de.planmarshall.workflow.ast;
