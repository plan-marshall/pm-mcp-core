/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.runtime.launch.LaunchRefusal;
import de.planmarshall.runtime.launch.WorkerLaunch;

/**
 * A worker start the supervisor asks for (PM-WORK-3, Job Record): the composed launch, the verified project enrolment
 * whose confinement the worker runs under, the budget of its role and the sub-paths of its harness that stay writable.
 * The generation id of the launch is the job id; a start whose id is not a free worker id is refused.
 *
 * @param launch        the composed launch
 * @param enrolment     the verified enrolment of the project
 * @param scope         the scope of the worker: the project or the machine
 * @param budgetKey     the budget key of the worker
 * @param budgetSeconds the worker budget of its role in seconds
 * @param budgetDerivation how the budget was derived
 * @param writablePaths the sub-paths the harness's profile declares writable
 * @param egressProxy   the loopback URL of the worker's egress proxy; {@code null} when none
 * @param roleClass     the role class of the launched role, taken from the launch; the worker's write set follows it
 * @param planWorktree  the plan worktree the worker runs in, the launch's working directory for a start in a plan
 *                      worktree; {@code null} for a start in any other working directory
 * @since 0.1
 */
@Builder
public record WorkerStart(WorkerLaunch launch, ProjectEnrolment enrolment, JobRecord.Scope scope, String budgetKey,
long budgetSeconds, JobRecord.BudgetDerivation budgetDerivation, List<Path> writablePaths,
@Nullable String egressProxy, RoleClass roleClass, @Nullable Path planWorktree) {

    /**
     * Copies the writable paths and validates the scope of a worker.
     *
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the scope is neither a project nor the machine, or the plan worktree is not
     *                                  absolute
     */
    public WorkerStart {
        Objects.requireNonNull(launch, "launch");
        Objects.requireNonNull(enrolment, "enrolment");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(roleClass, "roleClass");
        if (scope.type() != JobRecord.Scope.Type.PROJECT && scope.type() != JobRecord.Scope.Type.MACHINE) {
            throw new IllegalArgumentException("A worker belongs to a project or the machine, not a "
                    + scope.type().wireName());
        }
        if (planWorktree != null && !planWorktree.isAbsolute()) {
            throw new IllegalArgumentException("planWorktree must be absolute: " + planWorktree);
        }
        writablePaths = List.copyOf(Objects.requireNonNull(writablePaths, "writablePaths"));
    }

    /**
     * A worker launch the runtime refused before its spawn: what the job record needs to record the fate
     * {@code refused} with the refusal reason, without a composed launch.
     *
     * @param generationId the generation the launch would have had
     * @param role         the effective role that was launched
     * @param enrolment    the verified enrolment of the project
     * @param scope        the scope of the worker
     * @param budgetKey    the budget key of the worker
     * @param budgetSeconds the worker budget of its role
     * @param budgetDerivation how the budget was derived
     * @param workerBlock  the worker block the refusal is recorded with
     * @param reason       why the launch was refused
     * @param detail       the detail both clients show beside the reason
     * @since 0.1
     */
    @Builder
    public record Refusal(String generationId, EffectiveRole role, ProjectEnrolment enrolment, JobRecord.Scope scope,
    String budgetKey, long budgetSeconds, JobRecord.BudgetDerivation budgetDerivation, String workerBlock,
    LaunchRefusal reason, String detail) {

        /** @throws NullPointerException if a component is {@code null} */
        public Refusal {
            Objects.requireNonNull(generationId, "generationId");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(enrolment, "enrolment");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(budgetDerivation, "budgetDerivation");
            Objects.requireNonNull(workerBlock, "workerBlock");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
