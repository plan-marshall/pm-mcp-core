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
 * The JSON and JSONL state stores: the {@code format_version} envelopes, the digests, the store layout of
 * workspace, project and machine, and the global lock order.
 * <p>
 * The package also holds the path confinement to the workspace root and its worktrees (PM-SEC-2):
 * {@link de.planmarshall.core.store.PathConfinement} accepts a project path only when its canonical form lies
 * inside the enrolled repository root or inside a live linked git worktree of it
 * ({@link de.planmarshall.core.store.WorkspaceRoots}), and answers with a
 * {@link de.planmarshall.core.store.ConfinementOutcome}. The machine files below the instance directory are
 * outside this rule and have their own.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/store-schemas.adoc}</li>
 * <li>{@code doc/specification/filesystem-layout.adoc}</li>
 * </ul>
 */
package de.planmarshall.core.store;
