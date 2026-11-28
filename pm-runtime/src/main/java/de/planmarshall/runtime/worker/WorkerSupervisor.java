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
import org.jspecify.annotations.Nullable;

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
import de.planmarshall.runtime.launch.LaunchRefusal;
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
 * The role's starts are serialized by one start lock, so two concurrent callers never start beyond the role's count.
 * That lock is NOT the registry's monitor: the harness's start spacing is awaited outside the registry monitor, and a
 * worker's call never takes the start lock, so the pending worker's first call — which reads the registry through
 * {@link #generation(String)} — releases the wait instead of queueing behind it. A successful start logs
 * {@code WORKER_GENERATION_STARTED} exactly once.
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
    private final Object startLock = new Object();
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
         * @param report the bounded, redacted report of a refusal this decline counted as a start failure, which the
         *               assembly's clients show to the operator; {@code null} when the decline counted nothing
         * @since 0.1
         */
        record Declined(WorkerStartRefusal reason, @Nullable StartFailureReport report) implements StartOutcome {

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
     * @return one outcome per request the role's bounds and the guard admitted, in request order; the requests beyond
     *         the role's count, and the requests left after the guard began to hold mid-batch, are not attempted and
     *         have no outcome
     * @throws NullPointerException     if the list or an element is {@code null}
     * @throws IllegalArgumentException if the requests name different roles
     * @throws RuntimeException         whatever a start raises; the probe the guard admitted is settled before it
     *                                  propagates, so a throwing start never leaves the guard held with no future probe
     */
    public List<StartOutcome> start(List<WorkerStartRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        if (requests.isEmpty()) {
            return List.of();
        }
        var role = requests.getFirst().role();
        for (var request : requests) {
            requireSameRole(role, request.role());
        }
        synchronized (startLock) {
            var roleName = role.artifact().name();
            var harness = role.harness();
            synchronized (this) {
                if (!admitting) {
                    return List.of();
                }
                if (environment.startGuard().holds(roleName, harness)) {
                    return declineAll(requests, WorkerStartRefusal.ROLE_GUARD_HELD);
                }
                if (!environment.eligibilityGate().admits(roleName, harness)) {
                    return declineAll(requests, WorkerStartRefusal.HARNESS_INELIGIBLE);
                }
            }
            var outcomes = new ArrayList<StartOutcome>();
            try {
                for (var request : admitted(role, requests)) {
                    outcomes.add(startOne(request));
                    if (environment.startGuard().holds(roleName, harness)) {
                        // the guard filled mid-batch: the remaining requests are not attempted and count nothing
                        break;
                    }
                }
                return List.copyOf(outcomes);
            } finally {
                settleProbeUnlessStarted(role, outcomes);
            }
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

    /**
     * Retires the failed generations of one role on one harness from the registry, so a start-failure guard rebuilt from
     * the registry does not recreate a hold that was cleared. Only the terminal {@link WorkerLifecycle#START_FAILED}
     * generations of that role and harness are removed; every other generation stays.
     *
     * @param role    the role
     * @param harness the harness id of the harness
     * @throws NullPointerException if a value is {@code null}
     */
    public synchronized void forgetStartFailures(String role, String harness) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(harness, "harness");
        generations.values().removeIf(generation -> generation.lifecycle() == WorkerLifecycle.START_FAILED
                && generation.role().equals(role) && generation.harness().equals(harness));
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
        var harness = role.harness();
        var generationId = JobId.random(JobId.Kind.LLM).value();
        var block = HeadlessBlock.of(environment.profile());
        ModelSlots.Lease lease;
        WorkerLaunch launch;
        synchronized (this) {
            var slot = environment.slots().claim(generationId, role.artifact().name());
            if (slot.isEmpty()) {
                environment.slots().withdraw(generationId);
                return new StartOutcome.Declined(WorkerStartRefusal.NO_MODEL_SLOT, null);
            }
            lease = slot.get();
            var verification = environment.harnessVerifier().verify(harness, block);
            if (verification instanceof HarnessVerification.Refused refused) {
                return declineRefused(role, generationId, refused, lease);
            }
            var enrolment = ((HarnessVerification.Accepted) verification).enrolment();
            try {
                launch = environment.composer().compose(launchRequest(role, request, block, enrolment, generationId));
            } catch (LaunchComposer.CompositionException failure) {
                return declineRefused(role, generationId,
                        new HarnessVerification.Refused(failure.refusal(), failure.getMessage()), lease);
            }
        }
        awaitTurn(harness, generationId);
        synchronized (this) {
            var started = false;
            try {
                if (!admitting) {
                    // the drain began while this start waited for its turn: nothing is started or registered
                    environment.slots().release(lease);
                    return new StartOutcome.Declined(WorkerStartRefusal.STOPPED_ADMITTING, null);
                }
                var result = startWorker(role, block, launch, lease, request);
                if (result instanceof JobRunner.StartResult.Refused refused) {
                    environment.slots().release(lease);
                    var report = environment.startGuard().startFailed(role.artifact().name(), harness,
                            refused.reason());
                    return new StartOutcome.Declined(WorkerStartRefusal.LAUNCH_REFUSED, report);
                }
                var job = ((JobRunner.StartResult.Started) result).jobId();
                var generation = register(role, request, launch, job, lease);
                started = true;
                LOGGER.info(INFO.WORKER_GENERATION_STARTED, generation.generationId(), generation.role(),
                        generation.harness());
                return new StartOutcome.Started(generation);
            } finally {
                if (!started) {
                    environment.spacing().ended(harness, generationId);
                }
            }
        }
    }

    /**
     * Records a launch the runtime refused before its spawn, releases its model slot and, where the specification
     * counts the refusal, counts it into the role's start-failure guard. The refusal is recorded and the slot released
     * FIRST, so a guard whose count throws — the audit sink or the protocol's close at the hold — neither loses the
     * refusal nor leaks the slot; the count's bounded, redacted report travels on the declined outcome, which is where
     * the assembly's clients read the operator-facing text.
     *
     * @param role         the role whose launch was refused
     * @param generationId the refused launch's generation
     * @param refused      the refusal of the launch
     * @param lease        the model slot the refused launch held
     * @return the declined outcome, carrying the report when the refusal was counted
     */
    private StartOutcome declineRefused(EffectiveRole role, String generationId, HarnessVerification.Refused refused,
            ModelSlots.Lease lease) {
        refuse(role, generationId, refused);
        environment.slots().release(lease);
        var report = countsAsStartFailure(refused.reason())
                ? environment.startGuard().startFailed(role.artifact().name(), role.harness(), refused.detail())
                : null;
        return new StartOutcome.Declined(WorkerStartRefusal.LAUNCH_REFUSED, report);
    }

    /**
     * Waits for the harness's start spacing outside the registry monitor: the workers of one harness start one after
     * another on the machine, so a start waits until the previous worker of that harness made its first call or ended,
     * at most the spacing's bound. The start lock is held and no worker call takes it, so the pending worker's first
     * call reaches the release. The spacing only ever delays a start and never refuses one, so an interrupted wait
     * proceeds at once with the thread's flag restored.
     */
    private void awaitTurn(String harness, String generationId) {
        try {
            environment.spacing().awaitTurn(harness, generationId);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Starts the composed worker and answers the runner's result. A runner that throws has already settled or left no
     * job, so the slot is released and the failure passes on; a runner that refuses releases nothing here — the caller
     * releases the slot and the spacing turn, because no worker of the start is left. A started worker keeps the slot
     * for the generation. The release runs in a {@code finally} guarded by whether the start returned, so no failure of
     * the start needs to be caught.
     * <p>
     * The start carries the launch's role class and, for a start in a plan worktree, the launch's working directory as
     * the plan worktree, so the worker's write set follows its role class.
     */
    private JobRunner.StartResult startWorker(EffectiveRole role, HeadlessBlock block, WorkerLaunch launch,
            ModelSlots.Lease lease, WorkerStartRequest request) {
        var returned = false;
        try {
            var result = environment.starter().startWorker(WorkerStart.builder()
                    .launch(launch)
                    .enrolment(environment.enrolment())
                    .scope(new JobRecord.Scope(JobRecord.Scope.Type.PROJECT, null))
                    .budgetKey(budgetKey(role))
                    .budgetSeconds(role.artifact().budgetFloorSeconds())
                    .budgetDerivation(floorDerivation())
                    .writablePaths(block.writablePaths(environment.home()))
                    .egressProxy(null)
                    .roleClass(launch.roleClass())
                    .planWorktree(request.workdirKind() == LaunchRequest.WorkdirKind.PLAN_WORKTREE
                            ? launch.workingDirectory()
                            : null)
                    .build());
            returned = true;
            return result;
        } finally {
            if (!returned) {
                environment.slots().release(lease);
            }
        }
    }

    private WorkerGeneration register(EffectiveRole role, WorkerStartRequest request, WorkerLaunch launch, JobId jobId,
            ModelSlots.Lease lease) {
        var generation = WorkerGeneration.builder()
                .generationId(launch.generationId())
                .role(role.artifact().name())
                .harness(role.harness())
                .model(launch.model())
                .form(role.form())
                .workdirKind(request.workdirKind())
                .planId(request.planId())
                .workdir(launch.workingDirectory())
                .configurationDigest(role.configurationDigest())
                .startedAt(environment.clock().instant())
                .jobId(jobId)
                .slot(lease)
                .lifecycle(WorkerLifecycle.STARTING)
                .leasedTask(null)
                .servedTasks(List.of())
                .idleWakeups(0L)
                .contextTokens(WorkerState.ContextTokens.NOT_MEASURED)
                .worktreeMark(null)
                .build();
        generations.put(generation.generationId(), generation);
        return generation;
    }

    /**
     * Counts a launch the runtime refused before its spawn into the role's start-failure guard where the specification
     * counts it: the four enrolment-store refusals the Store Schemas part names — {@code harness_store_unreadable},
     * {@code harness_tampered}, {@code harness_version_unsupported} and {@code harness_not_enrolled} — a launch the
     * composer cannot compose, and a start the runner refuses. The update evidence is the one refusal the specification
     * does not count: its act is the verification of the executable, not a new enrolment of a broken store, so it stays
     * a decision about that one start. A refusal the supervisor declines before any launch is no launch refusal at all.
     *
     * @param reason the refusal reason of the launch
     * @return whether the refusal counts as one start failure of the role on the harness
     */
    private static boolean countsAsStartFailure(LaunchRefusal reason) {
        return reason != LaunchRefusal.HARNESS_UPDATE_UNVERIFIED;
    }

    private void refuse(EffectiveRole role, String generationId, HarnessVerification.Refused refused) {
        environment.starter().refuseWorker(WorkerStart.Refusal.builder()
                .generationId(generationId)
                .role(role)
                .enrolment(environment.enrolment())
                .scope(new JobRecord.Scope(JobRecord.Scope.Type.PROJECT, null))
                .budgetKey(budgetKey(role))
                .budgetSeconds(role.artifact().budgetFloorSeconds())
                .budgetDerivation(floorDerivation())
                .workerBlock(CanonicalJson.write(
                        LaunchComposer.refusalBlock(role, generationId, environment.clock().instant())))
                .reason(refused.reason())
                .detail(refused.detail())
                .build());
    }

    private LaunchRequest launchRequest(EffectiveRole role, WorkerStartRequest request, HeadlessBlock block,
            HarnessEnrolment enrolment, String generationId) {
        return LaunchRequest.builder()
                .role(role)
                .block(block)
                .enrolment(enrolment)
                .generationId(generationId)
                .runtimeHome(environment.runtimeHome())
                .jobPrivateDirectory(environment.base().resolve(RUN).resolve(JOBS).resolve(generationId))
                .workdirKind(request.workdirKind())
                .planId(request.planId())
                .workdir(request.workdir())
                .relaySocket(environment.relaySocket())
                .shimParagraph(environment.shimParagraph())
                .skills(environment.skills())
                .fresh(role.form() == RoleForm.FRESH)
                .writesFreeText(request.writesFreeText())
                .stateSkills(request.stateSkills())
                .domainSkills(request.domainSkills())
                .envelope(request.envelope())
                .inBandBoundChars(NO_IN_BAND_BOUND)
                .zeroToolPrompt(request.zeroToolPrompt())
                .resumeSessionId(request.resumeSessionId())
                .clock(environment.clock())
                .build();
    }

    /**
     * @return the requests the role's form and count admit, in request order: one per queued task for a fresh role, one
     *         per working directory for a warm role
     */
    private synchronized List<WorkerStartRequest> admitted(EffectiveRole role, List<WorkerStartRequest> requests) {
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
        return Collections.nCopies(requests.size(), new StartOutcome.Declined(reason, null));
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
    private record WorkingDirectory(LaunchRequest.WorkdirKind kind, @Nullable String planId, Path path) {
    }
}
