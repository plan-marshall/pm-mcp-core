/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The job runtime of a project's process jobs (PM-EXEC-1 to PM-EXEC-5, PM-ARCH-7, PM-SEC-2): the verification of a
 * job, its launch through {@code pm-exec}, its job log, its termination, the derivation of its fate and its
 * settlement, run by {@link de.planmarshall.runtime.job.SubprocessJobRunner}.
 * <h2>Verification</h2>
 * The verify-not-resolve pipeline of a job (PM-EXEC-1, PM-EXEC-2, PM-EXEC-3, PM-SEC-2): the deterministic check that
 * turns a {@link de.planmarshall.runtime.job.JobRequest} into a
 * {@link de.planmarshall.runtime.job.JobVerification}, either a verified job whose working directory and absolute
 * argument vector come from the verified enrolment, or a refusal. Nothing is resolved at launch: no executable is
 * looked up through {@code PATH}, no shebang decides an interpreter, no template is read from what a model can write.
 * <p>
 * {@link de.planmarshall.runtime.job.VerifyNotResolvePipeline} runs four stages in this order, and the first stage that
 * fails decides; a later stage never runs:
 * <ol>
 * <li>the enrolment check: the HMAC, digest and per-use checks of the project's enrolment file
 * ({@code enrolment_tampered}, {@code enrolment_insecure}, {@code enrolment_invalid}, {@code not_enrolled});</li>
 * <li>the working directory: confined to the enrolled repository root or a live linked worktree of it
 * ({@code path_traversal_rejected});</li>
 * <li>the executable and its digest, for a wrapper job the wrapper and its pinned files
 * ({@code executable_not_enrolled}, {@code executable_digest_mismatch}, {@code build_config_changed});</li>
 * <li>the argument check, last ({@code verb_not_supported}, {@code argument_rejected}); it also refuses a host tool
 * call that names an enrolled interpreter (the {@code python3} of a project script or of {@code pw}, the {@code ruby}
 * of a script host tool), which runs only through what it interprets.</li>
 * </ol>
 * <p>
 * A refusal is a value ({@link de.planmarshall.runtime.job.JobRefusal}), never an exception, and is logged once at
 * WARN. A fault of the key source, of the lock order or of the file system passes through as the exception it is.
 * <p>
 * The caller supplies what this package does not determine: the live linked worktrees of the project, which it has
 * verified, the module set of the project discovered for the {@code {module}} placeholder, and the default templates
 * of the build verbs ({@code DefaultTemplates}).
 * <h2>The runner</h2>
 * {@link de.planmarshall.runtime.job.SubprocessJobRunner} implements the service
 * {@link de.planmarshall.core.service.JobRunner} and the engine port {@link de.planmarshall.core.spi.JobStarter}. It
 * verifies a job in a transaction of its own and closes it before the launch, so no caller holds the enrolment lock
 * while a job is launched or awaited. The job record ({@link de.planmarshall.core.store.JobRecordStore}) is written at
 * its fixed points: a refused job in the one write of its fate {@code refused}; otherwise the write-ahead record
 * ({@code running}, {@code pid: null}) before the launch, the post-spawn record with the PID and the process start of
 * the launch report ({@link de.planmarshall.runtime.job.LaunchReport}) when the report arrives within the bounded
 * wait (otherwise the job keeps the write-ahead record), and the fate.
 * The budget of a job ({@link de.planmarshall.runtime.job.JobBudget}) starts at its spawn.
 * <p>
 * {@link de.planmarshall.runtime.job.PmExecLauncher} starts the verified argument vector through the launcher
 * {@code pm-exec}, which makes the job the leader of its own process group; its output goes to the job log
 * ({@link de.planmarshall.runtime.job.JobLog}), which is readable while the job runs and whose launch report is
 * stripped once.
 * <h2>Termination and settlement</h2>
 * A cancel names its cause, and the cause or the expiry of the budget is carried out by
 * {@link de.planmarshall.runtime.job.TerminationProtocol} against the job's process group: {@code SIGTERM}, the grace
 * period, {@code SIGKILL} when a member lives, and the cancel before the group exists. Every signal goes through
 * {@link de.planmarshall.runtime.job.ProcessGroupSignals}, the lowest signalling primitive. The cancel is answered
 * {@code true} exactly when its cause is the one the settlement reads: the runner closes the cancel door of the job when
 * it observes the end of the process, and a cancel after that is answered {@code false}. Once the process has ended,
 * {@link de.planmarshall.runtime.job.OutcomeDerivation} derives the fate from what the runtime observed
 * ({@link de.planmarshall.runtime.job.JobEvidence}), never from what the job reports about itself, and
 * {@link de.planmarshall.runtime.job.JobSettlement} writes it in the fixed write order: the fate first, the slot release
 * after it, the steps whose stores do not exist yet as named seams.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Verify-Not-Resolve Execution
 * Pipeline; Subprocess Lifecycle & Process-Group Isolation; Process Termination & Cancellation Protocol; Outcome
 * Derivation); {@code doc/specification/store-schemas/10-job-and-lesson-records.adoc} (Job Record).
 *
 * @since 0.1
 */
package de.planmarshall.runtime.job;
