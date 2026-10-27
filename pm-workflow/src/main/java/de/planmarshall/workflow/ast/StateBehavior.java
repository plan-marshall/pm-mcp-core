/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a state does: rest on links, run a primitive, run a job, run model tasks, call a sub-workflow, wait for a
 * question, or end the unit (PM-IMPL-5). The lists and maps of the variants are copied into immutable collections;
 * the maps keep declaration order.
 *
 * @since 0.1
 */
public sealed interface StateBehavior
        permits StateBehavior.InteractiveLinks,
        StateBehavior.AutoStep,
        StateBehavior.JobStep,
        StateBehavior.ModelJob,
        StateBehavior.SubWorkflowCall,
        StateBehavior.QuestionWait,
        StateBehavior.TerminalState {

    /**
     * {@code link ...}: a resting state that offers its declared links.
     *
     * @param links the declared links in source order
     */
    record InteractiveLinks(List<LinkSpec> links) implements StateBehavior {

        /**
         * Copies the links into an immutable list.
         *
         * @param links the declared links in source order
         */
        public InteractiveLinks {
            links = List.copyOf(links);
        }
    }

    /**
     * {@code auto <primitive_id>}: the server runs the primitive and routes its outcome.
     *
     * @param primitiveId the primitive the state runs
     * @param transitions the transitions of its outcomes
     */
    record AutoStep(String primitiveId, List<OutcomeTransition> transitions) implements StateBehavior {

        /**
         * Copies the transitions into an immutable list.
         *
         * @param primitiveId the primitive the state runs
         * @param transitions the transitions of its outcomes
         */
        public AutoStep {
            transitions = List.copyOf(transitions);
        }
    }

    /**
     * {@code job <primitive_id>} or {@code composite_job <primitive_id>}: the state waits for a job and routes its
     * outcome.
     *
     * @param primitiveId the primitive of the job
     * @param composite   whether the job is a composite job
     * @param transitions the transitions of its outcomes
     */
    record JobStep(String primitiveId, boolean composite, List<OutcomeTransition> transitions)
            implements StateBehavior {

        /**
         * Copies the transitions into an immutable list.
         *
         * @param primitiveId the primitive of the job
         * @param composite   whether the job is a composite job
         * @param transitions the transitions of its outcomes
         */
        public JobStep {
            transitions = List.copyOf(transitions);
        }
    }

    /**
     * {@code job llm <entity_kind> role <role_name> [each <primitive_id>] [ask]} with its {@code consult role}
     * clauses: a task of the named role for one entity of the kind, or with {@code each} one task per element of the
     * list that primitive returns; with {@code role each <primitive_id>} (only in the unit
     * {@code adjudicate.decisions}) one task per role that primitive returns. The transitions route the outcomes of the
     * kind's return-evaluation primitive and the reserved {@code lost}.
     *
     * @param entityKind        the entity kind of the tasks
     * @param roleName          the role of the tasks; empty for {@code role each}
     * @param fanOutPrimitiveId the primitive of {@code each}, whose list fans the tasks out
     * @param rolesPrimitiveId  the primitive of {@code role each}, which returns the roles
     * @param ask               whether the state declares {@code ask}
     * @param consultRoles      the roles of the {@code consult role} clauses in source order
     * @param transitions       the transitions of the evaluated outcomes
     */
    record ModelJob(String entityKind, Optional<String> roleName, Optional<String> fanOutPrimitiveId,
    Optional<String> rolesPrimitiveId, boolean ask, List<String> consultRoles,
    List<OutcomeTransition> transitions) implements StateBehavior {

        /**
         * Copies the consult roles and the transitions into immutable lists.
         *
         * @param entityKind        the entity kind of the tasks
         * @param roleName          the role of the tasks
         * @param fanOutPrimitiveId the primitive of {@code each}
         * @param rolesPrimitiveId  the primitive of {@code role each}
         * @param ask               whether the state declares {@code ask}
         * @param consultRoles      the roles of the {@code consult role} clauses
         * @param transitions       the transitions of the evaluated outcomes
         */
        public ModelJob {
            consultRoles = List.copyOf(consultRoles);
            transitions = List.copyOf(transitions);
        }
    }

    /**
     * {@code call <sub_workflow_id> [entry <name>]}: the state calls a sub-workflow through one of its entries with
     * bound parameters and routes its exits.
     *
     * @param subWorkflowId the identifier of the called sub-workflow
     * @param entry         the callee's entry the call names; empty when the call omits it, which only a callee with
     *                      exactly one entry admits
     * @param paramBindings the bound parameters by name, in source order
     * @param transitions   the transitions of the callee's exits
     */
    record SubWorkflowCall(String subWorkflowId, Optional<String> entry, Map<String, CallValue> paramBindings,
    List<OutcomeTransition> transitions) implements StateBehavior {

        /**
         * Copies the bindings into an unmodifiable map that keeps source order and the transitions into an immutable
         * list.
         *
         * @param subWorkflowId the identifier of the called sub-workflow
         * @param entry         the callee's entry the call names, or empty
         * @param paramBindings the bound parameters by name
         * @param transitions   the transitions of the callee's exits
         */
        public SubWorkflowCall {
            paramBindings = Collections.unmodifiableMap(new LinkedHashMap<>(paramBindings));
            transitions = List.copyOf(transitions);
        }
    }

    /**
     * {@code question <class>}: the state rests in the decision unit's operator waits over the scope's open question
     * record.
     *
     * @param questionClass the question class
     * @param transitions   the transitions of the class's closed resolution outcomes
     */
    record QuestionWait(String questionClass, List<OutcomeTransition> transitions)
            implements StateBehavior {

        /**
         * Copies the transitions into an immutable list.
         *
         * @param questionClass the question class
         * @param transitions   the transitions of the resolution outcomes
         */
        public QuestionWait {
            transitions = List.copyOf(transitions);
        }
    }

    /**
     * {@code terminal}: the state ends the unit.
     */
    record TerminalState() implements StateBehavior {
    }
}
