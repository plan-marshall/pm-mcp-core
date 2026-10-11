/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import java.util.LinkedHashMap;
import java.util.List;

import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ParamDecl;
import de.planmarshall.workflow.ast.ParamSpec;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateMigration;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.StepCompletion;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * Replaces every {@link SourceLocation} of a tree by {@link #NOWHERE}, so two trees can be compared by their structure
 * alone: an expected tree built in a test, or a tree read back from printed source.
 */
final class AstWithoutLocations {

    /** The location every node of a stripped tree carries. */
    static final SourceLocation NOWHERE = new SourceLocation("", 0, 0);

    private AstWithoutLocations() {
    }

    /**
     * Strips a tree.
     *
     * @param definition the tree as parsed
     * @return the same tree with every location replaced by {@link #NOWHERE}
     */
    static WorkflowDefinition strip(WorkflowDefinition definition) {
        var states = new LinkedHashMap<String, StateNode>();
        definition.states().forEach((id, state) -> states.put(id, strip(state)));
        return new WorkflowDefinition(definition.id(), definition.version(), definition.scope(),
                definition.params().stream().map(p -> new ParamDecl(p.name(), p.type(), NOWHERE)).toList(),
                definition.resultSchema(), definition.instance(), definition.declarations(), definition.decisions(),
                definition.partVersions(), definition.formatVersion(), definition.origin(), definition.derivedFrom(),
                definition.startState(),
                definition.migrations().stream()
                        .map(m -> new StateMigration(m.oldStateId(), m.newStateId(), NOWHERE)).toList(),
                states, NOWHERE);
    }

    private static StateNode strip(StateNode state) {
        return new StateNode(state.id(), state.phase(), state.terminalOutcome(), state.skillPath(),
                state.factsPrimitiveId(), strip(state.behavior()), NOWHERE);
    }

    private static StateBehavior strip(StateBehavior behavior) {
        return switch (behavior) {
            case StateBehavior.InteractiveLinks links -> new StateBehavior.InteractiveLinks(links(links.links()));
            case StateBehavior.AutoStep auto -> new StateBehavior.AutoStep(auto.primitiveId(),
                    transitions(auto.transitions()));
            case StateBehavior.JobStep job -> new StateBehavior.JobStep(job.primitiveId(), job.composite(),
                    transitions(job.transitions()));
            case StateBehavior.ModelJob job -> new StateBehavior.ModelJob(job.entityKind(), job.roleName(),
                    job.fanOutPrimitiveId(), job.rolesPrimitiveId(), job.ask(), job.consultRoles(),
                    transitions(job.transitions()));
            case StateBehavior.SubWorkflowCall call -> new StateBehavior.SubWorkflowCall(call.subWorkflowId(),
                    call.paramBindings(), transitions(call.transitions()));
            case StateBehavior.QuestionWait question -> new StateBehavior.QuestionWait(question.questionClass(),
                    transitions(question.transitions()), links(question.links()));
            case StateBehavior.TerminalState terminal -> terminal;
        };
    }

    private static List<LinkSpec> links(List<LinkSpec> links) {
        return links.stream()
                .map(link -> new LinkSpec(link.rel(), link.idPattern(), link.form(),
                        link.freeParams().stream()
                                .map(p -> new ParamSpec(p.name(), p.type(), p.optional(), NOWHERE)).toList(),
                        link.guard(), link.action(), transitions(link.transitions()), NOWHERE))
                .toList();
    }

    private static List<OutcomeTransition> transitions(List<OutcomeTransition> transitions) {
        return transitions.stream()
                .map(t -> new OutcomeTransition(t.outcomeName(), t.targetStateId(), t.resultGuard(),
                        t.completions().stream()
                                .map(c -> new StepCompletion(c.stepId(), c.status(), NOWHERE)).toList(),
                        NOWHERE))
                .toList();
    }
}
