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

/**
 * The provenance of a plan instance version: the mark that this version passed every check of
 * {@link PlanInstanceComposer} (PM-IMPL-5, Workflow DSL § Versions &amp; Composition Events, item 3).
 * <p>
 * Only the composer creates one, and only inside this package: the constructor is private and the factory is
 * package-private, so no caller of another package or module can build a version the store would accept. The store
 * writes a version only when it carries this mark, so {@link PlanInstanceStore} never writes a version built elsewhere,
 * beside its checks of the version, the header and the composition record.
 *
 * @param version the version the composer verified
 * @since 0.1
 */
public final class Verified {

    private final int version;

    private Verified(int version) {
        this.version = version;
    }

    /**
     * @param version the version the composer verified
     * @return the mark
     * @throws IllegalArgumentException if the version is below {@code 1}
     */
    static Verified of(int version) {
        if (version < 1) {
            throw new IllegalArgumentException("the instance version %d is not from 1".formatted(version));
        }
        return new Verified(version);
    }

    /** @return the version the composer verified */
    public int version() {
        return version;
    }
}
