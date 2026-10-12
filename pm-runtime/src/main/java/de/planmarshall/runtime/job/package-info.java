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
 * of the build verbs ({@code DefaultTemplates}). Launching the verified job through {@code pm-exec} and its
 * confinement and environment belong to the job runner packages.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Verify-Not-Resolve Execution
 * Pipeline).
 *
 * @since 0.1
 */
package de.planmarshall.runtime.job;
