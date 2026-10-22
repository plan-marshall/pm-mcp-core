/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.role.RoleCatalog;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleResolution;
import de.planmarshall.core.role.RoleResolver;
import de.planmarshall.core.role.RoleValidationReason;

/**
 * The one model caller (PM-WORK-1, The Model Caller; PM-WF-5): every model step of every workflow goes through it, and no
 * other part of the runtime knows what a role is for. When a model state creates a task, the caller acts in this order:
 * <ol>
 * <li><em>Resolve</em> reads the effective configuration of the state's role; an absent or invalid artifact closes the
 * task with {@code role_undefined}, and a class that does not fit the entity kind, or a consulted {@code zero_tool} role,
 * with {@code class_mismatch}.</li>
 * <li><em>Qualify</em> looks up a valid qualification record for the role's qualification digest; without one the task
 * closes with {@code role_unqualified}, and a warm artifact that qualifies only fresh runs fresh with
 * {@code form_downgraded}. A task of the {@code judge} or the {@code steward} skips this step.</li>
 * <li><em>Available</em> closes the task with {@code role_unavailable} while the start-failure guard of the role on its
 * harness holds.</li>
 * <li><em>Queue</em> queues the task for the role at the role's current configuration digest.</li>
 * <li><em>Serve</em> belongs to the supervisor: it serves the task with a warm worker or starts a fresh one. The caller
 * has no session path of its own — it neither starts a worker nor resumes a stored harness session.</li>
 * </ol>
 * The caller holds a resolver of the plan's effective role set ({@link RoleCatalog#forPlan}) and one of the steward's
 * machine-derived configuration ({@link RoleCatalog#resolveSteward()}); each resolution reads the stores afresh.
 *
 * @since 0.1
 */
public final class ModelCaller {

    private final RoleResolver roles;
    private final RoleResolver steward;
    private final QualificationLookup qualifications;
    private final AvailabilityGuard availability;
    private final ModelQueue queue;

    /**
     * @param roles          the resolver of the plan's effective role set, with the plan's overrides applied
     * @param steward        the resolver of the steward's configuration, taken from the machine
     * @param qualifications the qualification lookup
     * @param availability   the start-failure guard
     * @param queue          the task queue
     */
    public ModelCaller(RoleResolver roles, RoleResolver steward, QualificationLookup qualifications,
            AvailabilityGuard availability, ModelQueue queue) {
        this.roles = Objects.requireNonNull(roles, "roles");
        this.steward = Objects.requireNonNull(steward, "steward");
        this.qualifications = Objects.requireNonNull(qualifications, "qualifications");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    /**
     * @param job the task a model state creates
     * @return the queued task, or the reason the task closed without an attempt
     */
    public ModelCallResult call(ModelJob job) {
        var resolution = resolver(job.role()).resolve(job.role());
        if (resolution instanceof RoleResolution.Undefined undefined) {
            return close(job, ModelCallResult.ROLE_UNDEFINED, undefined.reason(), undefined.detail());
        }
        var role = ((RoleResolution.Defined) resolution).role();
        if (!job.accepts(role.roleClass())) {
            return close(job, ModelCallResult.CLASS_MISMATCH, Optional.empty(), job.requirement(role.roleClass()));
        }
        var form = role.form();
        var downgraded = false;
        if (!skipsQualification(job.role())) {
            var qualified = qualifications.qualifiedForm(job.role(), role.qualificationDigest(), role.form());
            if (qualified.isEmpty()) {
                return close(job, ModelCallResult.ROLE_UNQUALIFIED, Optional.empty(),
                        "no valid qualification record covers the role's qualification digest");
            }
            form = qualified.get();
            downgraded = role.form() == RoleForm.WARM && form == RoleForm.FRESH;
        }
        if (availability.holds(job.role(), role.harness())) {
            return close(job, ModelCallResult.ROLE_UNAVAILABLE, Optional.empty(),
                    "the start-failure guard of the role on '%s' holds".formatted(role.harness()));
        }
        var queued = queue.enqueue(new ModelQueue.Enqueue(job.scopeId(), job.stateId(), job.entityKey(), job.role(),
                form, role.configurationDigest(), role.harness(), role.model(), role.effort()));
        return new ModelCallResult.Queued(queued.queueTaskId(), job.role(), form, role.configurationDigest(),
                downgraded);
    }

    private RoleResolver resolver(String role) {
        return RoleCatalog.STEWARD.equals(role) ? steward : roles;
    }

    private static boolean skipsQualification(String role) {
        return RoleCatalog.JUDGE.equals(role) || RoleCatalog.STEWARD.equals(role);
    }

    private ModelCallResult close(ModelJob job, String reason, Optional<RoleValidationReason> validationReason,
            String detail) {
        queue.close(new ModelQueue.Close(job.scopeId(), job.stateId(), job.entityKey(), job.role(), reason));
        return new ModelCallResult.Closed(job.role(), reason, validationReason, detail);
    }
}
