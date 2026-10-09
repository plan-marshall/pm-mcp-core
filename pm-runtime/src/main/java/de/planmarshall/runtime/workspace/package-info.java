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
 * The entry through which the file, git and build operations of the daemon obtain a confined path of a project
 * (PM-SEC-2): {@link de.planmarshall.runtime.workspace.WorkspaceConfinement} passes a raw path to the check of
 * {@code pm-core} ({@link de.planmarshall.core.store.PathConfinement}) and answers with its
 * {@link de.planmarshall.core.store.ConfinementOutcome}. A refused path is a return value with the outcome code
 * {@code path_traversal_rejected} and a WARN record; nothing is thrown for it.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/filesystem-layout.adoc}</li>
 * </ul>
 */
package de.planmarshall.runtime.workspace;
