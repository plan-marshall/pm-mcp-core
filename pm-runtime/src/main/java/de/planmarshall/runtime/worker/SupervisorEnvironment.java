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
import java.time.InstantSource;
import java.util.Objects;

import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.runtime.job.SubprocessJobRunner;
import de.planmarshall.runtime.job.WorkerStart;
import de.planmarshall.runtime.launch.HarnessVerifier;
import de.planmarshall.runtime.launch.JobTokenStore;
import de.planmarshall.runtime.launch.LaunchComposer;
import de.planmarshall.runtime.launch.SkillSource;

/**
 * The collaborators and installation values one supervisor is built with (PM-WORK-3, PM-ARCH-8): the project it serves,
 * the runtime installation it launches from, the landed contracts it reads and the two seams the supervision adds.
 * <p>
 * Every collaborator is the landed type of its package — {@link HarnessVerifier}, {@link LaunchComposer},
 * {@link SubprocessJobRunner}, {@link JobTokenStore}, {@link SkillSource}, {@link LoadedProfile},
 * {@link AuditSink} — and no port stands between the supervisor and them: the start path calls the runner through
 * {@link WorkerStarter#over(SubprocessJobRunner)}, the one adapter that forwards the landed start and refusal calls. The
 * two further seams are the start-failure guard and the verification-record gate: they are consulted before every start,
 * and their implementations live beside the supervisor ({@code StartFailureGuard}, {@code ProfileWorkerEligibility}).
 * The model slots are reached through {@link ModelSlots}, whose production adapter delegates to the landed
 * {@link de.planmarshall.runtime.launch.ModelSlotScheduler}.
 *
 * @param projectId       the enrolment identifier of the project the supervisor serves
 * @param enrolment       the verified enrolment of that project
 * @param base            the runtime base, {@code <PM_MCP_BASE>}, which holds the job-private directories below
 *                        {@code run/jobs/}
 * @param home            the user home the harness profiles' {@code ~} stands for
 * @param runtimeHome     the runtime's installation home, which holds the {@code pm-mcp} binary
 * @param relaySocket     the daemon's socket the worker's relay connects to
 * @param shimParagraph   the paragraph of the shim skill of the client bundle
 * @param skills          where the launch prompt's skills are read
 * @param profile         the effective client profile of the worker's harness
 * @param harnessVerifier the launch-time verification of an enrolled harness
 * @param composer        the composition of one worker launch
 * @param starter         the job runtime that starts and settles a worker
 * @param jobTokens       the registry of the job tokens minted at the spawns
 * @param slots           the admission of the machine model slots
 * @param protocol        the module-boundary contract to the task protocol
 * @param audit           where the supervisor hands over its audit records
 * @param clock           the clock of the spawn instants and of every deadline
 * @param timers          the timers the deadlines and the probe backoff use
 * @param startGuard      the start-failure guard of the role on its harness
 * @param eligibilityGate the verification-record gate of the harness for the role
 * @since 0.1
 */
public record SupervisorEnvironment(String projectId, ProjectEnrolment enrolment, Path base, Path home,
        Path runtimeHome, Path relaySocket, String shimParagraph, SkillSource skills, LoadedProfile profile,
        HarnessVerifier harnessVerifier, LaunchComposer composer, WorkerStarter starter, JobTokenStore jobTokens,
        ModelSlots slots, WorkerProtocol protocol, AuditSink audit, InstantSource clock, WorkerTimers timers,
        StartGuard startGuard, EligibilityGate eligibilityGate) {

    /**
     * @throws NullPointerException if a required component is {@code null}
     */
    public SupervisorEnvironment {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(enrolment, "enrolment");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(runtimeHome, "runtimeHome");
        Objects.requireNonNull(relaySocket, "relaySocket");
        Objects.requireNonNull(shimParagraph, "shimParagraph");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(harnessVerifier, "harnessVerifier");
        Objects.requireNonNull(composer, "composer");
        Objects.requireNonNull(starter, "starter");
        Objects.requireNonNull(jobTokens, "jobTokens");
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(timers, "timers");
        Objects.requireNonNull(startGuard, "startGuard");
        Objects.requireNonNull(eligibilityGate, "eligibilityGate");
    }

    /**
     * The job-runtime seam of the start path: the two calls the supervisor makes to the landed
     * {@link SubprocessJobRunner}. The production adapter is {@link #over(SubprocessJobRunner)}, so the supervisor calls
     * exactly the landed start and refusal paths; a test supplies a starter that records the composed
     * {@link WorkerStart} and the {@link WorkerStart.Refusal} without spawning a process.
     *
     * @since 0.1
     */
    public interface WorkerStarter {

        /**
         * @param request the composed launch and its budget
         * @return the started job, or the refusal of the runner
         */
        JobRunner.StartResult startWorker(WorkerStart request);

        /**
         * @param refusal the launch the runtime refused before its spawn
         * @return the id of the refused job
         */
        JobId refuseWorker(WorkerStart.Refusal refusal);

        /**
         * @param runner the landed job runtime
         * @return the starter the supervisor uses in production
         * @throws NullPointerException if the runner is {@code null}
         */
        static WorkerStarter over(SubprocessJobRunner runner) {
            Objects.requireNonNull(runner, "runner");
            return new WorkerStarter() {

                @Override
                public JobRunner.StartResult startWorker(WorkerStart request) {
                    return runner.startWorker(request);
                }

                @Override
                public JobId refuseWorker(WorkerStart.Refusal refusal) {
                    return runner.refuseWorker(refusal);
                }
            };
        }
    }

    /**
     * The start-failure guard (PM-WORK-3, Start-Failure Guard): whether the guard of one role on one harness holds, so
     * no worker of it may be started there.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface StartGuard {

        /**
         * @param role    the role
         * @param harness the client id of the harness
         * @return whether the guard holds
         */
        boolean holds(String role, String harness);

        /**
         * @return a guard that holds for no role: the supervisor's behaviour before a guard is wired
         */
        static StartGuard open() {
            return (role, harness) -> false;
        }
    }

    /**
     * The verification-record gate (PM-CLIENT-5, the load-time stage): whether the harness may host a worker of one
     * role, decided from the harness's client profile, the runtime platform and the profile verification record before
     * every start.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface EligibilityGate {

        /**
         * @param role    the role
         * @param harness the client id of the harness
         * @return whether the harness admits a worker of the role
         */
        boolean admits(String role, String harness);

        /**
         * @return a gate that admits every role: the supervisor's behaviour before the gate is wired
         */
        static EligibilityGate open() {
            return (role, harness) -> true;
        }
    }
}
