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

/**
 * The one call a model holds open while the server waits (PM-WF-8, PM-IMPL-4): a worker's {@code pm_wait} and a
 * running {@code pm_build}. No other call is held by a model; the server selects the wait for either from the calling
 * connection's client profile and the request's progress token ({@link WaitSelection}).
 *
 * @since 0.1
 */
public enum ModelCall {

    /** A worker's {@code pm_wait}: it answers an offered task, {@code wait_again} or {@code end}. */
    PM_WAIT("pm_wait"),

    /** A running {@code pm_build}: it answers the build's result, or the running build with its {@code job_id}. */
    PM_BUILD("pm_build");

    private final String wireName;

    ModelCall(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the call in a tool result */
    public String wireName() {
        return wireName;
    }
}
