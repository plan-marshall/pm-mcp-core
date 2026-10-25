/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

/**
 * The outcome of the launch-time verification of an enrolled harness.
 *
 * @since 0.1
 */
public sealed interface HarnessVerification permits HarnessVerification.Accepted, HarnessVerification.Refused {

    /**
     * The build that may host the worker.
     *
     * @param enrolment the enrolment of the verified build
     * @since 0.1
     */
    record Accepted(HarnessEnrolment enrolment) implements HarnessVerification {
    }

    /**
     * The refusal of the launch before the spawn.
     *
     * @param reason why the launch is refused
     * @param detail the detail both clients show beside the reason
     * @since 0.1
     */
    record Refused(LaunchRefusal reason, String detail) implements HarnessVerification {
    }
}
