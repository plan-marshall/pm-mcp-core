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
import java.util.OptionalLong;

/**
 * The audit chain verification of the bounded verification, step 4 of the {@link StartupSequence}
 * (doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Startup Sequence, step 4.1; PM-ARCH-7, PM-SEC-6).
 * The package {@code de.planmarshall.runtime.audit} implements it: it verifies the HMAC-anchored head
 * {@code logs/audit.head}, the rotation index and the {@code prev_hash} chain of {@code logs/audit.jsonl} from its
 * last checkpoint.
 * <p>
 * The verification is incremental: its cost does not grow with the number of projects, plans or jobs, and a full
 * verification is the operator command {@code pm-operator audit verify}. It never repairs the chain and never stops
 * the start; an implementation reports a chain it cannot read as {@link Outcome.Broken} or
 * {@link Outcome.NotVerified} instead of throwing. A broken chain names why it is broken by one {@link BreakReason}
 * of the closed set of the specification (Store Schemas, Security &amp; Transition Audit Log, Hash chain) and, for a
 * broken link, the first broken line.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface AuditChainVerification {

    /**
     * The verification while the assembly wires no audit chain verification: it answers {@link Outcome.NotVerified}
     * for every start.
     */
    AuditChainVerification NONE = () -> new Outcome.NotVerified("no audit chain");

    /**
     * Verifies the audit chain from its last checkpoint.
     *
     * @return the outcome of the verification
     */
    Outcome verify();

    /**
     * Why an audit chain is broken, the closed set of {@code details.reason} of the {@code audit_chain_broken} record.
     */
    enum BreakReason {

        /**
         * A record does not link to the line before it: its {@code prev_hash} is not the SHA-256 of that line, or the
         * line cannot be parsed, as after a removed, reordered or edited record. The only reason with a first broken
         * line.
         */
        LINK_BROKEN("link_broken"),

        /**
         * The current log is missing while the anchored head counts records, or, after a rotation stopped after its
         * rename, the rotated file the head anchors. Any other rotated file the audit index lists and that is gone is
         * not reported: it is accounted for by its index entry as purged.
         */
        MISSING("missing"),

        /** The chain starts over where the anchored head or the audit index says it goes on. */
        RESET("reset"),

        /** The log holds fewer records than the anchored head counts. */
        TRUNCATED("truncated"),

        /** The head does not match the log, its HMAC does not verify, or a non-empty log has no head. */
        HEAD_MISMATCH("head_mismatch");

        private final String wireName;

        BreakReason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the reason in the {@code audit_chain_broken} record */
        public String wireName() {
            return wireName;
        }
    }

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
         * @param reason          why the chain is broken
         * @param firstBrokenLine the first broken line, counted from 1 in the file the diagnostic names (the current
         *                        log, the audit index or a rotated file); present exactly for
         *                        {@link BreakReason#LINK_BROKEN}
         * @param diagnostic      what is broken, naming the file and the position, never a key or a hash secret; it is
         *                        logged, never recorded
         */
        record Broken(BreakReason reason, OptionalLong firstBrokenLine, String diagnostic) implements Outcome {

            /**
             * @throws NullPointerException     if a component is {@code null}
             * @throws IllegalArgumentException if a first broken line is given for a reason other than
             *                                  {@link BreakReason#LINK_BROKEN}, is missing for it, or is below 1
             */
            public Broken {
                Objects.requireNonNull(reason, "reason");
                Objects.requireNonNull(firstBrokenLine, "firstBrokenLine");
                Objects.requireNonNull(diagnostic, "diagnostic");
                if (firstBrokenLine.isPresent() != (reason == BreakReason.LINK_BROKEN)) {
                    throw new IllegalArgumentException(
                            "A first broken line is given exactly for link_broken, not for " + reason.wireName());
                }
                if (firstBrokenLine.isPresent() && firstBrokenLine.getAsLong() < 1) {
                    throw new IllegalArgumentException(
                            "The first broken line is counted from 1: " + firstBrokenLine.getAsLong());
                }
            }

            /**
             * @param firstBrokenLine the first broken line of the log, counted from 1
             * @param diagnostic      what is broken, naming the file and the position
             * @return a chain broken by {@link BreakReason#LINK_BROKEN} at the line
             */
            public static Broken linkBroken(long firstBrokenLine, String diagnostic) {
                return new Broken(BreakReason.LINK_BROKEN, OptionalLong.of(firstBrokenLine), diagnostic);
            }

            /**
             * @param reason     why the chain is broken; never {@link BreakReason#LINK_BROKEN}, which needs a line
             * @param diagnostic what is broken, naming the file and the position
             * @return a chain broken without a first broken line
             * @throws IllegalArgumentException if the reason is {@link BreakReason#LINK_BROKEN}
             */
            public static Broken withoutLine(BreakReason reason, String diagnostic) {
                return new Broken(reason, OptionalLong.empty(), diagnostic);
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
