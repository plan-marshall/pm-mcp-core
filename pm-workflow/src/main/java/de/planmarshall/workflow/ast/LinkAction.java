/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

/**
 * What a link does (PM-IMPL-5). {@code do <primitive_id>} is the only link action; model work is a {@code job llm}
 * task state, never a link action.
 *
 * @since 0.1
 */
public sealed interface LinkAction permits LinkAction.ExecutePrimitive {

    /**
     * {@code do <primitive_id>}: the link runs the primitive.
     *
     * @param primitiveId the primitive the link runs
     */
    record ExecutePrimitive(String primitiveId) implements LinkAction {
    }
}
