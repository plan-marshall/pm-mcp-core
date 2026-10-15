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
import java.util.Optional;

import de.planmarshall.workflow.ast.UnitOrigin;

/**
 * A unit a plan instance names by digest, a template or a sub-unit (PM-IMPL-5): {@code unit_ref} of the instance
 * header. The digest is the SHA-256 of the unit text, as 64 lowercase hexadecimal characters.
 *
 * @param unit   the unit id
 * @param file   the unit file, if known
 * @param digest the SHA-256 of the unit text
 * @param origin where the unit comes from
 * @since 0.1
 */
public record UnitReference(String unit, Optional<String> file, String digest, UnitOrigin origin) {

    /**
     * Refuses a missing component; an absent file is an empty {@link Optional}.
     *
     * @param unit   the unit id
     * @param file   the unit file, if known
     * @param digest the SHA-256 of the unit text
     * @param origin where the unit comes from
     */
    public UnitReference {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(origin, "origin");
    }

    /**
     * @param origin where a unit comes from
     * @return the value of {@code origin} of a unit reference in the instance header
     */
    static String wireName(UnitOrigin origin) {
        return switch (origin) {
            case DEFAULT -> "default";
            case PROJECT -> "project";
        };
    }
}
