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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The intent of an epic (Store Schemas § Epic Header; {@code epic-intent}): what the epic is for, the checks that say
 * it is done, what it deliberately does not do, and what is not yet specified.
 * <p>
 * A plan the epic started seeds the required claims of its intent record from this: the row's own {@code done_when} and
 * each of the epic's non-goals must appear among the record's {@code source_claims} by their exact text, and the
 * task's facts carry them as {@code facts.required_claims} (Phase Workflows § The Intent Record, server check 6).
 *
 * @param destination      what the epic is for
 * @param doneWhen         the three to six checks that say the epic is done
 * @param nonGoals         what the epic deliberately does not do
 * @param notYetSpecified  questions that can be stated but are not yet answered
 * @since 0.1
 */
public record EpicIntent(String destination, List<String> doneWhen, List<String> nonGoals,
List<String> notYetSpecified) {

    /**
     * @throws IllegalArgumentException if a member is out of its bounds
     * @throws NullPointerException     if a value is {@code null}
     */
    public EpicIntent {
        Objects.requireNonNull(destination, "destination");
        if (destination.length() < 20 || destination.length() > 1000) {
            throw new IllegalArgumentException("a destination is 20 to 1000 characters: " + destination.length());
        }
        doneWhen = bounded(doneWhen, "done_when", 3, 6, 10, 300);
        nonGoals = bounded(nonGoals, "non_goals", 0, 30, 10, 500);
        notYetSpecified = bounded(notYetSpecified, "not_yet_specified", 0, 30, 10, 500);
    }

    /**
     * @param rowDoneWhen the {@code done_when} of the epic's plan row
     * @return the claims a plan the epic started must land, the row's check last
     */
    public List<String> requiredClaims(String rowDoneWhen) {
        Objects.requireNonNull(rowDoneWhen, "rowDoneWhen");
        var claims = new ArrayList<String>(doneWhen);
        claims.add(rowDoneWhen);
        claims.addAll(nonGoals);
        return List.copyOf(claims);
    }

    /**
     * @param rowDoneWhen the {@code done_when} of the epic's plan row
     * @return the required claims, as the task's {@code facts.required_claims} carries them
     */
    public List<String> factsRequiredClaims(String rowDoneWhen) {
        return requiredClaims(rowDoneWhen);
    }

    private static List<String> bounded(List<String> values, String member, int minItems, int maxItems, int min,
            int max) {
        Objects.requireNonNull(values, member);
        if (values.size() < minItems || values.size() > maxItems) {
            throw new IllegalArgumentException(
                    "%s must hold %d to %d items: %d".formatted(member, minItems, maxItems, values.size()));
        }
        for (var value : values) {
            Objects.requireNonNull(value, member + "[]");
            if (value.length() < min || value.length() > max) {
                throw new IllegalArgumentException(
                        "an item of %s is %d to %d characters: %d".formatted(member, min, max, value.length()));
            }
        }
        return List.copyOf(values);
    }
}
