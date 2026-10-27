/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import java.util.List;
import java.util.Objects;

/**
 * The residency of one skill of a task's required set (Model Work § Navigation Figures, § Skill Delivery Record &amp;
 * Re-Delivery): the bytes delivered in-band at the launch and the sections the worker read later on demand.
 *
 * @param uri             the skill URI
 * @param inBandBytes     the bytes delivered in-band for this generation
 * @param sectionsOnDemand the sections read through the skill tool, in the order they were read
 * @since 0.1
 */
public record SkillResidency(String uri, long inBandBytes, List<String> sectionsOnDemand) {

    /**
     * @throws NullPointerException     if the URI or the list is {@code null}
     * @throws IllegalArgumentException if the URI is blank or a byte count is negative
     */
    public SkillResidency {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(sectionsOnDemand, "sectionsOnDemand");
        if (uri.isBlank()) {
            throw new IllegalArgumentException("A residency names its skill");
        }
        if (inBandBytes < 0) {
            throw new IllegalArgumentException("A delivered byte count is not negative: " + inBandBytes);
        }
        sectionsOnDemand = List.copyOf(sectionsOnDemand);
    }
}
