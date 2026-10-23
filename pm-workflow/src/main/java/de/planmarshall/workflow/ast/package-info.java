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
 * ({@link de.planmarshall.workflow.ast.DecisionDeclaration}), its optional start state or its entry bindings
 * ({@link de.planmarshall.workflow.ast.WorkflowDefinition.EntryBinding}) and state migrations
 * ({@link de.planmarshall.workflow.ast.StateMigration}).</li>
 * <li><b>Frontmatter</b>: {@link de.planmarshall.workflow.ast.UnitFrontmatter}, what the unit is for a person, its
 * unit type ({@link de.planmarshall.workflow.ast.UnitType}), the stable icon id that stands for it, and its interface,
 * the entries and exits ({@link de.planmarshall.workflow.ast.UnitFrontmatter.InterfaceElement}) in declaration
 * order.</li>
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
 * a plan instance version, with its composition inputs ({@link de.planmarshall.workflow.ast.InstanceInputs}).</li>
 * <li><b>Call frame</b>: {@link de.planmarshall.workflow.ast.CallFrame}, one frame of a scope's call stack
 * (PM-WF-16).</li>
 * <li><b>Guard result</b>: {@link de.planmarshall.workflow.ast.GuardResult}, the three-valued result of evaluating a
 * guard.</li>
 * </ul>
 * Ten nodes carry the {@link de.planmarshall.workflow.ast.SourceLocation} the parser read them at: the frontmatter
 * ({@link de.planmarshall.workflow.ast.UnitFrontmatter}, at its opening {@code ---}), the definition
 * ({@link de.planmarshall.workflow.ast.WorkflowDefinition}), an entry binding
 * ({@link de.planmarshall.workflow.ast.WorkflowDefinition.EntryBinding}), a state
 * ({@link de.planmarshall.workflow.ast.StateNode}), a state migration
 * ({@link de.planmarshall.workflow.ast.StateMigration}), a sub-workflow parameter
 * ({@link de.planmarshall.workflow.ast.ParamDecl}), a link ({@link de.planmarshall.workflow.ast.LinkSpec}), a link
 * parameter ({@link de.planmarshall.workflow.ast.ParamSpec}), an outcome transition
 * ({@link de.planmarshall.workflow.ast.OutcomeTransition}) and a step completion
 * ({@link de.planmarshall.workflow.ast.StepCompletion}). Every other node carries none: the entries and exits of the
 * frontmatter, the behaviours of a state, the terminal outcome, the guard and its expression, the result guard, the
 * link action, the parameter types, the call values and decision declarations held by the {@code declare} and
 * {@code decide} maps of a definition, and the instance header.
 */
package de.planmarshall.workflow.ast;
