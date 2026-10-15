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

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The verified text of one sub-unit a composed version binds (PM-IMPL-5), handed to {@link PlanInstanceStore} with
 * the version: the store keeps it as {@code mcp/units/<digest>.pmwf} and reads no project copy.
 *
 * @param reference the reference of the unit, its id and the SHA-256 of its text
 * @param text      the verified text of the unit
 * @since 0.1
 */
public record SubUnitText(UnitReference reference, String text) {

    /**
     * Refuses a missing component.
     *
     * @param reference the reference of the unit
     * @param text      the verified text of the unit
     */
    public SubUnitText {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(text, "text");
    }

    /**
     * @return the UTF-8 bytes of the text
     */
    byte[] bytes() {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @return whether the SHA-256 of the text is the digest the reference names
     */
    boolean matchesDigest() {
        return PlanFacts.sha256(bytes()).equals(reference.digest());
    }
}
