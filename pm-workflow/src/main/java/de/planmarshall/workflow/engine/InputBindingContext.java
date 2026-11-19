/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The binding context of a call: the worktree root, the staging set of the scope, and the configuration value by key.
 * <p>
 * The engine uses it to validate a bound path (confinement and staging-set membership) and to resolve an
 * {@code input_fallback} value (PM-WF-16, Deliverable 3).
 *
 * @since 0.1
 */
public interface InputBindingContext {

    /** @return the worktree root the paths are confined against */
    Path worktreeRoot();

    /** @return the staging set of the scope: the paths that may be bound */
    Set<String> stagingSet();

    /** @return the configuration value for the given key, or empty when no such key exists */
    Optional<String> configValue(String key);
}