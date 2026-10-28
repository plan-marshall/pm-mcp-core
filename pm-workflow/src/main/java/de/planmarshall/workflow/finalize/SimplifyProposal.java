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

import java.util.Objects;

/**
 * One simplification the read-only simplify task proposes, {@code simplify-proposal} (Hypermedia Format § Named
 * Parameter Schemas; Phase Workflows § Finalize Step Ordering Bands): a file of the plan footprint, the change, and the
 * rationale. The server records it as a {@link ProposalRecord} and never applies it itself.
 *
 * @param file      the repository-relative path of the plan footprint
 * @param change    what to change, 1 to 4000 characters
 * @param rationale why the change is a simplification, 1 to 2000 characters
 * @since 0.1
 */
public record SimplifyProposal(String file, String change, String rationale) {

    /**
     * @throws IllegalArgumentException if a member is out of its bounds or the path is not repository-relative
     * @throws NullPointerException     if a value is {@code null}
     */
    public SimplifyProposal {
        Objects.requireNonNull(file, "file");
        if (file.isBlank() || file.length() > 512 || file.startsWith("/") || file.contains("..")) {
            throw new IllegalArgumentException("a proposal's file is a repository-relative path: " + file);
        }
        Objects.requireNonNull(change, "change");
        if (change.isEmpty() || change.length() > 4000) {
            throw new IllegalArgumentException("a proposal's change is 1 to 4000 characters: " + change.length());
        }
        Objects.requireNonNull(rationale, "rationale");
        if (rationale.isEmpty() || rationale.length() > 2000) {
            throw new IllegalArgumentException("a proposal's rationale is 1 to 2000 characters: " + rationale.length());
        }
    }
}
