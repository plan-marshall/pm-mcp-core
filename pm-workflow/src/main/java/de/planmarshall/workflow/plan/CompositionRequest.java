/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * Everything one composition reads, passed in as values (PM-IMPL-5, PM-WF-1, PM-EXT-7): the composer reads no store.
 * The facts stay where they are owned; this value is the read of them that one composition rests on.
 * <p>
 * The event decides the version: {@link CompositionEvent#START} composes version 1 and has no previous version, every
 * other event composes the version after the previous one. Whether the event's trigger is the server or the operator
 * follows from the event ({@link CompositionEvent#trigger()}).
 * <p>
 * The event and the {@value PlanFacts#POST_RUN_FACT} fact of the facts agree: {@link CompositionEvent#FALLBACK}
 * composes the post-run-only form {@code returned} or {@code handed_back}, {@link CompositionEvent#EMPTY_HARVEST} the
 * form {@code empty_harvest}, and every other event no post-run-only form, so its facts leave the fact unset. A request
 * whose event and fact disagree is refused.
 *
 * @param planId                  the id of the plan
 * @param event                   the composition event
 * @param template                the plan template, the unit {@code plan} the instance is composed from
 * @param templates               the phase units the template was merged from, by digest
 * @param subUnits                every sub-unit a call of the template may reach, with its reference
 * @param facts                   the facts of the plan, the effective configuration values included
 * @param configHash              the SHA-256 over the effective configuration
 * @param realizedFootprintDigest the SHA-256 over the realized footprint, if the composition reads one
 * @param providersDigest         the SHA-256 over the provider configuration, if the composition reads one
 * @param waivers                 the waivers in force
 * @param position                the current state and the call stack of the plan
 * @param previous                the version in force; empty only for {@link CompositionEvent#START}
 * @param verificationSteps       the Phase 5 verification step ids of the plan, before pruning
 * @param finalizeSteps           the Phase 6 steps of the plan with their order, before pruning
 * @since 0.1
 */
public record CompositionRequest(String planId, CompositionEvent event, WorkflowDefinition template,
List<UnitReference> templates, List<SubUnit> subUnits, PlanFacts facts, String configHash,
Optional<String> realizedFootprintDigest, Optional<String> providersDigest, List<Waiver> waivers,
InstancePosition position, Optional<PlanInstance> previous, List<String> verificationSteps,
List<Step> finalizeSteps) {

    /** The post-run-only form of a fallback that returns the plan. */
    static final String POST_RUN_RETURNED = "returned";
    /** The post-run-only form of a fallback that hands the plan back. */
    static final String POST_RUN_HANDED_BACK = "handed_back";
    /** The post-run-only form of an empty harvest. */
    static final String POST_RUN_EMPTY_HARVEST = "empty_harvest";

    /**
     * Refuses a missing component and an event that does not fit the previous version, and copies the lists into
     * unmodifiable lists that keep their order.
     *
     * @param planId                  the id of the plan
     * @param event                   the composition event
     * @param template                the plan template
     * @param templates               the phase units by digest
     * @param subUnits                the sub-units
     * @param facts                   the facts of the plan
     * @param configHash              the SHA-256 over the effective configuration
     * @param realizedFootprintDigest the SHA-256 over the realized footprint, if any
     * @param providersDigest         the SHA-256 over the provider configuration, if any
     * @param waivers                 the waivers in force
     * @param position                the position of the plan
     * @param previous                the version in force
     * @param verificationSteps       the Phase 5 verification step ids
     * @param finalizeSteps           the Phase 6 steps
     * @throws IllegalArgumentException if {@link CompositionEvent#START} has a previous version, another event has
     *                                  none, the previous version belongs to another plan, or the event and the
     *                                  {@code post_run} fact disagree
     */
    public CompositionRequest {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(template, "template");
        templates = List.copyOf(templates);
        subUnits = List.copyOf(subUnits);
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(configHash, "configHash");
        Objects.requireNonNull(realizedFootprintDigest, "realizedFootprintDigest");
        Objects.requireNonNull(providersDigest, "providersDigest");
        waivers = List.copyOf(waivers);
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(previous, "previous");
        verificationSteps = List.copyOf(verificationSteps);
        finalizeSteps = List.copyOf(finalizeSteps);
        if (event == CompositionEvent.START && previous.isPresent()) {
            throw new IllegalArgumentException("the event 'start' composes version 1 and has no previous version");
        }
        if (event != CompositionEvent.START && previous.isEmpty()) {
            throw new IllegalArgumentException("the event '%s' needs the version in force".formatted(event.wireName()));
        }
        previous.filter(instance -> !instance.header().planId().equals(planId)).ifPresent(instance -> {
            throw new IllegalArgumentException("the previous version belongs to the plan '%s'"
                    .formatted(instance.header().planId()));
        });
        checkPostRun(event, facts.postRun());
    }

    /**
     * The event and the {@value PlanFacts#POST_RUN_FACT} fact agree: {@link CompositionEvent#FALLBACK} composes
     * {@code returned} or {@code handed_back}, {@link CompositionEvent#EMPTY_HARVEST} composes {@code empty_harvest},
     * and every other event composes no post-run-only form.
     */
    private static void checkPostRun(CompositionEvent event, Optional<String> postRun) {
        var admitted = switch (event) {
            case FALLBACK -> List.of(POST_RUN_RETURNED, POST_RUN_HANDED_BACK);
            case EMPTY_HARVEST -> List.of(POST_RUN_EMPTY_HARVEST);
            default -> List.<String>of();
        };
        if (admitted.isEmpty() && postRun.isPresent()) {
            throw new IllegalArgumentException("the event '%s' has no %s fact, but it is '%s'"
                    .formatted(event.wireName(), PlanFacts.POST_RUN_FACT, postRun.get()));
        }
        if (!admitted.isEmpty() && postRun.filter(admitted::contains).isEmpty()) {
            throw new IllegalArgumentException("the event '%s' needs the %s fact %s, but it is %s"
                    .formatted(event.wireName(), PlanFacts.POST_RUN_FACT, admitted,
                            postRun.map("'%s'"::formatted).orElse("unset")));
        }
    }

    /**
     * @return the number of the version this request composes: {@code 1} for {@link CompositionEvent#START}, else one
     *         after the previous version
     */
    int nextVersion() {
        return previous.map(instance -> instance.version() + 1).orElse(1);
    }

    /**
     * One sub-unit a call may reach: its reference, named by digest in the header, and its tree, read by the
     * verification of the version.
     *
     * @param reference  the unit reference
     * @param definition the tree of the unit
     * @since 0.1
     */
    public record SubUnit(UnitReference reference, WorkflowDefinition definition) {

        /**
         * Refuses a missing component and a reference that names another unit.
         *
         * @param reference  the unit reference
         * @param definition the tree of the unit
         * @throws IllegalArgumentException if the reference names another unit than the tree
         */
        public SubUnit {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(definition, "definition");
            if (!reference.unit().equals(definition.id())) {
                throw new IllegalArgumentException("the reference '%s' names another unit than '%s'"
                        .formatted(reference.unit(), definition.id()));
            }
        }
    }

    /**
     * One Phase 6 step of the plan with its order, which the {@code order<n>} selector of a prune reads.
     *
     * @param id    the step id, {@code default:<name>} or {@code project:<name>}
     * @param order the order of the step in the finalize pipeline
     * @since 0.1
     */
    public record Step(String id, int order) {

        /**
         * Refuses a missing id.
         *
         * @param id    the step id
         * @param order the order of the step
         */
        public Step {
            Objects.requireNonNull(id, "id");
        }
    }
}
