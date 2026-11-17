/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.nio.file.Path;
import java.util.Objects;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.qualification.GradingConsent;
import de.planmarshall.core.role.qualification.GradingHarness;
import de.planmarshall.core.role.qualification.GradingOffer;
import de.planmarshall.core.role.qualification.GradingOutcome;
import de.planmarshall.core.role.qualification.GradingPlan;
import de.planmarshall.core.role.qualification.JudgeScorer;

/**
 * The runtime side of the entry {@code pm-operator roles qualify} (Model Work § Grading): the service method the client
 * calls beside the two grading ports this package implements.
 * <p>
 * The service is the grading engine as the assembly wires it — the engine lives in {@code pm-workflow}, which this module
 * does not depend on — over the adapter's {@link GradingHarness} and {@link JudgeScorer}: an item is offered as a task of
 * the task protocol and run through the supervisor, and the engine decides the measurements and writes the record.
 * <p>
 * The assembly in {@code plan-marshall-mcp} supplies the two ports the adapter needs — the task offers of the protocol and
 * the item worktrees of the project's repository — and the client renders the offer and the owed record; both are owed
 * outside this repository and listed in the pull request.
 *
 * @since 0.1
 */
public final class QualificationService {

    private final GradingEngine engine;

    /**
     * @param engine the grading engine of the assembly
     * @throws NullPointerException if the engine is {@code null}
     */
    public QualificationService(GradingEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * @param role            the role to grade
     * @param supervisor      the supervisor that starts the workers of the role and its judge
     * @param offers          the task protocol's offer port
     * @param worktrees       the item worktrees of the project's repository
     * @param projectCheckout the enrolled project checkout
     * @return the harness the engine offers the role's items through
     */
    public static GradingHarness harness(EffectiveRole role, WorkerSupervisor supervisor, ItemTaskOffers offers,
            ItemWorktrees worktrees, Path projectCheckout) {
        return new WorkerGradingHarness(role, supervisor, offers, worktrees, projectCheckout);
    }

    /**
     * @param judge      the judge configuration
     * @param supervisor the supervisor that starts its zero-tool workers
     * @param offers     the task protocol's offer port
     * @return the scorer the engine sends its rubric answers to
     */
    public static JudgeScorer scorer(EffectiveRole judge, WorkerSupervisor supervisor, ItemTaskOffers offers) {
        return new WorkerJudgeScorer(judge, supervisor, offers);
    }

    /**
     * @param plan a grading plan
     * @return the offer the operator consents to, with the expected tokens
     */
    public GradingOffer offer(GradingPlan plan) {
        return engine.offer(plan);
    }

    /**
     * @param plan    a grading plan
     * @param consent the operator's answer to the offer
     * @return the record the grading wrote, the decline, or the reason it was refused
     */
    public GradingOutcome grade(GradingPlan plan, GradingConsent consent) {
        return engine.grade(plan, consent);
    }

    /**
     * The grading engine as the assembly supplies it: the offer the operator consents to and the run that writes the
     * record (Model Work § Grading).
     *
     * @since 0.1
     */
    public interface GradingEngine {

        /**
         * @param plan a grading plan
         * @return the offer the operator consents to
         */
        GradingOffer offer(GradingPlan plan);

        /**
         * @param plan    a grading plan
         * @param consent the operator's answer
         * @return the outcome
         */
        GradingOutcome grade(GradingPlan plan, GradingConsent consent);
    }
}
