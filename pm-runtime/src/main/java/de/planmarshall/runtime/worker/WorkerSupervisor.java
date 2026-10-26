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

import de.cuioss.tools.logging.CuiLogger;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.runtime.job.SubprocessJobRunner;
import de.planmarshall.runtime.job.WorkerStart;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.launch.HarnessVerification;
import de.planmarshall.runtime.launch.HeadlessBlock;
import de.planmarshall.runtime.launch.LaunchComposer;
import de.planmarshall.runtime.launch.LaunchRequest;
import de.planmarshall.runtime.launch.WorkerLaunch;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;

/**
 * The supervisor of one role in one project (PM-WORK-3, PM-WF-14): it starts the workers its queued tasks need, up to
 * the role's {@code count}, and keeps the generations it started in a registry that every later step of the supervision
 * — detection, recycling, ending, fencing and replacement — reads.
 * <p>
 * The start decision is the specification's: a {@code fresh} role gets one worker per queued task, a {@code warm} role
 * one worker per working directory, never more than the role's {@code count} workers of its form at once and, for a
 * warm role, only while the role has queued work. Every start is bounded by the start-failure guard, the
 * verification-record gate, the machine model slots and one writer per plan, and it composes and starts through the
 * landed {@link LaunchComposer} and {@link SubprocessJobRunner#startWorker(WorkerStart)} — the supervisor keeps no
 * launch composition and no process of its own. A launch the runtime refuses before its spawn is recorded through
 * {@link SubprocessJobRunner#refuseWorker(WorkerStart.Refusal)} and reported as a declined start of the role on its
 * harness.
 * <p>
 * Every generation is registered with its {@link WorkerLifecycle}, which maps onto the job record's worker state
 * ({@link WorkerState.State}); {@link WorkerLifecycle#transitionTo(WorkerLifecycle)} refuses a transition the protocol
 * does not have and every transition out of a terminal state.
 * <p>
 * Starts of one supervisor are serialized, so two concurrent callers never start the role's workers beyond its count.
 * A successful start logs {@code WORKER_GENERATION_STARTED} exactly once.
 *
 * @since 0.1
 */
public final class WorkerSupervisor {

    private static final CuiLogger LOGGER = new CuiLogger(WorkerSupervisor.class);

    /** The budget key of the worker of a role; the learned budget of the plan package replaces the floor later. */
    private static final String BUDGET_KEY_PREFIX = "worker:";

    /** {@code 0}: the launch prompt carries no in-band bound. */
    private static final int NO_IN_BAND_BOUND = 0;

    private static final String JOBS = "jobs";
    private static final String RUN = "run";

    private final SupervisorEnvironment environment;
    private final Map<String, WorkerGeneration> generations = new LinkedHashMap<>();
    private boolean admitting = true;

