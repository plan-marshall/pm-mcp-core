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
 * The launch of a worker, the job of kind {@code llm} (PM-WORK-1, PM-WORK-3, PM-SKILL-4, PM-SEC-5, PM-EXEC-1): the
 * composed argument vector, environment, working directory and per-call MCP configuration
 * ({@link de.planmarshall.runtime.launch.LaunchComposer}), the launch prompt's shared prefix and per-task skills
 * ({@link de.planmarshall.runtime.launch.PromptComposer}), the writing skill derived from the link schemas
 * ({@link de.planmarshall.runtime.launch.FreeTextDerivation}), the job token and generation
 * ({@link de.planmarshall.runtime.launch.JobTokenStore}), and the harness enrolment store with its launch-time
 * verification and self-update ({@link de.planmarshall.runtime.launch.HarnessEnrolmentStore},
 * {@link de.planmarshall.runtime.launch.HarnessVerifier}). An entry of the store is either a serving program's or a
 * remote endpoint's, which records the origin and the credential key instead of a path, a digest and a version
 * ({@link de.planmarshall.runtime.launch.HarnessEnrolment.Binding}, ADR runtime-endpoint/005).
 * <p>
 * The package composes the launch only: it never starts a process of its own, and it keeps no session of a harness.
 * A resumed generation is parameterised through {@link de.planmarshall.runtime.launch.LaunchRequest#resumeSessionId()},
 * and the question-continuation package launches it.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/model-work/04-worker-launch.adoc}</li>
 * <li>{@code doc/specification/store-schemas/11-enrolment-store.adoc} (Harness Enrolment Store)</li>
 * <li>{@code doc/specification/store-schemas/10-job-and-lesson-records.adoc} (Job Record)</li>
 * <li>{@code doc/specification/client-profiles/01-profile-schema.adoc} (Profile Schema, Update Evidence)</li>
 * </ul>
 */
@NullMarked
package de.planmarshall.runtime.launch;

import org.jspecify.annotations.NullMarked;
