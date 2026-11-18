/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The consents one onboarding run already answered {@code no} (State Catalogue § Project Onboarding, {@code
 * project.run-consent}): a refused consent of the checkout's scripts is not asked again in that run, and the state goes
 * on without the scripts, what needs them recorded as unknown. A later run holds a fresh value and asks again.
 * <p>
 * The value records the answer of the run, never a store entry: a {@code no} records nothing.
 *
 * @param declined the kinds answered {@code no} in this run
 * @since 0.1
 */
public record RunConsents(Set<ConsentKind> declined) {

    /** Keeps the set unmodifiable. */
    public RunConsents {
        declined = Set.copyOf(declined);
    }

    /**
     * @return the answers of a run that answered nothing yet
     */
    public static RunConsents fresh() {
        return new RunConsents(Set.of());
    }

    /**
     * Records one answer of the run.
     *
     * @param kind the kind answered {@code no}
     * @return the answers including this one
     * @throws NullPointerException if the kind is {@code null}
     */
    public RunConsents declined(ConsentKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (declined.contains(kind)) {
            return this;
        }
        var next = new HashSet<>(declined);
        next.add(kind);
        return new RunConsents(next);
    }

    /**
     * @param kind a consent kind
     * @return whether the run still asks the operator for this kind; {@code false} after a {@code no} in this run
     * @throws NullPointerException if the kind is {@code null}
     */
    public boolean asksAgain(ConsentKind kind) {
        return !declined.contains(Objects.requireNonNull(kind, "kind"));
    }
}
