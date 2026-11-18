/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.Objects;

/**
 * The provenance of a plan instance version: the mark that this version passed every check of
 * {@link PlanInstanceComposer} (PM-IMPL-5, Workflow DSL § Versions &amp; Composition Events, item 3).
 * <p>
 * Only the composer creates one, and only inside this package: the constructor is private and the factory is
 * package-private, so no caller of another package or module can build a version the store would accept. The mark
 * names the version and the digest of the text that was composed, so a version whose text was changed after its
 * composition is refused as well: the store writes a version only when the mark is its own and its digest is the
 * text's.
 *
 * @since 0.1
 */
public final class Verified {

    private final int version;

    private final String textDigest;

    private Verified(int version, String textDigest) {
        if (version < 1) {
            throw new IllegalArgumentException("the instance version %d is not from 1".formatted(version));
        }
        if (textDigest.isBlank()) {
            throw new IllegalArgumentException("the mark of version %d carries no text digest".formatted(version));
        }
        this.version = version;
        this.textDigest = textDigest;
    }

    /**
     * @param version    the version the composer verified
     * @param textDigest the SHA-256 of the version's printed text
     * @return the mark
     */
    static Verified of(int version, String textDigest) {
        return new Verified(version, textDigest);
    }

    /** @return the version the composer verified */
    public int version() {
        return version;
    }

    /** @return the SHA-256 of the text the composer printed for the version */
    public String textDigest() {
        return textDigest;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Verified mark && version == mark.version && textDigest.equals(mark.textDigest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, textDigest);
    }

    @Override
    public String toString() {
        return "Verified[version=%d, textDigest=%s]".formatted(version, textDigest);
    }
}
