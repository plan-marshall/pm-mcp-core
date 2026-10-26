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

import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.ModelSlotStore;
import de.planmarshall.runtime.launch.ModelSlotScheduler;

/**
 * The admission of the machine model slots as the supervisor sees it (PM-WF-14, Model Slots &amp; One Writer per Plan):
 * one worker generation claims a slot before its launch, keeps it from the spawn to its exit — a warm worker for its
 * whole generation, idle or not — and releases it when its work ends. The curtain behind it is the landed
 * {@link ModelSlotScheduler}: {@link #over(ModelSlotScheduler, LeaseOwner, ModelSlotStore.WorkerClass,
 * ModelSlotStore.Form, String)} is the production adapter, and a test implements this interface without a store.
 * <p>
 * The three outcomes of an admission map onto the interface as follows: {@code Granted} is the {@link Lease},
 * {@code Queued} is no lease while the request keeps its place in the FIFO, and {@code Deferred} — the plan already has
 * a writer worker — is no lease as well, so the supervisor declines the start without waiting. Because the supervisor
 * creates the generation id per attempt, a declined start {@link #withdraw(String) withdraws} the place it asked for, so
 * a queued request never outlives the start that declined it; a caller that asks for the same generation twice is
 * answered with its place, exactly as the scheduler answers a repeated request.
 *
 * @since 0.1
 */
public interface ModelSlots {

    /**
     * One held model slot.
     *
     * @param generationId the worker generation that holds it
     * @param role         the role the worker serves
     * @since 0.1
     */
    record Lease(String generationId, String role) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Lease {
            Objects.requireNonNull(generationId, "generationId");
            Objects.requireNonNull(role, "role");
        }
    }

    /**
     * Asks for a slot for one worker.
     *
     * @param generationId the worker generation
     * @param role         the role the worker serves
     * @return the slot the worker holds, or empty when it waits or is deferred; the caller starts no worker then
     */
    Optional<Lease> claim(String generationId, String role);

    /**
     * Withdraws the place of a generation that never started: a request the scheduler queued leaves the FIFO, so it can
     * neither be admitted later for a worker that does not exist nor defer a later writer of its plan. A generation that
     * holds no place is unaffected.
     *
     * @param generationId the worker generation whose start declined
     */
    void withdraw(String generationId);

    /**
     * Releases the slot of a settled worker and admits the workers the release makes room for.
     *
     * @param lease the slot to release
     */
    void release(Lease lease);

    /**
     * @return the role of the head of the FIFO, when a worker waits for a slot
     */
    Optional<String> nextWaitingRole();

    /**
     * Releases every slot of one plan of the supervisor's project and drops its waiting workers: a paused plan holds no
     * model slot.
     *
     * @param planId the plan
     */
    void releasePlan(String planId);

    /**
     * The adapter over the landed model-slot admission: one supervisor's requests carry the project's lease owner, the
     * role's class and form and the plan the worker serves.
     *
     * @param scheduler   the landed admission of the machine's model slots
     * @param owner       the owner of the slots this adapter claims
     * @param workerClass the class of the workers that ask
     * @param form        the form of the workers that ask
     * @param planId      the plan the workers serve; {@code null} for a worker that serves no plan
     * @return the admission the supervisor uses
     * @throws NullPointerException if the scheduler, the owner, the class or the form is {@code null}
     */
    static ModelSlots over(ModelSlotScheduler scheduler, LeaseOwner owner, ModelSlotStore.WorkerClass workerClass,
            ModelSlotStore.Form form, String planId) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(workerClass, "workerClass");
        Objects.requireNonNull(form, "form");
        return new ModelSlots() {

            @Override
            public Optional<Lease> claim(String generationId, String role) {
                Objects.requireNonNull(generationId, "generationId");
                Objects.requireNonNull(role, "role");
                var admission = scheduler.request(new ModelSlotScheduler.Request(owner, generationId, role, workerClass,
                        form, planId));
                return switch (admission) {
                    case ModelSlotScheduler.Granted _ -> Optional.of(new Lease(generationId, role));
                    case ModelSlotScheduler.Queued _ -> Optional.empty();
                    case ModelSlotScheduler.Deferred _ -> Optional.empty();
                };
            }

            @Override
            public void withdraw(String generationId) {
                Objects.requireNonNull(generationId, "generationId");
                scheduler.release(generationId);
            }

            @Override
            public void release(Lease lease) {
                Objects.requireNonNull(lease, "lease");
                scheduler.release(lease.generationId());
            }

            @Override
            public Optional<String> nextWaitingRole() {
                return scheduler.snapshot().waiting().stream().findFirst().map(ModelSlotStore.Waiting::role);
            }

            @Override
            public void releasePlan(String plan) {
                Objects.requireNonNull(plan, "planId");
                scheduler.releasePlan(owner.projectId(), plan);
            }
        };
    }
}
