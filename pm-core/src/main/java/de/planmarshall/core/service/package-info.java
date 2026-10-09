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
 * The service interfaces the foundation declares and {@code pm-runtime} implements: the job runner, the secret
 * source and plan locking. The engine reaches the runtime through them alone.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/module-structure.adoc} (Modules, {@code pm-core})</li>
 * <li>{@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc}</li>
 * </ul>
 */
package de.planmarshall.core.service;
