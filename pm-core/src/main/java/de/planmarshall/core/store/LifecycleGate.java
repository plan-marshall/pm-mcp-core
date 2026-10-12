/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.util.Objects;

/**
 * The lifecycle gate of the release (PM-MIG-1): whether a project may create a plan or an epic.
 * <p>
 * The gate is a constant compiled into the release, never a setting: no configuration key, environment variable or
 * file opens it, and an edited enrolment is refused by its integrity check before the gate reads it. It stays closed
 * until the stage that opens it; while it is closed a plan or an epic is created only in a project enrolled as a
 * pilot ({@code pilot: true}), and every other project is refused with {@code lifecycle_closed}. Once it is open,
 * {@code pilot} has no effect and every enrolled project is admitted.
 * <p>
 * A released project ({@code released_at} set, PM-MIG-5) is refused with {@code lifecycle_closed} whatever the gate,
 * until it is enrolled again. The gate decides per creation: clearing {@code pilot} affects only later creations, and
 * the plans and epics that exist continue.
 *
 * @since 0.1
 */
public final class LifecycleGate {

    /** The position of the gate compiled into this release: closed. */
    private static final boolean OPEN_IN_THIS_RELEASE = false;

    /** The gate of this release. */
    public static final LifecycleGate RELEASE = new LifecycleGate(OPEN_IN_THIS_RELEASE);

    private final boolean open;

    /**
     * Only the release constant and the tests of the open position construct a gate.
     *
     * @param open whether the gate is open
     */
    LifecycleGate(boolean open) {
        this.open = open;
    }

    /**
     * What the gate decides for a project.
     *
     * @since 0.1
     */
    public enum Decision {
        /** A plan or an epic may be created in the project. */
        ADMITTED("admitted"),
        /** No plan and no epic may be created in the project. */
        LIFECYCLE_CLOSED("lifecycle_closed");

        private final String code;

        Decision(String code) {
            this.code = code;
        }

        /** @return the name of the decision in a representation */
        public String code() {
            return code;
        }
    }

    /** @return whether the gate is open */
    public boolean isOpen() {
        return open;
    }

    /**
     * @param enrolment the enrolment of the project, read and verified
     * @return whether a plan or an epic may be created in the project
     */
    public Decision decide(ProjectEnrolment enrolment) {
        Objects.requireNonNull(enrolment, "enrolment");
        if (enrolment.isReleased()) {
            return Decision.LIFECYCLE_CLOSED;
        }
        return open || enrolment.pilot() ? Decision.ADMITTED : Decision.LIFECYCLE_CLOSED;
    }
}
