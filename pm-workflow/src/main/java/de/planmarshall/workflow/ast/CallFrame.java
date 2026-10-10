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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One frame of a scope's call stack: the calling state and the sub-workflow it called with its bound arguments
 * (PM-IMPL-5, PM-WF-16). The bound arguments are copied into an unmodifiable map that keeps their order.
 *
 * @param callerWorkflowId the identifier of the calling unit
 * @param callerStateId    the identifier of the calling state
 * @param callerPhase      the phase of the calling state
 * @param subWorkflowId    the identifier of the called sub-workflow
 * @param boundArgs        the bound arguments by parameter name
 * @param pushedAt         when the frame was pushed
 * @since 0.1
 */
public record CallFrame(
String callerWorkflowId,
String callerStateId,
String callerPhase,
String subWorkflowId,
Map<String, CallValue> boundArgs,
String pushedAt) {

    /**
     * Copies the bound arguments into an unmodifiable map that keeps their order.
     *
     * @param callerWorkflowId the identifier of the calling unit
     * @param callerStateId    the identifier of the calling state
     * @param callerPhase      the phase of the calling state
     * @param subWorkflowId    the identifier of the called sub-workflow
     * @param boundArgs        the bound arguments by parameter name
     * @param pushedAt         when the frame was pushed
     */
    public CallFrame {
        boundArgs = Collections.unmodifiableMap(new LinkedHashMap<>(boundArgs));
    }
}
