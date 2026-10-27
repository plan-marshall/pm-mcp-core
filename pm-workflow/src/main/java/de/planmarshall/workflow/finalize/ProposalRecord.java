/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One recorded proposal of a {@code finalize.simplify} firing (Phase Workflows § Finalize Step Ordering Bands): the
 * session identifier {@code SP-<n>}, the proposal, and whether the {@code finalize.simplify-apply} task accepted or
 * declined it. A proposal is never prose and is never applied by the server.
 *
 * @param proposalId the identifier {@code SP-<n>}, never reused in the plan
 * @param proposal   the model's proposal
 * @param disposition whether it is open, accepted or declined
 * @since 0.1
 */
public record ProposalRecord(String proposalId, SimplifyProposal proposal, Disposition disposition) {

    /** The pattern of a proposal identifier. */
    public static final Pattern ID = Pattern.compile("SP-[0-9]+");

    /**
     * @throws IllegalArgumentException if the identifier is malformed or the disposition is missing
     * @throws NullPointerException     if a value is {@code null}
     */
    public ProposalRecord {
        Objects.requireNonNull(proposalId, "proposalId");
        if (!ID.matcher(proposalId).matches()) {
            throw new IllegalArgumentException("a proposal id is SP-<n>: " + proposalId);
        }
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(disposition, "disposition");
    }

    /**
     * @param disposition the new disposition
     * @return the record with the disposition
     */
    public ProposalRecord with(Disposition disposition) {
        return new ProposalRecord(proposalId, proposal, disposition);
    }

    /** The disposition of a proposal. */
    public enum Disposition {
        /** The apply task has not decided it yet. */
        OPEN("open"),
        /** The apply task applied it. */
        ACCEPTED("accepted"),
        /** The apply task did not apply it. */
        DECLINED("declined");

        private final String wireName;

        Disposition(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a step record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a step record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Disposition> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }
}
