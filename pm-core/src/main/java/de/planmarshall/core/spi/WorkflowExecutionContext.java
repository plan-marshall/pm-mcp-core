/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.util.Optional;

/**
 * What the engine hands a primitive for one execution (PM-IMPL-5). A primitive is stateless; whatever it needs to
 * know about the call it takes from here and from its parameter record.
 * <p>
 * The context names the scope the call addresses (PM-ARCH-4) and nothing else: it reads and writes no store.
 *
 * @since 0.1
 */
public interface WorkflowExecutionContext {

    /**
     * @return the identifier of the plan or epic the call addresses; empty for the workspace scope of the project,
     *         which has no identifier of its own. The reserved value a caller addresses the workspace scope with is
     *         never returned, so it cannot be taken for the identifier of a plan.
     */
    Optional<String> scopeId();
}
