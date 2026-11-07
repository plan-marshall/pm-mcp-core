/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.test;

import java.util.List;

import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.runtime.job.JobRequest.HostToolCall;

/**
 * Host tool calls for the tests of the job pipeline: the call of the enrolled host tool {@code git} with the arguments
 * a test gives, shared so that no test class copies it.
 */
public final class HostToolCalls {

    private HostToolCalls() {
    }

    /**
     * @param arguments the arguments of the call, in their order
     * @return a host tool call of {@code git} with these arguments
     */
    public static HostToolCall git(String... arguments) {
        return new HostToolCall(HostToolName.GIT, List.of(arguments));
    }
}
