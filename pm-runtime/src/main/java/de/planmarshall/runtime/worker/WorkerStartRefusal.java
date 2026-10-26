/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

/**
 * Why the supervisor declined a start itself (PM-WORK-3, Supervision &amp; Recycling). The set is closed, and it is
 * disjoint from the launch's own refusals ({@link de.planmarshall.runtime.launch.LaunchRefusal}), which the runtime
 * records on the refused job; a declined start leaves the queued task where it is.
 *
 * @since 0.1
 */
public enum WorkerStartRefusal {

    /** The start-failure guard of the role on the harness holds. */
    ROLE_GUARD_HELD("role_guard_held", "the start-failure guard of the role on the harness holds"),

    /** The verification-record gate of the harness does not admit workers of the role. */
    HARNESS_INELIGIBLE("harness_ineligible", "the verification record of the harness does not admit the role"),

    /** No model slot is free for the worker, or its plan's writer worker holds the slot. */
    NO_MODEL_SLOT("no_model_slot", "no model slot is free for the worker"),

    /** The runtime refused the launch before its spawn. */
    LAUNCH_REFUSED("launch_refused", "the runtime refused the launch before its spawn");

    private final String code;
    private final String reason;

    WorkerStartRefusal(String code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    /** @return the reason code, as a report carries it */
    public String code() {
        return code;
    }

    /** @return the reason text a report shows */
    public String reason() {
        return reason;
    }
}
