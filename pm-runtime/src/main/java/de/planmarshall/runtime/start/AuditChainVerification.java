/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import java.util.Objects;

/**
 * The audit chain verification of the bounded verification, step 4 of the {@link StartupSequence}
 * (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Startup Sequence, step 4.1; PM-ARCH-7, PM-SEC-6).
 * The audit trail fills this seam: it verifies the HMAC-anchored head {@code logs/audit.head}, the rotation index and
 * the {@code prev_hash} chain of {@code logs/audit.jsonl} from its last checkpoint.
 * <p>
 * The verification is incremental: its cost does not grow with the number of projects, plans or jobs, and a full
 * verification is the operator command {@code pm-operator audit verify}. It never repairs the chain and never stops
 * the start; an implementation reports a chain it cannot read as {@link Outcome.Broken} or
 * {@link Outcome.NotVerified} instead of throwing.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface AuditChainVerification {

    /** The verification while no audit chain exists: it answers {@link Outcome.NotVerified} for every start. */
    AuditChainVerification NONE = () -> new Outcome.NotVerified("no audit chain");

    /**
     * Verifies the audit chain from its last checkpoint.
     *
     * @return the outcome of the verification
     */
    Outcome verify();

    /**
     * The outcome of an audit chain verification.
     */
    sealed interface Outcome {

        /** The chain is intact from its last checkpoint. */
        record Intact() implements Outcome {
        }

        /**
         * The chain is broken; it is reported and recorded, never repaired.
         *
         * @param diagnostic what is broken, naming the file and the position, never a key or a hash secret
         */
        record Broken(String diagnostic) implements Outcome {

            /**
             * @throws NullPointerException if the diagnostic is {@code null}
             */
            public Broken {
                Objects.requireNonNull(diagnostic, "diagnostic");
            }
        }

        /**
         * The chain was not verified, for example because no chain exists yet.
         *
         * @param reason why the chain was not verified
         */
        record NotVerified(String reason) implements Outcome {

            /**
             * @throws NullPointerException if the reason is {@code null}
             */
            public NotVerified {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
