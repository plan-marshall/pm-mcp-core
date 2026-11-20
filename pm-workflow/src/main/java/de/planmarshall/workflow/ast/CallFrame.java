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
import java.util.Objects;

import de.planmarshall.core.id.CommandId;
import de.planmarshall.core.id.CorrelationId;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One frame of a scope's call stack: the calling state, the sub-workflow it called, the entry of the sub-workflow the
 * call went through, and the bound arguments with their provenance (PM-IMPL-5, PM-WF-16). The bound arguments and the
 * provenance are copied into unmodifiable maps that keep their order.
 *
 * @param callerWorkflowId the identifier of the calling unit
 * @param callerStateId    the identifier of the calling state
 * @param subWorkflowId    the identifier of the called sub-workflow
 * @param entry            the entry of the called sub-workflow the frame was entered through: the entry the call
 *                         names, or the sole entry of the callee when the call names none (PM-WF-16)
 * @param boundArgs        the bound arguments by parameter name
 * @param argSources       the provenance of every bound argument, by parameter name
 * @param commandId        the identity of the command this frame is, {@code null} before the bind draws it
 * @param correlationId    the identity of the engine advance that pushed the frame
 * @param pushedAt         when the frame was pushed
 * @since 0.1
 */
@Builder
public record CallFrame(
String callerWorkflowId,
String callerStateId,
String subWorkflowId,
String entry,
Map<String, CallValue> boundArgs,
Map<String, ArgSource> argSources,
@Nullable CommandId commandId,
@Nullable CorrelationId correlationId,
String pushedAt) {

    /**
     * Copies the bound arguments and their provenance into unmodifiable maps that keep their order.
     *
     * @param callerWorkflowId the identifier of the calling unit
     * @param callerStateId    the identifier of the calling state
     * @param subWorkflowId    the identifier of the called sub-workflow
     * @param entry            the entry of the called sub-workflow the frame was entered through
     * @param boundArgs        the bound arguments by parameter name
     * @param argSources       the provenance of every bound argument, by parameter name
     * @param pushedAt         when the frame was pushed
     */
    public CallFrame {
        Objects.requireNonNull(boundArgs, "boundArgs");
        Objects.requireNonNull(argSources, "argSources");
        boundArgs = Collections.unmodifiableMap(new LinkedHashMap<>(boundArgs));
        argSources = Collections.unmodifiableMap(new LinkedHashMap<>(argSources));
    }
}
