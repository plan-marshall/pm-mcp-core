/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.util.Objects;

/**
 * The outcome of the verify-not-resolve pipeline for one job (PM-EXEC-2): the job is either {@link Verified} or
 * {@link Refused}. A refusal is a value, never an exception.
 *
 * @since 0.1
 */
public sealed interface JobVerification permits JobVerification.Verified, JobVerification.Refused {

    /**
     * Every stage passed; the job may be launched as verified.
     *
     * @param job the verified job, never {@code null}
     * @since 0.1
     */
    record Verified(VerifiedJob job) implements JobVerification {

        /**
         * @param job the verified job
         */
        public Verified {
            Objects.requireNonNull(job, "job");
        }
    }

    /**
     * A stage refused the job; no later stage ran and nothing is launched.
     *
     * @param refusal why the job is refused, never {@code null}
     * @since 0.1
     */
    record Refused(JobRefusal refusal) implements JobVerification {

        /**
         * @param refusal why the job is refused
         */
        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