    /**
     * @param environment the collaborators and installation values of the project
     * @throws NullPointerException if the environment is {@code null}
     */
    public WorkerSupervisor(SupervisorEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    /**
     * What one start of a request decided.
     *
     * @since 0.1
     */
    public sealed interface StartOutcome permits StartOutcome.Started, StartOutcome.Declined {

        /**
         * The worker was started and registered.
         *
         * @param generation the started generation
         * @since 0.1
         */
        record Started(WorkerGeneration generation) implements StartOutcome {

            /** @throws NullPointerException if the generation is {@code null} */
            public Started {
                Objects.requireNonNull(generation, "generation");
            }
        }

        /**
         * The supervisor did not start the requested worker.
         *
         * @param reason why the start was declined
         * @since 0.1
         */
        record Declined(WorkerStartRefusal reason) implements StartOutcome {

            /** @throws NullPointerException if the reason is {@code null} */
            public Declined {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    /**
     * Starts the workers these requests ask for, as far as the role's form, its {@code count}, the guard, the gate and
     * the model slots admit them.
     *
     * @param requests the starts one caller asks for, all of one role
     * @return one outcome per request the role's bounds admitted, in request order; the requests beyond the role's
     *         count are not attempted and have no outcome
     * @throws NullPointerException     if the list or an element is {@code null}
     * @throws IllegalArgumentException if the requests name different roles
     * @throws RuntimeException         whatever a start raises; the probe the guard admitted is settled before it
     *                                  propagates, so a throwing start never leaves the guard held with no future probe
     */
    public synchronized List<StartOutcome> start(List<WorkerStartRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        if (requests.isEmpty() || !admitting) {
            return List.of();
        }
        var role = requests.getFirst().role();
        for (var request : requests) {
            requireSameRole(role, request.role());
        }
        var roleName = role.artifact().name();
        var harness = role.harness();
        if (environment.startGuard().holds(roleName, harness)) {
            return declineAll(requests, WorkerStartRefusal.ROLE_GUARD_HELD);
        }
        var outcomes = new ArrayList<StartOutcome>();
        try {
            if (!environment.eligibilityGate().admits(roleName, harness)) {
                return declineAll(requests, WorkerStartRefusal.HARNESS_INELIGIBLE);
            }
            for (var request : admitted(role, requests)) {
                outcomes.add(startOne(request));
            }
            return List.copyOf(outcomes);
        } finally {
            settleProbeUnlessStarted(role, outcomes);
        }
    }

    /**
     * @param generationId the worker's generation
     * @return the registered generation, or empty when this supervisor started none of that id
     */
    public synchronized Optional<WorkerGeneration> generation(String generationId) {
        Objects.requireNonNull(generationId, "generationId");
        return Optional.ofNullable(generations.get(generationId));
    }

    /** @return the registered generations in start order */
    public synchronized List<WorkerGeneration> generations() {
        return List.copyOf(generations.values());
    }

    /**
     * Replaces one registered generation with its successor value, so a lifecycle transition and the terminal state a
     * caller wrote are visible to the next decision.
     *
     * @param generation the generation in its new state
     * @throws NullPointerException if the generation is {@code null}
     */
    public synchronized void replaced(WorkerGeneration generation) {
        Objects.requireNonNull(generation, "generation");
        generations.put(generation.generationId(), generation);
        notifyAll();
    }

    /** Stops admitting starts, as the runtime drain requires; a start after this one starts nothing. */
    public synchronized void stopAdmitting() {
        admitting = false;
    }

    /** @return whether this supervisor still admits starts */
    public synchronized boolean admitting() {
        return admitting;
    }

    /** @return whether no generation of this supervisor is running any more */
    public synchronized boolean quiet() {
        return generations.values().stream().noneMatch(generation -> !generation.terminal());
    }

    /**
     * Waits until no generation runs any more, at most until the deadline.
     *
     * @param deadline the end of the drain timeout
     * @return {@code true} when nothing runs any more, {@code false} when the deadline passed first
     * @throws InterruptedException if the wait is interrupted
     */
    public synchronized boolean awaitQuiet(Instant deadline) throws InterruptedException {
        Objects.requireNonNull(deadline, "deadline");
        while (!quiet()) {
            var remaining = Duration.between(environment.clock().instant(), deadline).toMillis();
            if (remaining <= 0) {
                return false;
            }
            wait(remaining);
        }
        return true;
    }

    /** @return the environment this supervisor was built with */
    public SupervisorEnvironment environment() {
        return environment;
    }

    /**
     * Settles the probe the start-failure guard admitted to this start when no worker of it started: the start
     * returned only declines, or it threw. Neither settles the probe through a first call or a counted failure,
     * so the consumed probe is released and its backoff restarted, instead of the guard holding with no future
     * probe. A probe worker that did start keeps the probe in flight and settles it through its own first call or
     * its own failure.
     *
     * @param role     the role whose start the guard admitted
     * @param outcomes the outcomes attempted before the start returned or threw, in request order
     */
    private void settleProbeUnlessStarted(EffectiveRole role, List<StartOutcome> outcomes) {
        if (outcomes.stream().noneMatch(StartOutcome.Started.class::isInstance)) {
            environment.startGuard().probeDeclined(role.artifact().name(), role.harness());
        }
    }

    private StartOutcome startOne(WorkerStartRequest request) {
        var role = request.role();
        var generationId = JobId.random(JobId.Kind.LLM).value();
        var block = HeadlessBlock.of(environment.profile());
        var slot = environment.slots().claim(generationId, role.artifact().name());
        if (slot.isEmpty()) {
            return new StartOutcome.Declined(WorkerStartRefusal.NO_MODEL_SLOT);
        }
        var verification = environment.harnessVerifier().verify(role.harness(), block);
        if (verification instanceof HarnessVerification.Refused refused) {
            refuse(role, generationId, refused);
            environment.slots().release(slot.get());
            return new StartOutcome.Declined(WorkerStartRefusal.LAUNCH_REFUSED);
        }
        var enrolment = ((HarnessVerification.Accepted) verification).enrolment();
        WorkerLaunch launch;
        try {
            launch = environment.composer().compose(launchRequest(role, request, block, enrolment, generationId));
        } catch (LaunchComposer.CompositionException failure) {
            refuse(role, generationId, new HarnessVerification.Refused(failure.refusal(), failure.getMessage()));
            environment.slots().release(slot.get());
            return new StartOutcome.Declined(WorkerStartRefusal.LAUNCH_REFUSED);
        }
        var started = startWorker(role, block, launch, slot.get());
        if (started.isEmpty()) {
            environment.slots().release(slot.get());
            return new StartOutcome.Declined(WorkerStartRefusal.LAUNCH_REFUSED);
        }
        var generation = register(role, request, launch, started.get(), slot.get());
        LOGGER.info(INFO.WORKER_GENERATION_STARTED, generation.generationId(), generation.role(), generation.harness());
        return new StartOutcome.Started(generation);
    }

    /**
     * Starts the composed worker and returns its slot on every path that started none: a runner that throws has already
     * settled or left no job, so the slot is released. A successful start keeps the slot for the generation.
     */
    private Optional<JobId> startWorker(EffectiveRole role, HeadlessBlock block, WorkerLaunch launch,
            ModelSlots.Lease lease) {
        try {
            var result = environment.starter().startWorker(new WorkerStart(launch, environment.enrolment(),
                    new JobRecord.Scope(JobRecord.Scope.Type.PROJECT, null), budgetKey(role),
                    role.artifact().budgetFloorSeconds(), floorDerivation(), block.writablePaths(environment.home()),
                    Optional.empty()));
            return switch (result) {
                case JobRunner.StartResult.Started(var jobId) -> Optional.of(jobId);
                case JobRunner.StartResult.Refused _ -> Optional.empty();
            };
            // Deliberately RuntimeException: the landed start may fail with any runtime exception (an illegal worker
            // id, an unwritable record, an unreadable store), and the slot must be released on every one of them, so
            // narrowing the catch would leak the slot. Owner: runtime supervision.
            // cui-rewrite:disable InvalidExceptionUsageRecipe
        } catch (RuntimeException failure) {
            environment.slots().release(lease);
            throw failure;
        }
    }

    private WorkerGeneration register(EffectiveRole role, WorkerStartRequest request, WorkerLaunch launch, JobId jobId,
            ModelSlots.Lease lease) {
        var generation = new WorkerGeneration(launch.generationId(), role.artifact().name(), role.harness(),
                launch.model(), role.form(), request.workdirKind(), request.planId(), launch.workingDirectory(),
                role.configurationDigest(), environment.clock().instant(), jobId, lease, WorkerLifecycle.STARTING,
                null, List.of(), 0L, WorkerState.ContextTokens.NOT_MEASURED, null);
        generations.put(generation.generationId(), generation);
        return generation;
    }

    private void refuse(EffectiveRole role, String generationId, HarnessVerification.Refused refused) {
        environment.starter().refuseWorker(new WorkerStart.Refusal(generationId, role, environment.enrolment(),
                new JobRecord.Scope(JobRecord.Scope.Type.PROJECT, null), budgetKey(role),
                role.artifact().budgetFloorSeconds(), floorDerivation(),
                CanonicalJson.write(LaunchComposer.refusalBlock(role, generationId, environment.clock().instant())),
                refused.reason(), refused.detail()));
    }

    private LaunchRequest launchRequest(EffectiveRole role, WorkerStartRequest request, HeadlessBlock block,
            HarnessEnrolment enrolment, String generationId) {
        return new LaunchRequest(role, block, enrolment, generationId, environment.runtimeHome(),
                environment.base().resolve(RUN).resolve(JOBS).resolve(generationId), request.workdirKind(),
                request.planId(), request.workdir(), environment.relaySocket(), environment.shimParagraph(),
                environment.skills(), role.form() == RoleForm.FRESH, request.writesFreeText(), request.stateSkills(),
                request.domainSkills(), request.envelope(), NO_IN_BAND_BOUND, request.zeroToolPrompt(),
                request.resumeSessionId(), environment.clock());
    }

    /**
     * @return the requests the role's form and count admit, in request order: one per queued task for a fresh role, one
     *         per working directory for a warm role
     */
    private List<WorkerStartRequest> admitted(EffectiveRole role, List<WorkerStartRequest> requests) {
        var limit = role.artifact().count();
        var existing = existing(role);
        var room = Math.max(0, limit - existing.size());
        if (room == 0) {
            return List.of();
        }
        if (role.form() == RoleForm.FRESH) {
            return List.copyOf(requests.subList(0, Math.min(room, requests.size())));
        }
        var served = workingDirectories(existing);
        var admitted = new ArrayList<WorkerStartRequest>();
        for (var request : requests) {
            if (admitted.size() == room) {
                break;
            }
            if (served.add(workingDirectory(request))) {
                admitted.add(request);
            }
        }
        return List.copyOf(admitted);
    }

    /** @return the generations of the role's form that have not reached a terminal lifecycle state */
    private List<WorkerGeneration> existing(EffectiveRole role) {
        return generations.values().stream()
                .filter(generation -> generation.role().equals(role.artifact().name()))
                .filter(generation -> generation.form() == role.form())
                .filter(generation -> !generation.lifecycle().terminal())
                .toList();
    }

    private static Set<WorkingDirectory> workingDirectories(List<WorkerGeneration> generations) {
        var directories = new LinkedHashSet<WorkingDirectory>();
        for (var generation : generations) {
            directories.add(new WorkingDirectory(generation.workdirKind(), generation.planId(), generation.workdir()));
        }
        return directories;
    }

    private static WorkingDirectory workingDirectory(WorkerStartRequest request) {
        return new WorkingDirectory(request.workdirKind(), request.planId(), request.workdir());
    }

    private static List<StartOutcome> declineAll(List<WorkerStartRequest> requests, WorkerStartRefusal reason) {
        return Collections.nCopies(requests.size(), new StartOutcome.Declined(reason));
    }

    private static void requireSameRole(EffectiveRole expected, EffectiveRole actual) {
        if (!expected.artifact().name().equals(actual.artifact().name())) {
            throw new IllegalArgumentException("One start asks for one role, not '%s' and '%s'"
                    .formatted(expected.artifact().name(), actual.artifact().name()));
        }
    }

    private static String budgetKey(EffectiveRole role) {
        return BUDGET_KEY_PREFIX + role.artifact().name();
    }

    private static JobRecord.BudgetDerivation floorDerivation() {
        return new JobRecord.BudgetDerivation(JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT, null);
    }

    /**
     * The working directory a warm worker serves: its kind, the plan of a plan worktree and the path itself.
     *
     * @param kind   the kind of the location
     * @param planId the plan of a plan worktree; {@code null} otherwise
     * @param path   the working directory
     */
    private record WorkingDirectory(LaunchRequest.WorkdirKind kind, String planId, Path path) {
    }
}
